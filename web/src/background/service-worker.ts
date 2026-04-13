import { ConnectionStore, type ConnectionConfig } from "@/shared/storage/connection-store";
import { DeviceStore, type StoredDevice } from "@/shared/storage/device-store";
import { PasteStore } from "@/shared/storage/paste-store";
import { BlobStore } from "@/shared/storage/blob-store";
import { SyncApi } from "@/shared/api/sync";
import { PullApi } from "@/shared/api/pull";
import { CrossPasteHash, CrossPasteJson } from "@/shared/core";
import type { SyncInfo } from "@/shared/models/sync-info";
import { APP_VERSION } from "@/shared/app/version.generated";
import { collectPasteItems } from "@/shared/paste/paste-collector";
import { WsManager } from "@/shared/ws/ws-manager";
import { createWsMessageHandler } from "@/shared/ws/ws-message-handler";
import { WsMessageType, simpleEnvelope } from "@/shared/ws/ws-types";
import type { WsEnvelope } from "@/shared/ws/ws-types";

// ─── Per-device runtime status ──────────────────────────────────────────

const deviceStatuses = new Map<string, "synced" | "error">();

// ─── WebSocket manager (initialized in initialize()) ────────────────────

let wsManager: WsManager | null = null;

// ─── Current connection attempt ─────────────────────────────────────────

let connectingState: {
  host: string;
  port: number;
  targetAppInstanceId: string;
  syncInfo: SyncInfo;
} | null = null;

// ─── Clipboard monitoring ───────────────────────────────────────────────

let offscreenReady = false;

const CLIPBOARD_POLL_INTERVAL_MS = 1000; // 1 second
const STORAGE_KEY_LAST_HASH = "clipboard_lastHash";


async function ensureOffscreen(): Promise<void> {
  if (offscreenReady) return;

  const contexts = await chrome.runtime.getContexts({
    contextTypes: [chrome.runtime.ContextType.OFFSCREEN_DOCUMENT],
  });

  if (contexts.length > 0) {
    offscreenReady = true;
    return;
  }

  await chrome.offscreen.createDocument({
    url: "src/offscreen/offscreen.html",
    reasons: [chrome.offscreen.Reason.CLIPBOARD],
    justification: "Read system clipboard for sync",
  });
  offscreenReady = true;
}

async function getLastHash(): Promise<string> {
  const result = await chrome.storage.session.get(STORAGE_KEY_LAST_HASH);
  return (result[STORAGE_KEY_LAST_HASH] as string) ?? "";
}

async function setLastHash(hash: string): Promise<void> {
  await chrome.storage.session.set({ [STORAGE_KEY_LAST_HASH]: hash });
}

/** Convert a data URL to ArrayBuffer */
function dataUrlToArrayBuffer(dataUrl: string): ArrayBuffer {
  const base64 = dataUrl.split(",")[1] ?? "";
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}

async function pollClipboard(): Promise<void> {
  try {
    await ensureOffscreen();

    const response = await chrome.runtime.sendMessage({ type: "READ_CLIPBOARD" });
    const collected = collectPasteItems(response, CrossPasteHash.hashText);
    if (!collected) return;

    // Deduplicate by appear item hash
    const lastHash = await getLastHash();
    if (collected.hash === lastHash) return;

    await setLastHash(collected.hash);

    // Store file blobs separately, grouped by hash.
    // Most blobs use collected.hash, but image blobs may have their own hash
    // so Desktop can look them up by the PasteFiles item hash.
    if (collected.fileBlobs.length > 0) {
      const byHash = new Map<string, Array<{ name: string; data: ArrayBuffer }>>();
      for (const f of collected.fileBlobs) {
        const h = f.hash ?? collected.hash;
        let group = byHash.get(h);
        if (!group) {
          group = [];
          byHash.set(h, group);
        }
        group.push({ name: f.name, data: dataUrlToArrayBuffer(f.dataUrl) });
      }
      for (const [h, files] of byHash) {
        await BlobStore.putAll(h, files);
      }
    }

    const appInstanceId = await getAppInstanceId();
    const pasteData = {
      id: Date.now(),
      appInstanceId,
      favorite: false,
      pasteAppearItem: collected.pasteAppearItem,
      pasteCollection: collected.pasteCollection,
      pasteType: collected.pasteType,
      source: "Chrome",
      size: collected.size,
      hash: collected.hash,
      pasteState: 1, // LOADED
      receivedAt: Date.now(),
    };

    const newId = await PasteStore.createPasteData(pasteData);
    if (newId !== null) {
      const deleted = await PasteStore.markDeleteSameHash(newId, collected.hash);
      for (const h of deleted) await BlobStore.deleteForPaste(h);
      const evicted = await PasteStore.evictOverLimit();
      for (const h of evicted) await BlobStore.deleteForPaste(h);
      broadcastToSidePanel({ type: "PASTE_UPDATED" });

      // Push via WebSocket to all connected devices
      if (wsManager) {
        let normalizedPayload: Uint8Array;
        try {
          // Round-trip through Kotlin serializer to ensure wire format compatibility
          const normalized = CrossPasteJson.parsePasteData(JSON.stringify(pasteData));
          normalizedPayload = new TextEncoder().encode(normalized);
        } catch (e) {
          console.error("[WS] Failed to normalize pasteData for push:", e);
          return;
        }
        const envelope: WsEnvelope = {
          type: WsMessageType.PASTE_PUSH,
          payload: normalizedPayload,
          encrypted: false,
        };
        const devices = await DeviceStore.getAll();
        for (const device of devices) {
          if (!device.trusted) continue;
          if (wsManager.isConnected(device.targetAppInstanceId)) {
            wsManager.send(device.targetAppInstanceId, envelope).catch(() => {
              // WS send failed, desktop will poll on its own
            });
          }
        }
      }
    }
  } catch {
    offscreenReady = false;
  }
}

function startClipboardPolling(): void {
  async function loop() {
    await pollClipboard();
    setTimeout(loop, CLIPBOARD_POLL_INTERVAL_MS);
  }
  loop();
}

// ─── Identity ───────────────────────────────────────────────────────────

async function getAppInstanceId(): Promise<string> {
  const config = await ConnectionStore.getConfig();
  if (config?.appInstanceId) return config.appInstanceId;

  const newConfig: ConnectionConfig = {
    appInstanceId: crypto.randomUUID(),
    host: "",
    port: 0,
    targetAppInstanceId: "",
    trusted: false,
  };
  await ConnectionStore.saveConfig(newConfig);
  return newConfig.appInstanceId;
}

// ─── Device status helpers ──────────────────────────────────────────────

export interface DeviceWithStatus extends StoredDevice {
  status: "synced" | "error";
}

async function getDevicesWithStatus(): Promise<DeviceWithStatus[]> {
  const devices = await DeviceStore.getAll();
  return devices
    .filter((d) => d.trusted)
    .map((d) => ({
      ...d,
      status: (deviceStatuses.get(d.targetAppInstanceId) ?? "synced") as "synced" | "error",
    }));
}

// ─── Sync & heartbeat ──────────────────────────────────────────────────

async function syncAllDevices(): Promise<void> {
  const appInstanceId = await getAppInstanceId();
  const devices = await DeviceStore.getAll();

  for (const device of devices) {
    if (!device.trusted) continue;
    // Skip HTTP polling for devices with active WebSocket connections
    if (wsManager?.isConnected(device.targetAppInstanceId)) continue;
    try {
      const data = await PullApi.pullPaste({
        host: device.host,
        port: device.port,
        appInstanceId,
        targetAppInstanceId: device.targetAppInstanceId,
      });
      if (data) {
        const newId = await PasteStore.createPasteData(data);
        if (newId !== null) {
          const deleted = await PasteStore.markDeleteSameHash(newId, data.hash);
          for (const h of deleted) await BlobStore.deleteForPaste(h);
          const evicted = await PasteStore.evictOverLimit();
          for (const h of evicted) await BlobStore.deleteForPaste(h);
          broadcastToSidePanel({ type: "PASTE_UPDATED" });
          // Update last hash so polling doesn't re-capture synced content
          await setLastHash(data.hash);
        }
      }
      if (deviceStatuses.get(device.targetAppInstanceId) !== "synced") {
        deviceStatuses.set(device.targetAppInstanceId, "synced");
        broadcastToSidePanel({ type: "DEVICES_CHANGED" });
      }
    } catch {
      // Will retry on next alarm
    }
  }
}

async function sendHeartbeats(): Promise<void> {
  const appInstanceId = await getAppInstanceId();
  const devices = await DeviceStore.getAll();
  let changed = false;

  for (const device of devices) {
    if (!device.trusted) continue;
    // Skip HTTP heartbeat for devices with active WebSocket connections
    if (wsManager?.isConnected(device.targetAppInstanceId)) continue;
    try {
      await SyncApi.heartbeat({
        host: device.host,
        port: device.port,
        appInstanceId,
        targetAppInstanceId: device.targetAppInstanceId,
      });
      if (deviceStatuses.get(device.targetAppInstanceId) !== "synced") {
        deviceStatuses.set(device.targetAppInstanceId, "synced");
        changed = true;
      }
    } catch {
      if (deviceStatuses.get(device.targetAppInstanceId) !== "error") {
        deviceStatuses.set(device.targetAppInstanceId, "error");
        changed = true;
      }
    }
  }

  if (changed) {
    broadcastToSidePanel({ type: "DEVICES_CHANGED" });
  }
}

function startSyncAlarms(): void {
  chrome.alarms.create("sync-paste", { periodInMinutes: 0.5 });
  chrome.alarms.create("heartbeat", { periodInMinutes: 1 });
  chrome.alarms.create("ws-reconnect", { periodInMinutes: 0.5 });
}

function stopSyncAlarms(): void {
  chrome.alarms.clear("sync-paste");
  chrome.alarms.clear("heartbeat");
  chrome.alarms.clear("ws-reconnect");
}

// ─── Migration from legacy single-device format ────────────────────────

async function migrateFromLegacy(): Promise<void> {
  const devices = await DeviceStore.getAll();
  if (devices.length > 0) return;

  const config = await ConnectionStore.getConfig();
  if (!config?.trusted || !config.host || !config.port || !config.targetAppInstanceId) return;

  try {
    const syncInfo = await SyncApi.getSyncInfo({
      host: config.host,
      port: config.port,
      appInstanceId: config.appInstanceId,
    });

    const serverKeys = await ConnectionStore.getServerKeys();

    await DeviceStore.save({
      targetAppInstanceId: config.targetAppInstanceId,
      syncInfo,
      host: config.host,
      port: config.port,
      trusted: true,
      serverKeys: serverKeys ?? undefined,
      addedAt: Date.now(),
    });
  } catch {
    // Device not reachable — user needs to re-pair
  }
}

// ─── Startup ────────────────────────────────────────────────────────────

async function initializeWebSocket(): Promise<void> {
  const appInstanceId = await getAppInstanceId();
  wsManager = new WsManager(appInstanceId);

  const wsMessageHandler = createWsMessageHandler({
    sendToDevice: async (targetId, envelope) => {
      await wsManager?.send(targetId, envelope);
    },
    sendRequest: async (targetId, envelope) => {
      if (!wsManager) throw new Error("WsManager not initialized");
      return wsManager.sendRequest(targetId, envelope);
    },
    updateDeviceStatus: (targetId, status) => {
      if (deviceStatuses.get(targetId) !== status) {
        deviceStatuses.set(targetId, status);
        broadcastToSidePanel({ type: "DEVICES_CHANGED" });
      }
    },
    broadcastToSidePanel,
    getLastHash,
    setLastHash,
    onRemoteRemoveDevice: async (targetId) => {
      wsManager?.disconnectDevice(targetId);
      await DeviceStore.remove(targetId);
      deviceStatuses.delete(targetId);

      const remaining = await DeviceStore.getAll();
      if (!remaining.some((d) => d.trusted)) {
        stopSyncAlarms();
      }

      broadcastToSidePanel({ type: "DEVICES_CHANGED" });
    },
  });

  wsManager.onMessage = (targetId, envelope) => {
    wsMessageHandler.handleMessage(targetId, envelope).catch((e) => {
      console.error(`[WS] Failed to handle message from ${targetId}:`, e);
    });
  };

  wsManager.onStatusChange = (targetId, status) => {
    if (status === "ws_connected") {
      deviceStatuses.set(targetId, "synced");
    }
    broadcastToSidePanel({ type: "DEVICES_CHANGED" });
  };

  await wsManager.connectAllDevices();
}

async function initialize(): Promise<void> {
  await getAppInstanceId();
  await ensureOffscreen();
  startClipboardPolling();

  await migrateFromLegacy();

  const devices = await DeviceStore.getAll();
  const trustedDevices = devices.filter((d) => d.trusted);

  if (trustedDevices.length > 0) {
    trustedDevices.forEach((d) => {
      deviceStatuses.set(d.targetAppInstanceId, "synced");
    });
    startSyncAlarms();
    await initializeWebSocket();
  }
}

chrome.runtime.onInstalled.addListener(() => initialize());
chrome.runtime.onStartup?.addListener(() => initialize());

// ─── Alarm handler ──────────────────────────────────────────────────────

chrome.alarms.onAlarm.addListener(async (alarm) => {
  if (alarm.name === "sync-paste") {
    await syncAllDevices();
  } else if (alarm.name === "heartbeat") {
    await sendHeartbeats();
  } else if (alarm.name === "ws-reconnect") {
    if (wsManager) {
      await wsManager.connectAllDevices();
    }
  }
});

// ─── Broadcast ──────────────────────────────────────────────────────────

function broadcastToSidePanel(message: unknown): void {
  chrome.runtime.sendMessage(message).catch(() => {
    // Side panel not open — ignore
  });
}

// ─── Message handling ───────────────────────────────────────────────────

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message.type === "READ_CLIPBOARD") return false;
  handleMessage(message).then(sendResponse);
  return true;
});

async function handleGetDevices(): Promise<unknown> {
  return { devices: await getDevicesWithStatus() };
}

async function handleConnect(host: string, port: number): Promise<unknown> {
  try {
    const appInstanceId = await getAppInstanceId();
    const config = { host, port, appInstanceId };

    await SyncApi.telnet(config);
    const syncInfo = await SyncApi.getSyncInfo(config);
    const targetAppInstanceId = syncInfo.appInfo.appInstanceId;

    await SyncApi.showToken({ ...config, targetAppInstanceId });

    connectingState = { host, port, targetAppInstanceId, syncInfo };
    return { success: true, syncInfo };
  } catch (e) {
    connectingState = null;
    return { success: false, error: String(e) };
  }
}

async function handlePair(token: number): Promise<unknown> {
  try {
    if (!connectingState) throw new Error("Not connected");
    const appInstanceId = await getAppInstanceId();

    // Build extension's SyncInfo so the server can register this client
    // (Chrome extension can't be discovered via mDNS)
    const chromeVersion = navigator.userAgent.split("Chrome/")[1]?.split(" ")[0] ?? "unknown";
    const extensionSyncInfo: SyncInfo = {
      appInfo: {
        appInstanceId,
        appVersion: APP_VERSION,
        appRevision: "Unknown",
        userName: "Chrome Extension",
      },
      endpointInfo: {
        deviceId: appInstanceId,
        deviceName: "Chrome Extension",
        platform: { name: "ChromeExtension", arch: "web", bitMode: 64, version: chromeVersion },
        hostInfoList: [],
        port: 0,
      },
    };

    const response = await SyncApi.trust(
      {
        host: connectingState.host,
        port: connectingState.port,
        appInstanceId,
        targetAppInstanceId: connectingState.targetAppInstanceId,
      },
      token,
      extensionSyncInfo,
    );

    await DeviceStore.save({
      targetAppInstanceId: connectingState.targetAppInstanceId,
      syncInfo: connectingState.syncInfo,
      host: connectingState.host,
      port: connectingState.port,
      trusted: true,
      serverKeys: {
        signPublicKey: response.pairingResponse.signPublicKey,
        cryptPublicKey: response.pairingResponse.cryptPublicKey,
      },
      addedAt: Date.now(),
    });

    deviceStatuses.set(connectingState.targetAppInstanceId, "synced");

    // Attempt WebSocket upgrade after pairing
    if (!wsManager) {
      await initializeWebSocket();
    } else {
      const device = await DeviceStore.get(connectingState.targetAppInstanceId);
      if (device) await wsManager.connectDevice(device);
    }

    connectingState = null;

    startSyncAlarms();
    broadcastToSidePanel({ type: "DEVICES_CHANGED" });

    return { success: true };
  } catch (e) {
    return { success: false, error: String(e) };
  }
}

async function handleRemoveDevice(targetId: string): Promise<unknown> {
  // Notify desktop before disconnecting so the WS session is still available
  if (wsManager?.isConnected(targetId)) {
    await wsManager.send(targetId, simpleEnvelope(WsMessageType.NOTIFY_REMOVE)).catch(() => {});
  }
  wsManager?.disconnectDevice(targetId);
  await DeviceStore.remove(targetId);
  deviceStatuses.delete(targetId);

  const remaining = await DeviceStore.getAll();
  if (!remaining.some((d) => d.trusted)) {
    stopSyncAlarms();
  }

  broadcastToSidePanel({ type: "DEVICES_CHANGED" });
  return { success: true };
}

async function handleUpdateNote(targetId: string, noteName: string): Promise<unknown> {
  await DeviceStore.updateNote(targetId, noteName);
  broadcastToSidePanel({ type: "DEVICES_CHANGED" });
  return { success: true };
}

async function handleGetPastes(
  offset: number,
  limit: number,
  query: string,
  pasteType: number | null,
): Promise<unknown> {
  const items = (query || pasteType !== null)
    ? await PasteStore.searchItems(query, pasteType, offset, limit)
    : await PasteStore.getItems(offset, limit);
  return { items };
}

async function handleLocalCopy(pasteId: number): Promise<unknown> {
  const hash = await PasteStore.moveToTop(pasteId);
  if (hash) {
    await setLastHash(hash);
    broadcastToSidePanel({ type: "PASTE_UPDATED" });
  }
  return { success: hash !== null };
}

async function handleDeletePaste(pasteId: number): Promise<unknown> {
  const hash = await PasteStore.deleteById(pasteId);
  if (hash !== null) {
    await BlobStore.deleteForPaste(hash);
    await PasteStore.purgeDeleted();
    broadcastToSidePanel({ type: "PASTE_UPDATED" });
  }
  return { success: hash !== null };
}

async function handleMessage(
  message: Record<string, unknown>,
): Promise<unknown> {
  switch (message.type) {
    case "GET_DEVICES": return handleGetDevices();
    case "CONNECT": return handleConnect(message.host as string, message.port as number);
    case "PAIR": return handlePair(message.token as number);
    case "REMOVE_DEVICE": return handleRemoveDevice(message.targetAppInstanceId as string);
    case "UPDATE_NOTE": return handleUpdateNote(message.targetAppInstanceId as string, message.noteName as string);
    case "GET_PASTES": return handleGetPastes(
      (message.offset as number) ?? 0,
      (message.limit as number) ?? 50,
      (message.query as string) ?? "",
      message.pasteType as number | null ?? null,
    );
    case "COPY_ITEM": return { success: true };
    case "LOCAL_COPY": return handleLocalCopy(message.pasteId as number);
    case "DELETE_PASTE": return handleDeletePaste(message.pasteId as number);
    case "GET_WS_STATUS": return { statuses: wsManager?.getConnectionStates() ?? {} };
    default: return { error: "Unknown message type" };
  }
}

chrome.action.onClicked.addListener((tab) => {
  if (tab.id) {
    chrome.sidePanel.open({ tabId: tab.id });
  }
});

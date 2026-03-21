import { ConnectionStore, type ConnectionConfig } from "@/shared/storage/connection-store";
import { DeviceStore, type StoredDevice } from "@/shared/storage/device-store";
import { PasteStore } from "@/shared/storage/paste-store";
import { SyncApi } from "@/shared/api/sync";
import { PullApi } from "@/shared/api/pull";
import { CrossPasteHash } from "@/shared/core";
import type { SyncInfo } from "@/shared/models/sync-info";
import { detectPasteType } from "@/shared/paste/paste-type-detector";

// ─── Per-device runtime status ──────────────────────────────────────────

const deviceStatuses = new Map<string, "synced" | "error">();

// ─── Current connection attempt ─────────────────────────────────────────

let connectingState: {
  host: string;
  port: number;
  targetAppInstanceId: string;
  syncInfo: SyncInfo;
} | null = null;

// ─── Clipboard monitoring ───────────────────────────────────────────────

let offscreenReady = false;

const CLIPBOARD_POLL_ALARM = "clipboard-poll";
const CLIPBOARD_POLL_INTERVAL = 0.05; // ~3 seconds
const STORAGE_KEY_LAST_TEXT = "clipboard_lastText";
const STORAGE_KEY_LAST_IMAGE_HASH = "clipboard_lastImageHash";

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

async function getLastClipboardText(): Promise<string> {
  const result = await chrome.storage.session.get(STORAGE_KEY_LAST_TEXT);
  return (result[STORAGE_KEY_LAST_TEXT] as string) ?? "";
}

async function setLastClipboardText(text: string): Promise<void> {
  await chrome.storage.session.set({ [STORAGE_KEY_LAST_TEXT]: text });
}

async function getLastImageHash(): Promise<string> {
  const result = await chrome.storage.session.get(STORAGE_KEY_LAST_IMAGE_HASH);
  return (result[STORAGE_KEY_LAST_IMAGE_HASH] as string) ?? "";
}

async function setLastImageHash(hash: string): Promise<void> {
  await chrome.storage.session.set({ [STORAGE_KEY_LAST_IMAGE_HASH]: hash });
}

async function pollClipboard(): Promise<void> {
  try {
    await ensureOffscreen();

    const response = await chrome.runtime.sendMessage({ type: "READ_CLIPBOARD" });
    const text = response?.text as string | null;
    const imageDataUrl = response?.imageDataUrl as string | null;

    // Image takes priority over text
    if (imageDataUrl) {
      const imageHash = CrossPasteHash.hashText(imageDataUrl);
      const lastImageHash = await getLastImageHash();
      if (imageHash !== lastImageHash) {
        await setLastImageHash(imageHash);
        await handleImageClipboard(imageDataUrl, imageHash);
      }
      return;
    }

    if (!text || text.length === 0) return;

    const lastText = await getLastClipboardText();
    if (text === lastText) return;

    await setLastClipboardText(text);
    // Clear image hash so that re-copying the same image after text is detected
    await setLastImageHash("");
    await handleTextClipboard(text);
  } catch {
    offscreenReady = false;
  }
}

async function handleTextClipboard(text: string): Promise<void> {
  const hash = CrossPasteHash.hashText(text);
  const appInstanceId = await getAppInstanceId();
  const size = new TextEncoder().encode(text).length;

  const { pasteType, pasteItem } = detectPasteType(text);
  pasteItem.hash = hash;
  pasteItem.size = size;

  const pasteData = {
    id: Date.now(),
    appInstanceId,
    favorite: false,
    pasteAppearItem: pasteItem,
    pasteCollection: { pasteItems: [] },
    pasteType,
    source: "Chrome",
    size,
    hash,
    receivedAt: Date.now(),
  };

  const isNew = await PasteStore.addIfNew(pasteData);
  if (isNew) {
    broadcastToSidePanel({ type: "PASTE_UPDATED" });
  }
}

async function handleImageClipboard(
  imageDataUrl: string,
  hash: string,
): Promise<void> {
  const appInstanceId = await getAppInstanceId();
  // Estimate size from base64 data
  const base64Part = imageDataUrl.split(",")[1] ?? "";
  const size = Math.round((base64Part.length * 3) / 4);

  const pasteData = {
    id: Date.now(),
    appInstanceId,
    favorite: false,
    pasteAppearItem: {
      type: "images" as const,
      identifiers: ["image/png"],
      hash,
      size,
      count: 1,
      relativePathList: ["clipboard-image.png"],
      fileInfoTreeMap: {},
      dataUrl: imageDataUrl,
    },
    pasteCollection: { pasteItems: [] },
    pasteType: 4, // IMAGE
    source: "Chrome",
    size,
    hash,
    receivedAt: Date.now(),
  };

  const isNew = await PasteStore.addIfNew(pasteData);
  if (isNew) {
    broadcastToSidePanel({ type: "PASTE_UPDATED" });
  }
}

function startClipboardPolling(): void {
  chrome.alarms.create(CLIPBOARD_POLL_ALARM, {
    periodInMinutes: CLIPBOARD_POLL_INTERVAL,
  });
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
    try {
      const data = await PullApi.pullPaste({
        host: device.host,
        port: device.port,
        appInstanceId,
        targetAppInstanceId: device.targetAppInstanceId,
      });
      if (data) {
        const isNew = await PasteStore.addIfNew(data);
        if (isNew) {
          broadcastToSidePanel({ type: "PASTE_UPDATED" });

          if (data.pasteType === 0 && data.pasteAppearItem) {
            const textItem = data.pasteAppearItem as { text?: string };
            if (textItem.text) {
              await setLastClipboardText(textItem.text);
            }
          }
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
}

function stopSyncAlarms(): void {
  chrome.alarms.clear("sync-paste");
  chrome.alarms.clear("heartbeat");
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
  } else if (alarm.name === CLIPBOARD_POLL_ALARM) {
    await pollClipboard();
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

async function handleMessage(
  message: Record<string, unknown>,
): Promise<unknown> {
  switch (message.type) {
    case "GET_DEVICES": {
      return { devices: await getDevicesWithStatus() };
    }

    case "CONNECT": {
      const host = message.host as string;
      const port = message.port as number;
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

    case "PAIR": {
      const token = message.token as number;
      try {
        if (!connectingState) throw new Error("Not connected");
        const appInstanceId = await getAppInstanceId();

        const response = await SyncApi.trust(
          {
            host: connectingState.host,
            port: connectingState.port,
            appInstanceId,
            targetAppInstanceId: connectingState.targetAppInstanceId,
          },
          token,
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
        connectingState = null;

        startSyncAlarms();
        broadcastToSidePanel({ type: "DEVICES_CHANGED" });

        return { success: true };
      } catch (e) {
        return { success: false, error: String(e) };
      }
    }

    case "REMOVE_DEVICE": {
      const targetId = message.targetAppInstanceId as string;
      await DeviceStore.remove(targetId);
      deviceStatuses.delete(targetId);

      const remaining = await DeviceStore.getAll();
      if (!remaining.some((d) => d.trusted)) {
        stopSyncAlarms();
      }

      broadcastToSidePanel({ type: "DEVICES_CHANGED" });
      return { success: true };
    }

    case "UPDATE_NOTE": {
      const targetId = message.targetAppInstanceId as string;
      const noteName = message.noteName as string;
      await DeviceStore.updateNote(targetId, noteName);
      broadcastToSidePanel({ type: "DEVICES_CHANGED" });
      return { success: true };
    }

    case "GET_PASTES": {
      const offset = (message.offset as number) ?? 0;
      const limit = (message.limit as number) ?? 50;
      const items = await PasteStore.getItems(offset, limit);
      return { items };
    }

    case "COPY_ITEM": {
      return { success: true };
    }

    case "DELETE_PASTE": {
      const pasteId = message.pasteId as number;
      const deleted = await PasteStore.deleteById(pasteId);
      if (deleted) {
        broadcastToSidePanel({ type: "PASTE_UPDATED" });
      }
      return { success: deleted };
    }

    default:
      return { error: "Unknown message type" };
  }
}

chrome.action.onClicked.addListener((tab) => {
  if (tab.id) {
    chrome.sidePanel.open({ tabId: tab.id });
  }
});

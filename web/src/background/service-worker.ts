import { ConnectionStore, type ConnectionConfig } from "@/shared/storage/connection-store";
import { DeviceStore, type StoredDevice } from "@/shared/storage/device-store";
import { PasteStore } from "@/shared/storage/paste-store";
import { BlobStore } from "@/shared/storage/blob-store";
import { SyncApi } from "@/shared/api/sync";
import { PullApi } from "@/shared/api/pull";
import { CrossPasteHash, CrossPasteJson } from "@/shared/core";
import type { SyncInfo } from "@/shared/models/sync-info";
import type { PasteData } from "@/shared/models/paste-data";
import { APP_VERSION } from "@/shared/app/version.generated";
import { collectPasteItems } from "@/shared/paste/paste-collector";
import { WsManager } from "@/shared/ws/ws-manager";
import {
  createWsMessageHandler,
  type OversizePasteNotice,
} from "@/shared/ws/ws-message-handler";
import { buildTranslatorFromStorage } from "@/shared/i18n/i18n-core";
import { WsMessageType, simpleEnvelope } from "@/shared/ws/ws-types";
import type { WsEnvelope } from "@/shared/ws/ws-types";
import { ingestPaste } from "@/shared/paste/paste-ingestion";
import { initNativeHost, isDesktopConnected } from "./native-host";
import type { WsConnectionStatus } from "@/shared/ws/ws-types";
import {
  deriveSyncState,
  type DeviceRuntimeFacts,
} from "@/shared/sync/derive-state";
import { SyncState } from "@/shared/sync/sync-state";
import { SyncApiError, StandardErrorCode } from "@/shared/api/sync-error";
import {
  PROTOCOL_VERSION,
  isCompatibleVersion,
} from "@/shared/sync/protocol-version";
import {
  enqueueOversizeNotice,
  type OversizeNoticeMessage,
} from "@/shared/oversize-notice-queue";

// ─── Per-device runtime facts (source of truth) ───────────────────────
//
// Lives only in the service worker. The UI sees the derived SyncState
// (see getDevicesWithStatus). We broadcast DEVICES_CHANGED only when the
// derived state flips, to avoid spamming on every 60s probe.

interface RuntimeFacts {
  wsState: WsConnectionStatus | null;
  lastHttpSuccessAt: number | null;
  lastErrorCode: number | null;
  versionDrift: boolean;
  connecting: boolean;
}

const deviceRuntime = new Map<string, RuntimeFacts>();

function getOrCreateRuntime(targetId: string): RuntimeFacts {
  let state = deviceRuntime.get(targetId);
  if (!state) {
    state = {
      wsState: null,
      lastHttpSuccessAt: null,
      lastErrorCode: null,
      versionDrift: false,
      connecting: false,
    };
    deviceRuntime.set(targetId, state);
  }
  return state;
}

async function computeState(targetId: string): Promise<SyncState> {
  const runtime = getOrCreateRuntime(targetId);
  const device = await DeviceStore.get(targetId);
  const facts: DeviceRuntimeFacts = {
    ...runtime,
    needsRePair: device?.needsRePair === true,
  };
  return deriveSyncState(facts, Date.now());
}

async function updateRuntime(
  targetId: string,
  mutate: (state: RuntimeFacts) => void,
): Promise<void> {
  const before = await computeState(targetId);
  mutate(getOrCreateRuntime(targetId));
  const after = await computeState(targetId);
  if (before !== after) {
    broadcastToSidePanel({ type: "DEVICES_CHANGED" });
  }
}

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
// After a LOCAL_COPY, Chrome's clipboard.write sanitizes text/html and may
// regenerate text/plain from the HTML, so the first post-write poll reads
// bytes that don't match the original paste's hash. Within this window we
// absorb the re-read hash into lastHash once instead of ingesting it.
const STORAGE_KEY_LOCAL_COPY_UNTIL = "clipboard_localCopyUntil";
const LOCAL_COPY_SUPPRESS_MS = 2000;


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

async function getLocalCopyUntil(): Promise<number> {
  const result = await chrome.storage.session.get(STORAGE_KEY_LOCAL_COPY_UNTIL);
  return (result[STORAGE_KEY_LOCAL_COPY_UNTIL] as number) ?? 0;
}

async function setLocalCopyUntil(value: number): Promise<void> {
  await chrome.storage.session.set({ [STORAGE_KEY_LOCAL_COPY_UNTIL]: value });
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

// MV3 service workers don't expose URL.createObjectURL or the Blob URL scheme,
// so chrome.downloads.download has to be fed a data URL instead.
function arrayBufferToDataUrl(buffer: ArrayBuffer, mime = "application/octet-stream"): string {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  const CHUNK = 0x8000;
  for (let i = 0; i < bytes.length; i += CHUNK) {
    binary += String.fromCharCode.apply(null, Array.from(bytes.subarray(i, i + CHUNK)));
  }
  return `data:${mime};base64,${btoa(binary)}`;
}

/** Store file blobs grouped by their hash. */
async function storeFileBlobs(collected: { hash: string; fileBlobs: Array<{ name: string; dataUrl: string; hash?: string }> }): Promise<void> {
  if (collected.fileBlobs.length === 0) return;
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


/** Push paste data to all trusted WebSocket-connected devices. */
async function pushPasteToDevices(pasteData: PasteData): Promise<void> {
  if (!wsManager) return;

  let normalizedPayload: Uint8Array;
  try {
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

async function pollClipboard(): Promise<void> {
  try {
    await ensureOffscreen();

    const response = await chrome.runtime.sendMessage({ type: "READ_CLIPBOARD" });
    const collected = collectPasteItems(
      response,
      CrossPasteHash.hashText,
      (bytes: Uint8Array) =>
        CrossPasteHash.hashBytes(new Int8Array(bytes.buffer, bytes.byteOffset, bytes.byteLength)),
    );
    if (!collected) return;

    const lastHash = await getLastHash();
    if (collected.hash === lastHash) return;

    const suppressUntil = await getLocalCopyUntil();
    if (Date.now() < suppressUntil) {
      await setLastHash(collected.hash);
      await setLocalCopyUntil(0);
      return;
    }

    await setLastHash(collected.hash);

    await storeFileBlobs(collected);

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

    if ((await ingestPaste(pasteData, broadcastToSidePanel)) !== null) {
      await pushPasteToDevices(pasteData);
    }
  } catch (e) {
    console.error("[pollClipboard] error:", e);
    offscreenReady = false;
  }
}

let clipboardPollTimer: ReturnType<typeof setTimeout> | null = null;
let pollingEnabled = false;

function startClipboardPolling(): void {
  if (pollingEnabled) return;
  pollingEnabled = true;
  async function loop() {
    if (!pollingEnabled) return;
    await pollClipboard();
    if (!pollingEnabled) return;
    clipboardPollTimer = setTimeout(loop, CLIPBOARD_POLL_INTERVAL_MS);
  }
  loop();
}

function stopClipboardPolling(): void {
  pollingEnabled = false;
  if (clipboardPollTimer !== null) {
    clearTimeout(clipboardPollTimer);
    clipboardPollTimer = null;
  }
}

function pauseForDesktop(): void {
  console.log("[NativeMessaging] Desktop app detected, pausing extension");
  stopClipboardPolling();
  stopSyncAlarms();
  wsManager?.disconnectAll();
  broadcastToSidePanel({ type: "DESKTOP_STATUS_CHANGED", connected: true });
}

function resumeFromDesktop(): void {
  console.log("[NativeMessaging] Desktop app disconnected, resuming extension");
  startClipboardPolling();
  DeviceStore.getAll().then(async (devices) => {
    if (devices.some((d) => d.trusted)) {
      startSyncAlarms();
      if (!wsManager) {
        await initializeWebSocket();
      } else {
        await wsManager.connectAllDevices();
      }
    }
  });
  broadcastToSidePanel({ type: "DESKTOP_STATUS_CHANGED", connected: false });
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
  status: SyncState;
}

async function getDevicesWithStatus(): Promise<DeviceWithStatus[]> {
  const devices = await DeviceStore.getAll();
  const visible = devices.filter((d) => d.trusted || d.needsRePair);
  const now = Date.now();
  return visible.map((d) => {
    const facts: DeviceRuntimeFacts = {
      ...getOrCreateRuntime(d.targetAppInstanceId),
      needsRePair: d.needsRePair === true,
    };
    return { ...d, status: deriveSyncState(facts, now) };
  });
}

// ─── Sync & heartbeat ──────────────────────────────────────────────────

async function syncAllDevices(): Promise<void> {
  const appInstanceId = await getAppInstanceId();
  const devices = await DeviceStore.getAll();

  for (const device of devices) {
    if (!device.trusted) continue;
    if (device.needsRePair) continue;
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
        if ((await ingestPaste(data, broadcastToSidePanel)) !== null) {
          await setLastHash(data.hash);
        }
      }
      await updateRuntime(device.targetAppInstanceId, (s) => {
        s.lastHttpSuccessAt = Date.now();
      });
    } catch (e) {
      if (e instanceof SyncApiError) {
        // Peer is reachable but returned an error — freshness stays;
        // heartbeat will classify it (DECRYPT_FAIL etc.) on its next tick.
      } else {
        // Transport failure = peer unreachable. Collapse the freshness window
        // so the UI flips to DISCONNECTED instead of lingering as CONNECTED.
        await updateRuntime(device.targetAppInstanceId, (s) => {
          s.lastHttpSuccessAt = null;
        });
      }
    }
  }
}

/**
 * Desktop reports DECRYPT_FAIL when our stored cryptPublicKey no longer
 * matches its own (e.g. desktop DB wipe, crypto rotation, or a reinstall).
 * Recover by wiping the key and flipping the device to UNVERIFIED so the
 * UI prompts the user to re-pair.
 */
async function handleDecryptFail(targetId: string): Promise<void> {
  await DeviceStore.setNeedsRePair(targetId, true);
  wsManager?.disconnectDevice(targetId);
  await updateRuntime(targetId, (s) => {
    s.lastErrorCode = StandardErrorCode.DECRYPT_FAIL;
    s.wsState = null;
    s.lastHttpSuccessAt = null;
  });
}

async function sendHeartbeats(): Promise<void> {
  const appInstanceId = await getAppInstanceId();
  const devices = await DeviceStore.getAll();

  for (const device of devices) {
    if (!device.trusted) continue;
    if (device.needsRePair) continue;
    if (wsManager?.isConnected(device.targetAppInstanceId)) continue;

    try {
      const remoteVersion = await SyncApi.heartbeat({
        host: device.host,
        port: device.port,
        appInstanceId,
        targetAppInstanceId: device.targetAppInstanceId,
      });
      const drift = !isCompatibleVersion(remoteVersion);
      await updateRuntime(device.targetAppInstanceId, (s) => {
        s.lastHttpSuccessAt = Date.now();
        s.lastErrorCode = null;
        s.versionDrift = drift;
      });
    } catch (e) {
      if (e instanceof SyncApiError && e.isDecryptFail()) {
        await handleDecryptFail(device.targetAppInstanceId);
      } else if (e instanceof SyncApiError) {
        const errorCode = e.errorCode;
        await updateRuntime(device.targetAppInstanceId, (s) => {
          s.lastErrorCode = errorCode;
        });
      } else {
        console.debug("[heartbeat] transport error:", e);
        // Transport failure = peer unreachable. Collapse the freshness
        // window immediately so the UI flips to DISCONNECTED instead of
        // lingering as CONNECTED for up to FRESH_THRESHOLD_MS.
        await updateRuntime(device.targetAppInstanceId, (s) => {
          s.lastHttpSuccessAt = null;
        });
      }
    }
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
    updateDeviceStatus: async (targetId, status) => {
      // Receiving a WS message proves the channel is alive; treat NOTIFY_EXIT
      // as an explicit hint to immediately stale both channels (the WS will
      // close shortly anyway, but this avoids waiting for the close event).
      await updateRuntime(targetId, (s) => {
        if (status === "synced") {
          s.lastHttpSuccessAt = Date.now();
        } else {
          s.wsState = null;
          s.lastHttpSuccessAt = null;
        }
      });
    },
    broadcastToSidePanel,
    setLastHash,
    showOversizePasteNotice,
    onRemoteRemoveDevice: async (targetId) => {
      wsManager?.disconnectDevice(targetId);
      await DeviceStore.remove(targetId);
      deviceRuntime.delete(targetId);

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

  // Source of truth for WS liveness: every status transition is recorded,
  // not just the success case. The derived state (see deriveSyncState) handles
  // the rest, including the HTTP fallback grace window.
  wsManager.onStatusChange = async (targetId, status) => {
    await updateRuntime(targetId, (s) => {
      s.wsState = status;
    });
  };

  await wsManager.connectAllDevices();
}

async function initialize(): Promise<void> {
  await getAppInstanceId();
  await ensureOffscreen();

  const desktopRunning = await initNativeHost({
    onDesktopConnected: () => pauseForDesktop(),
    onDesktopDisconnected: () => resumeFromDesktop(),
  });

  if (!desktopRunning) {
    startClipboardPolling();
  }

  await migrateFromLegacy();

  const devices = await DeviceStore.getAll();
  const trustedDevices = devices.filter((d) => d.trusted);

  if (trustedDevices.length > 0 && !desktopRunning) {
    // Do not preset to "synced" — the first WS attempt or heartbeat decides
    // truth. UI may briefly show "error" until evidence arrives, which is
    // strictly better than lying with a green badge.
    startSyncAlarms();
    await initializeWebSocket();
  }
}

chrome.runtime.onInstalled.addListener(() => initialize());
chrome.runtime.onStartup?.addListener(() => initialize());

// ─── Alarm handler ──────────────────────────────────────────────────────

chrome.alarms.onAlarm.addListener(async (alarm) => {
  if (isDesktopConnected()) return;
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

function formatBytes(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${bytes} B`;
}

async function showOversizePasteNotice(
  sourceAppInstanceId: string,
  notice: OversizePasteNotice,
): Promise<void> {
  const device = await DeviceStore.get(sourceAppInstanceId);
  const deviceName =
    device?.noteName || device?.syncInfo.endpointInfo.deviceName || "Remote device";
  const t = await buildTranslatorFromStorage();
  const limit = formatBytes(notice.sizeLimitBytes);
  const actual = formatBytes(notice.actualSize);
  const title = t("paste_not_synced_title", deviceName);
  const message =
    notice.reason === "FILE_TOO_LARGE"
      ? t("paste_oversize_file", notice.fileName ?? "", actual, limit)
      : t("paste_oversize_total", actual, limit);

  const payload: OversizeNoticeMessage = { type: "OVERSIZE_NOTICE", title, message };
  // Always enqueue, then ping the side panel to drain. Avoids the MV3 quirk
  // where sendMessage resolves (not rejects) when the offscreen listener
  // receives but doesn't handle the message, which would silently drop notices.
  await enqueueOversizeNotice(payload);
  chrome.runtime.sendMessage({ type: "OVERSIZE_NOTICE_DRAIN" }).catch(() => {
    // Side panel not open — next mount will drain.
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

    const remoteVersion = await SyncApi.telnet(config);
    if (!isCompatibleVersion(remoteVersion)) {
      connectingState = null;
      return {
        success: false,
        error: `incompatible protocol version: remote=${remoteVersion} extension=${PROTOCOL_VERSION}`,
        incompatible: true,
      };
    }

    const syncInfo = await SyncApi.getSyncInfo(config);
    const targetAppInstanceId = syncInfo.appInfo.appInstanceId;

    await SyncApi.showToken({ ...config, targetAppInstanceId });

    if (connectingState && connectingState.targetAppInstanceId !== targetAppInstanceId) {
      const staleId = connectingState.targetAppInstanceId;
      await updateRuntime(staleId, (s) => { s.connecting = false; });
    }
    connectingState = { host, port, targetAppInstanceId, syncInfo };
    await updateRuntime(targetAppInstanceId, (s) => {
      s.connecting = true;
      s.versionDrift = false;
    });
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

    // SyncApi.trust just succeeded over HTTP, so the channel is proven alive.
    await DeviceStore.setNeedsRePair(connectingState.targetAppInstanceId, false);
    await updateRuntime(connectingState.targetAppInstanceId, (s) => {
      s.connecting = false;
      s.lastErrorCode = null;
      s.versionDrift = false;
      s.lastHttpSuccessAt = Date.now();
    });

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
    if (connectingState) {
      const id = connectingState.targetAppInstanceId;
      await updateRuntime(id, (s) => { s.connecting = false; });
    }
    return { success: false, error: String(e) };
  }
}

async function handleRePair(targetAppInstanceId: string): Promise<unknown> {
  const device = await DeviceStore.get(targetAppInstanceId);
  if (!device) return { success: false, error: "Device not found" };
  try {
    const appInstanceId = await getAppInstanceId();
    const config = { host: device.host, port: device.port, appInstanceId };

    const remoteVersion = await SyncApi.telnet(config);
    if (!isCompatibleVersion(remoteVersion)) {
      await updateRuntime(targetAppInstanceId, (s) => {
        s.versionDrift = true;
      });
      return {
        success: false,
        error: `incompatible protocol version: remote=${remoteVersion} extension=${PROTOCOL_VERSION}`,
        incompatible: true,
      };
    }

    await SyncApi.showToken({ ...config, targetAppInstanceId });
    if (connectingState && connectingState.targetAppInstanceId !== targetAppInstanceId) {
      const staleId = connectingState.targetAppInstanceId;
      await updateRuntime(staleId, (s) => { s.connecting = false; });
    }
    connectingState = {
      host: device.host,
      port: device.port,
      targetAppInstanceId,
      syncInfo: device.syncInfo,
    };
    await updateRuntime(targetAppInstanceId, (s) => {
      s.connecting = true;
      s.versionDrift = false;
    });
    return { success: true, syncInfo: device.syncInfo };
  } catch (e) {
    await updateRuntime(targetAppInstanceId, (s) => { s.connecting = false; });
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
  deviceRuntime.delete(targetId);

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
    await setLocalCopyUntil(Date.now() + LOCAL_COPY_SUPPRESS_MS);
    broadcastToSidePanel({ type: "PASTE_UPDATED" });
  }
  return { success: hash !== null };
}

async function handleDeletePaste(pasteId: number): Promise<unknown> {
  const hash = await PasteStore.deleteById(pasteId);
  if (hash !== null) {
    await BlobStore.deleteForPaste(hash);
    await PasteStore.purgeDeleted();
    broadcastToSidePanel({ type: "PASTE_DELETED", pasteId });
  }
  return { success: hash !== null };
}

async function handleDownloadFile(hash: string, fileName: string): Promise<unknown> {
  const data = await BlobStore.get(hash, fileName);
  if (!data) return { success: false, error: "File not found" };

  const url = arrayBufferToDataUrl(data);
  try {
    await chrome.downloads.download({ url, filename: fileName, saveAs: true });
    return { success: true };
  } catch (e) {
    return { success: false, error: String(e) };
  }
}

async function handleMessage(
  message: Record<string, unknown>,
): Promise<unknown> {
  switch (message.type) {
    case "GET_DEVICES": return handleGetDevices();
    case "CONNECT": return handleConnect(message.host as string, message.port as number);
    case "PAIR": return handlePair(message.token as number);
    case "REPAIR": return handleRePair(message.targetAppInstanceId as string);
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
    case "DOWNLOAD_FILE": return handleDownloadFile(message.hash as string, message.fileName as string);
    case "GET_WS_STATUS": return { statuses: wsManager?.getConnectionStates() ?? {} };
    case "GET_DESKTOP_STATUS": return { desktopConnected: isDesktopConnected() };
    default: return { error: "Unknown message type" };
  }
}

chrome.action.onClicked.addListener((tab) => {
  if (tab.id) {
    chrome.sidePanel.open({ tabId: tab.id });
  }
});

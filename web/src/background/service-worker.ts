import { ConnectionStore, type ConnectionConfig } from "@/shared/storage/connection-store";
import { DeviceStore, type StoredDevice } from "@/shared/storage/device-store";
import { PasteStore } from "@/shared/storage/paste-store";
import { BlobStore } from "@/shared/storage/blob-store";
import { SyncApi } from "@/shared/api/sync";
import { PullApi } from "@/shared/api/pull";
import {
  CrossPasteHash,
  CrossPasteJson,
  createPairingV3Initiator,
  type PairingV3Initiator,
} from "@/shared/core";
import { KeyStore, toInt8Array } from "@/shared/storage/key-store";
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
import { createWsPayloadDecryptor } from "@/shared/ws/ws-payload-crypto";
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
  ADVERTISED_PAIRING_VERSION,
  isCompatibleVersion,
  selectPairingMode,
} from "@/shared/sync/protocol-version";
import type { KeyExchangeResponse } from "@/shared/models/key-exchange";
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

/**
 * One pairing attempt. `handlePair` snapshots the current attempt and every
 * step (including persistence) operates on that snapshot — a concurrent
 * CONNECT replaces `connectingState`, and the superseded attempt then fails
 * its identity check instead of writing keys under the wrong device.
 */
interface ConnectingAttempt {
  /** Generation id assigned at CONNECT/REPAIR entry; PAIR and CANCEL_CONNECT
   * reference it so they can only act on the attempt they belong to. */
  id: number;
  host: string;
  port: number;
  targetAppInstanceId: string;
  syncInfo: SyncInfo;
  /** Trust handshake selected from the desktop's advertised pairingVersion. */
  pairingMode: 1 | 2 | 3;
  /** Live v3 initiator session (pairingMode 3 only). */
  v3?: PairingV3Initiator;
  /**
   * The warm-up key-exchange response (pairingMode 2 only). Reused at confirm
   * time so each pairing performs exactly ONE exchange — the desktop's token
   * refresh is counted per exchange and released once per confirm.
   * `requestTimestamp` is the generation marker for a targeted cancel.
   */
  v2Exchange?: KeyExchangeResponse & { requestTimestamp: number };
  /**
   * True while the trust round-trip that makes the DESKTOP persist keys is in
   * flight (v1 trust / v2 confirm / v3 commit). Cancelling in this window
   * would leave one-sided trust, so CANCEL_CONNECT refuses it.
   */
  finalizing?: boolean;
  /**
   * A cancel arrived while finalizing. The pairing settles first; a failure
   * path then honours the latch (cleanup, no session restart). A successful
   * completion ignores it — both sides are consistent at that point.
   */
  cancelRequested?: boolean;
}

let connectingState: ConnectingAttempt | null = null;

/** Monotonic generation for CONNECT/REPAIR requests, sampled at entry. */
let connectSeq = 0;

// ─── Clipboard monitoring ───────────────────────────────────────────────

let offscreenReady = false;

const CLIPBOARD_POLL_INTERVAL_MS = 1000; // 1 second
const STORAGE_KEY_LAST_HASH = "clipboard_lastHash";
// After any clipboard write of our own (LOCAL_COPY from the side panel, or a
// remote paste_push written via the offscreen document), Chrome's
// clipboard.write sanitizes text/html and may regenerate text/plain from the
// HTML, so the first post-write poll reads bytes that don't match the original
// paste's hash. Within this window we absorb the re-read hash into lastHash
// once instead of ingesting it.
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
        // Do NOT touch lastHash here: pulling stores the paste without writing
        // the clipboard, so lastHash must keep tracking the actual clipboard
        // content or the next poll re-ingests it as a new local copy.
        await ingestPaste(data, broadcastToSidePanel);
      }
      await updateRuntime(device.targetAppInstanceId, (s) => {
        s.lastHttpSuccessAt = Date.now();
      });
    } catch (e) {
      if (e instanceof SyncApiError && e.isDecryptFail()) {
        await handleDecryptFail(device.targetAppInstanceId);
      } else if (e instanceof SyncApiError) {
        // Peer is reachable but returned a non-crypto error — freshness stays;
        // heartbeat will classify it on its next tick.
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
 * A failed encrypted response or WS payload means our stored cryptPublicKey no
 * longer matches the desktop (e.g. desktop DB wipe, crypto rotation, or a reinstall).
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

  const wsPayloadDecryptor = createWsPayloadDecryptor();

  const wsMessageHandler = createWsMessageHandler({
    sendToDevice: async (targetId, envelope) => {
      await wsManager?.send(targetId, envelope);
    },
    decryptFromDevice: (targetId, payload) =>
      wsPayloadDecryptor.decryptFromDevice(targetId, payload),
    onDecryptFailure: handleDecryptFail,
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
    getLastHash,
    armClipboardWriteSuppress: () => setLocalCopyUntil(Date.now() + LOCAL_COPY_SUPPRESS_MS),
    disarmClipboardWriteSuppress: () => setLocalCopyUntil(0),
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

// Host permission is granted by the sidepanel via `chrome.permissions.request`
// during a user gesture; the service worker can only verify, never request.
async function hasHostPermission(host: string, port: number): Promise<boolean> {
  return chrome.permissions.contains({ origins: [`http://${host}:${port}/*`] });
}

/**
 * The extension's own SyncInfo, self-registered on the trust/commit request
 * (the extension can't be discovered via mDNS). Advertises our pairing
 * capability so the desktop shows the matching credential UI for this peer.
 */
function buildExtensionSyncInfo(appInstanceId: string): SyncInfo {
  const chromeVersion = navigator.userAgent.split("Chrome/")[1]?.split(" ")[0] ?? "unknown";
  return {
    appInfo: {
      appInstanceId,
      appVersion: APP_VERSION,
      appRevision: "Unknown",
      userName: "Chrome Extension",
      pairingVersion: ADVERTISED_PAIRING_VERSION,
    },
    endpointInfo: {
      deviceId: appInstanceId,
      deviceName: "Chrome Extension",
      platform: { name: "ChromeExtension", arch: "web", bitMode: 64, version: chromeVersion },
      hostInfoList: [],
      port: 0,
    },
  };
}

/**
 * Negotiate the trust handshake from the desktop's advertised pairingVersion
 * and run its pre-pair step, so the desktop is displaying the right credential
 * (v1 token / v2 SAS / v3 PIN) when this resolves. Sets `connectingState`.
 */
async function beginPairing(
  seq: number,
  host: string,
  port: number,
  syncInfo: SyncInfo,
): Promise<{ pairingMode: 1 | 2 | 3; attemptId: number }> {
  const appInstanceId = await getAppInstanceId();
  const targetAppInstanceId = syncInfo.appInfo.appInstanceId;
  const config = { host, port, appInstanceId, targetAppInstanceId };
  const pairingMode = selectPairingMode(syncInfo.appInfo.pairingVersion);

  let v3: PairingV3Initiator | undefined;
  let v2Exchange: (KeyExchangeResponse & { requestTimestamp: number }) | undefined;
  if (pairingMode === 3) {
    v3 = await startV3Session(appInstanceId, targetAppInstanceId, config);
    // The desktop is now showing this session's PIN card.
  } else if (pairingMode === 2) {
    // The one exchange of this attempt: the desktop computes and displays the
    // SAS and starts its token refresh, released again by the confirm.
    v2Exchange = await SyncApi.exchangeV2(config);
  } else {
    await SyncApi.showToken(config);
  }

  if (seq !== connectSeq) {
    // A newer CONNECT/REPAIR started while our preflight ran (regardless of
    // which finishes first). Release what we created and bow out.
    void releaseAttempt({
      id: seq,
      host,
      port,
      targetAppInstanceId,
      syncInfo,
      pairingMode,
      v3,
      v2Exchange,
    });
    throw new Error("pairing attempt superseded");
  }

  // Install first (no awaits in between), then release the replaced attempt —
  // same target or not — so its desktop-side session never lingers.
  const previous = connectingState;
  const attempt: ConnectingAttempt = {
    id: seq,
    host,
    port,
    targetAppInstanceId,
    syncInfo,
    pairingMode,
    v3,
    v2Exchange,
  };
  connectingState = attempt;
  if (previous) {
    void releaseAttempt(previous);
    if (previous.targetAppInstanceId !== targetAppInstanceId) {
      await updateRuntime(previous.targetAppInstanceId, (s) => { s.connecting = false; });
    }
  }
  await updateRuntime(targetAppInstanceId, (s) => {
    s.connecting = true;
    s.versionDrift = false;
  });
  return { pairingMode, attemptId: seq };
}

type PairingV3Config = {
  host: string;
  port: number;
  appInstanceId: string;
  targetAppInstanceId: string;
};

/** Create a fresh v3 initiator session against the target (intent → offer). */
async function startV3Session(
  appInstanceId: string,
  targetAppInstanceId: string,
  config: PairingV3Config,
): Promise<PairingV3Initiator> {
  // Best-effort: surfaces the pairing screen (and acceptance window) on the
  // desktop. May be disabled by desktop config — the user can open it by hand.
  await SyncApi.showPairingCode(config).catch(() => {});
  const keys = (await KeyStore.getKeys()) ?? (await KeyStore.generateAndStore());
  const v3 = await createPairingV3Initiator(
    appInstanceId,
    toInt8Array(keys.signPublicKey),
    toInt8Array(keys.signPrivateKey),
    toInt8Array(keys.cryptPublicKey),
    toInt8Array(keys.cryptPrivateKey),
  );
  const intentJson = await v3.createIntent(targetAppInstanceId, "Chrome Extension");
  let offerJson: string;
  try {
    offerJson = await SyncApi.pairingV3Intent(config, intentJson);
  } catch (e) {
    if (e instanceof SyncApiError && e.errorCode === StandardErrorCode.PAIRING_DISABLED) {
      throw new Error("pairing_disabled");
    }
    throw e;
  }
  const refusal = await v3.acceptOffer(offerJson);
  if (refusal !== null && refusal !== undefined) {
    throw new Error(`pairing v3 offer refused: ${refusal}`);
  }
  return v3;
}

/** Best-effort release of an attempt's desktop-side session and key material. */
async function releaseAttempt(attempt: ConnectingAttempt): Promise<void> {
  const appInstanceId = await getAppInstanceId();
  const config = {
    host: attempt.host,
    port: attempt.port,
    appInstanceId,
    targetAppInstanceId: attempt.targetAppInstanceId,
  };
  const v3 = attempt.v3;
  if (v3) {
    attempt.v3 = undefined;
    try {
      const cancelJson = v3.buildCancel();
      if (cancelJson) {
        await SyncApi.pairingV3Cancel(config, cancelJson);
      }
    } catch {
      // Desktop unreachable or session already gone — the session TTL cleans up.
    } finally {
      v3.destroy();
    }
  }
  const v2Exchange = attempt.v2Exchange;
  if (v2Exchange) {
    attempt.v2Exchange = undefined;
    // Release the desktop's pending exchange (refresh count + SAS overlay),
    // naming its generation so a cancel that arrives after a newer exchange
    // (fast reconnect) releases nothing. Desktops predating the cancel route
    // just fail; ones predating the header ignore it.
    await SyncApi.cancelV2(config, v2Exchange.requestTimestamp).catch(() => {});
  }
}

async function handleConnect(host: string, port: number): Promise<unknown> {
  // Generation assigned at entry: an attempt started earlier but arriving at
  // install later than a newer one can never win, whatever the network timing.
  const seq = ++connectSeq;
  try {
    if (!(await hasHostPermission(host, port))) {
      return { success: false, error: "host_permission_denied" };
    }
    const appInstanceId = await getAppInstanceId();
    const config = { host, port, appInstanceId };

    const remoteVersion = await SyncApi.telnet(config);
    if (!isCompatibleVersion(remoteVersion)) {
      return {
        success: false,
        error: `incompatible protocol version: remote=${remoteVersion} extension=${PROTOCOL_VERSION}`,
        incompatible: true,
      };
    }

    const syncInfo = await SyncApi.getSyncInfo(config);
    const { pairingMode, attemptId } = await beginPairing(seq, host, port, syncInfo);
    return { success: true, syncInfo, pairingMode, attemptId };
  } catch (e) {
    // Do NOT clear connectingState here: this attempt never installed itself
    // (beginPairing installs at the very end), so the global may belong to a
    // newer CONNECT that must not be clobbered by our late failure.
    return { success: false, error: e instanceof Error ? e.message : String(e) };
  }
}

/** Persist the paired device and bring the sync machinery up. */
async function completePairing(
  attempt: ConnectingAttempt,
  serverKeys: {
    signPublicKey: string;
    cryptPublicKey: string;
  },
): Promise<void> {
  // A concurrent CONNECT superseded this attempt while its trust round-trip
  // was in flight: do NOT bind the returned keys to whatever the global state
  // points at now.
  if (connectingState !== attempt) throw new Error("pairing attempt superseded");
  await DeviceStore.save({
    targetAppInstanceId: attempt.targetAppInstanceId,
    syncInfo: attempt.syncInfo,
    host: attempt.host,
    port: attempt.port,
    trusted: true,
    serverKeys,
    addedAt: Date.now(),
  });

  // The trust round-trip just succeeded over HTTP, so the channel is proven alive.
  await DeviceStore.setNeedsRePair(attempt.targetAppInstanceId, false);
  await updateRuntime(attempt.targetAppInstanceId, (s) => {
    s.connecting = false;
    s.lastErrorCode = null;
    s.versionDrift = false;
    s.lastHttpSuccessAt = Date.now();
  });

  // Attempt WebSocket upgrade after pairing
  if (!wsManager) {
    await initializeWebSocket();
  } else {
    const device = await DeviceStore.get(attempt.targetAppInstanceId);
    if (device) await wsManager.connectDevice(device);
  }

  attempt.v3?.destroy();
  if (connectingState === attempt) {
    connectingState = null;
  }

  startSyncAlarms();
  broadcastToSidePanel({ type: "DEVICES_CHANGED" });
}

/** v1: bearer-token trust (pre-2.0 desktops). */
async function pairV1(attempt: ConnectingAttempt, token: number): Promise<void> {
  const appInstanceId = await getAppInstanceId();
  attempt.finalizing = true;
  try {
    const response = await SyncApi.trust(
      {
        host: attempt.host,
        port: attempt.port,
        appInstanceId,
        targetAppInstanceId: attempt.targetAppInstanceId,
      },
      token,
      buildExtensionSyncInfo(appInstanceId),
    );
    await completePairing(attempt, {
      signPublicKey: response.pairingResponse.signPublicKey,
      cryptPublicKey: response.pairingResponse.cryptPublicKey,
    });
  } finally {
    attempt.finalizing = false;
  }
}

/** v2: SAS compare over the ECDH key exchange. */
async function pairV2(attempt: ConnectingAttempt, token: number): Promise<void> {
  const appInstanceId = await getAppInstanceId();
  const config = {
    host: attempt.host,
    port: attempt.port,
    appInstanceId,
    targetAppInstanceId: attempt.targetAppInstanceId,
  };
  // Reuse the connect-time exchange: one exchange per confirm keeps the
  // desktop's token-refresh counter balanced (each exchange starts a refresh,
  // each confirm releases exactly one).
  let exchange = attempt.v2Exchange ?? (await SyncApi.exchangeV2(config));
  attempt.v2Exchange = exchange;
  let localSAS = await SyncApi.computeLocalSAS(exchange.cryptPublicKey);
  if (token !== localSAS) {
    // The desktop shows a different code than we derived: possible MITM.
    throw new Error("sas_mismatch");
  }
  const syncInfo = buildExtensionSyncInfo(appInstanceId);
  attempt.finalizing = true;
  try {
    try {
      await SyncApi.confirmV2(config, exchange.signPublicKey, syncInfo);
    } catch (e) {
      // The server-side pending exchange lives 60s; a slow user hits
      // EXCHANGE_TIMEOUT. Re-exchange once (same stable keys → same SAS,
      // counter +1 balanced by the confirm below) and retry.
      if (!(e instanceof SyncApiError && e.errorCode === StandardErrorCode.EXCHANGE_TIMEOUT)) {
        throw e;
      }
      exchange = await SyncApi.exchangeV2(config);
      attempt.v2Exchange = exchange;
      localSAS = await SyncApi.computeLocalSAS(exchange.cryptPublicKey);
      if (token !== localSAS) {
        throw new Error("sas_mismatch");
      }
      await SyncApi.confirmV2(config, exchange.signPublicKey, syncInfo);
    }
    await completePairing(attempt, {
      signPublicKey: exchange.signPublicKey,
      cryptPublicKey: exchange.cryptPublicKey,
    });
  } finally {
    attempt.finalizing = false;
  }
}

/**
 * Discard the attempt's dead v3 session and start a fresh one (new intent →
 * new offer → new PIN on the desktop). Used when the current session cannot
 * continue — e.g. the proof was accepted server-side but the response was
 * lost, leaving client and server states irreconcilable.
 */
async function restartV3Session(
  attempt: ConnectingAttempt,
  appInstanceId: string,
  config: PairingV3Config,
): Promise<void> {
  await releaseAttempt(attempt);
  // The user asked to cancel while we were finalizing: don't open a brand-new
  // session (and desktop PIN card) they no longer want.
  if (attempt.cancelRequested) return;
  attempt.v3 = await startV3Session(appInstanceId, attempt.targetAppInstanceId, config);
}

const V3_COMMIT_ATTEMPTS = 3;
const V3_COMMIT_RETRY_DELAY_MS = 300;
const V3_ROTATION_POLL_ATTEMPTS = 5;
const V3_ROTATION_POLL_DELAY_MS = 200;

function delayMs(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * Replace the dead session so a later attempt starts clean, swallowing the
 * failure when the desktop is unreachable — the attempt is over either way.
 */
async function restartV3SessionQuietly(
  attempt: ConnectingAttempt,
  appInstanceId: string,
  config: PairingV3Config,
): Promise<void> {
  try {
    await restartV3Session(attempt, appInstanceId, config);
  } catch {
    // Desktop unreachable.
  }
}

/**
 * Re-POST the byte-identical intent until the acceptor publishes a rotated
 * PIN generation. The server rotates asynchronously after a proof failure, so
 * the first refresh can race it and return the OLD generation — accepting
 * that would make the user's freshly displayed PIN fail again. Falls back to
 * the last offer if no rotation shows up in time.
 */
async function refreshOfferForNewGeneration(
  config: PairingV3Config,
  v3: PairingV3Initiator,
): Promise<string> {
  const previousGeneration = v3.tokenGeneration();
  let offerJson = "";
  for (let i = 0; i < V3_ROTATION_POLL_ATTEMPTS; i++) {
    offerJson = await SyncApi.pairingV3Intent(config, v3.intentJson());
    const generation = (JSON.parse(offerJson) as { tokenGeneration?: number }).tokenGeneration;
    if (typeof generation === "number" && generation > previousGeneration) {
      return offerJson;
    }
    if (i < V3_ROTATION_POLL_ATTEMPTS - 1) {
      await delayMs(V3_ROTATION_POLL_DELAY_MS);
    }
  }
  return offerJson;
}

/**
 * Recover from a failed proof POST; always throws. One wrong PIN
 * (PROOF_INVALID) or a timeout (PIN_EXPIRED) kills the generation
 * server-side: refresh the offer (byte-identical intent) so the desktop shows
 * a fresh PIN, then ask the user to re-enter via "pin_expired". Any other
 * failure (network flake, transient server error, or a proof that WAS
 * accepted while its response got lost) leaves a session that cannot be
 * proven again — replace it with a fresh one so the user can retry with the
 * new PIN, and surface the original error.
 */
async function recoverFromV3ProofFailure(
  attempt: ConnectingAttempt,
  v3: PairingV3Initiator,
  appInstanceId: string,
  config: PairingV3Config,
  e: unknown,
): Promise<never> {
  if (
    e instanceof SyncApiError &&
    (e.errorCode === StandardErrorCode.PAIRING_PIN_EXPIRED ||
      e.errorCode === StandardErrorCode.PAIRING_PROOF_INVALID)
  ) {
    try {
      const offerJson = await refreshOfferForNewGeneration(config, v3);
      const refusal = await v3.acceptOffer(offerJson);
      if (refusal === null || refusal === undefined) {
        throw new Error("pin_expired");
      }
    } catch (inner) {
      if (inner instanceof Error && inner.message === "pin_expired") throw inner;
      // Refresh refused (e.g. session consumed): fall through to restart.
    }
    await restartV3Session(attempt, appInstanceId, config);
    throw new Error("pin_expired");
  }
  await restartV3SessionQuietly(attempt, appInstanceId, config);
  throw e;
}

/**
 * POST the commit until an ACK arrives. The commit bytes are deterministic
 * for this session, so bounded retries on transport failure are idempotent —
 * mirrors the desktop initiator. This closes most of the "acceptor trusted us
 * but our ACK got lost" window. On definitive refusal or exhausted retries
 * the session is replaced (clearing `finalizing` first so a pending cancel is
 * honoured) and the failure is thrown.
 */
async function commitV3WithRetries(
  attempt: ConnectingAttempt,
  appInstanceId: string,
  config: PairingV3Config,
  commitJson: string,
  syncInfo: SyncInfo,
): Promise<string> {
  let lastCommitError: unknown = null;
  for (let i = 0; i < V3_COMMIT_ATTEMPTS; i++) {
    try {
      return await SyncApi.pairingV3Commit(config, commitJson, syncInfo);
    } catch (e) {
      if (e instanceof SyncApiError) {
        // Definitive refusal — the session is consumed or invalid. Replace
        // it so the next attempt does not hit a dead engine.
        attempt.finalizing = false;
        await restartV3SessionQuietly(attempt, appInstanceId, config);
        throw e;
      }
      lastCommitError = e;
      if (i < V3_COMMIT_ATTEMPTS - 1) {
        await delayMs(V3_COMMIT_RETRY_DELAY_MS);
      }
    }
  }
  // All commit attempts lost. The acceptor may have trusted us already; a
  // fresh session on the next try converges both sides (it re-persists the
  // same long-term keys).
  attempt.finalizing = false;
  await restartV3SessionQuietly(attempt, appInstanceId, config);
  throw lastCommitError instanceof Error ? lastCommitError : new Error(String(lastCommitError));
}

/** v3: SPAKE2 proof with the PIN shown on the desktop's device card. */
async function pairV3(attempt: ConnectingAttempt, token: number): Promise<void> {
  const v3 = attempt.v3;
  if (!v3) throw new Error("Not connected");
  const appInstanceId = await getAppInstanceId();
  const config: PairingV3Config = {
    host: attempt.host,
    port: attempt.port,
    appInstanceId,
    targetAppInstanceId: attempt.targetAppInstanceId,
  };
  // TokenInput yields a number; the PIN is 6 digits with leading zeros preserved.
  const pin = String(token).padStart(6, "0");
  const proofJson = await v3.buildProof(pin);
  let proofResponseJson: string;
  try {
    proofResponseJson = await SyncApi.pairingV3Proof(config, proofJson);
  } catch (e) {
    return recoverFromV3ProofFailure(attempt, v3, appInstanceId, config, e);
  }
  if (!(await v3.verifyProofResponse(proofResponseJson))) {
    // The engine hard-failed (possible MITM); it cannot build another proof.
    // Replace the session so the next attempt starts clean with a fresh PIN.
    await restartV3SessionQuietly(attempt, appInstanceId, config);
    throw new Error("verification_failed");
  }
  const commitJson = await v3.buildCommit();
  const syncInfo = buildExtensionSyncInfo(appInstanceId);
  attempt.finalizing = true;
  try {
    const ackJson = await commitV3WithRetries(attempt, appInstanceId, config, commitJson, syncInfo);
    if (!(await v3.verifyCommitAck(ackJson))) {
      // Invalid receipt after an accepted commit: hard failure — replace the
      // session so a retry is possible at all.
      attempt.finalizing = false;
      await restartV3SessionQuietly(attempt, appInstanceId, config);
      throw new Error("verification_failed");
    }
    await completePairing(attempt, {
      signPublicKey: CrossPasteHash.base64Encode(v3.acceptorSignPublicKey()),
      cryptPublicKey: CrossPasteHash.base64Encode(v3.acceptorCryptPublicKey()),
    });
  } finally {
    attempt.finalizing = false;
  }
}

async function handlePair(token: number, attemptId?: number): Promise<unknown> {
  // Snapshot: every step of this attempt (including persistence) binds to the
  // state captured here, not to whatever a concurrent CONNECT may install.
  const attempt = connectingState;
  try {
    if (!attempt) throw new Error("Not connected");
    // A PAIR from a stale dialog must not drive a newer attempt.
    if (attemptId !== undefined && attempt.id !== attemptId) {
      throw new Error("pairing attempt superseded");
    }
    switch (attempt.pairingMode) {
      case 3:
        await pairV3(attempt, token);
        break;
      case 2:
        await pairV2(attempt, token);
        break;
      default:
        await pairV1(attempt, token);
    }
    return { success: true };
  } catch (e) {
    if (attempt) {
      // A cancel arrived while the trust round-trip was finalizing; the
      // pairing failed, so honour it now.
      if (attempt.cancelRequested) {
        if (connectingState === attempt) {
          connectingState = null;
        }
        await releaseAttempt(attempt);
        await updateRuntime(attempt.targetAppInstanceId, (s) => { s.connecting = false; });
      } else if (!(e instanceof Error && e.message === "pin_expired")) {
        // A rotated v3 PIN keeps the attempt alive; anything else ends it.
        await updateRuntime(attempt.targetAppInstanceId, (s) => { s.connecting = false; });
      }
    }
    return { success: false, error: e instanceof Error ? e.message : String(e) };
  }
}

/**
 * The user closed the pairing dialog (or navigated away) mid-attempt: release
 * the desktop-side session / key material and clear the connecting flag.
 */
async function handleCancelConnect(attemptId?: number): Promise<unknown> {
  const attempt = connectingState;
  // A cancel scoped to a different (older) attempt must not touch the
  // currently installed one — e.g. another side panel's dialog closing.
  if (!attempt || (attemptId !== undefined && attempt.id !== attemptId)) {
    return { success: true };
  }
  // The desktop-persisting round-trip is in flight: cancelling now could
  // leave the desktop trusting us while we discard its keys. Latch the
  // request; the failure path honours it once the round-trip settles (a
  // successful completion ignores it — both sides are consistent then).
  if (attempt.finalizing) {
    attempt.cancelRequested = true;
    return { success: false, finalizing: true };
  }
  connectingState = null;
  await releaseAttempt(attempt);
  await updateRuntime(attempt.targetAppInstanceId, (s) => { s.connecting = false; });
  return { success: true };
}

async function handleRePair(targetAppInstanceId: string): Promise<unknown> {
  const seq = ++connectSeq;
  const device = await DeviceStore.get(targetAppInstanceId);
  if (!device) return { success: false, error: "Device not found" };
  if (!(await hasHostPermission(device.host, device.port))) {
    return { success: false, error: "host_permission_denied" };
  }
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

    // Refresh the desktop's SyncInfo — the stored copy may predate a desktop
    // upgrade that changed its advertised pairingVersion. If a DIFFERENT
    // identity answers at that address, abort: the v1/v2 servers don't verify
    // targetAppInstanceId, so continuing would bind a stranger's keys to this
    // device record.
    const refreshed = await SyncApi.getSyncInfo(config);
    if (refreshed.appInfo.appInstanceId !== targetAppInstanceId) {
      return { success: false, error: "device_identity_changed" };
    }
    const { pairingMode, attemptId } = await beginPairing(seq, device.host, device.port, refreshed);
    return { success: true, syncInfo: refreshed, pairingMode, attemptId };
  } catch (e) {
    await updateRuntime(targetAppInstanceId, (s) => { s.connecting = false; });
    return { success: false, error: e instanceof Error ? e.message : String(e) };
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
    case "PAIR": return handlePair(message.token as number, message.attemptId as number | undefined);
    case "CANCEL_CONNECT": return handleCancelConnect(message.attemptId as number | undefined);
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

import { WsClient } from "./ws-client";
import type { WsEnvelope, WsConnectionStatus } from "./ws-types";
import type { StoredDevice } from "@/shared/storage/device-store";
import { DeviceStore } from "@/shared/storage/device-store";

const BACKOFF_BASE_MS = 1_000;
const BACKOFF_MAX_MS = 30_000;
const MAX_FAILURES_BEFORE_HTTP_ONLY = 5;

/** Delay before considering a connection "stable" and resetting failure count. */
const STABLE_CONNECTION_MS = 5_000;

interface DeviceConnection {
  client: WsClient;
  reconnectTimer: ReturnType<typeof setTimeout> | null;
  stabilityTimer: ReturnType<typeof setTimeout> | null;
  consecutiveFailures: number;
}

/**
 * Multi-device WebSocket connection manager.
 * Manages connections to all paired desktop devices with reconnection and fallback.
 */
export class WsManager {
  private sessions = new Map<string, DeviceConnection>();
  private appInstanceId: string;

  onMessage: ((targetAppInstanceId: string, envelope: WsEnvelope) => void) | null = null;
  onStatusChange: ((targetAppInstanceId: string, status: WsConnectionStatus) => void) | null = null;

  constructor(appInstanceId: string) {
    this.appInstanceId = appInstanceId;
  }

  /**
   * Connect to a specific device via WebSocket.
   * Returns true if already connected or connection succeeds.
   */
  async connectDevice(device: StoredDevice): Promise<boolean> {
    const existing = this.sessions.get(device.targetAppInstanceId);
    if (existing?.client.isActive) return true;

    // Cancel any pending reconnect
    if (existing?.reconnectTimer) {
      clearTimeout(existing.reconnectTimer);
    }

    const client = new WsClient({
      host: device.host,
      port: device.port,
      appInstanceId: this.appInstanceId,
      targetAppInstanceId: device.targetAppInstanceId,
    });

    const conn: DeviceConnection = {
      client,
      reconnectTimer: null,
      stabilityTimer: null,
      consecutiveFailures: existing?.consecutiveFailures ?? 0,
    };
    this.sessions.set(device.targetAppInstanceId, conn);

    client.onMessage = (envelope) => {
      this.onMessage?.(device.targetAppInstanceId, envelope);
    };

    client.onDisconnect = (reason) => {
      console.log(`[WsManager] Disconnected from ${device.targetAppInstanceId}: ${reason}`);
      if (conn.stabilityTimer) {
        clearTimeout(conn.stabilityTimer);
        conn.stabilityTimer = null;
      }
      this.scheduleReconnect(device);
    };

    client.onConnect = () => {
      console.log(`[WsManager] Connected to ${device.targetAppInstanceId}`);
      this.onStatusChange?.(device.targetAppInstanceId, "ws_connected");
      // Only reset failure count after the connection proves stable
      conn.stabilityTimer = setTimeout(() => {
        conn.stabilityTimer = null;
        if (conn.client.isActive) {
          conn.consecutiveFailures = 0;
        }
      }, STABLE_CONNECTION_MS);
    };

    const success = await client.connect();
    if (!success) {
      this.scheduleReconnect(device);
      return false;
    }

    return true;
  }

  /**
   * Connect to all trusted devices that don't have an active WebSocket.
   */
  async connectAllDevices(): Promise<void> {
    const devices = await DeviceStore.getAll();
    // Reset failure counts for http_only devices so they get a fresh attempt
    for (const d of devices) {
      const conn = this.sessions.get(d.targetAppInstanceId);
      if (conn && conn.consecutiveFailures >= MAX_FAILURES_BEFORE_HTTP_ONLY) {
        conn.consecutiveFailures = 0;
      }
    }
    const promises = devices
      .filter((d) => d.trusted && !this.isConnected(d.targetAppInstanceId))
      .map((d) => this.connectDevice(d).catch(() => false));
    await Promise.allSettled(promises);
  }

  /**
   * Disconnect a specific device.
   */
  disconnectDevice(targetAppInstanceId: string): void {
    const conn = this.sessions.get(targetAppInstanceId);
    if (!conn) return;

    if (conn.reconnectTimer) clearTimeout(conn.reconnectTimer);
    if (conn.stabilityTimer) clearTimeout(conn.stabilityTimer);
    conn.client.close();
    this.sessions.delete(targetAppInstanceId);
  }

  /**
   * Disconnect all devices.
   */
  disconnectAll(): void {
    for (const [id] of this.sessions) {
      this.disconnectDevice(id);
    }
  }

  /**
   * Check if a device has an active WebSocket connection.
   */
  isConnected(targetAppInstanceId: string): boolean {
    return this.sessions.get(targetAppInstanceId)?.client.isActive === true;
  }

  /**
   * Send an envelope to a connected device.
   * Returns false if the device is not connected.
   */
  async send(targetAppInstanceId: string, envelope: WsEnvelope): Promise<boolean> {
    const conn = this.sessions.get(targetAppInstanceId);
    if (!conn?.client.isActive) return false;

    try {
      await conn.client.sendEnvelope(envelope);
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Send a request to a connected device and wait for a correlated response.
   * Throws if the device is not connected or the request times out.
   */
  async sendRequest(targetAppInstanceId: string, envelope: WsEnvelope): Promise<WsEnvelope> {
    const conn = this.sessions.get(targetAppInstanceId);
    if (!conn?.client.isActive) {
      throw new Error(`Device ${targetAppInstanceId} not connected`);
    }
    return conn.client.sendRequest(envelope);
  }

  /**
   * Get connection status for all known devices.
   */
  getConnectionStates(): Record<string, WsConnectionStatus> {
    const states: Record<string, WsConnectionStatus> = {};
    for (const [id, conn] of this.sessions) {
      if (conn.client.isActive) {
        states[id] = "ws_connected";
      } else if (conn.consecutiveFailures >= MAX_FAILURES_BEFORE_HTTP_ONLY) {
        states[id] = "http_only";
      } else {
        states[id] = "ws_reconnecting";
      }
    }
    return states;
  }

  // ─── Reconnection ──────────────────────────────────────────────────

  private scheduleReconnect(device: StoredDevice): void {
    const conn = this.sessions.get(device.targetAppInstanceId);
    if (!conn) return;

    if (conn.reconnectTimer) clearTimeout(conn.reconnectTimer);
    conn.consecutiveFailures++;

    const status: WsConnectionStatus =
      conn.consecutiveFailures >= MAX_FAILURES_BEFORE_HTTP_ONLY ? "http_only" : "ws_reconnecting";
    this.onStatusChange?.(device.targetAppInstanceId, status);

    if (status === "http_only") {
      // Stop trying until next connectAllDevices() call resets
      return;
    }

    const delay = Math.min(
      BACKOFF_BASE_MS * Math.pow(2, conn.consecutiveFailures - 1),
      BACKOFF_MAX_MS,
    );

    console.log(
      `[WsManager] Reconnecting to ${device.targetAppInstanceId} in ${delay}ms ` +
      `(attempt ${conn.consecutiveFailures})`,
    );

    conn.reconnectTimer = setTimeout(() => {
      conn.reconnectTimer = null;
      this.connectDevice(device).catch(() => {
        // Error handled inside connectDevice
      });
    }, delay);
  }
}

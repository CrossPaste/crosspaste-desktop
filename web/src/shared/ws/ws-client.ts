import {
  type WsEnvelope,
  type WsEnvelopeHeader,
  WsMessageType,
  simpleEnvelope,
  toHeader,
} from "./ws-types";

const HEARTBEAT_INTERVAL_MS = 20_000;
const HEARTBEAT_ACK_TIMEOUT_MS = 10_000;

export type WsClientState = "idle" | "connecting" | "connected" | "closed";

export interface WsClientConfig {
  host: string;
  port: number;
  appInstanceId: string;
  targetAppInstanceId: string;
}

/**
 * Single-device WebSocket client.
 * Manages one WebSocket connection to one desktop device.
 *
 * Wire protocol: Text frame (JSON WsEnvelopeHeader) + optional Binary frame (payload).
 * A send queue ensures frame pairs are not interleaved by concurrent callers.
 */
export class WsClient {
  private ws: WebSocket | null = null;
  private state: WsClientState = "idle";

  // Frame parsing state
  private pendingHeader: WsEnvelopeHeader | null = null;

  // Heartbeat
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null;
  private heartbeatAckTimer: ReturnType<typeof setTimeout> | null = null;

  // Send queue — each entry is a group of frames that must be sent atomically
  private sendQueue: Array<{
    frames: Array<ArrayBuffer | string>;
    resolve: () => void;
    reject: (e: Error) => void;
  }> = [];
  private isSending = false;

  readonly config: WsClientConfig;

  onMessage: ((envelope: WsEnvelope) => void) | null = null;
  onDisconnect: ((reason: string) => void) | null = null;
  onConnect: (() => void) | null = null;

  constructor(config: WsClientConfig) {
    this.config = config;
  }

  get isActive(): boolean {
    return this.ws !== null && this.ws.readyState === WebSocket.OPEN;
  }

  get currentState(): WsClientState {
    return this.state;
  }

  /**
   * Open a WebSocket connection. Resolves true on success, false on failure.
   */
  connect(): Promise<boolean> {
    if (this.state === "connected" || this.state === "connecting") {
      return Promise.resolve(this.state === "connected");
    }

    this.state = "connecting";

    return new Promise<boolean>((resolve) => {
      const url =
        `ws://${this.config.host}:${this.config.port}/ws/sync` +
        `?appInstanceId=${encodeURIComponent(this.config.appInstanceId)}` +
        `&targetAppInstanceId=${encodeURIComponent(this.config.targetAppInstanceId)}`;

      const ws = new WebSocket(url);
      ws.binaryType = "arraybuffer";

      ws.onopen = () => {
        this.ws = ws;
        this.state = "connected";
        this.pendingHeader = null;
        this.startHeartbeat();
        this.onConnect?.();
        resolve(true);
      };

      ws.onerror = () => {
        if (this.state === "connecting") {
          this.state = "closed";
          resolve(false);
        }
      };

      ws.onclose = (event) => {
        const wasConnected = this.state === "connected";
        this.cleanup();
        if (wasConnected) {
          this.onDisconnect?.(event.reason || "Connection closed");
        } else if (this.state === "connecting") {
          this.state = "closed";
          resolve(false);
        }
      };

      ws.onmessage = (event) => {
        this.handleFrame(event.data);
      };
    });
  }

  /**
   * Send a logical envelope (header Text frame + optional Binary payload frame).
   * The frame group is enqueued atomically so concurrent callers cannot interleave.
   */
  async sendEnvelope(envelope: WsEnvelope): Promise<void> {
    if (!this.isActive) {
      throw new Error("WebSocket not connected");
    }

    const headerJson = JSON.stringify(toHeader(envelope));
    const frames: Array<ArrayBuffer | string> = [headerJson];

    if (envelope.payload.length > 0) {
      const buf = new ArrayBuffer(envelope.payload.byteLength);
      new Uint8Array(buf).set(envelope.payload);
      frames.push(buf);
    }

    await this.enqueueFrameGroup(frames);
  }

  /**
   * Close the connection gracefully.
   */
  close(): void {
    const ws = this.ws;
    this.cleanup();
    // Send close frame after cleanup to prevent onclose from firing again
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.close(1000, "Normal closure");
    }
  }

  // ─── Frame parsing ──────────────────────────────────────────────────

  private handleFrame(data: unknown): void {
    if (typeof data === "string") {
      // Text frame: JSON header
      try {
        const header = JSON.parse(data) as WsEnvelopeHeader;
        if (header.hasPayload) {
          // Wait for the next Binary frame
          this.pendingHeader = header;
        } else {
          // Complete message with no payload
          this.dispatchEnvelope({
            type: header.type,
            payload: new Uint8Array(0),
            encrypted: header.encrypted,
            requestId: header.requestId,
          });
        }
      } catch {
        console.error("[WsClient] Failed to parse text frame");
      }
    } else if (data instanceof ArrayBuffer) {
      // Binary frame: payload for the pending header
      if (this.pendingHeader) {
        const header = this.pendingHeader;
        this.pendingHeader = null;
        this.dispatchEnvelope({
          type: header.type,
          payload: new Uint8Array(data),
          encrypted: header.encrypted,
          requestId: header.requestId,
        });
      } else {
        console.warn("[WsClient] Received unexpected binary frame");
      }
    }
  }

  private dispatchEnvelope(envelope: WsEnvelope): void {
    // Handle heartbeat_ack internally
    if (envelope.type === WsMessageType.HEARTBEAT_ACK) {
      this.clearHeartbeatAckTimer();
      return;
    }

    this.onMessage?.(envelope);
  }

  // ─── Heartbeat ──────────────────────────────────────────────────────

  private startHeartbeat(): void {
    this.stopHeartbeat();
    this.heartbeatTimer = setInterval(() => {
      if (!this.isActive) return;
      this.sendEnvelope(simpleEnvelope(WsMessageType.HEARTBEAT)).catch(() => {
        // Send failed, connection is likely dead
      });

      // Expect ack within timeout
      this.heartbeatAckTimer = setTimeout(() => {
        console.warn("[WsClient] Heartbeat ACK timeout, closing connection");
        this.ws?.close(4000, "Heartbeat timeout");
      }, HEARTBEAT_ACK_TIMEOUT_MS);
    }, HEARTBEAT_INTERVAL_MS);
  }

  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
    this.clearHeartbeatAckTimer();
  }

  private clearHeartbeatAckTimer(): void {
    if (this.heartbeatAckTimer) {
      clearTimeout(this.heartbeatAckTimer);
      this.heartbeatAckTimer = null;
    }
  }

  // ─── Send queue ─────────────────────────────────────────────────────

  private enqueueFrameGroup(frames: Array<ArrayBuffer | string>): Promise<void> {
    return new Promise((resolve, reject) => {
      this.sendQueue.push({ frames, resolve, reject });
      this.drainQueue();
    });
  }

  private drainQueue(): void {
    if (this.isSending || this.sendQueue.length === 0) return;
    this.isSending = true;

    const item = this.sendQueue.shift()!;
    try {
      // Send all frames in the group synchronously to guarantee atomicity
      for (const frame of item.frames) {
        this.ws!.send(frame);
      }
      item.resolve();
    } catch (e) {
      item.reject(e instanceof Error ? e : new Error(String(e)));
    }

    this.isSending = false;
    if (this.sendQueue.length > 0) {
      this.drainQueue();
    }
  }

  // ─── Cleanup ────────────────────────────────────────────────────────

  private cleanup(): void {
    this.stopHeartbeat();
    if (this.ws) {
      this.ws.onopen = null;
      this.ws.onclose = null;
      this.ws.onerror = null;
      this.ws.onmessage = null;
    }
    this.ws = null;
    this.state = "closed";
    this.pendingHeader = null;
    // Reject any pending sends
    for (const item of this.sendQueue) {
      item.reject(new Error("Connection closed"));
    }
    this.sendQueue = [];
    this.isSending = false;
  }
}

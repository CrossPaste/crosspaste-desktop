import {
  type WsEnvelope,
  type WsEnvelopeHeader,
  WsMessageType,
  simpleEnvelope,
} from "./ws-types";
import {
  type WsAuthChallenge,
  type WsAuthProcessor,
  type WsAuthProofMessage,
  WS_AUTH_VERSION,
  WsAuthContext,
  WS_SUPPORTED_CAPABILITIES,
  clientProofCode,
  verifyServerProof,
} from "./ws-auth";

const HEARTBEAT_INTERVAL_MS = 20_000;
const HEARTBEAT_ACK_TIMEOUT_MS = 10_000;
const DEFAULT_REQUEST_TIMEOUT_MS = 30_000;

/** Mirrors desktop WS_AUTHENTICATION_TIMEOUT (5s). */
const AUTHENTICATION_TIMEOUT_MS = 5_000;

/** Bound messages received before the server proof finishes verification. */
const MAX_HANDSHAKE_BUFFERED_MESSAGES = 4;

/** Mirrors desktop WS_MAX_PAYLOAD_SIZE / WS_MAX_PAYLOAD_CHUNK_COUNT. */
const MAX_PAYLOAD_SIZE = 128 * 1024 * 1024;
const MAX_PAYLOAD_CHUNK_COUNT = 128;

export type WsClientState = "idle" | "connecting" | "connected" | "closed";

export interface WsClientConfig {
  host: string;
  port: number;
  appInstanceId: string;
  targetAppInstanceId: string;
  /**
   * Builds the per-peer MAC processor (K/JS SecureMessageProcessor) from the
   * stored key material. Called once per connection attempt; a rejection
   * fails the connect (no key material -> cannot authenticate).
   */
  createAuthProcessor: () => Promise<WsAuthProcessor>;
  /** Advertised in AUTH_PROOF; >= 3 lets the peer send chunked payloads. */
  pairingVersion: number;
}

interface PendingRequest {
  resolve: (envelope: WsEnvelope) => void;
  reject: (error: Error) => void;
  timer: ReturnType<typeof setTimeout>;
}

/** A fully reassembled incoming message: wire header + payload bytes. */
interface IncomingMessage {
  header: WsEnvelopeHeader;
  payload: Uint8Array;
}

interface HandshakeWaiter {
  resolve: (message: IncomingMessage) => void;
  reject: (error: Error) => void;
  timer: ReturnType<typeof setTimeout>;
}

interface ActiveConnect {
  generation: number;
  settle: (connected: boolean) => void;
}

/**
 * Single-device WebSocket client.
 * Manages one WebSocket connection to one desktop device.
 *
 * Wire protocol: Text frame (JSON WsEnvelopeHeader) + `payloadChunkCount`
 * Binary frames (payload). The connection is only reported as connected after
 * the desktop's authentication handshake (AUTH_CHALLENGE → AUTH_PROOF →
 * AUTH_ACK) succeeds; from then on every envelope in both directions carries
 * a sequence-numbered HMAC (see ws-auth.ts). A send queue ensures sequence
 * numbers are assigned in the exact order frames hit the wire.
 */
export class WsClient {
  private ws: WebSocket | null = null;
  private state: WsClientState = "idle";

  // Frame parsing state
  private pendingHeader: WsEnvelopeHeader | null = null;
  private pendingChunks: Uint8Array[] = [];
  private pendingChunksSize = 0;

  // Authentication
  private authContext: WsAuthContext | null = null;
  private handshakeWaiter: HandshakeWaiter | null = null;
  // Messages that arrive while the handshake logic is between awaits (e.g.
  // the server's AUTH_CHALLENGE lands while createAuthProcessor is still
  // deriving keys). Bounded; handshake frames are drained by
  // nextHandshakeMessage and post-ACK frames by runHandshake.
  private handshakeBuffer: IncomingMessage[] = [];
  // Invalidates async work from a socket that closed or was superseded.
  private connectionGeneration = 0;
  private activeConnect: ActiveConnect | null = null;
  // Serializes incoming verification so receive sequence numbers are checked
  // in arrival order even though verification is async.
  private incomingChain: Promise<void> = Promise.resolve();

  // Heartbeat
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null;
  private heartbeatAckTimer: ReturnType<typeof setTimeout> | null = null;

  // Send queue — envelopes are MAC'd and written strictly one at a time so
  // auth sequence numbers match wire order and frame groups never interleave.
  private sendQueue: Array<{
    envelope: WsEnvelope;
    resolve: () => void;
    reject: (e: Error) => void;
  }> = [];
  private isSending = false;

  // Request-response tracking
  private pendingRequests: Map<string, PendingRequest> = new Map();

  readonly config: WsClientConfig;

  onMessage: ((envelope: WsEnvelope) => void) | null = null;
  onDisconnect: ((reason: string) => void) | null = null;
  onConnect: (() => void) | null = null;

  constructor(config: WsClientConfig) {
    this.config = config;
  }

  get isActive(): boolean {
    return (
      this.state === "connected" && this.ws !== null && this.ws.readyState === WebSocket.OPEN
    );
  }

  get currentState(): WsClientState {
    return this.state;
  }

  /**
   * Open a WebSocket connection and run the authentication handshake.
   * Resolves true only after AUTH_ACK verifies; false on any failure.
   */
  connect(): Promise<boolean> {
    if (this.state === "connected" || this.state === "connecting") {
      return Promise.resolve(this.state === "connected");
    }

    this.state = "connecting";
    const connectionGeneration = ++this.connectionGeneration;

    return new Promise<boolean>((resolve) => {
      let settled = false;
      const settle = (value: boolean) => {
        if (!settled) {
          settled = true;
          if (this.activeConnect?.generation === connectionGeneration) {
            this.activeConnect = null;
          }
          resolve(value);
        }
      };
      this.activeConnect = { generation: connectionGeneration, settle };

      const url =
        `ws://${this.config.host}:${this.config.port}/ws/sync` +
        `?appInstanceId=${encodeURIComponent(this.config.appInstanceId)}` +
        `&targetAppInstanceId=${encodeURIComponent(this.config.targetAppInstanceId)}` +
        `&authVersion=${WS_AUTH_VERSION}`;

      const ws = new WebSocket(url);
      ws.binaryType = "arraybuffer";
      this.ws = ws;
      const isCurrentConnection = () =>
        this.connectionGeneration === connectionGeneration && this.ws === ws;

      ws.onopen = () => {
        if (!isCurrentConnection()) return;
        this.pendingHeader = null;
        this.runHandshake(ws, connectionGeneration)
          .then(() => {
            if (
              !isCurrentConnection() ||
              this.state !== "connecting" ||
              ws.readyState !== WebSocket.OPEN
            ) {
              settle(false);
              return;
            }
            this.state = "connected";
            this.startHeartbeat();
            this.onConnect?.();
            settle(true);
          })
          .catch((e) => {
            if (!isCurrentConnection()) {
              settle(false);
              return;
            }
            console.warn(`[WsClient] Authentication handshake failed: ${e}`);
            this.cleanup("Authentication failed");
            if (ws.readyState === WebSocket.OPEN) {
              ws.close(1000, "Authentication failed");
            }
            settle(false);
          });
      };

      ws.onerror = () => {
        if (isCurrentConnection() && this.state === "connecting") {
          this.cleanup("WebSocket connection error");
          if (
            ws.readyState === WebSocket.CONNECTING ||
            ws.readyState === WebSocket.OPEN
          ) {
            ws.close(1000, "Connection error");
          }
          settle(false);
        }
      };

      ws.onclose = (event) => {
        if (!isCurrentConnection()) {
          settle(false);
          return;
        }
        const wasConnected = this.state === "connected";
        this.cleanup(event.reason || "Connection closed");
        if (wasConnected) {
          this.onDisconnect?.(event.reason || "Connection closed");
        } else {
          settle(false);
        }
      };

      ws.onmessage = (event) => {
        if (!isCurrentConnection()) return;
        this.handleFrame(event.data);
      };
    });
  }

  /**
   * Desktop-mirroring client side of the handshake (WsClientConnector.kt):
   * receive AUTH_CHALLENGE, answer with our HMAC proof, verify the server's
   * proof in AUTH_ACK, then arm the per-envelope MAC context.
   */
  private async runHandshake(ws: WebSocket, connectionGeneration: number): Promise<void> {
    const processor = await this.config.createAuthProcessor();
    this.ensureCurrentHandshake(ws, connectionGeneration);

    const challengeMessage = await this.nextHandshakeMessage();
    this.ensureCurrentHandshake(ws, connectionGeneration);
    if (challengeMessage.header.type !== WsMessageType.AUTH_CHALLENGE) {
      throw new Error(`Expected auth challenge, got ${challengeMessage.header.type}`);
    }
    const challenge = JSON.parse(
      new TextDecoder().decode(challengeMessage.payload),
    ) as WsAuthChallenge;

    const proof: WsAuthProofMessage = {
      authenticationCode: await clientProofCode(
        processor,
        this.config.appInstanceId,
        this.config.targetAppInstanceId,
        challenge,
      ),
      pairingVersion: this.config.pairingVersion,
      capabilities: [...WS_SUPPORTED_CAPABILITIES],
    };
    this.ensureCurrentHandshake(ws, connectionGeneration);
    await this.sendRawEnvelope({
      type: WsMessageType.AUTH_PROOF,
      payload: new TextEncoder().encode(JSON.stringify(proof)),
      encrypted: false,
    });

    const ackMessage = await this.nextHandshakeMessage();
    this.ensureCurrentHandshake(ws, connectionGeneration);
    if (ackMessage.header.type !== WsMessageType.AUTH_ACK) {
      throw new Error(`Expected auth ack, got ${ackMessage.header.type}`);
    }
    const ack = JSON.parse(new TextDecoder().decode(ackMessage.payload)) as WsAuthProofMessage;
    const serverVerified = await verifyServerProof(
      processor,
      this.config.appInstanceId,
      this.config.targetAppInstanceId,
      challenge,
      ack,
    );
    this.ensureCurrentHandshake(ws, connectionGeneration);
    if (!serverVerified) {
      throw new Error("Server authentication proof invalid");
    }

    const authContext = new WsAuthContext(
      challenge.sessionId,
      this.config.appInstanceId,
      this.config.targetAppInstanceId,
      processor,
    );
    this.authContext = authContext;

    // AUTH_ACK is the server's final handshake frame. The server publishes the
    // session immediately afterwards, so authenticated application envelopes
    // can arrive while verifyServerProof is still awaiting WebCrypto. Move any
    // such frames into the authenticated receive chain without losing order.
    const bufferedMessages = this.handshakeBuffer.splice(0);
    for (const message of bufferedMessages) {
      this.handleIncoming(message);
    }
    await this.incomingChain;
    this.ensureCurrentHandshake(ws, connectionGeneration);
  }

  private ensureCurrentHandshake(ws: WebSocket, connectionGeneration: number): void {
    if (
      this.connectionGeneration !== connectionGeneration ||
      this.ws !== ws ||
      this.state !== "connecting" ||
      ws.readyState !== WebSocket.OPEN
    ) {
      throw new Error("WebSocket connection changed during authentication");
    }
  }

  private nextHandshakeMessage(): Promise<IncomingMessage> {
    const buffered = this.handshakeBuffer.shift();
    if (buffered) {
      return Promise.resolve(buffered);
    }
    return new Promise<IncomingMessage>((resolve, reject) => {
      const timer = setTimeout(() => {
        this.handshakeWaiter = null;
        reject(new Error("Authentication handshake timed out"));
      }, AUTHENTICATION_TIMEOUT_MS);
      this.handshakeWaiter = { resolve, reject, timer };
    });
  }

  /**
   * Send a logical envelope with an authenticated header.
   * The envelope is enqueued atomically so concurrent callers cannot
   * interleave frames or reorder auth sequence numbers.
   */
  async sendEnvelope(envelope: WsEnvelope): Promise<void> {
    if (!this.isActive) {
      throw new Error("WebSocket not connected");
    }

    return new Promise((resolve, reject) => {
      this.sendQueue.push({ envelope, resolve, reject });
      void this.drainQueue();
    });
  }

  /**
   * Send a request envelope and wait for the correlated response.
   * Generates a requestId, sends the envelope, and resolves when a
   * response with the same requestId arrives (or rejects on timeout).
   */
  sendRequest(
    envelope: WsEnvelope,
    timeoutMs: number = DEFAULT_REQUEST_TIMEOUT_MS,
  ): Promise<WsEnvelope> {
    const requestId = crypto.randomUUID();
    const requestEnvelope: WsEnvelope = { ...envelope, requestId };

    return new Promise<WsEnvelope>((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pendingRequests.delete(requestId);
        reject(new Error(`Request ${requestId} timed out after ${timeoutMs}ms`));
      }, timeoutMs);

      this.pendingRequests.set(requestId, { resolve, reject, timer });

      this.sendEnvelope(requestEnvelope).catch((e) => {
        this.pendingRequests.delete(requestId);
        clearTimeout(timer);
        reject(e);
      });
    });
  }

  /**
   * Close the connection gracefully.
   */
  close(): void {
    const ws = this.ws;
    this.cleanup();
    // Send close frame after cleanup to prevent onclose from firing again
    if (
      ws &&
      (ws.readyState === WebSocket.CONNECTING || ws.readyState === WebSocket.OPEN)
    ) {
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
          const chunkCount = header.payloadChunkCount ?? 1;
          if (chunkCount < 1 || chunkCount > MAX_PAYLOAD_CHUNK_COUNT) {
            console.error(`[WsClient] Invalid payload chunk count: ${chunkCount}`);
            this.ws?.close(1008, "Invalid payload chunk count");
            return;
          }
          // Wait for the next `chunkCount` Binary frames
          this.pendingHeader = header;
          this.pendingChunks = [];
          this.pendingChunksSize = 0;
        } else {
          this.handleIncoming({ header, payload: new Uint8Array(0) });
        }
      } catch {
        console.error("[WsClient] Failed to parse text frame");
      }
    } else if (data instanceof ArrayBuffer) {
      // Binary frame: payload chunk for the pending header
      const header = this.pendingHeader;
      if (!header) {
        console.warn("[WsClient] Received unexpected binary frame");
        return;
      }
      this.pendingChunksSize += data.byteLength;
      if (this.pendingChunksSize > MAX_PAYLOAD_SIZE) {
        console.error("[WsClient] Payload exceeds maximum size");
        this.ws?.close(1009, "Payload too large");
        return;
      }
      this.pendingChunks.push(new Uint8Array(data));
      if (this.pendingChunks.length < (header.payloadChunkCount ?? 1)) {
        return;
      }

      const chunks = this.pendingChunks;
      this.pendingHeader = null;
      this.pendingChunks = [];
      let payload: Uint8Array;
      if (chunks.length === 1) {
        payload = chunks[0];
      } else {
        payload = new Uint8Array(this.pendingChunksSize);
        let offset = 0;
        for (const chunk of chunks) {
          payload.set(chunk, offset);
          offset += chunk.length;
        }
      }
      this.pendingChunksSize = 0;
      this.handleIncoming({ header, payload });
    }
  }

  private handleIncoming(message: IncomingMessage): void {
    const authContext = this.authContext;
    if (!authContext) {
      // Handshake in progress: route to the pending handshake step (these
      // messages are authenticated by their proof payloads, not header MACs)
      // or buffer briefly until the handshake logic asks for the next one.
      if (this.handshakeWaiter) {
        const waiter = this.handshakeWaiter;
        this.handshakeWaiter = null;
        clearTimeout(waiter.timer);
        waiter.resolve(message);
      } else if (this.state === "connecting") {
        if (this.handshakeBuffer.length >= MAX_HANDSHAKE_BUFFERED_MESSAGES) {
          console.warn("[WsClient] Authentication message buffer overflow");
          this.ws?.close(1008, "Authentication message buffer overflow");
          return;
        }
        this.handshakeBuffer.push(message);
      }
      return;
    }

    // Verification is async; chain it so sequence checks run in arrival order.
    this.incomingChain = this.incomingChain.then(async () => {
      const valid = await authContext.verify(message.header, message.payload);
      // A cleanup (disconnect) may have run while verifying; drop stale messages.
      if (this.authContext !== authContext) return;
      if (!valid) {
        console.warn("[WsClient] Rejected envelope with invalid authentication");
        this.ws?.close(1008, "Invalid message authentication");
        return;
      }
      this.dispatchEnvelope({
        type: message.header.type,
        payload: message.payload,
        encrypted: message.header.encrypted,
        requestId: message.header.requestId,
      });
    });
  }

  private dispatchEnvelope(envelope: WsEnvelope): void {
    // Handle heartbeat_ack internally
    if (envelope.type === WsMessageType.HEARTBEAT_ACK) {
      this.clearHeartbeatAckTimer();
      return;
    }

    // Check if this is a response to a pending request
    if (envelope.requestId) {
      const pending = this.pendingRequests.get(envelope.requestId);
      if (pending) {
        this.pendingRequests.delete(envelope.requestId);
        clearTimeout(pending.timer);
        pending.resolve(envelope);
        return;
      }
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

  /**
   * Sends without authentication — only valid for the handshake's AUTH_PROOF,
   * which is authenticated by its payload rather than a header MAC.
   */
  private async sendRawEnvelope(envelope: WsEnvelope): Promise<void> {
    const header: WsEnvelopeHeader = {
      type: envelope.type,
      encrypted: envelope.encrypted,
      hasPayload: envelope.payload.length > 0,
      requestId: envelope.requestId,
    };
    this.sendFrames(header, envelope.payload);
  }

  private sendFrames(header: WsEnvelopeHeader, payload: Uint8Array): void {
    const ws = this.ws;
    if (!ws || ws.readyState !== WebSocket.OPEN) {
      throw new Error("WebSocket not connected");
    }
    ws.send(JSON.stringify(header));
    if (payload.length > 0) {
      const buf = new ArrayBuffer(payload.byteLength);
      new Uint8Array(buf).set(payload);
      ws.send(buf);
    }
  }

  private async drainQueue(): Promise<void> {
    if (this.isSending) return;
    this.isSending = true;

    while (this.sendQueue.length > 0) {
      const item = this.sendQueue.shift()!;
      try {
        // The MAC covers a per-direction sequence number, so header creation
        // and the actual send must happen together, one envelope at a time.
        const authContext = this.authContext;
        if (!authContext) {
          throw new Error("WebSocket not authenticated");
        }
        const header = await authContext.createHeader(item.envelope);
        this.sendFrames(header, item.envelope.payload);
        item.resolve();
      } catch (e) {
        item.reject(e instanceof Error ? e : new Error(String(e)));
      }
    }

    this.isSending = false;
  }

  // ─── Cleanup ────────────────────────────────────────────────────────

  private cleanup(reason: string = "Connection closed"): void {
    const activeConnect = this.activeConnect;
    this.activeConnect = null;
    activeConnect?.settle(false);
    this.connectionGeneration++;
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
    this.pendingChunks = [];
    this.pendingChunksSize = 0;
    this.authContext = null;
    if (this.handshakeWaiter) {
      const waiter = this.handshakeWaiter;
      this.handshakeWaiter = null;
      clearTimeout(waiter.timer);
      waiter.reject(new Error(reason));
    }
    this.handshakeBuffer = [];
    this.incomingChain = Promise.resolve();
    // Reject any pending sends
    for (const item of this.sendQueue) {
      item.reject(new Error("Connection closed"));
    }
    this.sendQueue = [];
    this.isSending = false;
    // Reject any pending requests
    for (const [, pending] of this.pendingRequests) {
      clearTimeout(pending.timer);
      pending.reject(new Error("Connection closed"));
    }
    this.pendingRequests.clear();
  }
}

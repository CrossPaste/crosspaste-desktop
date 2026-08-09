import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { WsClient } from "../ws-client";
import {
  type WsAuthChallenge,
  type WsAuthProcessor,
  type WsAuthProofMessage,
  WsAuthContext,
  base64ToBytes,
  bytesToBase64,
  handshakePayload,
} from "../ws-auth";
import { WsMessageType, type WsEnvelope, type WsEnvelopeHeader } from "../ws-types";

/** Deterministic fake MAC shared by "extension" and "desktop" in these tests. */
function fakeProcessor(): WsAuthProcessor {
  const mac = (data: Int8Array): Int8Array => {
    const out = new Int8Array(data.length + 1);
    out[0] = 42;
    out.set(data, 1);
    return out;
  };
  return {
    authenticationCode: async (data) => mac(data),
    verifyAuthentication: async (data, expected) => {
      const computed = mac(data);
      if (computed.length !== expected.length) return false;
      for (let i = 0; i < computed.length; i++) {
        if (computed[i] !== expected[i]) return false;
      }
      return true;
    },
  };
}

function gatedVerificationProcessor(): {
  processor: WsAuthProcessor;
  release: () => void;
  firstVerificationStarted: Promise<void>;
  firstVerificationCompleted: Promise<void>;
} {
  const delegate = fakeProcessor();
  let release!: () => void;
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  let markStarted!: () => void;
  const firstVerificationStarted = new Promise<void>((resolve) => {
    markStarted = resolve;
  });
  let markCompleted!: () => void;
  const firstVerificationCompleted = new Promise<void>((resolve) => {
    markCompleted = resolve;
  });
  let gateNextVerification = true;
  return {
    processor: {
      authenticationCode: (data) => delegate.authenticationCode(data),
      verifyAuthentication: async (data, expected) => {
        if (gateNextVerification) {
          gateNextVerification = false;
          markStarted();
          await gate;
          const valid = await delegate.verifyAuthentication(data, expected);
          markCompleted();
          return valid;
        }
        return delegate.verifyAuthentication(data, expected);
      },
    },
    release,
    firstVerificationStarted,
    firstVerificationCompleted,
  };
}

class MockWebSocket {
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSING = 2;
  static readonly CLOSED = 3;
  static instances: MockWebSocket[] = [];

  readonly url: string;
  binaryType = "blob";
  readyState = MockWebSocket.CONNECTING;
  sent: Array<string | ArrayBuffer> = [];
  closedWith: { code?: number; reason?: string } | null = null;

  onopen: (() => void) | null = null;
  onclose: ((event: { code: number; reason: string }) => void) | null = null;
  onerror: (() => void) | null = null;
  onmessage: ((event: { data: unknown }) => void) | null = null;

  constructor(url: string) {
    this.url = url;
    MockWebSocket.instances.push(this);
  }

  send(data: string | ArrayBuffer): void {
    this.sent.push(data);
  }

  close(code?: number, reason?: string): void {
    this.closedWith = { code, reason };
    this.readyState = MockWebSocket.CLOSED;
    this.onclose?.({ code: code ?? 1000, reason: reason ?? "" });
  }

  // Test helpers
  serverOpen(): void {
    this.readyState = MockWebSocket.OPEN;
    this.onopen?.();
  }

  serverSend(header: WsEnvelopeHeader, payload?: Uint8Array): void {
    this.onmessage?.({ data: JSON.stringify(header) });
    if (payload && payload.length > 0) {
      const buf = new ArrayBuffer(payload.byteLength);
      new Uint8Array(buf).set(payload);
      this.onmessage?.({ data: buf });
    }
  }
}

/** Waits until the client has written `count` frames to the socket. */
async function waitForSent(ws: MockWebSocket, count: number): Promise<void> {
  await vi.waitFor(() => {
    expect(ws.sent.length).toBeGreaterThanOrEqual(count);
  });
}

function sentEnvelope(ws: MockWebSocket, index: number): { header: WsEnvelopeHeader; payload: Uint8Array } {
  const header = JSON.parse(ws.sent[index] as string) as WsEnvelopeHeader;
  if (!header.hasPayload) return { header, payload: new Uint8Array(0) };
  return { header, payload: new Uint8Array(ws.sent[index + 1] as ArrayBuffer) };
}

const CHALLENGE: WsAuthChallenge = {
  sessionId: "session-1",
  nonce: bytesToBase64(new Uint8Array([9, 9, 9, 9])),
  pairingVersion: 3,
};

function makeClient(processor: WsAuthProcessor): WsClient {
  return new WsClient({
    host: "192.168.0.109",
    port: 13129,
    appInstanceId: "ext-id",
    targetAppInstanceId: "desktop-id",
    createAuthProcessor: async () => processor,
    pairingVersion: 3,
  });
}

/** Drives the desktop side of the handshake against the mock socket. */
async function completeHandshake(
  ws: MockWebSocket,
  processor: WsAuthProcessor,
): Promise<void> {
  ws.serverOpen();
  ws.serverSend(
    { type: WsMessageType.AUTH_CHALLENGE, encrypted: false, hasPayload: true },
    new TextEncoder().encode(JSON.stringify(CHALLENGE)),
  );

  // Client answers with AUTH_PROOF (header + payload frames).
  await waitForSent(ws, 2);
  const { header, payload } = sentEnvelope(ws, 0);
  expect(header.type).toBe(WsMessageType.AUTH_PROOF);
  const proof = JSON.parse(new TextDecoder().decode(payload)) as WsAuthProofMessage;
  expect(proof.pairingVersion).toBe(3);
  expect(
    await processor.verifyAuthentication(
      new Int8Array(handshakePayload("client-proof", "ext-id", "desktop-id", CHALLENGE).buffer),
      new Int8Array(base64ToBytes(proof.authenticationCode).buffer),
    ),
  ).toBe(true);

  // Desktop acks with its own proof.
  const ackCode = await processor.authenticationCode(
    new Int8Array(handshakePayload("server-proof", "desktop-id", "ext-id", CHALLENGE).buffer),
  );
  const ack: WsAuthProofMessage = {
    authenticationCode: bytesToBase64(
      new Uint8Array(ackCode.buffer, ackCode.byteOffset, ackCode.byteLength),
    ),
    pairingVersion: 3,
  };
  ws.serverSend(
    { type: WsMessageType.AUTH_ACK, encrypted: false, hasPayload: true },
    new TextEncoder().encode(JSON.stringify(ack)),
  );
}

/** Sends a valid challenge and ack without verifying the client's proof. */
async function sendChallengeAndAck(
  ws: MockWebSocket,
  processor: WsAuthProcessor,
): Promise<void> {
  ws.serverOpen();
  ws.serverSend(
    { type: WsMessageType.AUTH_CHALLENGE, encrypted: false, hasPayload: true },
    new TextEncoder().encode(JSON.stringify(CHALLENGE)),
  );
  await waitForSent(ws, 2);

  const ackCode = await processor.authenticationCode(
    new Int8Array(
      handshakePayload("server-proof", "desktop-id", "ext-id", CHALLENGE).buffer,
    ),
  );
  const ack: WsAuthProofMessage = {
    authenticationCode: bytesToBase64(
      new Uint8Array(ackCode.buffer, ackCode.byteOffset, ackCode.byteLength),
    ),
    pairingVersion: 3,
  };
  ws.serverSend(
    { type: WsMessageType.AUTH_ACK, encrypted: false, hasPayload: true },
    new TextEncoder().encode(JSON.stringify(ack)),
  );
}

describe("WsClient authentication", () => {
  beforeEach(() => {
    MockWebSocket.instances = [];
    vi.stubGlobal("WebSocket", MockWebSocket);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("dials with authVersion and connects only after the handshake verifies", async () => {
    const processor = fakeProcessor();
    const client = makeClient(processor);

    const connectPromise = client.connect();
    const ws = MockWebSocket.instances[0];
    expect(ws.url).toContain("authVersion=1");

    expect(client.isActive).toBe(false);
    await completeHandshake(ws, processor);

    await expect(connectPromise).resolves.toBe(true);
    expect(client.isActive).toBe(true);
  });

  it("cancels a pending connection when closed before the socket opens", async () => {
    const client = makeClient(fakeProcessor());

    const connectPromise = client.connect();
    const ws = MockWebSocket.instances[0];
    client.close();

    await expect(connectPromise).resolves.toBe(false);
    expect(client.currentState).toBe("closed");
    expect(ws.closedWith).toEqual({ code: 1000, reason: "Normal closure" });
  });

  it("cancels a pending connection after a socket error", async () => {
    const processor = fakeProcessor();
    const client = makeClient(processor);

    const connectPromise = client.connect();
    const ws = MockWebSocket.instances[0];
    ws.onerror?.();

    await expect(connectPromise).resolves.toBe(false);
    expect(client.currentState).toBe("closed");
    expect(ws.closedWith).toEqual({ code: 1000, reason: "Connection error" });

    const retryPromise = client.connect();
    const retryWs = MockWebSocket.instances[1];
    await completeHandshake(retryWs, processor);

    await expect(retryPromise).resolves.toBe(true);
    expect(client.isActive).toBe(true);
  });

  it("fails the connect when the server ack proof is invalid", async () => {
    const processor = fakeProcessor();
    const client = makeClient(processor);

    const connectPromise = client.connect();
    const ws = MockWebSocket.instances[0];
    ws.serverOpen();
    ws.serverSend(
      { type: WsMessageType.AUTH_CHALLENGE, encrypted: false, hasPayload: true },
      new TextEncoder().encode(JSON.stringify(CHALLENGE)),
    );
    await waitForSent(ws, 2);

    const forgedAck: WsAuthProofMessage = {
      authenticationCode: bytesToBase64(new Uint8Array([1, 2, 3])),
    };
    ws.serverSend(
      { type: WsMessageType.AUTH_ACK, encrypted: false, hasPayload: true },
      new TextEncoder().encode(JSON.stringify(forgedAck)),
    );

    await expect(connectPromise).resolves.toBe(false);
    expect(client.isActive).toBe(false);
  });

  it("drains authenticated messages received while the server proof is being verified", async () => {
    const { processor, release, firstVerificationStarted } = gatedVerificationProcessor();
    const client = makeClient(processor);
    const received: WsEnvelope[] = [];
    client.onMessage = (envelope) => received.push(envelope);

    const connectPromise = client.connect();
    const ws = MockWebSocket.instances[0];
    await sendChallengeAndAck(ws, processor);
    await firstVerificationStarted;

    const desktopContext = new WsAuthContext("session-1", "desktop-id", "ext-id", processor);
    const envelope: WsEnvelope = {
      type: WsMessageType.SYNC_INFO,
      payload: new TextEncoder().encode("first authenticated message"),
      encrypted: false,
    };
    ws.serverSend(
      { ...(await desktopContext.createHeader(envelope)), hasPayload: true },
      envelope.payload,
    );

    release();

    await expect(connectPromise).resolves.toBe(true);
    expect(received).toEqual([envelope]);
  });

  it("does not publish connected after the socket closes during server proof verification", async () => {
    const {
      processor,
      release,
      firstVerificationStarted,
      firstVerificationCompleted,
    } = gatedVerificationProcessor();
    const client = makeClient(processor);
    const onConnect = vi.fn();
    client.onConnect = onConnect;

    const connectPromise = client.connect();
    const ws = MockWebSocket.instances[0];
    await sendChallengeAndAck(ws, processor);
    await firstVerificationStarted;

    ws.close(1006, "Connection lost");
    release();
    await firstVerificationCompleted;
    await new Promise((resolve) => setTimeout(resolve, 0));

    await expect(connectPromise).resolves.toBe(false);
    expect(client.currentState).toBe("closed");
    expect(onConnect).not.toHaveBeenCalled();
  });

  it("resolves false when the server closes during the handshake (policy rejection)", async () => {
    const client = makeClient(fakeProcessor());

    const connectPromise = client.connect();
    const ws = MockWebSocket.instances[0];
    ws.serverOpen();
    ws.close(1008, "Authenticated WebSocket required");

    await expect(connectPromise).resolves.toBe(false);
    expect(client.isActive).toBe(false);
  });

  it("MACs outgoing envelopes with ordered sequences and verifies incoming ones", async () => {
    const processor = fakeProcessor();
    const client = makeClient(processor);
    const connectPromise = client.connect();
    const ws = MockWebSocket.instances[0];
    await completeHandshake(ws, processor);
    await connectPromise;

    // Desktop-side mirror of the client's auth context.
    const desktopContext = new WsAuthContext("session-1", "desktop-id", "ext-id", processor);

    const sentBefore = ws.sent.length;
    await client.sendEnvelope({
      type: WsMessageType.SYNC_INFO,
      payload: new TextEncoder().encode("{}"),
      encrypted: false,
    });
    await client.sendEnvelope({
      type: WsMessageType.HEARTBEAT,
      payload: new Uint8Array(0),
      encrypted: false,
    });

    const first = sentEnvelope(ws, sentBefore);
    expect(first.header.authSequence).toBe(0);
    // ext -> desktop verifies against a receiver context mirroring the desktop.
    const desktopReceiver = new WsAuthContext("session-1", "desktop-id", "ext-id", processor);
    expect(await desktopReceiver.verify(first.header, first.payload)).toBe(true);

    const second = sentEnvelope(ws, sentBefore + 2);
    expect(second.header.authSequence).toBe(1);
    expect(await desktopReceiver.verify(second.header, second.payload)).toBe(true);

    // Incoming authenticated message dispatches to onMessage.
    const received: WsEnvelope[] = [];
    client.onMessage = (envelope) => received.push(envelope);
    const pushPayload = new TextEncoder().encode("pushed");
    const pushEnvelope: WsEnvelope = {
      type: WsMessageType.PASTE_PUSH,
      payload: pushPayload,
      encrypted: false,
    };
    const pushHeader = await desktopContext.createHeader(pushEnvelope);
    ws.serverSend({ ...pushHeader, hasPayload: true }, pushPayload);
    await vi.waitFor(() => {
      expect(received).toHaveLength(1);
    });
    expect(received[0].type).toBe(WsMessageType.PASTE_PUSH);

    // A tampered incoming message closes the connection.
    const tamperedHeader = await desktopContext.createHeader(pushEnvelope);
    ws.serverSend({ ...tamperedHeader, hasPayload: true }, new TextEncoder().encode("evil!!"));
    await vi.waitFor(() => {
      expect(ws.closedWith?.code).toBe(1008);
    });
  });

  it("reassembles chunked payloads before verification", async () => {
    const processor = fakeProcessor();
    const client = makeClient(processor);
    const connectPromise = client.connect();
    const ws = MockWebSocket.instances[0];
    await completeHandshake(ws, processor);
    await connectPromise;

    const desktopContext = new WsAuthContext("session-1", "desktop-id", "ext-id", processor);
    const payload = new TextEncoder().encode("abcdef");
    const envelope: WsEnvelope = {
      type: WsMessageType.PASTE_PUSH,
      payload,
      encrypted: false,
    };
    const header = await desktopContext.createHeader(envelope);

    const received: WsEnvelope[] = [];
    client.onMessage = (e) => received.push(e);

    ws.onmessage?.({
      data: JSON.stringify({ ...header, hasPayload: true, payloadChunkCount: 2 }),
    });
    const firstChunk = new ArrayBuffer(3);
    new Uint8Array(firstChunk).set(payload.subarray(0, 3));
    ws.onmessage?.({ data: firstChunk });
    const secondChunk = new ArrayBuffer(3);
    new Uint8Array(secondChunk).set(payload.subarray(3));
    ws.onmessage?.({ data: secondChunk });

    await vi.waitFor(() => {
      expect(received).toHaveLength(1);
    });
    expect(new TextDecoder().decode(received[0].payload)).toBe("abcdef");
  });
});

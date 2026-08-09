import { describe, expect, it } from "vitest";
import {
  type WsAuthChallenge,
  type WsAuthProcessor,
  WsAuthContext,
  base64ToBytes,
  bytesToBase64,
  clientProofCode,
  envelopePayload,
  handshakePayload,
  verifyServerProof,
} from "../ws-auth";
import type { WsEnvelope } from "../ws-types";

/**
 * Independent re-encoding of core's CanonicalWriter wire format
 * ([u32 len][domain utf8] then per field [u8 id][u32 len][payload], all
 * big-endian), used to golden-check the production encoder.
 */
function canonical(domain: string, fields: Array<[number, Uint8Array]>): Uint8Array {
  const encoder = new TextEncoder();
  const parts: number[] = [];
  const pushU32 = (v: number) => parts.push((v >>> 24) & 0xff, (v >>> 16) & 0xff, (v >>> 8) & 0xff, v & 0xff);
  const domainBytes = encoder.encode(domain);
  pushU32(domainBytes.length);
  parts.push(...domainBytes);
  for (const [id, payload] of fields) {
    parts.push(id);
    pushU32(payload.length);
    parts.push(...payload);
  }
  return new Uint8Array(parts);
}

const utf8 = (s: string) => new TextEncoder().encode(s);
const u32bytes = (v: number) => new Uint8Array([(v >>> 24) & 0xff, (v >>> 16) & 0xff, (v >>> 8) & 0xff, v & 0xff]);
const u64bytes = (v: number) => {
  const b = new Uint8Array(8);
  new DataView(b.buffer).setBigUint64(0, BigInt(v), false);
  return b;
};

/** Deterministic fake MAC: (domain-tagged) copy of the input prefixed by a key byte. */
function fakeProcessor(keyByte: number): WsAuthProcessor {
  const mac = (data: Int8Array): Int8Array => {
    const out = new Int8Array(data.length + 1);
    out[0] = keyByte;
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

const challenge: WsAuthChallenge = {
  sessionId: "session-1",
  nonce: bytesToBase64(new Uint8Array([1, 2, 3, 4])),
  pairingVersion: 3,
};

describe("canonical payload encoding", () => {
  it("handshakePayload matches the CanonicalWriter wire format", () => {
    const actual = handshakePayload("client-proof", "ext-id", "desktop-id", challenge);
    const expected = canonical("crosspaste-ws-handshake-v1", [
      [1, utf8("client-proof")],
      [2, utf8("ext-id")],
      [3, utf8("desktop-id")],
      [4, utf8("session-1")],
      [5, new Uint8Array([1, 2, 3, 4])],
    ]);
    expect(Array.from(actual)).toEqual(Array.from(expected));
  });

  it("envelopePayload matches the CanonicalWriter wire format", () => {
    const envelope: WsEnvelope = {
      type: "paste_push",
      payload: new Uint8Array([9, 8, 7]),
      encrypted: true,
      requestId: "req-1",
    };
    const actual = envelopePayload("session-1", 5, "ext-id", "desktop-id", envelope);
    const expected = canonical("crosspaste-ws-envelope-v1", [
      [1, utf8("session-1")],
      [2, u64bytes(5)],
      [3, utf8("ext-id")],
      [4, utf8("desktop-id")],
      [5, utf8("paste_push")],
      [6, u32bytes(1)],
      [7, utf8("req-1")],
      [8, new Uint8Array([9, 8, 7])],
    ]);
    expect(Array.from(actual)).toEqual(Array.from(expected));
  });

  it("encodes a missing requestId as an empty string field", () => {
    const envelope: WsEnvelope = { type: "heartbeat", payload: new Uint8Array(0), encrypted: false };
    const actual = envelopePayload("s", 0, "a", "b", envelope);
    const expected = canonical("crosspaste-ws-envelope-v1", [
      [1, utf8("s")],
      [2, u64bytes(0)],
      [3, utf8("a")],
      [4, utf8("b")],
      [5, utf8("heartbeat")],
      [6, u32bytes(0)],
      [7, new Uint8Array(0)],
      [8, new Uint8Array(0)],
    ]);
    expect(Array.from(actual)).toEqual(Array.from(expected));
  });
});

describe("WsAuthContext", () => {
  function contextPair(): { sender: WsAuthContext; receiver: WsAuthContext } {
    const processor = fakeProcessor(42);
    return {
      sender: new WsAuthContext("session", "first", "second", processor),
      receiver: new WsAuthContext("session", "second", "first", processor),
    };
  }

  it("round-trips an authenticated envelope and rejects replay", async () => {
    const { sender, receiver } = contextPair();
    const envelope: WsEnvelope = { type: "heartbeat", payload: new Uint8Array(0), encrypted: false };

    const header = await sender.createHeader(envelope);
    expect(header.authSessionId).toBe("session");
    expect(header.authSequence).toBe(0);
    expect(await receiver.verify(header, envelope.payload)).toBe(true);
    // Same header again = replay: the receive sequence has moved on.
    expect(await receiver.verify(header, envelope.payload)).toBe(false);
  });

  it("rejects tampered payloads without advancing the receive sequence", async () => {
    const { sender, receiver } = contextPair();
    const envelope: WsEnvelope = {
      type: "paste_push",
      payload: utf8("payload"),
      encrypted: false,
    };

    const header = await sender.createHeader(envelope);
    expect(await receiver.verify(header, utf8("tampered"))).toBe(false);
    // The genuine payload still verifies afterwards.
    expect(await receiver.verify(header, envelope.payload)).toBe(true);
  });

  it("increments the send sequence per envelope and enforces receive order", async () => {
    const { sender, receiver } = contextPair();
    const envelope: WsEnvelope = { type: "heartbeat", payload: new Uint8Array(0), encrypted: false };

    const first = await sender.createHeader(envelope);
    const second = await sender.createHeader(envelope);
    expect(first.authSequence).toBe(0);
    expect(second.authSequence).toBe(1);

    // Out-of-order delivery fails; in-order succeeds.
    expect(await receiver.verify(second, envelope.payload)).toBe(false);
    expect(await receiver.verify(first, envelope.payload)).toBe(true);
    expect(await receiver.verify(second, envelope.payload)).toBe(true);
  });

  it("rejects headers from another session or without auth fields", async () => {
    const { sender, receiver } = contextPair();
    const envelope: WsEnvelope = { type: "heartbeat", payload: new Uint8Array(0), encrypted: false };

    const header = await sender.createHeader(envelope);
    expect(await receiver.verify({ ...header, authSessionId: "other" }, envelope.payload)).toBe(false);
    expect(await receiver.verify({ ...header, authenticationCode: null }, envelope.payload)).toBe(false);
    expect(await receiver.verify({ ...header, authSequence: null }, envelope.payload)).toBe(false);
    // Untouched header still verifies (no sequence was consumed by the failures).
    expect(await receiver.verify(header, envelope.payload)).toBe(true);
  });
});

describe("handshake proofs", () => {
  it("client proof and server proof cover mirrored directions", async () => {
    const extension = fakeProcessor(7);
    const desktop = fakeProcessor(7);

    const proofB64 = await clientProofCode(extension, "ext-id", "desktop-id", challenge);
    // The desktop verifies the client proof with source=ext, target=desktop.
    expect(
      await desktop.verifyAuthentication(
        new Int8Array(handshakePayload("client-proof", "ext-id", "desktop-id", challenge).buffer),
        new Int8Array(base64ToBytes(proofB64).buffer),
      ),
    ).toBe(true);

    // The server's ack signs source=desktop, target=ext; the extension verifies it.
    const ackCode = await desktop.authenticationCode(
      new Int8Array(handshakePayload("server-proof", "desktop-id", "ext-id", challenge).buffer),
    );
    const ackB64 = bytesToBase64(new Uint8Array(ackCode.buffer, ackCode.byteOffset, ackCode.byteLength));
    expect(
      await verifyServerProof(extension, "ext-id", "desktop-id", challenge, {
        authenticationCode: ackB64,
      }),
    ).toBe(true);

    // A proof for the wrong role never validates as the server ack.
    expect(
      await verifyServerProof(extension, "ext-id", "desktop-id", challenge, {
        authenticationCode: proofB64,
      }),
    ).toBe(false);
  });
});

import type { WsEnvelope, WsEnvelopeHeader } from "./ws-types";
import { toHeader, WsCapability } from "./ws-types";

/**
 * WebSocket authentication for the extension, byte-compatible with desktop's
 * `WsAuthenticationCodec` / `WsAuthenticationContext`
 * (app/src/commonMain/kotlin/com/crosspaste/net/ws/WsAuthentication.kt).
 *
 * The desktop requires `authVersion=1` plus an AUTH_CHALLENGE → AUTH_PROOF →
 * AUTH_ACK handshake for any WebSocket that does not originate from loopback;
 * afterwards every envelope carries an HMAC over a canonical encoding of the
 * session, per-direction sequence number, and envelope contents. The HMAC
 * itself comes from the Kotlin/JS core (`SecureMessageProcessor`
 * authenticationCode / verifyAuthentication), so only the canonical byte
 * layout is ported here.
 */

export const WS_AUTH_VERSION = 1;

export const WS_AUTH_ROLE_CLIENT_PROOF = "client-proof";
export const WS_AUTH_ROLE_SERVER_PROOF = "server-proof";

export const WS_SUPPORTED_CAPABILITIES = [WsCapability.PASTE_PUSH_ACK] as const;

const HANDSHAKE_DOMAIN = "crosspaste-ws-handshake-v1";
const ENVELOPE_DOMAIN = "crosspaste-ws-envelope-v1";

/** Subset of the K/JS `JsSecureMessageProcessor` surface used for MACs. */
export interface WsAuthProcessor {
  authenticationCode(data: Int8Array): Promise<Int8Array>;
  verifyAuthentication(data: Int8Array, expectedCode: Int8Array): Promise<boolean>;
}

/** `WsAuthChallenge` JSON payload of an AUTH_CHALLENGE envelope. */
export interface WsAuthChallenge {
  sessionId: string;
  /** base64 (Kotlin Base64ByteArraySerializer) */
  nonce: string;
  pairingVersion?: number | null;
  capabilities?: string[];
}

/** `WsAuthProof` JSON payload of AUTH_PROOF / AUTH_ACK envelopes. */
export interface WsAuthProofMessage {
  /** base64 (Kotlin Base64ByteArraySerializer) */
  authenticationCode: string;
  pairingVersion?: number | null;
  capabilities?: string[];
}

export function base64ToBytes(b64: string): Uint8Array {
  return Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
}

export function bytesToBase64(bytes: Uint8Array): string {
  let binary = "";
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

/**
 * Deterministic encoder mirroring core's `CanonicalWriter`: the domain
 * separator as `[u32 length][utf8]`, then each field as `[u8 id][u32
 * length][payload]`; integers big-endian, strings UTF-8, Long as 8 bytes.
 */
class CanonicalWriter {
  private chunks: Uint8Array[] = [];
  private size = 0;

  constructor(domain: string) {
    const domainBytes = new TextEncoder().encode(domain);
    this.append(u32(domainBytes.length));
    this.append(domainBytes);
  }

  fieldBytes(id: number, bytes: Uint8Array): this {
    this.append(new Uint8Array([id]));
    this.append(u32(bytes.length));
    this.append(bytes);
    return this;
  }

  fieldString(id: number, value: string): this {
    return this.fieldBytes(id, new TextEncoder().encode(value));
  }

  fieldInt(id: number, value: number): this {
    return this.fieldBytes(id, u32(value));
  }

  fieldLong(id: number, value: number): this {
    return this.fieldBytes(id, u64(value));
  }

  build(): Uint8Array {
    const result = new Uint8Array(this.size);
    let offset = 0;
    for (const chunk of this.chunks) {
      result.set(chunk, offset);
      offset += chunk.length;
    }
    return result;
  }

  private append(bytes: Uint8Array): void {
    this.chunks.push(bytes);
    this.size += bytes.length;
  }
}

function u32(value: number): Uint8Array {
  const bytes = new Uint8Array(4);
  new DataView(bytes.buffer).setUint32(0, value, false);
  return bytes;
}

function u64(value: number): Uint8Array {
  const bytes = new Uint8Array(8);
  new DataView(bytes.buffer).setBigUint64(0, BigInt(value), false);
  return bytes;
}

/** Mirror of `WsAuthenticationCodec.handshakePayload`. */
export function handshakePayload(
  role: string,
  sourceAppInstanceId: string,
  targetAppInstanceId: string,
  challenge: WsAuthChallenge,
): Uint8Array {
  return new CanonicalWriter(HANDSHAKE_DOMAIN)
    .fieldString(1, role)
    .fieldString(2, sourceAppInstanceId)
    .fieldString(3, targetAppInstanceId)
    .fieldString(4, challenge.sessionId)
    .fieldBytes(5, base64ToBytes(challenge.nonce))
    .build();
}

/** Mirror of `WsAuthenticationCodec.envelopePayload`. */
export function envelopePayload(
  sessionId: string,
  sequence: number,
  sourceAppInstanceId: string,
  targetAppInstanceId: string,
  envelope: WsEnvelope,
): Uint8Array {
  return new CanonicalWriter(ENVELOPE_DOMAIN)
    .fieldString(1, sessionId)
    .fieldLong(2, sequence)
    .fieldString(3, sourceAppInstanceId)
    .fieldString(4, targetAppInstanceId)
    .fieldString(5, envelope.type)
    .fieldInt(6, envelope.encrypted ? 1 : 0)
    .fieldString(7, envelope.requestId ?? "")
    .fieldBytes(8, envelope.payload)
    .build();
}

function toInt8(bytes: Uint8Array): Int8Array {
  return new Int8Array(bytes.buffer, bytes.byteOffset, bytes.byteLength);
}

function toUint8(bytes: Int8Array): Uint8Array {
  return new Uint8Array(bytes.buffer, bytes.byteOffset, bytes.byteLength);
}

/**
 * Post-handshake per-envelope MAC state, mirroring desktop's
 * `WsAuthenticationContext`: independent send/receive sequence counters, so a
 * replayed or reordered envelope fails verification.
 */
export class WsAuthContext {
  private nextSendSequence = 0;
  private nextReceiveSequence = 0;

  constructor(
    private readonly sessionId: string,
    private readonly localAppInstanceId: string,
    private readonly remoteAppInstanceId: string,
    private readonly processor: WsAuthProcessor,
  ) {}

  async createHeader(envelope: WsEnvelope): Promise<WsEnvelopeHeader> {
    const sequence = this.nextSendSequence++;
    const code = await this.processor.authenticationCode(
      toInt8(
        envelopePayload(
          this.sessionId,
          sequence,
          this.localAppInstanceId,
          this.remoteAppInstanceId,
          envelope,
        ),
      ),
    );
    return {
      ...toHeader(envelope),
      authSessionId: this.sessionId,
      authSequence: sequence,
      authenticationCode: bytesToBase64(toUint8(code)),
    };
  }

  async verify(header: WsEnvelopeHeader, payload: Uint8Array): Promise<boolean> {
    if (
      header.authSequence == null ||
      header.authenticationCode == null ||
      header.authSessionId !== this.sessionId ||
      header.authSequence !== this.nextReceiveSequence
    ) {
      return false;
    }
    const envelope: WsEnvelope = {
      type: header.type,
      payload,
      encrypted: header.encrypted,
      requestId: header.requestId,
    };
    const valid = await this.processor.verifyAuthentication(
      toInt8(
        envelopePayload(
          this.sessionId,
          header.authSequence,
          this.remoteAppInstanceId,
          this.localAppInstanceId,
          envelope,
        ),
      ),
      toInt8(base64ToBytes(header.authenticationCode)),
    );
    if (valid) this.nextReceiveSequence++;
    return valid;
  }
}

/** Compute the AUTH_PROOF authentication code for the client side. */
export async function clientProofCode(
  processor: WsAuthProcessor,
  localAppInstanceId: string,
  targetAppInstanceId: string,
  challenge: WsAuthChallenge,
): Promise<string> {
  const code = await processor.authenticationCode(
    toInt8(
      handshakePayload(
        WS_AUTH_ROLE_CLIENT_PROOF,
        localAppInstanceId,
        targetAppInstanceId,
        challenge,
      ),
    ),
  );
  return bytesToBase64(toUint8(code));
}

/** Verify the AUTH_ACK server proof. */
export async function verifyServerProof(
  processor: WsAuthProcessor,
  localAppInstanceId: string,
  targetAppInstanceId: string,
  challenge: WsAuthChallenge,
  ack: WsAuthProofMessage,
): Promise<boolean> {
  return processor.verifyAuthentication(
    toInt8(
      handshakePayload(
        WS_AUTH_ROLE_SERVER_PROOF,
        targetAppInstanceId,
        localAppInstanceId,
        challenge,
      ),
    ),
    toInt8(base64ToBytes(ack.authenticationCode)),
  );
}

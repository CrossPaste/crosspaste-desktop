/**
 * Wire protocol types matching the Kotlin WsMessage.kt definitions.
 * Protocol: Text frame (JSON header) + optional Binary frame (payload).
 */

export const WsMessageType = {
  HEARTBEAT: "heartbeat",
  HEARTBEAT_ACK: "heartbeat_ack",
  PASTE_PUSH: "paste_push",
  SYNC_INFO: "sync_info",
  NOTIFY_EXIT: "notify_exit",
  NOTIFY_REMOVE: "notify_remove",
  FILE_PULL_REQUEST: "file_pull_request",
  FILE_PULL_RESPONSE: "file_pull_response",
  PASTE_REJECTED_OVERSIZE: "paste_rejected_oversize",
  ERROR: "error",
  AUTH_CHALLENGE: "auth_challenge",
  AUTH_PROOF: "auth_proof",
  AUTH_ACK: "auth_ack",
} as const;

export type WsMessageTypeValue = (typeof WsMessageType)[keyof typeof WsMessageType];

/** JSON header sent as a Text frame on the wire. */
export interface WsEnvelopeHeader {
  type: string;
  encrypted: boolean;
  hasPayload: boolean;
  requestId?: string | null;
  authSessionId?: string | null;
  authSequence?: number | null;
  /** base64 (Kotlin Base64ByteArraySerializer) */
  authenticationCode?: string | null;
  /**
   * Number of Binary frames whose concatenation forms the payload. Absent or
   * 1 on legacy senders; > 1 only after this side advertised pairing
   * version >= 3 in its AUTH_PROOF.
   */
  payloadChunkCount?: number;
}

/** In-memory envelope combining header + raw payload bytes. */
export interface WsEnvelope {
  type: string;
  payload: Uint8Array;
  encrypted: boolean;
  requestId?: string | null;
}

/** Build a WsEnvelopeHeader from an envelope. */
export function toHeader(envelope: WsEnvelope): WsEnvelopeHeader {
  return {
    type: envelope.type,
    encrypted: envelope.encrypted,
    hasPayload: envelope.payload.length > 0,
    requestId: envelope.requestId,
  };
}

/** Create an envelope with no payload. */
export function simpleEnvelope(type: string): WsEnvelope {
  return { type, payload: new Uint8Array(0), encrypted: false };
}

/** WebSocket connection status for a device. */
export type WsConnectionStatus = "ws_connected" | "ws_reconnecting" | "http_only";

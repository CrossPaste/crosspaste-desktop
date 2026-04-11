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
  ERROR: "error",
} as const;

export type WsMessageTypeValue = (typeof WsMessageType)[keyof typeof WsMessageType];

/** JSON header sent as a Text frame on the wire. */
export interface WsEnvelopeHeader {
  type: string;
  encrypted: boolean;
  hasPayload: boolean;
  requestId?: string | null;
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

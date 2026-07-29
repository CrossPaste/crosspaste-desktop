/**
 * Wire shapes of the v2 SAS trust routes (`/sync/trust/v2/*`).
 * Mirror of desktop `com.crosspaste.dto.secure.*` — ByteArray fields travel as
 * standard base64 strings (RFC 4648, padded).
 */

export interface KeyExchangeResponse {
  signPublicKey: string;
  cryptPublicKey: string;
  timestamp: number;
  signature: string;
}

export interface TrustConfirmResponse {
  timestamp: number;
  signature: string;
}

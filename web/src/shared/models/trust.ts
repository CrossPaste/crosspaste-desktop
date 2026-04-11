export interface PairingRequest {
  signPublicKey: string; // base64-encoded DER public key bytes
  cryptPublicKey: string; // base64-encoded DER public key bytes
  token: number;
  timestamp: number;
}

export interface PairingResponse {
  signPublicKey: string;
  cryptPublicKey: string;
  timestamp: number;
}

export interface TrustRequest {
  pairingRequest: PairingRequest;
  signature: string; // base64-encoded DER ECDSA signature
}

export interface TrustResponse {
  pairingResponse: PairingResponse;
  signature: string;
}

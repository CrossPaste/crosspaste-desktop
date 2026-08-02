import { CrossPasteHash, createSecureMessageProcessor } from "@/shared/core";
import { KeyStore, toInt8Array } from "@/shared/storage/key-store";
import { DeviceStore } from "@/shared/storage/device-store";

/**
 * Decrypts WS envelope payloads flagged `encrypted` by the desktop (sent when
 * its "Encrypted Sync" setting is on). The AES key is derived from our ECDH
 * private key and the peer's crypt public key persisted at pairing time —
 * mirror of desktop `WsMessageHandler.handlePastePush`'s decrypt branch.
 */
export interface WsPayloadDecryptor {
  decryptFromDevice(appInstanceId: string, payload: Uint8Array): Promise<Uint8Array>;
}

interface DecryptProcessor {
  decrypt(data: Int8Array): Promise<Int8Array>;
}

export interface WsPayloadDecryptorDeps {
  /** Our stored DER ECDH private key, or null when no key pair exists yet. */
  getOwnCryptPrivateKey: () => Promise<Int8Array | null>;
  /** The peer's trusted DER ECDH public key as base64, or null if unpaired. */
  getPeerCryptPublicKeyB64: (appInstanceId: string) => Promise<string | null>;
  /** Build the cipher for one peer (default: K/JS core implementation). */
  createProcessor: (
    ownCryptPrivateKeyDer: Int8Array,
    peerCryptPublicKeyDer: Int8Array,
  ) => Promise<DecryptProcessor>;
}

const defaultDeps: WsPayloadDecryptorDeps = {
  getOwnCryptPrivateKey: async () => {
    const keys = await KeyStore.getKeys();
    return keys ? toInt8Array(keys.cryptPrivateKey) : null;
  },
  getPeerCryptPublicKeyB64: async (appInstanceId) => {
    const device = await DeviceStore.get(appInstanceId);
    return device?.serverKeys?.cryptPublicKey ?? null;
  },
  createProcessor: (ownPrivate, peerPublic) =>
    createSecureMessageProcessor(ownPrivate, peerPublic),
};

export function createWsPayloadDecryptor(
  deps: WsPayloadDecryptorDeps = defaultDeps,
): WsPayloadDecryptor {
  // Cached per device; keyed by the peer key so a re-pair (key rotation)
  // transparently rebuilds the cipher.
  const cache = new Map<string, { peerKeyB64: string; processor: DecryptProcessor }>();

  return {
    async decryptFromDevice(appInstanceId, payload) {
      const peerKeyB64 = await deps.getPeerCryptPublicKeyB64(appInstanceId);
      if (!peerKeyB64) {
        throw new Error(`No trusted crypt public key for device ${appInstanceId}`);
      }

      let cached = cache.get(appInstanceId);
      if (!cached || cached.peerKeyB64 !== peerKeyB64) {
        const ownPrivate = await deps.getOwnCryptPrivateKey();
        if (!ownPrivate) {
          throw new Error("Extension key pair not initialized");
        }
        const processor = await deps.createProcessor(
          ownPrivate,
          CrossPasteHash.base64Decode(peerKeyB64),
        );
        cached = { peerKeyB64, processor };
        cache.set(appInstanceId, cached);
      }

      const plain = await cached.processor.decrypt(
        new Int8Array(payload.buffer, payload.byteOffset, payload.byteLength),
      );
      return new Uint8Array(plain.buffer, plain.byteOffset, plain.byteLength);
    },
  };
}

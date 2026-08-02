import { describe, expect, it, vi } from "vitest";
import {
  createWsPayloadDecryptor,
  type WsPayloadDecryptorDeps,
} from "../ws-payload-crypto";

vi.mock("@/shared/core", () => ({
  CrossPasteHash: {
    base64Decode: (str: string) =>
      Int8Array.from(atob(str), (c) => c.charCodeAt(0)),
  },
  createSecureMessageProcessor: vi.fn(),
}));

const OWN_PRIVATE = new Int8Array([1, 2, 3]);
const PEER_KEY_B64 = btoa("peer-public-key");

function makeDeps(overrides: Partial<WsPayloadDecryptorDeps> = {}): {
  deps: WsPayloadDecryptorDeps;
  decrypt: ReturnType<typeof vi.fn>;
  createProcessor: ReturnType<typeof vi.fn>;
} {
  const decrypt = vi.fn(async (data: Int8Array) => new Int8Array(data).reverse());
  const createProcessor = vi.fn(async () => ({ decrypt }));
  return {
    deps: {
      getOwnCryptPrivateKey: async () => OWN_PRIVATE,
      getPeerCryptPublicKeyB64: async () => PEER_KEY_B64,
      createProcessor,
      ...overrides,
    },
    decrypt,
    createProcessor,
  };
}

describe("createWsPayloadDecryptor", () => {
  it("decrypts a payload via the processor built from stored keys", async () => {
    const { deps, createProcessor } = makeDeps();
    const decryptor = createWsPayloadDecryptor(deps);

    const result = await decryptor.decryptFromDevice("device-1", new Uint8Array([1, 2, 3]));

    expect(Array.from(result)).toEqual([3, 2, 1]);
    expect(createProcessor).toHaveBeenCalledTimes(1);
    const [ownKey, peerKey] = createProcessor.mock.calls[0];
    expect(ownKey).toBe(OWN_PRIVATE);
    expect(new TextDecoder().decode(new Uint8Array(peerKey))).toBe("peer-public-key");
  });

  it("reuses the cached processor while the peer key is unchanged", async () => {
    const { deps, createProcessor } = makeDeps();
    const decryptor = createWsPayloadDecryptor(deps);

    await decryptor.decryptFromDevice("device-1", new Uint8Array([1]));
    await decryptor.decryptFromDevice("device-1", new Uint8Array([2]));

    expect(createProcessor).toHaveBeenCalledTimes(1);
  });

  it("rebuilds the processor when the peer key rotates (re-pair)", async () => {
    let peerKey = PEER_KEY_B64;
    const { deps, createProcessor } = makeDeps({
      getPeerCryptPublicKeyB64: async () => peerKey,
    });
    const decryptor = createWsPayloadDecryptor(deps);

    await decryptor.decryptFromDevice("device-1", new Uint8Array([1]));
    peerKey = btoa("rotated-peer-key");
    await decryptor.decryptFromDevice("device-1", new Uint8Array([2]));

    expect(createProcessor).toHaveBeenCalledTimes(2);
  });

  it("rejects when the device has no trusted peer key", async () => {
    const { deps } = makeDeps({ getPeerCryptPublicKeyB64: async () => null });
    const decryptor = createWsPayloadDecryptor(deps);

    await expect(
      decryptor.decryptFromDevice("device-1", new Uint8Array([1])),
    ).rejects.toThrow("No trusted crypt public key");
  });

  it("rejects when the extension key pair is missing", async () => {
    const { deps } = makeDeps({ getOwnCryptPrivateKey: async () => null });
    const decryptor = createWsPayloadDecryptor(deps);

    await expect(
      decryptor.decryptFromDevice("device-1", new Uint8Array([1])),
    ).rejects.toThrow("key pair not initialized");
  });

  it("handles payloads that are views into a larger buffer", async () => {
    const { deps } = makeDeps();
    const decryptor = createWsPayloadDecryptor(deps);
    const buffer = new Uint8Array([9, 9, 1, 2, 3, 9]).buffer;
    const view = new Uint8Array(buffer, 2, 3);

    const result = await decryptor.decryptFromDevice("device-1", view);

    expect(Array.from(result)).toEqual([3, 2, 1]);
  });
});

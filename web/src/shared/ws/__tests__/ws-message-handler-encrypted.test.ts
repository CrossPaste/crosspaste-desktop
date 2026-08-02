import { beforeEach, describe, expect, it, vi } from "vitest";
import { createWsMessageHandler, type WsMessageHandlerDeps } from "../ws-message-handler";
import { WsMessageType, type WsEnvelope } from "../ws-types";
import { ingestPaste } from "@/shared/paste/paste-ingestion";

vi.mock("@/shared/core", () => ({
  CrossPasteHash: { hashBytes: vi.fn(() => "hash") },
}));
vi.mock("@/shared/models/paste-data", () => ({
  parsePasteData: (jsonString: string) => {
    try {
      return JSON.parse(jsonString);
    } catch {
      return null;
    }
  },
}));
vi.mock("@/shared/paste/paste-ingestion", () => ({
  ingestPaste: vi.fn(async () => 1),
}));
vi.mock("@/shared/storage/paste-store", () => ({
  PasteStore: { latestReceivedAtByHash: vi.fn(async () => null) },
}));
vi.mock("@/shared/storage/blob-store", () => ({
  BlobStore: { get: vi.fn(async () => null) },
}));
vi.mock("@/shared/clipboard/clipboard-sync-writer", () => ({
  writeRemotePasteToClipboard: vi.fn(async () => false),
}));
vi.mock("@/shared/ws/paste-push-policy", () => ({
  shouldWriteRemotePaste: vi.fn(() => false),
}));

const PASTE_JSON = JSON.stringify({
  id: 1,
  hash: "abc",
  pasteAppearItem: null,
  pasteCollection: { pasteItems: [] },
});

function makeDeps(overrides: Partial<WsMessageHandlerDeps> = {}): WsMessageHandlerDeps {
  return {
    sendToDevice: vi.fn(async () => {}),
    sendRequest: vi.fn(async () => ({ type: "", payload: new Uint8Array(0), encrypted: false })),
    updateDeviceStatus: vi.fn(),
    broadcastToSidePanel: vi.fn(),
    setLastHash: vi.fn(async () => {}),
    getLastHash: vi.fn(async () => ""),
    armClipboardWriteSuppress: vi.fn(async () => {}),
    disarmClipboardWriteSuppress: vi.fn(async () => {}),
    onRemoteRemoveDevice: vi.fn(async () => {}),
    showOversizePasteNotice: vi.fn(async () => {}),
    decryptFromDevice: vi.fn(async (_id, payload) => payload),
    ...overrides,
  };
}

describe("encrypted WS envelope handling", () => {
  beforeEach(() => {
    vi.mocked(ingestPaste).mockClear();
  });

  it("decrypts an encrypted paste_push before parsing", async () => {
    // "Ciphertext" bytes are unrelated to the JSON; only the decryptor knows
    // how to turn them into the plaintext payload.
    const ciphertext = new Uint8Array([0xde, 0xad, 0xbe, 0xef]);
    const decryptFromDevice = vi.fn(async (_id: string, payload: Uint8Array) => {
      expect(payload).toBe(ciphertext);
      return new TextEncoder().encode(PASTE_JSON);
    });
    const deps = makeDeps({ decryptFromDevice });
    const handler = createWsMessageHandler(deps);
    const envelope: WsEnvelope = {
      type: WsMessageType.PASTE_PUSH,
      payload: ciphertext,
      encrypted: true,
    };

    await handler.handleMessage("device-1", envelope);

    expect(decryptFromDevice).toHaveBeenCalledTimes(1);
    expect(ingestPaste).toHaveBeenCalledTimes(1);
    expect(vi.mocked(ingestPaste).mock.calls[0][0]).toMatchObject({ id: 1, hash: "abc" });
    expect(deps.updateDeviceStatus).toHaveBeenCalledWith("device-1", "synced");
  });

  it("passes plaintext paste_push through without calling the decryptor", async () => {
    const deps = makeDeps();
    const handler = createWsMessageHandler(deps);
    const envelope: WsEnvelope = {
      type: WsMessageType.PASTE_PUSH,
      payload: new TextEncoder().encode(PASTE_JSON),
      encrypted: false,
    };

    await handler.handleMessage("device-1", envelope);

    expect(deps.decryptFromDevice).not.toHaveBeenCalled();
    expect(ingestPaste).toHaveBeenCalledTimes(1);
  });

  it("drops the message when decryption fails", async () => {
    const deps = makeDeps({
      decryptFromDevice: vi.fn(async () => {
        throw new Error("no key material");
      }),
    });
    const handler = createWsMessageHandler(deps);
    const envelope: WsEnvelope = {
      type: WsMessageType.PASTE_PUSH,
      payload: new Uint8Array([1, 2, 3]),
      encrypted: true,
    };

    await handler.handleMessage("device-1", envelope);

    expect(ingestPaste).not.toHaveBeenCalled();
    expect(deps.updateDeviceStatus).not.toHaveBeenCalled();
  });
});

import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

/**
 * v2/v3 pairing API tests with REAL crypto: the Kotlin/JS core runs over
 * Node's WebCrypto, so key generation, signing, verification, and the SAS
 * derivation are exercised for real. Only `fetch` and `chrome.storage` are
 * stubbed.
 */

// ─── chrome.storage.local stub (KeyStore dependency) ────────────────────
const localData: Record<string, unknown> = {};
vi.stubGlobal("chrome", {
  storage: {
    local: {
      async get(key: string) {
        return { [key]: localData[key] };
      },
      async set(items: Record<string, unknown>) {
        Object.assign(localData, items);
      },
      async remove(key: string) {
        delete localData[key];
      },
    },
  },
});

import { SyncApi } from "../sync";
import { KeyStore, toInt8Array } from "@/shared/storage/key-store";
import { CrossPasteCrypto, CrossPasteHash } from "@/shared/core";

const CONFIG = {
  host: "192.168.1.10",
  port: 13129,
  appInstanceId: "extension-app",
  targetAppInstanceId: "desktop-app",
};

function jsonResponse(body: unknown): Response {
  return {
    ok: true,
    status: 200,
    text: async () => JSON.stringify(body),
  } as unknown as Response;
}

/** A desktop-side identity for fabricating signed responses. */
async function generateServerKeys() {
  const pair = await CrossPasteCrypto.generateKeyPair();
  return {
    signPublicKey: pair.signPublicKey,
    signPrivateKey: pair.signPrivateKey,
    cryptPublicKey: pair.cryptPublicKey,
    cryptPrivateKey: pair.cryptPrivateKey,
  };
}

/**
 * KeyExchangeRequest and KeyExchangeResponse share the same shape AND the
 * same signature recipe (base64(signPub)+base64(cryptPub)+timestamp), so a
 * request built with the server's keys IS a valid response.
 */
async function fabricateExchangeResponse(server: Awaited<ReturnType<typeof generateServerKeys>>) {
  const json = await CrossPasteCrypto.buildKeyExchangeRequest(
    server.signPrivateKey,
    server.signPublicKey,
    server.cryptPublicKey,
  );
  return JSON.parse(json) as {
    signPublicKey: string;
    cryptPublicKey: string;
    timestamp: number;
    signature: string;
  };
}

beforeEach(() => {
  for (const key of Object.keys(localData)) delete localData[key];
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.stubGlobal("chrome", {
    storage: {
      local: {
        async get(key: string) {
          return { [key]: localData[key] };
        },
        async set(items: Record<string, unknown>) {
          Object.assign(localData, items);
        },
        async remove(key: string) {
          delete localData[key];
        },
      },
    },
  });
});

describe("SyncApi.exchangeV2", () => {
  it("POSTs a signed exchange request and verifies the response", async () => {
    const server = await generateServerKeys();
    const responseBody = await fabricateExchangeResponse(server);

    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(responseBody));
    vi.stubGlobal("fetch", fetchMock);

    const result = await SyncApi.exchangeV2(CONFIG);
    expect(result.cryptPublicKey).toBe(responseBody.cryptPublicKey);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("http://192.168.1.10:13129/sync/trust/v2/exchange");
    expect(init.method).toBe("POST");
    expect(init.headers.appInstanceId).toBe("extension-app");

    // The request we sent must itself carry valid fields
    const sent = JSON.parse(init.body);
    expect(typeof sent.signPublicKey).toBe("string");
    expect(typeof sent.signature).toBe("string");
    expect(typeof sent.timestamp).toBe("number");

    // The generation marker surfaced to the caller IS the signed request
    // timestamp — the value a targeted cancelV2 must echo.
    expect(result.requestTimestamp).toBe(sent.timestamp);
  });

  it("rejects a tampered exchange response", async () => {
    const server = await generateServerKeys();
    const responseBody = await fabricateExchangeResponse(server);
    // Flip the timestamp after signing — the signature must no longer verify.
    responseBody.timestamp += 1;

    const fetchMock =
      vi.fn()
        .mockResolvedValueOnce(jsonResponse(responseBody))
        .mockResolvedValueOnce(jsonResponse(""));
    vi.stubGlobal("fetch", fetchMock);

    await expect(SyncApi.exchangeV2(CONFIG)).rejects.toThrow(/verification/);

    const exchangeRequest = JSON.parse(fetchMock.mock.calls[0][1].body);
    expect(fetchMock.mock.calls[1][0]).toContain("/sync/trust/v2/cancel");
    expect(fetchMock.mock.calls[1][1].headers["crosspaste-exchange-timestamp"])
      .toBe(String(exchangeRequest.timestamp));
  });

  it("cancels its exact generation when the exchange response is lost", async () => {
    const fetchMock =
      vi.fn()
        .mockRejectedValueOnce(new Error("response lost"))
        .mockResolvedValueOnce(jsonResponse(""));
    vi.stubGlobal("fetch", fetchMock);

    await expect(SyncApi.exchangeV2(CONFIG)).rejects.toThrow(/response lost/);

    const exchangeRequest = JSON.parse(fetchMock.mock.calls[0][1].body);
    expect(fetchMock.mock.calls[1][0]).toContain("/sync/trust/v2/cancel");
    expect(fetchMock.mock.calls[1][1].headers["crosspaste-exchange-timestamp"])
      .toBe(String(exchangeRequest.timestamp));
  });

  it("generates distinct generations for concurrent exchange requests", async () => {
    const keys = await KeyStore.generateAndStore();
    const requests = await Promise.all(
      Array.from({ length: 32 }, async () => {
        const json = await CrossPasteCrypto.buildKeyExchangeRequest(
          toInt8Array(keys.signPrivateKey),
          toInt8Array(keys.signPublicKey),
          toInt8Array(keys.cryptPublicKey),
        );
        return JSON.parse(json) as { timestamp: number };
      }),
    );

    const generations = requests.map((request) => request.timestamp);
    expect(new Set(generations).size).toBe(generations.length);
  });
});

describe("SyncApi.cancelV2", () => {
  it("sends the exchange generation header when provided", async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(""));
    vi.stubGlobal("fetch", fetchMock);

    await SyncApi.cancelV2(CONFIG, 1234567890);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("http://192.168.1.10:13129/sync/trust/v2/cancel");
    expect(init.method).toBe("POST");
    expect(init.headers["crosspaste-exchange-timestamp"]).toBe("1234567890");
  });

  it("omits the generation header when not provided", async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(""));
    vi.stubGlobal("fetch", fetchMock);

    await SyncApi.cancelV2(CONFIG);

    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers["crosspaste-exchange-timestamp"]).toBeUndefined();
  });
});

describe("SyncApi.computeLocalSAS", () => {
  it("is order-independent between the two key owners", async () => {
    const server = await generateServerKeys();
    const serverCryptB64 = CrossPasteHash.base64Encode(server.cryptPublicKey);

    const localKeys = await KeyStore.generateAndStore();
    const sas = await SyncApi.computeLocalSAS(serverCryptB64);

    // Recompute with swapped argument order via the core facade
    const swapped = await CrossPasteCrypto.computeSAS(
      server.cryptPublicKey,
      toInt8Array(localKeys.cryptPublicKey),
    );
    expect(sas).toBe(swapped);
    expect(sas).toBeGreaterThanOrEqual(0);
    expect(sas).toBeLessThan(1_000_000);
  });
});

describe("SyncApi.confirmV2", () => {
  it("POSTs a signed confirm carrying our SyncInfo header and verifies the response", async () => {
    const server = await generateServerKeys();
    // TrustConfirmRequest and TrustConfirmResponse share shape and recipe
    // (sign over the decimal timestamp), so build the response with server keys.
    const confirmResponse = JSON.parse(
      await CrossPasteCrypto.buildTrustConfirmRequest(server.signPrivateKey),
    );

    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(confirmResponse));
    vi.stubGlobal("fetch", fetchMock);

    const serverSignB64 = CrossPasteHash.base64Encode(server.signPublicKey);
    const syncInfo = {
      appInfo: {
        appInstanceId: "extension-app",
        appVersion: "1.0",
        appRevision: "Unknown",
        userName: "Chrome Extension",
        pairingVersion: 3,
      },
      endpointInfo: {
        deviceId: "extension-app",
        deviceName: "Chrome Extension",
        platform: { name: "ChromeExtension", arch: "web", bitMode: 64, version: "1" },
        hostInfoList: [],
        port: 0,
      },
    };

    await SyncApi.confirmV2(CONFIG, serverSignB64, syncInfo);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("http://192.168.1.10:13129/sync/trust/v2/confirm");
    expect(init.headers["crosspaste-host"]).toBe("192.168.1.10");
    const headerSyncInfo = JSON.parse(atob(init.headers["crosspaste-sync-info"]));
    expect(headerSyncInfo.appInfo.pairingVersion).toBe(3);
  });

  it("rejects a confirm response signed by the wrong key", async () => {
    const server = await generateServerKeys();
    const impostor = await generateServerKeys();
    const confirmResponse = JSON.parse(
      await CrossPasteCrypto.buildTrustConfirmRequest(impostor.signPrivateKey),
    );

    vi.stubGlobal("fetch", vi.fn().mockResolvedValueOnce(jsonResponse(confirmResponse)));

    const serverSignB64 = CrossPasteHash.base64Encode(server.signPublicKey);
    await expect(
      SyncApi.confirmV2(CONFIG, serverSignB64, {
        appInfo: {
          appInstanceId: "extension-app",
          appVersion: "1.0",
          appRevision: "Unknown",
          userName: "Chrome Extension",
        },
        endpointInfo: {
          deviceId: "extension-app",
          deviceName: "Chrome Extension",
          platform: { name: "ChromeExtension", arch: "web", bitMode: 64, version: "1" },
          hostInfoList: [],
          port: 0,
        },
      }),
    ).rejects.toThrow(/verification/);
  });
});

describe("SyncApi pairing v3 transport", () => {
  it("moves opaque JSON to the v3 routes and returns raw response JSON", async () => {
    const offer = { sessionId: "c2Vzc2lvbg==", tokenGeneration: 1 };
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(offer));
    vi.stubGlobal("fetch", fetchMock);

    const offerJson = await SyncApi.pairingV3Intent(CONFIG, JSON.stringify({ protocolVersion: 3 }));
    expect(JSON.parse(offerJson)).toEqual(offer);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("http://192.168.1.10:13129/sync/pairing/v3/intent");
    expect(JSON.parse(init.body)).toEqual({ protocolVersion: 3 });
  });

  it("carries our SyncInfo header on commit", async () => {
    const ack = { sessionId: "c2Vzc2lvbg==" };
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(ack));
    vi.stubGlobal("fetch", fetchMock);

    await SyncApi.pairingV3Commit(CONFIG, JSON.stringify({ commit: true }), {
      appInfo: {
        appInstanceId: "extension-app",
        appVersion: "1.0",
        appRevision: "Unknown",
        userName: "Chrome Extension",
        pairingVersion: 3,
      },
      endpointInfo: {
        deviceId: "extension-app",
        deviceName: "Chrome Extension",
        platform: { name: "ChromeExtension", arch: "web", bitMode: 64, version: "1" },
        hostInfoList: [],
        port: 0,
      },
    });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("http://192.168.1.10:13129/sync/pairing/v3/commit");
    expect(init.headers["crosspaste-host"]).toBe("192.168.1.10");
    expect(init.headers["crosspaste-sync-info"]).toBeTruthy();
  });
});

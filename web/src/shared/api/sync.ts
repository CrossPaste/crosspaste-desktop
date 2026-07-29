import { apiGet, apiPost, type RequestConfig } from "./client";
import type { SyncInfo } from "@/shared/models/sync-info";
import type { TrustResponse } from "@/shared/models/trust";
import type { KeyExchangeResponse, TrustConfirmResponse } from "@/shared/models/key-exchange";
import { KeyStore, toInt8Array, type StoredKeyPair } from "@/shared/storage/key-store";
import { CrossPasteCrypto, CrossPasteHash } from "@/shared/core";

async function ensureKeys(): Promise<StoredKeyPair> {
  return (await KeyStore.getKeys()) ?? (await KeyStore.generateAndStore());
}

function syncInfoHeader(syncInfo: SyncInfo): string {
  return btoa(JSON.stringify(syncInfo));
}

function toRequestConfig(config: {
  host: string;
  port: number;
  appInstanceId: string;
  targetAppInstanceId?: string;
}): RequestConfig {
  return {
    host: config.host,
    port: config.port,
    appInstanceId: config.appInstanceId,
    targetAppInstanceId: config.targetAppInstanceId,
  };
}

export const SyncApi = {
  /** Connectivity check — GET /sync/telnet → returns VERSION (integer) */
  async telnet(config: {
    host: string;
    port: number;
    appInstanceId: string;
  }): Promise<number> {
    return apiGet<number>(toRequestConfig(config), "/sync/telnet");
  },

  /** Get device sync info — GET /sync/syncInfo → returns SyncInfo JSON */
  async getSyncInfo(config: {
    host: string;
    port: number;
    appInstanceId: string;
  }): Promise<SyncInfo> {
    return apiGet<SyncInfo>(toRequestConfig(config), "/sync/syncInfo");
  },

  /** Request the server to show a pairing token — GET /sync/showToken → empty response */
  async showToken(config: {
    host: string;
    port: number;
    appInstanceId: string;
    targetAppInstanceId?: string;
  }): Promise<void> {
    await apiGet<unknown>(toRequestConfig(config), "/sync/showToken");
  },

  /** Submit trust/pairing request with 6-digit token */
  async trust(
    config: {
      host: string;
      port: number;
      appInstanceId: string;
      targetAppInstanceId: string;
    },
    token: number,
    clientSyncInfo?: SyncInfo,
  ): Promise<TrustResponse> {
    const keys = await ensureKeys();

    // Build signed trust request via core crypto
    const trustRequestJson = await CrossPasteCrypto.buildTrustRequest(
      toInt8Array(keys.signPrivateKey),
      toInt8Array(keys.signPublicKey),
      toInt8Array(keys.cryptPublicKey),
      token,
    );

    const reqConfig = toRequestConfig(config);
    const extraHeaders: Record<string, string> = { "crosspaste-host": config.host };
    if (clientSyncInfo) {
      extraHeaders["crosspaste-sync-info"] = syncInfoHeader(clientSyncInfo);
    }
    const response = await apiPost<TrustResponse>(
      reqConfig,
      "/sync/trust",
      JSON.parse(trustRequestJson),
      extraHeaders,
    );

    return response;
  },

  /**
   * v2 SAS pairing, step 1 — POST /sync/trust/v2/exchange.
   * Sends our signed public keys; the desktop computes and DISPLAYS the SAS.
   * Returns the desktop's verified key-exchange response, or throws when the
   * response signature/keys don't verify.
   */
  async exchangeV2(config: {
    host: string;
    port: number;
    appInstanceId: string;
    targetAppInstanceId: string;
  }): Promise<KeyExchangeResponse> {
    const keys = await ensureKeys();
    const requestJson = await CrossPasteCrypto.buildKeyExchangeRequest(
      toInt8Array(keys.signPrivateKey),
      toInt8Array(keys.signPublicKey),
      toInt8Array(keys.cryptPublicKey),
    );
    const response = await apiPost<KeyExchangeResponse>(
      toRequestConfig(config),
      "/sync/trust/v2/exchange",
      JSON.parse(requestJson),
    );
    const valid = await CrossPasteCrypto.verifyKeyExchangeResponse(JSON.stringify(response));
    if (!valid) {
      throw new Error("Key exchange response failed verification");
    }
    return response;
  },

  /**
   * Compute the local 6-digit SAS for the exchanged keys. Must equal the code
   * the desktop is displaying; a mismatch means a possible MITM.
   */
  async computeLocalSAS(serverCryptPublicKeyB64: string): Promise<number> {
    const keys = await ensureKeys();
    return CrossPasteCrypto.computeSAS(
      toInt8Array(keys.cryptPublicKey),
      CrossPasteHash.base64Decode(serverCryptPublicKeyB64),
    );
  },

  /**
   * v2 SAS pairing, step 2 — POST /sync/trust/v2/confirm.
   * Call only after the user-entered code matched [computeLocalSAS]. The
   * desktop persists our crypt key and registers the SyncInfo carried in the
   * crosspaste-sync-info header (the extension is not mDNS-discoverable).
   */
  async confirmV2(
    config: {
      host: string;
      port: number;
      appInstanceId: string;
      targetAppInstanceId: string;
    },
    serverSignPublicKeyB64: string,
    clientSyncInfo: SyncInfo,
  ): Promise<void> {
    const keys = await ensureKeys();
    const requestJson = await CrossPasteCrypto.buildTrustConfirmRequest(
      toInt8Array(keys.signPrivateKey),
    );
    const response = await apiPost<TrustConfirmResponse>(
      toRequestConfig(config),
      "/sync/trust/v2/confirm",
      JSON.parse(requestJson),
      {
        "crosspaste-host": config.host,
        "crosspaste-sync-info": syncInfoHeader(clientSyncInfo),
      },
    );
    const valid = await CrossPasteCrypto.verifyTrustConfirmResponse(
      CrossPasteHash.base64Decode(serverSignPublicKeyB64),
      JSON.stringify(response),
    );
    if (!valid) {
      throw new Error("Trust confirm response failed verification");
    }
  },

  /**
   * Ask the desktop to open its pairing-code screen and the v3 acceptance
   * window — GET /sync/showPairingCode. Fails with
   * REMOTE_SHOW_PAIRING_CODE_DISABLED when the desktop config forbids it.
   */
  async showPairingCode(config: {
    host: string;
    port: number;
    appInstanceId: string;
    targetAppInstanceId?: string;
  }): Promise<void> {
    await apiGet<unknown>(toRequestConfig(config), "/sync/showPairingCode");
  },

  /**
   * Pairing v3 transport. Bodies and responses are opaque JSON strings owned
   * by the Kotlin/JS `PairingV3Initiator`; this layer only moves them.
   */
  async pairingV3Intent(
    config: {
      host: string;
      port: number;
      appInstanceId: string;
      targetAppInstanceId: string;
    },
    intentJson: string,
  ): Promise<string> {
    const offer = await apiPost<unknown>(
      toRequestConfig(config),
      "/sync/pairing/v3/intent",
      JSON.parse(intentJson),
    );
    return JSON.stringify(offer);
  },

  async pairingV3Proof(
    config: {
      host: string;
      port: number;
      appInstanceId: string;
      targetAppInstanceId: string;
    },
    proofJson: string,
  ): Promise<string> {
    const response = await apiPost<unknown>(
      toRequestConfig(config),
      "/sync/pairing/v3/proof",
      JSON.parse(proofJson),
    );
    return JSON.stringify(response);
  },

  /** The commit also self-registers our SyncInfo via the sync-info header. */
  async pairingV3Commit(
    config: {
      host: string;
      port: number;
      appInstanceId: string;
      targetAppInstanceId: string;
    },
    commitJson: string,
    clientSyncInfo: SyncInfo,
  ): Promise<string> {
    const ack = await apiPost<unknown>(
      toRequestConfig(config),
      "/sync/pairing/v3/commit",
      JSON.parse(commitJson),
      {
        "crosspaste-host": config.host,
        "crosspaste-sync-info": syncInfoHeader(clientSyncInfo),
      },
    );
    return JSON.stringify(ack);
  },

  async pairingV3Cancel(
    config: {
      host: string;
      port: number;
      appInstanceId: string;
      targetAppInstanceId: string;
    },
    cancelJson: string,
  ): Promise<void> {
    await apiPost<unknown>(
      toRequestConfig(config),
      "/sync/pairing/v3/cancel",
      JSON.parse(cancelJson),
    );
  },

  /** Heartbeat — GET /sync/heartbeat → returns VERSION (integer) */
  async heartbeat(config: {
    host: string;
    port: number;
    appInstanceId: string;
    targetAppInstanceId: string;
  }): Promise<number> {
    return apiGet<number>(toRequestConfig(config), "/sync/heartbeat");
  },
};

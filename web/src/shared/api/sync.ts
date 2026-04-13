import { apiGet, apiPost, type RequestConfig } from "./client";
import type { SyncInfo } from "@/shared/models/sync-info";
import type { TrustResponse } from "@/shared/models/trust";
import { KeyStore, toInt8Array } from "@/shared/storage/key-store";
import { CrossPasteCrypto } from "@/shared/core";

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
  }): Promise<void> {
    await apiGet<unknown>(toRequestConfig(config), "/sync/telnet");
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
    // Ensure we have keypairs
    let keys = await KeyStore.getKeys();
    if (!keys) {
      keys = await KeyStore.generateAndStore();
    }

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
      extraHeaders["crosspaste-sync-info"] = btoa(JSON.stringify(clientSyncInfo));
    }
    const response = await apiPost<TrustResponse>(
      reqConfig,
      "/sync/trust",
      JSON.parse(trustRequestJson),
      extraHeaders,
    );

    return response;
  },

  /** Heartbeat — GET /sync/heartbeat → returns VERSION (integer) */
  async heartbeat(config: {
    host: string;
    port: number;
    appInstanceId: string;
    targetAppInstanceId: string;
  }): Promise<void> {
    await apiGet<unknown>(toRequestConfig(config), "/sync/heartbeat");
  },
};

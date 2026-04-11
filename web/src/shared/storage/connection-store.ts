export interface ConnectionConfig {
  appInstanceId: string;
  host: string;
  port: number;
  targetAppInstanceId: string;
  trusted: boolean;
}

export interface ServerKeys {
  signPublicKey: string;
  cryptPublicKey: string;
}

const STORAGE_KEYS = {
  CONNECTION: "connection_config",
  SERVER_KEYS: "server_keys",
} as const;

export const ConnectionStore = {
  async getConfig(): Promise<ConnectionConfig | null> {
    const result = await chrome.storage.local.get(STORAGE_KEYS.CONNECTION);
    return (result[STORAGE_KEYS.CONNECTION] as ConnectionConfig) ?? null;
  },

  async saveConfig(config: ConnectionConfig): Promise<void> {
    await chrome.storage.local.set({ [STORAGE_KEYS.CONNECTION]: config });
  },

  async clearTrust(): Promise<void> {
    await chrome.storage.local.remove([
      STORAGE_KEYS.CONNECTION,
      STORAGE_KEYS.SERVER_KEYS,
    ]);
  },

  async saveServerKeys(keys: ServerKeys): Promise<void> {
    await chrome.storage.local.set({ [STORAGE_KEYS.SERVER_KEYS]: keys });
  },

  async getServerKeys(): Promise<ServerKeys | null> {
    const result = await chrome.storage.local.get(STORAGE_KEYS.SERVER_KEYS);
    return (result[STORAGE_KEYS.SERVER_KEYS] as ServerKeys) ?? null;
  },
};

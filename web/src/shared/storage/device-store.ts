import type { SyncInfo } from "@/shared/models/sync-info";

export interface ServerKeys {
  signPublicKey: string;
  cryptPublicKey: string;
}

export interface StoredDevice {
  targetAppInstanceId: string;
  syncInfo: SyncInfo;
  host: string;
  port: number;
  trusted: boolean;
  noteName?: string;
  serverKeys?: ServerKeys;
  /**
   * True when the device was auto-wiped after UNMATCHED and is now awaiting
   * a re-pair. Surfaces in UI as SyncState.UNVERIFIED. Cleared on successful
   * pair.
   */
  needsRePair?: boolean;
  addedAt: number;
}

const STORAGE_KEY = "devices";

export const DeviceStore = {
  async getAll(): Promise<StoredDevice[]> {
    const result = await chrome.storage.local.get(STORAGE_KEY);
    return (result[STORAGE_KEY] as StoredDevice[]) ?? [];
  },

  async save(device: StoredDevice): Promise<void> {
    const devices = await this.getAll();
    const index = devices.findIndex(
      (d) => d.targetAppInstanceId === device.targetAppInstanceId,
    );
    if (index >= 0) {
      devices[index] = device;
    } else {
      devices.push(device);
    }
    await chrome.storage.local.set({ [STORAGE_KEY]: devices });
  },

  async remove(targetAppInstanceId: string): Promise<void> {
    const devices = await this.getAll();
    const filtered = devices.filter(
      (d) => d.targetAppInstanceId !== targetAppInstanceId,
    );
    await chrome.storage.local.set({ [STORAGE_KEY]: filtered });
  },

  async updateNote(
    targetAppInstanceId: string,
    noteName: string,
  ): Promise<void> {
    const devices = await this.getAll();
    const device = devices.find(
      (d) => d.targetAppInstanceId === targetAppInstanceId,
    );
    if (device) {
      device.noteName = noteName || undefined;
      await chrome.storage.local.set({ [STORAGE_KEY]: devices });
    }
  },

  async get(targetAppInstanceId: string): Promise<StoredDevice | null> {
    const devices = await this.getAll();
    return (
      devices.find(
        (d) => d.targetAppInstanceId === targetAppInstanceId,
      ) ?? null
    );
  },

  async setNeedsRePair(
    targetAppInstanceId: string,
    needsRePair: boolean,
  ): Promise<void> {
    const devices = await this.getAll();
    const device = devices.find(
      (d) => d.targetAppInstanceId === targetAppInstanceId,
    );
    if (!device) return;
    if (needsRePair) {
      device.needsRePair = true;
      device.serverKeys = undefined;
    } else {
      device.needsRePair = undefined;
    }
    await chrome.storage.local.set({ [STORAGE_KEY]: devices });
  },
};

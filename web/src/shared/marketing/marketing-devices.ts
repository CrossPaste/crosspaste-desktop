import type { StoredDevice } from "@/shared/storage/device-store";
import { SyncState } from "@/shared/sync/sync-state";

export interface MarketingDevice extends StoredDevice {
  status: SyncState;
}

const ADDED_AT = Date.UTC(2026, 4, 1);

const macDevice: MarketingDevice = {
  targetAppInstanceId: "macos-marketing",
  host: "192.168.1.10",
  port: 13129,
  trusted: true,
  noteName: "My Macbook",
  addedAt: ADDED_AT,
  status: SyncState.CONNECTED,
  syncInfo: {
    appInfo: {
      appInstanceId: "macos-marketing",
      appVersion: "1.1.1",
      appRevision: "0",
      userName: "user",
    },
    endpointInfo: {
      deviceId: "2",
      deviceName: "device2",
      platform: {
        name: "Macos",
        arch: "arm",
        bitMode: 64,
        version: "15.3.1",
      },
      hostInfoList: [{ networkPrefixLength: 24, hostAddress: "192.168.1.10" }],
      port: 13129,
    },
  },
};

const winDevice: MarketingDevice = {
  targetAppInstanceId: "windows-marketing",
  host: "192.168.1.20",
  port: 13129,
  trusted: true,
  noteName: "My Win",
  addedAt: ADDED_AT,
  status: SyncState.CONNECTED,
  syncInfo: {
    appInfo: {
      appInstanceId: "windows-marketing",
      appVersion: "1.1.1",
      appRevision: "0",
      userName: "user",
    },
    endpointInfo: {
      deviceId: "1",
      deviceName: "device1",
      platform: {
        name: "Windows",
        arch: "x86",
        bitMode: 64,
        version: "11",
      },
      hostInfoList: [{ networkPrefixLength: 24, hostAddress: "192.168.1.20" }],
      port: 13129,
    },
  },
};

export function getMarketingDevices(): MarketingDevice[] {
  return [macDevice, winDevice];
}

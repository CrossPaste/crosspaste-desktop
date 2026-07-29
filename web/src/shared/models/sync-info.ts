export interface AppInfo {
  appInstanceId: string;
  appVersion: string;
  appRevision: string;
  userName: string;
  /**
   * Highest pairing protocol the peer supports. Absent (older peers) means
   * v1 bearer-token pairing only. Mirrors desktop `AppInfo.pairingVersion`.
   */
  pairingVersion?: number;
}

export interface Platform {
  name: string; // "Windows" | "Macos" | "Linux" | "Android" | "iPhone" | "iPad"
  arch: string;
  bitMode: number;
  version: string;
}

export interface HostInfo {
  networkPrefixLength: number;
  hostAddress: string;
}

export interface EndpointInfo {
  deviceId: string;
  deviceName: string;
  platform: Platform;
  hostInfoList: HostInfo[];
  port: number;
}

export interface SyncInfo {
  appInfo: AppInfo;
  endpointInfo: EndpointInfo;
}

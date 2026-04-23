import { useState, useEffect, useCallback } from "react";
import type { SyncInfo } from "@/shared/models/sync-info";
import type { StoredDevice } from "@/shared/storage/device-store";
import type { WsConnectionStatus } from "@/shared/ws/ws-types";
import { SyncState } from "@/shared/sync/sync-state";

export interface DeviceInfo extends StoredDevice {
  status: SyncState;
  wsStatus?: WsConnectionStatus;
}

async function sendMessage(
  message: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  return chrome.runtime.sendMessage(message);
}

/**
 * Request host permission for the target device over a user gesture (UI thread).
 * Service worker cannot call `chrome.permissions.request` — the gesture is lost
 * by the time the message is dispatched, so the prompt must be raised here.
 */
async function ensureHostPermission(host: string, port: number): Promise<boolean> {
  const origins = [`http://${host}:${port}/*`];
  const already = await chrome.permissions.contains({ origins });
  if (already) return true;
  return chrome.permissions.request({ origins });
}

export function useConnection() {
  const [devices, setDevices] = useState<DeviceInfo[]>([]);

  const loadDevices = useCallback(async () => {
    const [devResponse, wsResponse] = await Promise.all([
      sendMessage({ type: "GET_DEVICES" }),
      sendMessage({ type: "GET_WS_STATUS" }),
    ]);
    const devs = (devResponse as { devices: DeviceInfo[] }).devices;
    const statuses = (wsResponse as { statuses: Record<string, WsConnectionStatus> }).statuses;
    setDevices(
      devs.map((d) => ({
        ...d,
        wsStatus: statuses[d.targetAppInstanceId],
      })),
    );
  }, []);

  useEffect(() => {
    loadDevices();
    const listener = (message: Record<string, unknown>) => {
      if (message.type === "DEVICES_CHANGED") {
        loadDevices();
      }
    };
    chrome.runtime.onMessage.addListener(listener);
    return () => chrome.runtime.onMessage.removeListener(listener);
  }, [loadDevices]);

  const connect = useCallback(async (host: string, port: number) => {
    const granted = await ensureHostPermission(host, port);
    if (!granted) {
      return { success: false, error: "host_permission_denied" };
    }
    const result = (await sendMessage({ type: "CONNECT", host, port })) as {
      success: boolean;
      syncInfo?: SyncInfo;
      error?: string;
      incompatible?: boolean;
    };
    return result;
  }, []);

  const pair = useCallback(
    async (token: number) => {
      const result = (await sendMessage({ type: "PAIR", token })) as {
        success: boolean;
        error?: string;
      };
      if (result.success) await loadDevices();
      return result;
    },
    [loadDevices],
  );

  const rePair = useCallback(
    async (targetAppInstanceId: string) => {
      const device = devices.find((d) => d.targetAppInstanceId === targetAppInstanceId);
      if (!device) {
        return { success: false, error: "Device not found" };
      }
      const granted = await ensureHostPermission(device.host, device.port);
      if (!granted) {
        return { success: false, error: "host_permission_denied" };
      }
      const result = (await sendMessage({
        type: "REPAIR",
        targetAppInstanceId,
      })) as { success: boolean; syncInfo?: SyncInfo; error?: string; incompatible?: boolean };
      return result;
    },
    [devices],
  );

  const removeDevice = useCallback(
    async (targetAppInstanceId: string) => {
      await sendMessage({ type: "REMOVE_DEVICE", targetAppInstanceId });
    },
    [],
  );

  const updateNote = useCallback(
    async (targetAppInstanceId: string, noteName: string) => {
      await sendMessage({
        type: "UPDATE_NOTE",
        targetAppInstanceId,
        noteName,
      });
    },
    [],
  );

  return { devices, connect, pair, rePair, removeDevice, updateNote };
}

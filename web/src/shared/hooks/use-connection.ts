import { useState, useEffect, useCallback } from "react";
import type { SyncInfo } from "@/shared/models/sync-info";
import type { StoredDevice } from "@/shared/storage/device-store";
import type { WsConnectionStatus } from "@/shared/ws/ws-types";

export interface DeviceInfo extends StoredDevice {
  status: "synced" | "error";
  wsStatus?: WsConnectionStatus;
}

async function sendMessage(
  message: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  return chrome.runtime.sendMessage(message);
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
    const result = (await sendMessage({
      type: "CONNECT",
      host,
      port,
    })) as { success: boolean; syncInfo?: SyncInfo; error?: string };
    return result;
  }, []);

  const pair = useCallback(
    async (token: number) => {
      const result = (await sendMessage({ type: "PAIR", token })) as {
        success: boolean;
        error?: string;
      };
      if (result.success) {
        await loadDevices();
      }
      return result;
    },
    [loadDevices],
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

  return { devices, connect, pair, removeDevice, updateNote };
}

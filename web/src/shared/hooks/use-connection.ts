import { useState, useEffect, useCallback } from "react";
import type { SyncInfo } from "@/shared/models/sync-info";
import type { StoredDevice } from "@/shared/storage/device-store";

export interface DeviceInfo extends StoredDevice {
  status: "synced" | "error";
}

async function sendMessage(
  message: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  return chrome.runtime.sendMessage(message);
}

export function useConnection() {
  const [devices, setDevices] = useState<DeviceInfo[]>([]);

  const loadDevices = useCallback(async () => {
    const response = await sendMessage({ type: "GET_DEVICES" });
    setDevices((response as { devices: DeviceInfo[] }).devices);
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

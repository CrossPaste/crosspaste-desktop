import { DeviceCard } from "./DeviceCard";
import type { DeviceInfo } from "@/shared/hooks/use-connection";

interface Props {
  devices: DeviceInfo[];
  onEditNote?: (device: DeviceInfo) => void;
  onRemove?: (targetAppInstanceId: string) => void;
}

export function MyDevicesSection({ devices, onEditNote, onRemove }: Props) {
  if (devices.length === 0) return null;

  return (
    <div className="flex flex-col gap-3">
      <span className="text-base font-semibold text-m3-on-surface">
        我的设备
      </span>
      {devices.map((device) => (
        <DeviceCard
          key={device.targetAppInstanceId}
          syncInfo={device.syncInfo}
          status={device.status === "synced" ? "synced" : "error"}
          noteName={device.noteName}
          onEditNote={() => onEditNote?.(device)}
          onRemove={() => onRemove?.(device.targetAppInstanceId)}
        />
      ))}
    </div>
  );
}

import { DeviceCard } from "./DeviceCard";
import { useI18n } from "@/shared/i18n/use-i18n";
import type { DeviceInfo } from "@/shared/hooks/use-connection";

interface Props {
  devices: DeviceInfo[];
  onClick?: (device: DeviceInfo) => void;
  onEditNote?: (device: DeviceInfo) => void;
  onRemove?: (targetAppInstanceId: string) => void;
}

export function MyDevicesSection({ devices, onClick, onEditNote, onRemove }: Props) {
  const t = useI18n();

  if (devices.length === 0) return null;

  return (
    <div className="flex flex-col gap-3">
      <span className="text-base font-semibold text-m3-on-surface">
        {t("my_devices")}
      </span>
      {devices.map((device) => (
        <DeviceCard
          key={device.targetAppInstanceId}
          syncInfo={device.syncInfo}
          status={device.status === "synced" ? "synced" : "error"}
          noteName={device.noteName}
          onClick={() => onClick?.(device)}
          onEditNote={() => onEditNote?.(device)}
          onRemove={() => onRemove?.(device.targetAppInstanceId)}
        />
      ))}
    </div>
  );
}

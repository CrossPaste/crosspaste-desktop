import { DeviceCard } from "./DeviceCard";
import { useI18n } from "@/shared/i18n/use-i18n";
import type { DeviceInfo } from "@/shared/hooks/use-connection";

interface Props {
  devices: DeviceInfo[];
  desktopConnected?: boolean;
  onClick?: (device: DeviceInfo) => void;
  onEditNote?: (device: DeviceInfo) => void;
  onRemove?: (targetAppInstanceId: string) => void;
  onRePair: (targetId: string) => Promise<unknown>;
}

export function MyDevicesSection({ devices, desktopConnected, onClick, onEditNote, onRemove, onRePair }: Props) {
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
          status={desktopConnected ? "paused" : device.status}
          noteName={device.noteName}
          onClick={() => onClick?.(device)}
          onRePair={() => onRePair(device.targetAppInstanceId)}
          onEditNote={() => onEditNote?.(device)}
          onRemove={() => onRemove?.(device.targetAppInstanceId)}
        />
      ))}
    </div>
  );
}

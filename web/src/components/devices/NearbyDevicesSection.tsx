import { RefreshCw } from "lucide-react";
import { DeviceCard } from "./DeviceCard";
import { useI18n } from "@/shared/i18n/use-i18n";
import type { SyncInfo } from "@/shared/models/sync-info";

interface Props {
  devices: SyncInfo[];
  onRefresh: () => void;
  onBlock?: (deviceId: string) => void;
  onLink?: (deviceId: string) => void;
  refreshing?: boolean;
}

export function NearbyDevicesSection({
  devices,
  onRefresh,
  onBlock,
  onLink,
  refreshing,
}: Props) {
  const t = useI18n();

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <span className="text-base font-semibold text-m3-on-surface">
          {t("nearby_devices")}
        </span>
        <button
          onClick={onRefresh}
          className="flex items-center justify-center w-7 h-7 rounded-md"
        >
          <RefreshCw
            size={16}
            className={`text-m3-on-surface-variant ${refreshing ? "animate-spin" : ""}`}
          />
        </button>
      </div>
      {devices.map((syncInfo) => (
        <DeviceCard
          key={syncInfo.endpointInfo.deviceId}
          syncInfo={syncInfo}
          status="nearby"
          onBlock={() => onBlock?.(syncInfo.endpointInfo.deviceId)}
          onLink={() => onLink?.(syncInfo.endpointInfo.deviceId)}
        />
      ))}
      {devices.length === 0 && (
        <div className="flex items-center justify-center py-8 text-sm text-m3-on-surface-variant">
          {t("nearby_devices_not_found")}
        </div>
      )}
    </div>
  );
}

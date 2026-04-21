import {
  ArrowLeft,
  Laptop,
  Smartphone,
  Monitor,
  RefreshCw,
  CircleX,
  Edit,
  Trash2,
  Loader,
  KeyRound,
  ShieldAlert,
  AlertTriangle,
} from "lucide-react";
import { useI18n } from "@/shared/i18n/use-i18n";
import type { DeviceInfo } from "@/shared/hooks/use-connection";
import { SyncState } from "@/shared/sync/sync-state";

interface Props {
  device: DeviceInfo;
  onBack: () => void;
  onEditNote: () => void;
  onRemove: () => void;
  onRePair?: () => void;
}

const PLATFORM_ICON: Record<string, typeof Laptop> = {
  Macos: Laptop,
  Windows: Monitor,
  Linux: Monitor,
  Android: Smartphone,
  iPhone: Smartphone,
  iPad: Smartphone,
};

const PLATFORM_ICON_COLOR: Record<string, string> = {
  Macos: "text-m3-on-surface",
  Windows: "text-m3-primary",
  Linux: "text-m3-primary",
  Android: "text-m3-success",
  iPhone: "text-m3-primary",
  iPad: "text-m3-primary",
};

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between px-4 py-3">
      <span className="text-sm text-m3-on-surface-variant">{label}</span>
      <span className="text-sm font-medium text-m3-on-surface text-right truncate max-w-[60%]">
        {value}
      </span>
    </div>
  );
}

export function DeviceDetailView({
  device,
  onBack,
  onEditNote,
  onRemove,
  onRePair,
}: Props) {
  const t = useI18n();
  const { syncInfo, status, noteName } = device;
  const { appInfo, endpointInfo } = syncInfo;
  const platformName = endpointInfo.platform.name;
  const IconComponent = PLATFORM_ICON[platformName] ?? Monitor;
  const iconColor = PLATFORM_ICON_COLOR[platformName] ?? "text-m3-on-surface";
  const displayName = noteName || endpointInfo.deviceName;
  const displayPlatform =
    platformName === "Macos" ? "macOS" : platformName;

  return (
    <div className="flex flex-col h-full">
      {/* Top bar */}
      <div className="flex items-center gap-2 px-4 py-3">
        <button
          onClick={onBack}
          className="flex items-center justify-center w-8 h-8 rounded-lg hover:bg-m3-surface-container transition-colors"
        >
          <ArrowLeft size={20} className="text-m3-on-surface" />
        </button>
        <span className="text-base font-semibold text-m3-on-surface">
          {t("device_detail")}
        </span>
      </div>

      <div className="flex-1 overflow-y-auto px-5 pb-6">
        <div className="flex flex-col gap-5">
          {/* Device header card */}
          <div className="flex flex-col items-center gap-3 py-5">
            <div className="flex items-center justify-center w-16 h-16 rounded-2xl bg-m3-surface-container">
              <IconComponent size={32} className={iconColor} />
            </div>
            <div className="flex flex-col items-center gap-1">
              <span className="text-lg font-bold text-m3-on-surface">
                {displayName}
              </span>
              {noteName && (
                <span className="text-xs text-m3-on-surface-variant">
                  {endpointInfo.deviceName}
                </span>
              )}
              <span className="text-sm text-m3-on-surface-variant">
                {displayPlatform} {endpointInfo.platform.version}
              </span>
            </div>

            {/* Status badge */}
            {status === SyncState.CONNECTED ? (
              <div className="flex items-center gap-1.5 rounded-full bg-m3-success-container px-3 py-1">
                <RefreshCw size={12} className="text-m3-success" />
                <span className="text-xs font-medium text-m3-success">
                  {t("sync_status_synced")}
                </span>
              </div>
            ) : status === SyncState.CONNECTING ? (
              <div className="flex items-center gap-1.5 rounded-full bg-m3-primary-container px-3 py-1">
                <Loader size={12} className="text-m3-primary animate-spin" />
                <span className="text-xs font-medium text-m3-primary">
                  {t("sync_status_connecting")}
                </span>
              </div>
            ) : status === SyncState.UNVERIFIED ? (
              <div className="flex items-center gap-1.5 rounded-full bg-m3-warning-container px-3 py-1">
                <KeyRound size={12} className="text-m3-warning" />
                <span className="text-xs font-medium text-m3-warning">
                  {t("sync_status_unverified")}
                </span>
              </div>
            ) : status === SyncState.UNMATCHED ? (
              <div className="flex items-center gap-1.5 rounded-full bg-m3-error-container px-3 py-1">
                <ShieldAlert size={12} className="text-m3-error" />
                <span className="text-xs font-medium text-m3-error">
                  {t("sync_status_unmatched")}
                </span>
              </div>
            ) : status === SyncState.INCOMPATIBLE ? (
              <div className="flex items-center gap-1.5 rounded-full bg-m3-error-container px-3 py-1">
                <AlertTriangle size={12} className="text-m3-error" />
                <span className="text-xs font-medium text-m3-error">
                  {t("sync_status_incompatible")}
                </span>
              </div>
            ) : (
              <div className="flex items-center gap-1.5 rounded-full bg-m3-error-container px-3 py-1">
                <CircleX size={12} className="text-m3-error" />
                <span className="text-xs font-medium text-m3-error">
                  {t("sync_status_disconnected")}
                </span>
              </div>
            )}
          </div>

          {/* Base Info */}
          <div className="flex flex-col gap-1">
            <span className="text-xs font-semibold text-m3-on-surface-variant uppercase tracking-wide px-1">
              {t("base_info")}
            </span>
            <div className="flex flex-col rounded-[14px] bg-m3-surface-container overflow-hidden divide-y divide-m3-outline-variant/30">
              <InfoRow
                label={t("app_version")}
                value={appInfo.appVersion}
              />
              <InfoRow
                label={t("user_name")}
                value={appInfo.userName}
              />
              <InfoRow
                label={t("device_id")}
                value={endpointInfo.deviceId}
              />
              <InfoRow
                label={t("arch")}
                value={endpointInfo.platform.arch}
              />
              {endpointInfo.hostInfoList.map((host, i) => (
                <InfoRow
                  key={i}
                  label={
                    endpointInfo.hostInfoList.length > 1
                      ? `IP ${i}`
                      : "IP"
                  }
                  value={host.hostAddress}
                />
              ))}
              <InfoRow
                label={t("port")}
                value={String(endpointInfo.port)}
              />
            </div>
          </div>

          {/* Actions */}
          <div className="flex flex-col gap-2">
            {onRePair && (status === SyncState.UNMATCHED || status === SyncState.UNVERIFIED) && (
              <button
                onClick={onRePair}
                className="flex items-center gap-3 w-full px-4 py-2.5 text-sm text-m3-on-surface hover:bg-m3-surface-container transition-colors"
              >
                <KeyRound size={16} className="text-m3-on-surface-variant" />
                <span>{t("repair_device")}</span>
              </button>
            )}
            <button
              onClick={onEditNote}
              className="flex items-center gap-3 w-full px-4 py-3 rounded-[14px] bg-m3-surface-container text-sm text-m3-on-surface hover:bg-m3-surface-container-high transition-colors"
            >
              <Edit size={18} className="text-m3-on-surface-variant" />
              <span className="font-medium">{t("add_note")}</span>
            </button>
            <button
              onClick={onRemove}
              className="flex items-center gap-3 w-full px-4 py-3 rounded-[14px] bg-m3-error-container/30 text-sm text-m3-error hover:bg-m3-error-container/50 transition-colors"
            >
              <Trash2 size={18} />
              <span className="font-medium">{t("remove_device")}</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

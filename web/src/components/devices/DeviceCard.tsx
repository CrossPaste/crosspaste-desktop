import { useState, useEffect, useRef } from "react";
import {
  Laptop,
  Smartphone,
  Monitor,
  RefreshCw,
  CircleX,
  Pause,
  Edit,
  Trash2,
  Zap,
  Loader,
  KeyRound,
  ShieldAlert,
  AlertTriangle,
} from "lucide-react";
import type { SyncInfo } from "@/shared/models/sync-info";
import { useI18n } from "@/shared/i18n/use-i18n";
import type { WsConnectionStatus } from "@/shared/ws/ws-types";
import { SyncState } from "@/shared/sync/sync-state";

type DeviceStatus = SyncState | "paused";

interface Props {
  syncInfo: SyncInfo;
  status: DeviceStatus;
  wsStatus?: WsConnectionStatus;
  noteName?: string;
  onClick?: () => void;
  onRetry?: () => void;
  onRePair?: () => void;
  onEditNote?: () => void;
  onRemove?: () => void;
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

function DeviceStatusBadge({
  status,
  wsStatus,
  onRetry,
  onRePair,
}: {
  status: DeviceStatus;
  wsStatus?: WsConnectionStatus;
  onRetry?: () => void;
  onRePair?: () => void;
}) {
  const t = useI18n();

  if (status === SyncState.CONNECTED) {
    return (
      <div className="flex items-center gap-1.5 shrink-0">
        {wsStatus === "ws_connected" && (
          <div className="flex items-center justify-center w-5 h-5 rounded-md bg-m3-primary-container" title="WebSocket">
            <Zap size={10} className="text-m3-primary" />
          </div>
        )}
        <div className="flex items-center gap-1 rounded-md bg-m3-success-container px-2 py-1">
          <RefreshCw size={10} className="text-m3-success" />
          <span className="text-[10px] font-medium text-m3-success leading-none">
            {t("sync_status_synced")}
          </span>
        </div>
      </div>
    );
  }

  if (status === "paused") {
    return (
      <div className="flex items-center gap-1 rounded-md bg-m3-warning-container px-2 py-1 shrink-0">
        <Pause size={10} className="text-m3-warning" />
        <span className="text-[10px] font-medium text-m3-warning leading-none">
          {t("sync_status_paused")}
        </span>
      </div>
    );
  }

  if (status === SyncState.CONNECTING) {
    return (
      <div className="flex items-center gap-1 rounded-md bg-m3-primary-container px-2 py-1 shrink-0">
        <Loader size={10} className="text-m3-primary animate-spin" />
        <span className="text-[10px] font-medium text-m3-primary leading-none">
          {t("sync_status_connecting")}
        </span>
      </div>
    );
  }

  if (status === SyncState.INCOMPATIBLE) {
    return (
      <div className="flex items-center gap-1 rounded-md bg-m3-error-container px-2 py-1 shrink-0">
        <AlertTriangle size={10} className="text-m3-error" />
        <span className="text-[10px] font-medium text-m3-error leading-none">
          {t("sync_status_incompatible")}
        </span>
      </div>
    );
  }

  if (status === SyncState.UNMATCHED) {
    return (
      <div className="flex items-center gap-2 shrink-0">
        <div className="flex items-center gap-1 rounded-md bg-m3-error-container px-2 py-1">
          <ShieldAlert size={10} className="text-m3-error" />
          <span className="text-[10px] font-medium text-m3-error leading-none">
            {t("sync_status_unmatched")}
          </span>
        </div>
        {onRePair && (
          <button
            onClick={onRePair}
            className="flex items-center gap-1 rounded-md bg-m3-error-container px-2 py-1 text-[10px] font-medium text-m3-error"
          >
            <KeyRound size={10} />
            {t("repair_device")}
          </button>
        )}
      </div>
    );
  }

  if (status === SyncState.UNVERIFIED) {
    return (
      <div className="flex items-center gap-2 shrink-0">
        <div className="flex items-center gap-1 rounded-md bg-m3-warning-container px-2 py-1">
          <KeyRound size={10} className="text-m3-warning" />
          <span className="text-[10px] font-medium text-m3-warning leading-none">
            {t("sync_status_unverified")}
          </span>
        </div>
        {onRePair && (
          <button
            onClick={onRePair}
            className="flex items-center gap-1 rounded-md bg-m3-warning-container px-2 py-1 text-[10px] font-medium text-m3-warning"
          >
            <KeyRound size={10} />
            {t("repair_device")}
          </button>
        )}
      </div>
    );
  }

  // DISCONNECTED (and any unhandled numeric) fall through to the
  // generic error pill with a retry button — same behavior as before.
  return (
    <div className="flex items-center gap-2 shrink-0">
      <div className="flex items-center gap-1 rounded-md bg-m3-error-container px-2 py-1">
        <CircleX size={10} className="text-m3-error" />
        <span className="text-[10px] font-medium text-m3-error leading-none">
          {t("sync_status_disconnected")}
        </span>
      </div>
      {onRetry && (
        <button
          onClick={onRetry}
          className="flex items-center justify-center w-7 h-7 rounded-md bg-m3-error-container"
        >
          <RefreshCw size={14} className="text-m3-error" />
        </button>
      )}
    </div>
  );
}

function DeviceContextMenu({
  x,
  y,
  menuRef,
  onEditNote,
  onRemove,
  onClose,
}: {
  x: number;
  y: number;
  menuRef: React.RefObject<HTMLDivElement | null>;
  onEditNote?: () => void;
  onRemove?: () => void;
  onClose: () => void;
}) {
  const t = useI18n();

  return (
    <div
      ref={menuRef}
      className="fixed z-50 min-w-[160px] rounded-xl bg-m3-surface-bright shadow-lg border border-m3-outline-variant/20 py-1"
      style={{ left: x, top: y }}
    >
      {onEditNote && (
        <button
          onClick={() => {
            onClose();
            onEditNote();
          }}
          className="flex items-center gap-3 w-full px-4 py-2.5 text-sm text-m3-on-surface hover:bg-m3-surface-container transition-colors"
        >
          <Edit size={16} className="text-m3-on-surface-variant" />
          <span>{t("add_note")}</span>
        </button>
      )}
      {onRemove && (
        <button
          onClick={() => {
            onClose();
            onRemove();
          }}
          className="flex items-center gap-3 w-full px-4 py-2.5 text-sm text-m3-error hover:bg-m3-error-container/30 transition-colors"
        >
          <Trash2 size={16} />
          <span>{t("remove_device")}</span>
        </button>
      )}
    </div>
  );
}

export function DeviceCard({
  syncInfo,
  status,
  wsStatus,
  noteName,
  onClick,
  onRetry,
  onRePair,
  onEditNote,
  onRemove,
}: Props) {
  const { endpointInfo } = syncInfo;
  const platformName = endpointInfo.platform.name;
  const IconComponent = PLATFORM_ICON[platformName] ?? Monitor;
  const iconColor = PLATFORM_ICON_COLOR[platformName] ?? "text-m3-on-surface";

  const [contextMenu, setContextMenu] = useState<{ x: number; y: number } | null>(null);
  const menuRef = useRef<HTMLDivElement>(null);

  const hasContextMenu = onEditNote || onRemove;

  const handleContextMenu = (e: React.MouseEvent) => {
    if (!hasContextMenu) return;
    e.preventDefault();
    const maxY = window.innerHeight - 100;
    const maxX = window.innerWidth - 180;
    setContextMenu({
      x: Math.min(e.clientX, maxX),
      y: Math.min(e.clientY, maxY),
    });
  };

  useEffect(() => {
    if (!contextMenu) return;
    const handleClick = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setContextMenu(null);
      }
    };
    const handleEsc = (e: KeyboardEvent) => {
      if (e.key === "Escape") setContextMenu(null);
    };
    window.addEventListener("mousedown", handleClick);
    window.addEventListener("keydown", handleEsc);
    return () => {
      window.removeEventListener("mousedown", handleClick);
      window.removeEventListener("keydown", handleEsc);
    };
  }, [contextMenu]);

  const displayName = noteName || endpointInfo.deviceName;

  return (
    <>
      <div
        onClick={onClick}
        onContextMenu={handleContextMenu}
        className={`flex items-center gap-3.5 rounded-[14px] bg-m3-surface-container p-4${onClick ? " cursor-pointer hover:bg-m3-surface-container-high transition-colors" : ""}`}
      >
        {/* Device Icon */}
        <div className="flex items-center justify-center w-11 h-11 rounded-xl bg-m3-surface shrink-0">
          <IconComponent size={22} className={iconColor} />
        </div>

        {/* Device Info */}
        <div className="flex-1 min-w-0 flex flex-col gap-1">
          <div className="flex items-center gap-1.5">
            <span className="text-[15px] font-semibold text-m3-on-surface truncate">
              {displayName}
            </span>
            {noteName && (
              <span className="text-xs text-m3-on-surface-variant truncate">
                ({endpointInfo.deviceName})
              </span>
            )}
          </div>
          <span className="text-xs text-m3-on-surface-variant">
            {platformName === "Macos" ? "macOS" : platformName} {endpointInfo.platform.version}
          </span>
        </div>

        <DeviceStatusBadge
          status={status}
          wsStatus={wsStatus}
          onRetry={onRetry}
          onRePair={onRePair}
        />
      </div>

      {contextMenu && (
        <DeviceContextMenu
          x={contextMenu.x}
          y={contextMenu.y}
          menuRef={menuRef}
          onEditNote={onEditNote}
          onRemove={onRemove}
          onClose={() => setContextMenu(null)}
        />
      )}
    </>
  );
}

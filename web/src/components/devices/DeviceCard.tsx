import { useState, useEffect, useRef } from "react";
import {
  Laptop,
  Smartphone,
  Monitor,
  RefreshCw,
  CircleX,
  Ban,
  Link,
  Edit,
  Trash2,
} from "lucide-react";
import type { SyncInfo } from "@/shared/models/sync-info";

type DeviceStatus = "synced" | "error" | "nearby";

interface Props {
  syncInfo: SyncInfo;
  status: DeviceStatus;
  noteName?: string;
  onRetry?: () => void;
  onBlock?: () => void;
  onLink?: () => void;
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

export function DeviceCard({
  syncInfo,
  status,
  noteName,
  onRetry,
  onBlock,
  onLink,
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
    // Clamp position to avoid menu going off-screen
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
        onContextMenu={handleContextMenu}
        className="flex items-center gap-3.5 rounded-[14px] bg-m3-surface-container p-4"
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

        {/* Status Badge & Actions */}
        {status === "synced" && (
          <div className="flex items-center gap-1 rounded-md bg-m3-success-container px-2 py-1 shrink-0">
            <RefreshCw size={10} className="text-m3-success" />
            <span className="text-[10px] font-medium text-m3-success leading-none">
              同步正常
            </span>
          </div>
        )}

        {status === "error" && (
          <div className="flex items-center gap-2 shrink-0">
            <div className="flex items-center gap-1 rounded-md bg-m3-error-container px-2 py-1">
              <CircleX size={10} className="text-m3-error" />
              <span className="text-[10px] font-medium text-m3-error leading-none">
                连接失败
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
        )}

        {status === "nearby" && (
          <div className="flex items-center gap-2 shrink-0">
            {onBlock && (
              <button
                onClick={onBlock}
                className="flex items-center justify-center w-7 h-7 rounded-md bg-m3-error-container"
              >
                <Ban size={14} className="text-m3-error" />
              </button>
            )}
            {onLink && (
              <button
                onClick={onLink}
                className="flex items-center justify-center w-7 h-7 rounded-md bg-m3-primary-container"
              >
                <Link size={14} className="text-m3-primary" />
              </button>
            )}
          </div>
        )}
      </div>

      {/* Right-click Context Menu */}
      {contextMenu && (
        <div
          ref={menuRef}
          className="fixed z-50 min-w-[160px] rounded-xl bg-m3-surface-bright shadow-lg border border-m3-outline-variant/20 py-1"
          style={{ left: contextMenu.x, top: contextMenu.y }}
        >
          {onEditNote && (
            <button
              onClick={() => {
                setContextMenu(null);
                onEditNote();
              }}
              className="flex items-center gap-3 w-full px-4 py-2.5 text-sm text-m3-on-surface hover:bg-m3-surface-container transition-colors"
            >
              <Edit size={16} className="text-m3-on-surface-variant" />
              <span>添加备注</span>
            </button>
          )}
          {onRemove && (
            <button
              onClick={() => {
                setContextMenu(null);
                onRemove();
              }}
              className="flex items-center gap-3 w-full px-4 py-2.5 text-sm text-m3-error hover:bg-m3-error-container/30 transition-colors"
            >
              <Trash2 size={16} />
              <span>删除设备</span>
            </button>
          )}
        </div>
      )}
    </>
  );
}

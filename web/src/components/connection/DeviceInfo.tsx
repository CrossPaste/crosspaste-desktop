import type { SyncInfo } from "@/shared/models/sync-info";

const PLATFORM_ICONS: Record<string, string> = {
  Windows: "💻",
  Macos: "🖥",
  Linux: "🐧",
  Android: "📱",
  iPhone: "📱",
  iPad: "📱",
};

const STATUS_DOT: Record<string, string> = {
  connected: "bg-yellow-400",
  pairing: "bg-yellow-400 animate-pulse",
  trusted: "bg-green-400",
};

interface Props {
  syncInfo: SyncInfo;
  status?: string;
}

export function DeviceInfo({ syncInfo, status = "connected" }: Props) {
  const { endpointInfo, appInfo } = syncInfo;
  const icon = PLATFORM_ICONS[endpointInfo.platform.name] ?? "💻";
  const dotClass = STATUS_DOT[status] ?? "bg-gray-400";

  return (
    <div className="bg-white dark:bg-gray-800 rounded-lg p-4 shadow-sm border border-gray-200 dark:border-gray-700">
      <div className="flex items-center gap-3">
        <span className="text-2xl">{icon}</span>
        <div className="flex-1 min-w-0">
          <p className="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">
            {endpointInfo.deviceName}
          </p>
          <p className="text-xs text-gray-500 dark:text-gray-400">
            {endpointInfo.platform.name} · {appInfo.userName} · v
            {appInfo.appVersion}
          </p>
        </div>
        <span className={`w-2 h-2 rounded-full shrink-0 ${dotClass}`} />
      </div>
    </div>
  );
}

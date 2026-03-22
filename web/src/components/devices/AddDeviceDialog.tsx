import { useState, useCallback, useEffect } from "react";
import { Plus, X, Laptop, Smartphone, Monitor } from "lucide-react";
import { TokenInput } from "@/components/connection/TokenInput";
import { useI18n } from "@/shared/i18n/use-i18n";
import type { SyncInfo } from "@/shared/models/sync-info";
import { decodeConnectCode, isConnectCode } from "@/shared/utils/connect-code";

type Phase = "input" | "token";

interface Props {
  open: boolean;
  onClose: () => void;
  onConnect: (host: string, port: number) => Promise<{ success: boolean; syncInfo?: SyncInfo; error?: string }>;
  onPair: (token: number) => Promise<{ success: boolean; error?: string }>;
}

function isValidIp(ip: string): boolean {
  const parts = ip.split(".");
  if (parts.length !== 4) return false;
  return parts.every((p) => {
    const n = Number(p);
    return p !== "" && !isNaN(n) && n >= 0 && n <= 255 && String(n) === p;
  });
}

function isValidPort(port: string): boolean {
  const n = Number(port);
  return port !== "" && !isNaN(n) && n >= 1 && n <= 65535 && String(n) === port;
}

const PLATFORM_ICON: Record<string, typeof Laptop> = {
  Macos: Laptop,
  Windows: Monitor,
  Linux: Monitor,
  Android: Smartphone,
  iPhone: Smartphone,
  iPad: Smartphone,
};

export function AddDeviceDialog({ open, onClose, onConnect, onPair }: Props) {
  const t = useI18n();
  const [phase, setPhase] = useState<Phase>("input");
  const [connectCode, setConnectCode] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [syncInfo, setSyncInfo] = useState<SyncInfo | null>(null);

  // Auto-format: insert dash after 5 chars
  const handleCodeChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    let raw = e.target.value.replace(/[^0-9A-Za-z-]/g, "");
    const digits = raw.replace(/-/g, "");
    if (digits.length > 10) return;
    if (digits.length > 5) {
      raw = digits.slice(0, 5) + "-" + digits.slice(5);
    } else {
      raw = digits;
    }
    setConnectCode(raw.toUpperCase());
  };

  const codeReady = isConnectCode(connectCode);

  // Reset state when dialog opens/closes
  useEffect(() => {
    if (!open) {
      const timer = setTimeout(() => {
        setPhase("input");
        setConnectCode("");
        setLoading(false);
        setError(null);
        setSyncInfo(null);
      }, 200);
      return () => clearTimeout(timer);
    }
  }, [open]);

  const handleConnect = useCallback(async () => {
    if (loading) return;
    setLoading(true);
    setError(null);

    try {
      let host: string;
      let port: number;

      if (isConnectCode(connectCode)) {
        const decoded = decodeConnectCode(connectCode);
        host = decoded.ip;
        port = decoded.port;
      } else {
        // Fallback: try parsing as ip:port
        const parts = connectCode.split(":");
        host = parts[0];
        port = parseInt(parts[1] ?? "13129", 10);
        if (!isValidIp(host) || !isValidPort(String(port))) {
          setError(t("connection_failed_check"));
          setLoading(false);
          return;
        }
      }

      const result = await onConnect(host, port);
      if (result.success) {
        setSyncInfo(result.syncInfo ?? null);
        setPhase("token");
      } else {
        setError(result.error ?? t("connection_failed_check"));
      }
    } catch {
      setError(t("connection_failed_check"));
    }
    setLoading(false);
  }, [connectCode, loading, onConnect, t]);

  const handlePair = useCallback(async (token: number) => {
    setLoading(true);
    setError(null);
    const result = await onPair(token);
    setLoading(false);
    if (result.success) {
      onClose();
    } else {
      setError(result.error ?? t("verification_failed_retry"));
    }
  }, [onPair, onClose, t]);

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && codeReady && !loading) {
      handleConnect();
    }
  };

  if (!open) return null;

  const deviceName = syncInfo?.endpointInfo.deviceName;
  const platformName = syncInfo?.endpointInfo.platform.name;
  const platformVersion = syncInfo?.endpointInfo.platform.version;
  const IconComponent = PLATFORM_ICON[platformName ?? ""] ?? Monitor;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="w-[340px] rounded-2xl bg-m3-surface p-6 shadow-xl">
        {/* Title */}
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-3">
            <Plus size={20} className="text-m3-primary" />
            <span className="text-lg font-semibold text-m3-on-surface">
              {t("add_device_manually")}
            </span>
          </div>
          <button onClick={onClose} className="p-1 rounded-md hover:bg-m3-surface-container">
            <X size={18} className="text-m3-on-surface-variant" />
          </button>
        </div>

        {phase === "input" ? (
          <>
            {/* Description */}
            <p className="text-sm text-m3-on-surface-variant mb-5 leading-relaxed">
              {t("add_device_manually_desc")}
            </p>

            {/* Connection Code Input */}
            <div className="flex flex-col gap-3 mb-5" onKeyDown={handleKeyDown}>
              <div>
                <label className="block text-xs font-medium text-m3-on-surface-variant mb-1.5">
                  {t("connect_code")}
                </label>
                <input
                  type="text"
                  value={connectCode}
                  onChange={handleCodeChange}
                  placeholder="XXXXX-XXXXX"
                  autoComplete="off"
                  spellCheck={false}
                  className="w-full px-3 py-2.5 text-center text-lg font-mono font-semibold tracking-[0.15em] rounded-xl border border-m3-outline-variant bg-m3-surface text-m3-on-surface placeholder:text-m3-outline placeholder:font-normal placeholder:text-sm placeholder:tracking-normal focus:outline-none focus:ring-2 focus:ring-m3-primary focus:border-transparent"
                />
              </div>
            </div>

            {/* Error */}
            {error && (
              <p className="text-xs text-m3-error mb-4">{error}</p>
            )}

            {/* Actions */}
            <div className="flex items-center justify-end gap-2">
              <button
                onClick={onClose}
                className="px-4 py-2 text-sm font-medium text-m3-on-surface-variant rounded-xl hover:bg-m3-surface-container transition-colors"
              >
                {t("cancel")}
              </button>
              <button
                onClick={handleConnect}
                disabled={!codeReady || loading}
                className="px-4 py-2 text-sm font-medium text-white rounded-xl bg-m3-primary hover:opacity-90 disabled:opacity-40 transition-colors"
              >
                {loading ? t("connecting") : t("connect")}
              </button>
            </div>
          </>
        ) : (
          <>
            {/* Device Info Card */}
            {syncInfo && (
              <div className="flex items-center gap-3 rounded-[14px] bg-m3-surface-container p-3.5 mb-5">
                <div className="flex items-center justify-center w-10 h-10 rounded-xl bg-m3-surface shrink-0">
                  <IconComponent size={20} className="text-m3-on-surface" />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold text-m3-on-surface truncate">
                    {deviceName}
                  </p>
                  <p className="text-xs text-m3-on-surface-variant">
                    {platformName === "Macos" ? "macOS" : platformName} {platformVersion}
                  </p>
                </div>
              </div>
            )}

            {/* Token Input */}
            <p className="text-sm text-m3-on-surface-variant mb-4 leading-relaxed">
              {t("enter_pairing_code_desc")}
            </p>

            <div className="mb-5">
              <TokenInput
                onComplete={handlePair}
                disabled={loading}
              />
            </div>

            {/* Error */}
            {error && (
              <p className="text-xs text-m3-error mb-4 text-center">{error}</p>
            )}

            {/* Actions */}
            <div className="flex items-center justify-end gap-2">
              <button
                onClick={onClose}
                className="px-4 py-2 text-sm font-medium text-m3-on-surface-variant rounded-xl hover:bg-m3-surface-container transition-colors"
              >
                {t("cancel")}
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

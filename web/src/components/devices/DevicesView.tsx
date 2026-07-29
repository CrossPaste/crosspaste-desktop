import { useState, useCallback, useEffect, useRef } from "react";
import { Plus, Info } from "lucide-react";
import { MyDevicesSection } from "./MyDevicesSection";
import { AddDeviceDialog } from "./AddDeviceDialog";
import { EditNoteDialog } from "./EditNoteDialog";
import { DeviceDetailView } from "./DeviceDetailView";
import { useI18n } from "@/shared/i18n/use-i18n";
import type { DeviceInfo } from "@/shared/hooks/use-connection";
import type { SyncInfo } from "@/shared/models/sync-info";

interface Props {
  devices: DeviceInfo[];
  desktopConnected?: boolean;
  onConnect: (
    host: string,
    port: number,
  ) => Promise<{
    success: boolean;
    syncInfo?: SyncInfo;
    error?: string;
    pairingMode?: number;
    attemptId?: number;
  }>;
  onPair: (token: number, attemptId?: number) => Promise<{ success: boolean; error?: string }>;
  onRemoveDevice: (targetAppInstanceId: string) => void;
  onUpdateNote: (targetAppInstanceId: string, noteName: string) => void;
  onRePair: (targetId: string) => Promise<{
    success: boolean;
    syncInfo?: SyncInfo;
    error?: string;
    incompatible?: boolean;
    pairingMode?: number;
    attemptId?: number;
  }>;
  /** Abort an in-flight pairing attempt when its dialog closes early. */
  onCancelConnect: (attemptId?: number) => void;
}

export function DevicesView({
  devices,
  desktopConnected,
  onConnect,
  onPair,
  onRemoveDevice,
  onUpdateNote,
  onRePair,
  onCancelConnect,
}: Props) {
  const t = useI18n();
  const [showAddDialog, setShowAddDialog] = useState(false);
  const [editingDevice, setEditingDevice] = useState<DeviceInfo | null>(null);
  const [selectedDevice, setSelectedDevice] = useState<string | null>(null);
  const [rePairSyncInfo, setRePairSyncInfo] = useState<SyncInfo | null>(null);
  const [rePairMode, setRePairMode] = useState<number | undefined>(undefined);
  // The attempt this view owns; PAIR and CANCEL_CONNECT are scoped to it so a
  // stale dialog (or another side panel) can never drive a newer attempt.
  const attemptIdRef = useRef<number | undefined>(undefined);

  // Leaving the Devices tab unmounts the dialogs without their onClose firing;
  // cancel our in-flight pairing attempt so the desktop-side session (v3 PIN
  // card, downgrade guard) is released rather than lingering to its TTL. The
  // worker refuses the cancel while a trust round-trip is finalizing.
  useEffect(
    () => () => {
      if (attemptIdRef.current !== undefined) onCancelConnect(attemptIdRef.current);
    },
    [onCancelConnect],
  );

  const handleConnect = useCallback(
    async (host: string, port: number) => {
      const result = await onConnect(host, port);
      if (result.success) attemptIdRef.current = result.attemptId;
      return {
        success: result.success,
        syncInfo: result.syncInfo,
        pairingMode: result.pairingMode,
        // Keep known error markers so the dialog can translate them precisely.
        error: result.success ? undefined : result.error ?? t("connection_failed_check"),
      };
    },
    [onConnect, t],
  );

  const handlePair = useCallback(
    (token: number) => onPair(token, attemptIdRef.current),
    [onPair],
  );

  const handleCancelConnect = useCallback(() => {
    onCancelConnect(attemptIdRef.current);
  }, [onCancelConnect]);

  const handleRePair = useCallback(
    async (targetAppInstanceId: string) => {
      const result = await onRePair(targetAppInstanceId);
      if (result.success && result.syncInfo) {
        attemptIdRef.current = result.attemptId;
        setRePairSyncInfo(result.syncInfo);
        setRePairMode(result.pairingMode);
      }
      return result;
    },
    [onRePair],
  );

  // Find the currently selected device from the live devices list
  const detailDevice = selectedDevice
    ? devices.find((d) => d.targetAppInstanceId === selectedDevice) ?? null
    : null;

  return (
    <>
      {detailDevice ? (
        <DeviceDetailView
          device={detailDevice}
          onBack={() => setSelectedDevice(null)}
          onEditNote={() => setEditingDevice(detailDevice)}
          onRemove={() => {
            onRemoveDevice(detailDevice.targetAppInstanceId);
            setSelectedDevice(null);
          }}
          onRePair={() => handleRePair(detailDevice.targetAppInstanceId)}
        />
      ) : (
        <div className="relative flex flex-col h-full">
          <div className="flex-1 overflow-y-auto px-5 py-2">
            <div className="flex flex-col gap-6">
              {/* Connection guide */}
              <div className="rounded-2xl bg-m3-surface-container p-5">
                <div className="flex items-center gap-2 mb-3">
                  <div className="flex items-center justify-center w-7 h-7 rounded-lg bg-m3-primary-container">
                    <Info size={16} className="text-m3-primary" />
                  </div>
                  <span className="text-sm font-semibold text-m3-on-surface">
                    {t("devices_guide_title")}
                  </span>
                </div>
                <p className="text-xs text-m3-on-surface-variant leading-relaxed mb-3">
                  {t("devices_guide_desc")}
                </p>
                <div className="flex flex-col gap-2">
                  <div className="flex items-start gap-2.5">
                    <span className="flex items-center justify-center w-5 h-5 rounded-full bg-m3-primary-container text-m3-primary text-[10px] font-bold shrink-0 mt-px">
                      1
                    </span>
                    <span className="text-xs text-m3-on-surface-variant leading-relaxed">
                      {t("devices_guide_step1")}
                    </span>
                  </div>
                  <div className="flex items-start gap-2.5">
                    <span className="flex items-center justify-center w-5 h-5 rounded-full bg-m3-primary-container text-m3-primary text-[10px] font-bold shrink-0 mt-px">
                      2
                    </span>
                    <span className="text-xs text-m3-on-surface-variant leading-relaxed">
                      {t("devices_guide_step2")}
                    </span>
                  </div>
                </div>
              </div>

              {devices.length > 0 && (
                <MyDevicesSection
                  devices={devices}
                  desktopConnected={desktopConnected}
                  onClick={(device) => setSelectedDevice(device.targetAppInstanceId)}
                  onEditNote={(device) => setEditingDevice(device)}
                  onRemove={(targetAppInstanceId) => onRemoveDevice(targetAppInstanceId)}
                  onRePair={handleRePair}
                />
              )}
            </div>
          </div>

          {/* FAB - Add Device */}
          <div className="absolute bottom-4 right-4">
            <button
              onClick={() => setShowAddDialog(true)}
              className="flex items-center gap-2 px-5 py-3 rounded-2xl bg-m3-success-container text-m3-success shadow-lg hover:shadow-xl transition-shadow"
            >
              <Plus size={20} />
              <span className="text-sm font-medium">{t("add_device_manually")}</span>
            </button>
          </div>

          <AddDeviceDialog
            open={showAddDialog}
            onClose={() => {
              setShowAddDialog(false);
              handleCancelConnect();
            }}
            onConnect={handleConnect}
            onPair={handlePair}
          />
        </div>
      )}

      <AddDeviceDialog
        open={rePairSyncInfo !== null}
        onClose={() => {
          setRePairSyncInfo(null);
          handleCancelConnect();
        }}
        onConnect={handleConnect}
        onPair={async (token) => {
          const result = await handlePair(token);
          if (result.success) setRePairSyncInfo(null);
          return result;
        }}
        initialSyncInfo={rePairSyncInfo ?? undefined}
        initialPairingMode={rePairMode}
      />

      {editingDevice && (
        <EditNoteDialog
          deviceName={editingDevice.syncInfo.endpointInfo.deviceName}
          currentNote={editingDevice.noteName ?? ""}
          onConfirm={(note) => {
            onUpdateNote(editingDevice.targetAppInstanceId, note);
            setEditingDevice(null);
          }}
          onClose={() => setEditingDevice(null)}
        />
      )}
    </>
  );
}

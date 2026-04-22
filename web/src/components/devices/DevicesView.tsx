import { useState, useCallback } from "react";
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
  onConnect: (host: string, port: number) => Promise<{ success: boolean; syncInfo?: SyncInfo }>;
  onPair: (token: number) => Promise<{ success: boolean; error?: string }>;
  onRemoveDevice: (targetAppInstanceId: string) => void;
  onUpdateNote: (targetAppInstanceId: string, noteName: string) => void;
  onRePair: (targetId: string) => Promise<{ success: boolean; syncInfo?: SyncInfo; error?: string; incompatible?: boolean }>;
}

export function DevicesView({
  devices,
  desktopConnected,
  onConnect,
  onPair,
  onRemoveDevice,
  onUpdateNote,
  onRePair,
}: Props) {
  const t = useI18n();
  const [showAddDialog, setShowAddDialog] = useState(false);
  const [editingDevice, setEditingDevice] = useState<DeviceInfo | null>(null);
  const [selectedDevice, setSelectedDevice] = useState<string | null>(null);
  const [rePairSyncInfo, setRePairSyncInfo] = useState<SyncInfo | null>(null);

  const handleConnect = useCallback(
    async (host: string, port: number) => {
      const result = await onConnect(host, port);
      return {
        success: result.success,
        syncInfo: result.syncInfo,
        error: result.success ? undefined : t("connection_failed_check"),
      };
    },
    [onConnect, t],
  );

  const handleRePair = useCallback(
    async (targetAppInstanceId: string) => {
      const result = await onRePair(targetAppInstanceId);
      if (result.success && result.syncInfo) {
        setRePairSyncInfo(result.syncInfo);
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
            onClose={() => setShowAddDialog(false)}
            onConnect={handleConnect}
            onPair={onPair}
          />
        </div>
      )}

      <AddDeviceDialog
        open={rePairSyncInfo !== null}
        onClose={() => setRePairSyncInfo(null)}
        onConnect={handleConnect}
        onPair={async (token) => {
          const result = await onPair(token);
          if (result.success) setRePairSyncInfo(null);
          return result;
        }}
        initialSyncInfo={rePairSyncInfo ?? undefined}
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

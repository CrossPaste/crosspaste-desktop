import { useState, useCallback } from "react";
import { Plus } from "lucide-react";
import { MyDevicesSection } from "./MyDevicesSection";
import { NearbyDevicesSection } from "./NearbyDevicesSection";
import { AddDeviceDialog } from "./AddDeviceDialog";
import { EditNoteDialog } from "./EditNoteDialog";
import type { DeviceInfo } from "@/shared/hooks/use-connection";
import type { SyncInfo } from "@/shared/models/sync-info";

interface Props {
  devices: DeviceInfo[];
  onConnect: (host: string, port: number) => Promise<{ success: boolean; syncInfo?: SyncInfo }>;
  onPair: (token: number) => Promise<{ success: boolean; error?: string }>;
  onRemoveDevice: (targetAppInstanceId: string) => void;
  onUpdateNote: (targetAppInstanceId: string, noteName: string) => void;
}

export function DevicesView({
  devices,
  onConnect,
  onPair,
  onRemoveDevice,
  onUpdateNote,
}: Props) {
  const [showAddDialog, setShowAddDialog] = useState(false);
  const [editingDevice, setEditingDevice] = useState<DeviceInfo | null>(null);
  const [nearbyDevices] = useState<SyncInfo[]>([]);
  const [refreshing, setRefreshing] = useState(false);

  const handleConnect = useCallback(
    async (host: string, port: number) => {
      const result = await onConnect(host, port);
      return {
        success: result.success,
        syncInfo: result.syncInfo,
        error: result.success ? undefined : "连接失败，请检查 IP 和端口是否正确",
      };
    },
    [onConnect],
  );

  const handleRefreshNearby = useCallback(() => {
    setRefreshing(true);
    setTimeout(() => setRefreshing(false), 1500);
  }, []);

  return (
    <div className="relative flex flex-col h-full">
      <div className="flex-1 overflow-y-auto px-5 py-2">
        <div className="flex flex-col gap-6">
          {devices.length > 0 && (
            <MyDevicesSection
              devices={devices}
              onEditNote={(device) => setEditingDevice(device)}
              onRemove={(targetAppInstanceId) => onRemoveDevice(targetAppInstanceId)}
            />
          )}
          <NearbyDevicesSection
            devices={nearbyDevices}
            onRefresh={handleRefreshNearby}
            refreshing={refreshing}
          />
        </div>
      </div>

      {/* FAB - Add Device */}
      <div className="absolute bottom-4 right-4">
        <button
          onClick={() => setShowAddDialog(true)}
          className="flex items-center gap-2 px-5 py-3 rounded-2xl bg-m3-success-container text-m3-success shadow-lg hover:shadow-xl transition-shadow"
        >
          <Plus size={20} />
          <span className="text-sm font-medium">添加设备</span>
        </button>
      </div>

      <AddDeviceDialog
        open={showAddDialog}
        onClose={() => setShowAddDialog(false)}
        onConnect={handleConnect}
        onPair={onPair}
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
    </div>
  );
}

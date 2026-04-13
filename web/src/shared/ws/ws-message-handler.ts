import { type WsEnvelope, WsMessageType, simpleEnvelope } from "./ws-types";
import { parsePasteData } from "@/shared/models/paste-data";
import { PasteStore } from "@/shared/storage/paste-store";
import { BlobStore } from "@/shared/storage/blob-store";

export interface WsMessageHandlerDeps {
  /** Send an envelope back to the device that sent the message. */
  sendToDevice: (targetAppInstanceId: string, envelope: WsEnvelope) => Promise<void>;
  /** Update device status (synced/error). */
  updateDeviceStatus: (targetAppInstanceId: string, status: "synced" | "error") => void;
  /** Broadcast a message to the side panel UI. */
  broadcastToSidePanel: (message: unknown) => void;
  /** Get/set the clipboard last hash to prevent re-capture of synced content. */
  getLastHash: () => Promise<string>;
  setLastHash: (hash: string) => Promise<void>;
  /** Handle remote removal: remove device from storage and disconnect WebSocket. */
  onRemoteRemoveDevice: (targetAppInstanceId: string) => Promise<void>;
}

/**
 * Handles inbound WebSocket messages from desktop devices.
 * Mirrors the Kotlin WsMessageHandler logic.
 */
export function createWsMessageHandler(deps: WsMessageHandlerDeps) {
  return {
    async handleMessage(appInstanceId: string, envelope: WsEnvelope): Promise<void> {
      switch (envelope.type) {
        case WsMessageType.HEARTBEAT:
          await deps.sendToDevice(
            appInstanceId,
            simpleEnvelope(WsMessageType.HEARTBEAT_ACK),
          );
          break;

        case WsMessageType.PASTE_PUSH:
          await handlePastePush(appInstanceId, envelope);
          break;

        case WsMessageType.NOTIFY_EXIT:
          console.log(`[WsHandler] notify_exit from ${appInstanceId}`);
          deps.updateDeviceStatus(appInstanceId, "error");
          break;

        case WsMessageType.NOTIFY_REMOVE:
          console.log(`[WsHandler] notify_remove from ${appInstanceId}, removing device`);
          await deps.onRemoteRemoveDevice(appInstanceId);
          break;

        default:
          console.warn(`[WsHandler] Unknown message type: ${envelope.type} from ${appInstanceId}`);
      }
    },
  };

  async function handlePastePush(appInstanceId: string, envelope: WsEnvelope): Promise<void> {
    try {
      // Decode payload — no encryption support yet (encrypted=false)
      const jsonString = new TextDecoder().decode(envelope.payload);
      const pasteData = parsePasteData(jsonString);
      if (!pasteData) {
        console.error(`[WsHandler] Failed to parse paste_push from ${appInstanceId}`);
        return;
      }

      const newId = await PasteStore.createPasteData(pasteData);
      if (newId !== null) {
        const deleted = await PasteStore.markDeleteSameHash(newId, pasteData.hash);
        for (const h of deleted) await BlobStore.deleteForPaste(h);
        const evicted = await PasteStore.evictOverLimit();
        for (const h of evicted) await BlobStore.deleteForPaste(h);

        deps.broadcastToSidePanel({ type: "PASTE_UPDATED" });
        // Prevent clipboard poller from re-capturing this synced content
        await deps.setLastHash(pasteData.hash);
      }

      deps.updateDeviceStatus(appInstanceId, "synced");
      console.log(`[WsHandler] paste_push from ${appInstanceId} processed`);
    } catch (e) {
      console.error(`[WsHandler] paste_push from ${appInstanceId} failed:`, e);
    }
  }
}

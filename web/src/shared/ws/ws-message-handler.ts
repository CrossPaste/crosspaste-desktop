import { type WsEnvelope, WsMessageType, simpleEnvelope } from "./ws-types";
import { parsePasteData, type PasteData } from "@/shared/models/paste-data";
import { PasteType } from "@/shared/models/paste-item";
import type { FilesPasteItem, ImagesPasteItem } from "@/shared/models/paste-item";
import { BlobStore } from "@/shared/storage/blob-store";
import { ingestPaste } from "@/shared/paste/paste-ingestion";
import { writeRemotePasteToClipboard } from "@/shared/clipboard/clipboard-sync-writer";

/** Payload of a FILE_PULL_REQUEST message (matches Kotlin WsPullFileRequest). */
interface WsPullFileRequest {
  id: number;
  chunkIndex: number;
  hash: string;
  fileName: string;
}

export interface WsMessageHandlerDeps {
  /** Send an envelope back to the device that sent the message. */
  sendToDevice: (targetAppInstanceId: string, envelope: WsEnvelope) => Promise<void>;
  /** Send a request and wait for the correlated response. */
  sendRequest: (targetAppInstanceId: string, envelope: WsEnvelope) => Promise<WsEnvelope>;
  /** Update device status (synced/error). */
  updateDeviceStatus: (targetAppInstanceId: string, status: "synced" | "error") => void;
  /** Broadcast a message to the side panel UI. */
  broadcastToSidePanel: (message: unknown) => void;
  /** Set the clipboard last hash after writing to clipboard, so poll skips it. */
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

        case WsMessageType.FILE_PULL_REQUEST:
          await handleFilePullRequest(appInstanceId, envelope);
          break;

        default:
          console.warn(`[WsHandler] Unknown message type: ${envelope.type} from ${appInstanceId}`);
      }
    },
  };

  async function handlePastePush(appInstanceId: string, envelope: WsEnvelope): Promise<void> {
    try {
      const jsonString = new TextDecoder().decode(envelope.payload);
      const pasteData = parsePasteData(jsonString);
      if (!pasteData) {
        console.error(`[WsHandler] Failed to parse paste_push from ${appInstanceId}`);
        return;
      }

      const newId = await ingestPaste(pasteData, deps.broadcastToSidePanel);
      if (newId !== null) {
        await pullFilesIfNeeded(appInstanceId, pasteData);

        const written = await writeRemotePasteToClipboard(pasteData);
        if (written) {
          await deps.setLastHash(pasteData.hash);
        }
      }

      deps.updateDeviceStatus(appInstanceId, "synced");
    } catch (e) {
      console.error(`[WS-PASTE-PUSH] Failed from ${appInstanceId}:`, e);
    }
  }

  /**
   * Handle FILE_PULL_REQUEST from Desktop.
   * Desktop wants to pull a file from Chrome's BlobStore.
   * Supports whole-file mode only (hash + fileName).
   */
  async function handleFilePullRequest(
    appInstanceId: string,
    envelope: WsEnvelope,
  ): Promise<void> {
    const requestId = envelope.requestId;
    if (!requestId) {
      console.warn(`[WsHandler] FILE_PULL_REQUEST from ${appInstanceId} missing requestId`);
      return;
    }

    try {
      const request: WsPullFileRequest = JSON.parse(new TextDecoder().decode(envelope.payload));
      console.log(`[WsHandler] FILE_PULL_REQUEST from ${appInstanceId}:`, request);

      // Whole-file mode: retrieve blob by hash + fileName
      if (!request.hash || !request.fileName) {
        await sendErrorResponse(appInstanceId, requestId, "Missing hash or fileName");
        return;
      }

      const data = await BlobStore.get(request.hash, request.fileName);
      if (!data) {
        await sendErrorResponse(
          appInstanceId,
          requestId,
          `Blob not found: ${request.hash}/${request.fileName}`,
        );
        return;
      }

      const response: WsEnvelope = {
        type: WsMessageType.FILE_PULL_RESPONSE,
        payload: new Uint8Array(data),
        encrypted: false,
        requestId,
      };
      await deps.sendToDevice(appInstanceId, response);
      console.log(
        `[WsHandler] Served file ${request.fileName} (${data.byteLength} bytes) to ${appInstanceId}`,
      );
    } catch (e) {
      console.error(`[WsHandler] FILE_PULL_REQUEST from ${appInstanceId} failed:`, e);
      try {
        await sendErrorResponse(appInstanceId, requestId, `Internal error: ${String(e)}`);
      } catch {
        // Best effort
      }
    }
  }

  /**
   * After receiving a paste_push with file/image items from desktop,
   * pull the actual file content via WebSocket FILE_PULL_REQUEST.
   */
  async function pullFilesIfNeeded(
    appInstanceId: string,
    pasteData: PasteData,
  ): Promise<void> {
    const allItems = [pasteData.pasteAppearItem, ...pasteData.pasteCollection.pasteItems].filter(
      Boolean,
    );
    const fileItems = allItems.filter(
      (item): item is FilesPasteItem | ImagesPasteItem =>
        item !== null && (item.type === PasteType.FILE || item.type === PasteType.IMAGE),
    );

    if (fileItems.length === 0) return;

    for (const item of fileItems) {
      if (!item.relativePathList || item.relativePathList.length === 0) continue;
      const hash = item.hash;
      if (!hash) continue;

      for (const fileName of item.relativePathList) {
        // Skip if we already have this blob
        const existing = await BlobStore.get(hash, fileName);
        if (existing) continue;

        try {
          const request = {
            id: pasteData.id,
            chunkIndex: -1,
            hash,
            fileName,
          };

          const requestEnvelope: WsEnvelope = {
            type: WsMessageType.FILE_PULL_REQUEST,
            payload: new TextEncoder().encode(JSON.stringify(request)),
            encrypted: false,
          };

          const response = await deps.sendRequest(appInstanceId, requestEnvelope);

          if (response.type === WsMessageType.ERROR) {
            const errorMsg = new TextDecoder().decode(response.payload);
            console.warn(`[WsHandler] File pull error for ${fileName}: ${errorMsg}`);
            continue;
          }

          if (response.payload.length > 0) {
            await BlobStore.put(hash, fileName, response.payload.slice().buffer as ArrayBuffer);
            console.log(
              `[WsHandler] Pulled file ${fileName} (${response.payload.length} bytes) from ${appInstanceId}`,
            );
          }
        } catch (e) {
          console.error(`[WsHandler] Failed to pull file ${fileName} from ${appInstanceId}:`, e);
        }
      }
    }
  }

  async function sendErrorResponse(
    appInstanceId: string,
    requestId: string,
    message: string,
  ): Promise<void> {
    const errorEnvelope: WsEnvelope = {
      type: WsMessageType.ERROR,
      payload: new TextEncoder().encode(message),
      encrypted: false,
      requestId,
    };
    await deps.sendToDevice(appInstanceId, errorEnvelope);
  }
}

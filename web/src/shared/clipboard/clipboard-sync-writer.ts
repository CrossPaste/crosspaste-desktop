import type { PasteData } from "@/shared/models/paste-data";
import { PasteType, PasteTypeInt } from "@/shared/models/paste-item";
import type {
  TextPasteItem,
  UrlPasteItem,
  HtmlPasteItem,
  ImagesPasteItem,
  ColorPasteItem,
} from "@/shared/models/paste-item";
import { BlobStore } from "@/shared/storage/blob-store";
import { argbToHex } from "@/shared/utils/color";

/**
 * Write a remotely-synced paste to the system clipboard via the offscreen document.
 * Used by the service worker when receiving paste_push from desktop.
 *
 * Supports text, URL, HTML, color, and image types.
 * File type is skipped (Chrome cannot write file references to clipboard).
 *
 * Returns true if clipboard was written successfully.
 */
export async function writeRemotePasteToClipboard(pasteData: PasteData): Promise<boolean> {
  const pasteType = pasteData.pasteType;

  // File and RTF types cannot be written to system clipboard
  if (pasteType === PasteTypeInt.FILE || pasteType === PasteTypeInt.RTF) {
    return false;
  }

  const item = pasteData.pasteAppearItem ?? pasteData.pasteCollection.pasteItems[0];
  if (!item) return false;

  const writeData: { text?: string; html?: string; imageDataUrl?: string } = {};

  switch (item.type) {
    case PasteType.TEXT:
      writeData.text = (item as TextPasteItem).text;
      break;
    case PasteType.URL:
      writeData.text = (item as UrlPasteItem).url;
      break;
    case PasteType.COLOR:
      writeData.text = argbToHex((item as ColorPasteItem).color);
      break;
    case PasteType.HTML: {
      const htmlItem = item as HtmlPasteItem;
      writeData.html = htmlItem.html;
      break;
    }
    case PasteType.IMAGE: {
      const imgItem = item as ImagesPasteItem;
      if (imgItem.dataUrl) {
        writeData.imageDataUrl = imgItem.dataUrl;
      } else if (imgItem.hash && imgItem.relativePathList?.length > 0) {
        const blob = await BlobStore.get(imgItem.hash, imgItem.relativePathList[0]);
        if (blob) {
          const bytes = new Uint8Array(blob);
          let binary = "";
          for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
          writeData.imageDataUrl = `data:image/png;base64,${btoa(binary)}`;
        }
      }
      break;
    }
    default:
      return false;
  }

  if (!writeData.text && !writeData.html && !writeData.imageDataUrl) return false;

  try {
    const result = await chrome.runtime.sendMessage({
      type: "WRITE_CLIPBOARD",
      data: writeData,
    });
    return result === true;
  } catch (e) {
    console.error("[ClipboardSyncWriter] Failed to write clipboard:", e);
    return false;
  }
}

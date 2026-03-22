import { PasteType } from "@/shared/models/paste-item";
import type {
  PasteItem,
  TextPasteItem,
  UrlPasteItem,
  HtmlPasteItem,
  ImagesPasteItem,
  ColorPasteItem,
} from "@/shared/models/paste-item";
import type { PasteData } from "@/shared/models/paste-data";
import { argbToHex } from "@/shared/utils/color";

/**
 * Extract plain text from a PasteItem (for text/plain MIME).
 */
function getPlainText(item: PasteItem): string | null {
  switch (item.type) {
    case PasteType.TEXT:
      return (item as TextPasteItem).text;
    case PasteType.URL:
      return (item as UrlPasteItem).url;
    case PasteType.COLOR:
      return argbToHex((item as ColorPasteItem).color);
    default:
      return null;
  }
}

/**
 * Copy a PasteData back to the system clipboard, restoring all available formats.
 * Mirrors desktop DesktopTransferableProducer: writes all items (appear + collection)
 * as their respective MIME types so paste targets receive the best format.
 *
 * Before writing, notifies the service worker to move the paste to the top
 * and set lastHash, so the next poll deduplicates and skips re-recording.
 */
export async function copyPasteData(data: PasteData): Promise<void> {
  const allItems: PasteItem[] = [];
  if (data.pasteAppearItem) allItems.push(data.pasteAppearItem);
  allItems.push(...data.pasteCollection.pasteItems);

  if (allItems.length === 0) return;

  const blobs: Record<string, Blob> = {};
  let plainText: string | null = null;

  for (const item of allItems) {
    switch (item.type) {
      case PasteType.TEXT:
      case PasteType.URL:
      case PasteType.COLOR:
        if (!plainText) plainText = getPlainText(item);
        break;
      case PasteType.HTML:
        if (!blobs["text/html"]) {
          blobs["text/html"] = new Blob([(item as HtmlPasteItem).html], { type: "text/html" });
        }
        break;
      case PasteType.IMAGE: {
        const dataUrl = (item as ImagesPasteItem).dataUrl;
        if (dataUrl && !blobs["image/png"]) {
          const res = await fetch(dataUrl);
          const blob = await res.blob();
          blobs[blob.type] = blob;
        }
        break;
      }
    }
  }

  // Always include text/plain if available (fallback for paste targets)
  if (plainText) {
    blobs["text/plain"] = new Blob([plainText], { type: "text/plain" });
  }

  if (Object.keys(blobs).length === 0) return;

  // Move paste to top and set lastHash before writing clipboard.
  // The poller's lastHash dedup will then skip the re-read content.
  if (data._id) {
    await chrome.runtime
      .sendMessage({ type: "LOCAL_COPY", pasteId: data._id })
      .catch(() => {});
  }

  await navigator.clipboard.write([new ClipboardItem(blobs)]);
}

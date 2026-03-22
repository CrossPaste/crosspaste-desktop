import { PasteTypeInt } from "@/shared/models/paste-item";
import type { PasteItem } from "@/shared/models/paste-item";
import type { PasteCollection } from "@/shared/models/paste-data";
import { detectPasteType, parseColor } from "./paste-type-detector";

/**
 * Priority map matching desktop's PasteType.kt.
 * Higher value = higher priority = becomes pasteAppearItem.
 */
const PRIORITY: Record<number, number> = {
  [PasteTypeInt.TEXT]: 0,
  [PasteTypeInt.URL]: 1,
  [PasteTypeInt.IMAGE]: 2,
  [PasteTypeInt.RTF]: 3,
  [PasteTypeInt.HTML]: 4,
  [PasteTypeInt.COLOR]: 5,
  [PasteTypeInt.FILE]: 6,
};

interface ClipboardFileInfo {
  name: string;
  size: number;
  mimeType: string;
  dataUrl: string | null;
}

interface ClipboardResult {
  text: string | null;
  html: string | null;
  htmlBackgroundColor: number | null;
  rtf: string | null;
  imageDataUrl: string | null;
  files: ClipboardFileInfo[] | null;
}

interface CollectedPaste {
  pasteAppearItem: PasteItem;
  pasteCollection: PasteCollection;
  pasteType: number;
  size: number;
  hash: string;
  /** Files that need blob storage (name → dataUrl) */
  fileBlobs: Array<{ name: string; dataUrl: string }>;
}

/**
 * Collect all clipboard formats into PasteItems, sort by priority,
 * and split into pasteAppearItem + pasteCollection.
 * Mirrors desktop PasteCollector + SortPlugin behavior.
 */
export function collectPasteItems(
  result: ClipboardResult,
  hashText: (s: string) => string,
): CollectedPaste | null {
  const items: Array<{ pasteType: number; item: PasteItem }> = [];
  const fileBlobs: Array<{ name: string; dataUrl: string }> = [];

  // Collect files (highest priority if present)
  if (result.files && result.files.length > 0) {
    const totalSize = result.files.reduce((sum, f) => sum + f.size, 0);
    // Skip if total exceeds 32MB
    if (totalSize <= 32 * 1024 * 1024) {
      const filesHash = hashText(
        result.files.map((f) => `${f.name}:${f.size}`).join("|"),
      );
      items.push({
        pasteType: PasteTypeInt.FILE,
        item: {
          type: "files",
          identifiers: result.files.map((f) => f.mimeType),
          hash: filesHash,
          size: totalSize,
          count: result.files.length,
          relativePathList: result.files.map((f) => f.name),
          fileInfoTreeMap: {},
        },
      });
      for (const f of result.files) {
        if (f.dataUrl) {
          fileBlobs.push({ name: f.name, dataUrl: f.dataUrl });
        }
      }
    }
  }

  // Collect image
  if (result.imageDataUrl) {
    const base64Part = result.imageDataUrl.split(",")[1] ?? "";
    const imgSize = Math.round((base64Part.length * 3) / 4);
    const imgHash = hashText(result.imageDataUrl);
    items.push({
      pasteType: PasteTypeInt.IMAGE,
      item: {
        type: "images",
        identifiers: ["image/png"],
        hash: imgHash,
        size: imgSize,
        count: 1,
        relativePathList: ["clipboard-image.png"],
        fileInfoTreeMap: {},
        dataUrl: result.imageDataUrl,
      },
    });
  }

  // Collect HTML
  if (result.html) {
    const htmlSize = new TextEncoder().encode(result.html).length;
    items.push({
      pasteType: PasteTypeInt.HTML,
      item: {
        type: "html",
        identifiers: ["text/html"],
        hash: hashText(result.html),
        size: htmlSize,
        html: result.html,
        extraInfo: result.htmlBackgroundColor !== null
          ? { background: result.htmlBackgroundColor }
          : undefined,
      },
    });
  }

  // Collect RTF
  if (result.rtf) {
    const rtfSize = new TextEncoder().encode(result.rtf).length;
    items.push({
      pasteType: PasteTypeInt.RTF,
      item: {
        type: "rtf",
        identifiers: ["text/rtf"],
        hash: hashText(result.rtf),
        size: rtfSize,
        rtf: result.rtf,
      },
    });
  }

  // Collect text (may be detected as URL, Color, or Text)
  if (result.text && result.text.length > 0) {
    const textSize = new TextEncoder().encode(result.text).length;
    const detected = detectPasteType(result.text);
    detected.pasteItem.hash = hashText(result.text);
    detected.pasteItem.size = textSize;
    items.push({
      pasteType: detected.pasteType,
      item: detected.pasteItem,
    });
  }

  if (items.length === 0) return null;

  // TextToColor post-processing (matching desktop TextToColorPlugin):
  // If no color item exists but text is present, try converting text to color
  const hasColor = items.some((i) => i.pasteType === PasteTypeInt.COLOR);
  if (!hasColor && result.text) {
    const trimmed = result.text.trim();
    if (!trimmed.includes("\n")) {
      const colorValue = parseColor(trimmed);
      if (colorValue !== null) {
        const textSize = new TextEncoder().encode(trimmed).length;
        items.push({
          pasteType: PasteTypeInt.COLOR,
          item: {
            type: "color",
            identifiers: ["text/plain"],
            hash: hashText(trimmed),
            size: textSize,
            color: colorValue,
          },
        });
      }
    }
  }

  // Sort by priority descending (matching desktop SortPlugin)
  items.sort((a, b) => (PRIORITY[b.pasteType] ?? 0) - (PRIORITY[a.pasteType] ?? 0));

  const appearItem = items[0];
  const collectionItems = items.slice(1).map((i) => i.item);
  const totalSize = items.reduce((sum, i) => sum + i.item.size, 0);

  return {
    pasteAppearItem: appearItem.item,
    pasteCollection: { pasteItems: collectionItems },
    pasteType: appearItem.pasteType,
    size: totalSize,
    hash: appearItem.item.hash,
    fileBlobs,
  };
}

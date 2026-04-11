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

type TypedItem = { pasteType: number; item: PasteItem };
type HashFn = (s: string) => string;

const MAX_FILE_SIZE = 32 * 1024 * 1024; // 32MB

function collectFileItems(
  files: ClipboardFileInfo[],
  hashText: HashFn,
): { items: TypedItem[]; fileBlobs: Array<{ name: string; dataUrl: string }> } {
  const fileBlobs: Array<{ name: string; dataUrl: string }> = [];
  if (files.length === 0) return { items: [], fileBlobs };

  const totalSize = files.reduce((sum, f) => sum + f.size, 0);
  if (totalSize > MAX_FILE_SIZE) return { items: [], fileBlobs };

  const filesHash = hashText(files.map((f) => `${f.name}:${f.size}`).join("|"));
  for (const f of files) {
    if (f.dataUrl) fileBlobs.push({ name: f.name, dataUrl: f.dataUrl });
  }

  return {
    items: [{
      pasteType: PasteTypeInt.FILE,
      item: {
        type: "files",
        identifiers: files.map((f) => f.mimeType),
        hash: filesHash,
        size: totalSize,
        count: files.length,
        relativePathList: files.map((f) => f.name),
        fileInfoTreeMap: {},
      },
    }],
    fileBlobs,
  };
}

function collectImageItem(imageDataUrl: string, hashText: HashFn): TypedItem {
  const base64Part = imageDataUrl.split(",")[1] ?? "";
  const imgSize = Math.round((base64Part.length * 3) / 4);
  return {
    pasteType: PasteTypeInt.IMAGE,
    item: {
      type: "images",
      identifiers: ["image/png"],
      hash: hashText(imageDataUrl),
      size: imgSize,
      count: 1,
      relativePathList: ["clipboard-image.png"],
      fileInfoTreeMap: {},
      dataUrl: imageDataUrl,
    },
  };
}

function collectHtmlItem(html: string, bgColor: number | null, hashText: HashFn): TypedItem {
  return {
    pasteType: PasteTypeInt.HTML,
    item: {
      type: "html",
      identifiers: ["text/html"],
      hash: hashText(html),
      size: new TextEncoder().encode(html).length,
      html,
      extraInfo: bgColor !== null ? { background: bgColor } : undefined,
    },
  };
}

function collectRtfItem(rtf: string, hashText: HashFn): TypedItem {
  return {
    pasteType: PasteTypeInt.RTF,
    item: {
      type: "rtf",
      identifiers: ["text/rtf"],
      hash: hashText(rtf),
      size: new TextEncoder().encode(rtf).length,
      rtf,
    },
  };
}

function collectTextItem(text: string, hashText: HashFn): TypedItem {
  const textSize = new TextEncoder().encode(text).length;
  const detected = detectPasteType(text);
  detected.pasteItem.hash = hashText(text);
  detected.pasteItem.size = textSize;
  return { pasteType: detected.pasteType, item: detected.pasteItem };
}

function collectTextToColorItem(text: string, existing: TypedItem[], hashText: HashFn): TypedItem | null {
  if (existing.some((i) => i.pasteType === PasteTypeInt.COLOR)) return null;
  const trimmed = text.trim();
  if (trimmed.includes("\n")) return null;
  const colorValue = parseColor(trimmed);
  if (colorValue === null) return null;
  return {
    pasteType: PasteTypeInt.COLOR,
    item: {
      type: "color",
      identifiers: ["text/plain"],
      hash: hashText(trimmed),
      size: new TextEncoder().encode(trimmed).length,
      color: colorValue,
    },
  };
}

/**
 * Collect all clipboard formats into PasteItems, sort by priority,
 * and split into pasteAppearItem + pasteCollection.
 * Mirrors desktop PasteCollector + SortPlugin behavior.
 */
export function collectPasteItems(
  result: ClipboardResult,
  hashText: HashFn,
): CollectedPaste | null {
  const items: TypedItem[] = [];
  let fileBlobs: Array<{ name: string; dataUrl: string }> = [];

  if (result.files && result.files.length > 0) {
    const collected = collectFileItems(result.files, hashText);
    items.push(...collected.items);
    fileBlobs = collected.fileBlobs;
  }
  if (result.imageDataUrl) items.push(collectImageItem(result.imageDataUrl, hashText));
  if (result.html) items.push(collectHtmlItem(result.html, result.htmlBackgroundColor, hashText));
  if (result.rtf) items.push(collectRtfItem(result.rtf, hashText));
  if (result.text && result.text.length > 0) items.push(collectTextItem(result.text, hashText));

  if (items.length === 0) return null;

  if (result.text) {
    const colorItem = collectTextToColorItem(result.text, items, hashText);
    if (colorItem) items.push(colorItem);
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

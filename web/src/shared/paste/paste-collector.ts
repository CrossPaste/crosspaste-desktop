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
  /** Files that need blob storage (name → dataUrl, optional per-item hash) */
  fileBlobs: Array<{ name: string; dataUrl: string; hash?: string }>;
}

type TypedItem = { pasteType: number; item: PasteItem };
type HashFn = (s: string) => string;
type HashBytesFn = (bytes: Uint8Array) => string;

/** Decode a data URL's base64 payload to raw bytes. */
function dataUrlToBytes(dataUrl: string): Uint8Array {
  const base64 = dataUrl.split(",")[1] ?? "";
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

/** Hash file content — uses raw-byte hash if available, otherwise falls back to string hash. */
function hashFileContent(
  dataUrl: string,
  hashText: HashFn,
  hashBytes?: HashBytesFn,
): string {
  if (hashBytes) {
    return hashBytes(dataUrlToBytes(dataUrl));
  }
  return hashText(dataUrl);
}

const MAX_FILE_SIZE = 32 * 1024 * 1024; // 32MB

function collectFileItems(
  files: ClipboardFileInfo[],
  hashText: HashFn,
  hashBytes?: HashBytesFn,
): { items: TypedItem[]; fileBlobs: Array<{ name: string; dataUrl: string; hash?: string }> } {
  const fileBlobs: Array<{ name: string; dataUrl: string; hash?: string }> = [];
  if (files.length === 0) return { items: [], fileBlobs };

  const totalSize = files.reduce((sum, f) => sum + f.size, 0);
  if (totalSize > MAX_FILE_SIZE) return { items: [], fileBlobs };

  const filesHash = hashText(files.map((f) => `${f.name}:${f.size}`).join("|"));
  for (const f of files) {
    if (f.dataUrl) fileBlobs.push({ name: f.name, dataUrl: f.dataUrl });
  }

  // Build fileInfoTreeMap so Desktop can resolve file paths and create placeholders.
  // Format matches Kotlin's SingleFileInfoTree: { type: "file", size, hash }
  // Hash raw file bytes (matching Desktop's getFileHash) when hashBytes is available.
  const fileInfoTreeMap: Record<string, unknown> = {};
  for (const f of files) {
    fileInfoTreeMap[f.name] = {
      type: "file",
      size: f.size,
      hash: f.dataUrl ? hashFileContent(f.dataUrl, hashText, hashBytes) : hashText(`${f.name}:${f.size}`),
    };
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
        fileInfoTreeMap,
      },
    }],
    fileBlobs,
  };
}

function collectImageItem(imageDataUrl: string, hashText: HashFn, hashBytes?: HashBytesFn): TypedItem {
  const base64Part = imageDataUrl.split(",")[1] ?? "";
  const imgSize = Math.round((base64Part.length * 3) / 4);
  const imgHash = hashFileContent(imageDataUrl, hashText, hashBytes);
  return {
    pasteType: PasteTypeInt.IMAGE,
    item: {
      type: "images",
      identifiers: ["image/png"],
      hash: imgHash,
      size: imgSize,
      count: 1,
      relativePathList: ["clipboard-image.png"],
      fileInfoTreeMap: {
        "clipboard-image.png": { type: "file", size: imgSize, hash: imgHash },
      },
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
  hashBytes?: HashBytesFn,
): CollectedPaste | null {
  const items: TypedItem[] = [];
  let fileBlobs: Array<{ name: string; dataUrl: string; hash?: string }> = [];

  if (result.files && result.files.length > 0) {
    const collected = collectFileItems(result.files, hashText, hashBytes);
    items.push(...collected.items);
    fileBlobs = collected.fileBlobs;
  }
  if (result.imageDataUrl) {
    const imageItem = collectImageItem(result.imageDataUrl, hashText, hashBytes);
    items.push(imageItem);
    // Store image data in BlobStore so it can be served via FILE_PULL_REQUEST.
    // Use the image item's own hash (not the appear item's hash) so Desktop
    // can look it up by the PasteFiles hash it receives in the paste data.
    fileBlobs.push({ name: "clipboard-image.png", dataUrl: result.imageDataUrl, hash: imageItem.item.hash });
  }
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

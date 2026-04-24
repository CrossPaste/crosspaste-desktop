import { PasteType } from "@/shared/models/paste-item";
import type {
  PasteItem,
  TextPasteItem,
  UrlPasteItem,
  HtmlPasteItem,
  RtfPasteItem,
  ColorPasteItem,
} from "@/shared/models/paste-item";
import type { PasteData } from "@/shared/models/paste-data";
import { argbToHex } from "@/shared/utils/color";

/**
 * Extract plain text for HTML5 drag-and-drop into input fields.
 *
 * A single PasteData often contains multiple representations of the same copy
 * (e.g. an HTML paste also ships the extracted TEXT sibling). Prefer the
 * plain-text sibling so a drop into <input>/<textarea> yields readable text;
 * only fall back to the HTML/RTF source when no plain sibling exists.
 *
 * Returns null for types that cannot be inserted as text (pure image/file).
 */
export function getDragText(data: PasteData): string | null {
  const items: PasteItem[] = [];
  if (data.pasteAppearItem) items.push(data.pasteAppearItem);
  items.push(...data.pasteCollection.pasteItems);

  for (const item of items) {
    switch (item.type) {
      case PasteType.TEXT:
        return (item as TextPasteItem).text;
      case PasteType.URL:
        return (item as UrlPasteItem).url;
      case PasteType.COLOR:
        return argbToHex((item as ColorPasteItem).color);
    }
  }

  for (const item of items) {
    if (item.type === PasteType.HTML) return (item as HtmlPasteItem).html;
    if (item.type === PasteType.RTF) return (item as RtfPasteItem).rtf;
  }

  return null;
}

const IMAGE_MIME_MAP: Record<string, string> = {
  png: "image/png",
  jpg: "image/jpeg",
  jpeg: "image/jpeg",
  gif: "image/gif",
  webp: "image/webp",
  svg: "image/svg+xml",
  bmp: "image/bmp",
  ico: "image/x-icon",
  tiff: "image/tiff",
  tif: "image/tiff",
};

function imageMimeFromFileName(fileName: string): string {
  const ext = fileName.split(".").pop()?.toLowerCase() ?? "";
  return IMAGE_MIME_MAP[ext] ?? "image/png";
}

/**
 * Populate a DataTransfer with image drag payloads. Covers three drop targets:
 * - `text/plain` / `text/uri-list` — URL text into inputs or address bars.
 * - `text/html` — inline <img> so rich editors (Gmail, WYSIWYG) embed the image.
 * - `DownloadURL` — Chrome-specific format that lets the user drop onto the
 *   OS desktop / file manager and Chrome downloads the blob as a file.
 */
export function applyImageDragData(
  dataTransfer: DataTransfer,
  imageUrl: string,
  fileName: string,
): void {
  const mime = imageMimeFromFileName(fileName);
  dataTransfer.setData("text/plain", fileName);
  dataTransfer.setData("text/uri-list", imageUrl);
  dataTransfer.setData("text/html", `<img src="${imageUrl}" alt="${fileName}">`);
  dataTransfer.setData("DownloadURL", `${mime}:${fileName}:${imageUrl}`);
  dataTransfer.effectAllowed = "copy";
}

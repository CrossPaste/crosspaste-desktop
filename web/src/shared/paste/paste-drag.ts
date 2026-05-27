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
import { imageMimeFromFileName } from "@/shared/utils/mime";

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
  const mime = imageMimeFromFileName(fileName) ?? "image/png";
  dataTransfer.setData("text/plain", fileName);
  dataTransfer.setData("text/uri-list", imageUrl);
  dataTransfer.setData("text/html", `<img src="${imageUrl}" alt="${fileName}">`);
  dataTransfer.setData("DownloadURL", `${mime}:${fileName}:${imageUrl}`);
  dataTransfer.effectAllowed = "copy";
}

/**
 * Replace Chrome's default rectangular drag snapshot with a rounded one.
 *
 * Why: the card source has `border-radius` + `overflow: hidden`, but Chrome's
 * default drag image rasterizes the bounding box and paints the corners
 * opaque, so the ghost looks rectangular even though the card on screen is
 * rounded. We render an off-screen clone (carrying the same styles) and feed
 * it to `setDragImage` so the captured snapshot keeps the rounded corners.
 *
 * Iframes inside the clone (HTML preview) are swapped for placeholders — a
 * reattached iframe re-fetches its srcdoc and would snapshot blank anyway.
 */
export function setRoundedDragImage(
  source: HTMLElement,
  dataTransfer: DataTransfer,
  pointerX: number,
  pointerY: number,
): void {
  const rect = source.getBoundingClientRect();
  const clone = source.cloneNode(true) as HTMLElement;

  clone.querySelectorAll("iframe").forEach((iframe) => {
    const placeholder = document.createElement("div");
    placeholder.className = iframe.className;
    placeholder.style.cssText = iframe.style.cssText;
    iframe.replaceWith(placeholder);
  });

  Object.assign(clone.style, {
    position: "fixed",
    top: "-10000px",
    left: "-10000px",
    width: `${rect.width}px`,
    height: `${rect.height}px`,
    margin: "0",
    pointerEvents: "none",
  });
  document.body.appendChild(clone);

  dataTransfer.setDragImage(
    clone,
    pointerX - rect.left,
    pointerY - rect.top,
  );

  setTimeout(() => clone.remove(), 0);
}

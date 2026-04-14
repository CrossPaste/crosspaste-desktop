import { PasteTypeInt } from "@/shared/models/paste-item";
import type { PasteProcessPlugin, TypedItem } from "./paste-process-plugin";

function isSingleImgInBody(html: string): boolean {
  const imgCount = (html.match(/<img[\s>]/gi) || []).length;
  if (imgCount !== 1) return false;
  const textContent = html.replace(/<[^>]*>/g, "").trim();
  return textContent.length === 0;
}

export class RemoveHtmlImagePlugin implements PasteProcessPlugin {
  process(items: TypedItem[]): TypedItem[] {
    const hasImage = items.some((i) => i.pasteType === PasteTypeInt.IMAGE);
    if (!hasImage) return items;
    const htmlItem = items.find((i) => i.pasteType === PasteTypeInt.HTML);
    if (!htmlItem) return items;
    const html = (htmlItem.item as { html: string }).html;
    if (isSingleImgInBody(html)) {
      return items.filter((i) => i !== htmlItem);
    }
    return items;
  }
}

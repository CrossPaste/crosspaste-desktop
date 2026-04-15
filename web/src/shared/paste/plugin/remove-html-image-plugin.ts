import { PasteTypeInt } from "@/shared/models/paste-item";
import type { PasteProcessContext, PasteProcessPlugin, TypedItem } from "./paste-process-plugin";

function stripComments(html: string): string {
  return html.replace(/<!--[\s\S]*?-->/g, "");
}

export function isSingleImgInBody(html: string): boolean {
  const stripped = stripComments(html);
  const imgCount = (stripped.match(/<img[\s/>]/gi) || []).length;
  if (imgCount !== 1) return false;
  const textContent = stripped.replace(/<[^>]*>/g, "").trim();
  return textContent.length === 0;
}

export class RemoveHtmlImagePlugin implements PasteProcessPlugin {
  process(items: TypedItem[], _context: PasteProcessContext): TypedItem[] {
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

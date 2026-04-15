import { PasteTypeInt } from "@/shared/models/paste-item";
import { parseColor } from "@/shared/paste/paste-type-detector";
import type { PasteProcessContext, PasteProcessPlugin, TypedItem } from "./paste-process-plugin";

export class TextToColorPlugin implements PasteProcessPlugin {
  process(items: TypedItem[], context: PasteProcessContext): TypedItem[] {
    if (items.some((i) => i.pasteType === PasteTypeInt.COLOR)) return items;

    const textItem = items.find((i) => i.pasteType === PasteTypeInt.TEXT || i.pasteType === PasteTypeInt.URL);
    if (!textItem) return items;

    const text = (textItem.item as { text?: string }).text ?? "";
    const trimmed = text.trim();
    if (trimmed.includes("\n")) return items;

    const colorValue = parseColor(trimmed);
    if (colorValue === null) return items;

    return [
      ...items,
      {
        pasteType: PasteTypeInt.COLOR,
        item: {
          type: "color",
          identifiers: (textItem.item as { identifiers: string[] }).identifiers,
          hash: context.hashText(trimmed),
          size: new TextEncoder().encode(trimmed).length,
          color: colorValue,
        },
      },
    ];
  }
}

import { PasteTypeInt } from "@/shared/models/paste-item";
import type { PasteProcessContext, PasteProcessPlugin, TypedItem } from "./paste-process-plugin";

const PRIORITY: Record<number, number> = {
  [PasteTypeInt.TEXT]: 0,
  [PasteTypeInt.URL]: 1,
  [PasteTypeInt.IMAGE]: 2,
  [PasteTypeInt.RTF]: 3,
  [PasteTypeInt.HTML]: 4,
  [PasteTypeInt.COLOR]: 5,
  [PasteTypeInt.FILE]: 6,
};

export class SortPlugin implements PasteProcessPlugin {
  process(items: TypedItem[], _context: PasteProcessContext): TypedItem[] {
    return [...items].sort((a, b) => (PRIORITY[b.pasteType] ?? 0) - (PRIORITY[a.pasteType] ?? 0));
  }
}

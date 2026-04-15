import type { PasteItem } from "@/shared/models/paste-item";

export interface TypedItem {
  pasteType: number;
  item: PasteItem;
}

export interface PasteProcessContext {
  hashText: (s: string) => string;
}

export interface PasteProcessPlugin {
  process(items: TypedItem[], context: PasteProcessContext): TypedItem[];
}

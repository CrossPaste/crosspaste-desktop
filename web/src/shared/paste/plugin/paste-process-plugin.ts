import type { PasteItem } from "@/shared/models/paste-item";

export interface TypedItem {
  pasteType: number;
  item: PasteItem;
}

export interface PasteProcessPlugin {
  process(items: TypedItem[]): TypedItem[];
}

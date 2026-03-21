import type { PasteItem } from "./paste-item";
import { CrossPasteJson } from "@/shared/core";

export interface PasteCollection {
  pasteItems: PasteItem[];
}

export interface PasteData {
  id: number;
  appInstanceId: string;
  favorite: boolean;
  pasteAppearItem: PasteItem | null;
  pasteCollection: PasteCollection;
  pasteType: number;
  source: string | null;
  size: number;
  hash: string;
  /** Local-only: timestamp when this item was received */
  receivedAt?: number;
}

/**
 * Parse PasteData from a JSON string using the core Kotlin serializer.
 * This ensures format compatibility with the server's PasteDataSerializer.
 */
export function parsePasteData(jsonString: string): PasteData | null {
  try {
    const normalized = CrossPasteJson.parsePasteData(jsonString);
    const data = JSON.parse(normalized) as PasteData;
    data.receivedAt = Date.now();
    return data;
  } catch {
    return null;
  }
}

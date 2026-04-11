import type { PasteItem } from "./paste-item";
import { CrossPasteJson } from "@/shared/core";

/** Matches desktop PasteState constants */
export const PasteState = {
  DELETED: -1,
  LOADING: 0,
  LOADED: 1,
} as const;

export interface PasteCollection {
  pasteItems: PasteItem[];
}

export interface PasteData {
  /** Auto-increment key assigned by IndexedDB (only present when read from store) */
  _id?: number;
  id: number;
  appInstanceId: string;
  favorite: boolean;
  pasteAppearItem: PasteItem | null;
  pasteCollection: PasteCollection;
  pasteType: number;
  source: string | null;
  size: number;
  hash: string;
  pasteState: number;
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
    data.pasteState = PasteState.LOADED;
    return data;
  } catch {
    return null;
  }
}

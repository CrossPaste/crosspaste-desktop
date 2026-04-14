import { PasteStore } from "@/shared/storage/paste-store";
import { BlobStore } from "@/shared/storage/blob-store";
import type { PasteData } from "@/shared/models/paste-data";

/**
 * Unified paste ingestion: store, deduplicate, evict, and broadcast.
 * Both clipboard polling and WebSocket paste_push flow through here.
 *
 * Returns the new internal ID if stored, or null if rejected.
 */
export async function ingestPaste(
  pasteData: PasteData,
  broadcastToSidePanel: (message: unknown) => void,
): Promise<number | null> {
  const newId = await PasteStore.createPasteData(pasteData);
  if (newId === null) return null;

  const deleted = await PasteStore.markDeleteSameHash(newId, pasteData.hash);
  for (const h of deleted) await BlobStore.deleteForPaste(h);

  const evicted = await PasteStore.evictOverLimit();
  for (const h of evicted) await BlobStore.deleteForPaste(h);

  broadcastToSidePanel({ type: "PASTE_UPDATED" });
  return newId;
}

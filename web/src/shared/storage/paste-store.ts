import { openDB, type IDBPDatabase } from "idb";
import type { PasteData } from "@/shared/models/paste-data";
import { PasteState } from "@/shared/models/paste-data";
import { PASTE_TYPE_FROM_INT } from "@/shared/models/paste-item";
import type { PasteItem } from "@/shared/models/paste-item";

const DB_NAME = "crosspaste";
const STORE_NAME = "pastes";
const DB_VERSION = 1;
const MAX_ITEMS = 100;
const MAX_ITEM_SIZE = 5 * 1024 * 1024; // 5MB

async function getDb(): Promise<IDBPDatabase> {
  return openDB(DB_NAME, DB_VERSION, {
    upgrade(db) {
      const store = db.createObjectStore(STORE_NAME, {
        keyPath: "_id",
        autoIncrement: true,
      });
      // hash is NOT unique — matches desktop where id is the primary key
      // and multiple records with the same hash can exist temporarily
      store.createIndex("hash", "hash");
      store.createIndex("pasteState", "pasteState");
      store.createIndex("receivedAt", "receivedAt");
    },
  });
}

export const PasteStore = {
  /**
   * Create a new paste record (LOADED state).
   * Matches desktop flow: always insert a new record, then mark-delete old same-hash entries.
   * Returns the new record's _id, or null if rejected (oversized).
   */
  async createPasteData(data: PasteData): Promise<number | null> {
    if (data.size > MAX_ITEM_SIZE) return null;

    const db = await getDb();
    const id = (await db.add(STORE_NAME, {
      ...data,
      pasteState: PasteState.LOADED,
      receivedAt: Date.now(),
    })) as number;

    return id;
  },

  /**
   * Mark-delete old entries with the same hash, excluding the given id.
   * Matches desktop PasteReleaseService.markDeleteSameHash().
   * Returns hashes of deleted entries for blob cleanup.
   */
  async markDeleteSameHash(excludeId: number, hash: string): Promise<string[]> {
    const db = await getDb();
    const tx = db.transaction(STORE_NAME, "readwrite");
    const hashIndex = tx.store.index("hash");
    const deletedHashes: string[] = [];

    let cursor = await hashIndex.openCursor(hash);
    while (cursor) {
      const entry = cursor.value as PasteData;
      if (entry._id !== excludeId && entry.pasteState !== PasteState.DELETED) {
        entry.pasteState = PasteState.DELETED;
        await cursor.update(entry);
        deletedHashes.push(entry.hash);
      }
      cursor = await cursor.continue();
    }

    await tx.done;
    return deletedHashes;
  },

  /**
   * Evict oldest LOADED items if over limit.
   * Returns hashes of evicted entries for blob cleanup.
   */
  async evictOverLimit(): Promise<string[]> {
    const db = await getDb();

    // Count only LOADED items
    const tx = db.transaction(STORE_NAME, "readonly");
    const stateIndex = tx.store.index("pasteState");
    const loadedCount = await stateIndex.count(PasteState.LOADED);
    await tx.done;

    if (loadedCount <= MAX_ITEMS) return [];

    const evictedHashes: string[] = [];
    const evictTx = db.transaction(STORE_NAME, "readwrite");
    const timeIndex = evictTx.store.index("receivedAt");
    let cursor = await timeIndex.openCursor();
    let toDelete = loadedCount - MAX_ITEMS;

    while (cursor && toDelete > 0) {
      const entry = cursor.value as PasteData;
      if (entry.pasteState === PasteState.LOADED) {
        entry.pasteState = PasteState.DELETED;
        await cursor.update(entry);
        evictedHashes.push(entry.hash);
        toDelete--;
      }
      cursor = await cursor.continue();
    }

    await evictTx.done;
    return evictedHashes;
  },

  /**
   * Purge all DELETED records from the store.
   * Call periodically to reclaim storage.
   */
  async purgeDeleted(): Promise<void> {
    const db = await getDb();
    const tx = db.transaction(STORE_NAME, "readwrite");
    const stateIndex = tx.store.index("pasteState");

    let cursor = await stateIndex.openCursor(PasteState.DELETED);
    while (cursor) {
      await cursor.delete();
      cursor = await cursor.continue();
    }

    await tx.done;
  },

  /** Get LOADED items sorted by receivedAt descending */
  async getItems(offset: number, limit: number): Promise<PasteData[]> {
    const db = await getDb();
    const tx = db.transaction(STORE_NAME, "readonly");
    const index = tx.store.index("receivedAt");
    const items: PasteData[] = [];

    let cursor = await index.openCursor(null, "prev");
    let skipped = 0;

    while (cursor && items.length < limit) {
      const entry = cursor.value as PasteData;
      if (entry.pasteState === PasteState.LOADED) {
        if (skipped >= offset) {
          items.push(entry);
        } else {
          skipped++;
        }
      }
      cursor = await cursor.continue();
    }

    return items;
  },

  /** Mark a paste as DELETED by auto-increment ID. Returns the hash if found. */
  async deleteById(id: number): Promise<string | null> {
    const db = await getDb();
    const existing = await db.get(STORE_NAME, id);
    if (!existing) return null;

    const entry = existing as PasteData;
    entry.pasteState = PasteState.DELETED;
    await db.put(STORE_NAME, entry);
    return entry.hash;
  },

  /** Move a paste to the top by updating its receivedAt to now. Returns its hash. */
  async moveToTop(id: number): Promise<string | null> {
    const db = await getDb();
    const entry = await db.get(STORE_NAME, id);
    if (!entry) return null;
    const paste = entry as PasteData;
    paste.receivedAt = Date.now();
    await db.put(STORE_NAME, paste);
    return paste.hash;
  },

  /** Clear all stored pastes */
  async clear(): Promise<void> {
    const db = await getDb();
    await db.clear(STORE_NAME);
  },

  /** Search LOADED items with optional text query and type filter */
  async searchItems(
    query: string,
    pasteType: number | null,
    offset: number,
    limit: number,
  ): Promise<PasteData[]> {
    const db = await getDb();
    const tx = db.transaction(STORE_NAME, "readonly");
    const index = tx.store.index("receivedAt");
    const items: PasteData[] = [];
    const lowerQuery = query.toLowerCase();

    let cursor = await index.openCursor(null, "prev");
    let skipped = 0;

    while (cursor && items.length < limit) {
      const entry = cursor.value as PasteData;
      if (entry.pasteState === PasteState.LOADED) {
        const matchesType = pasteType === null || entry.pasteType === pasteType;
        const matchesQuery = !lowerQuery || matchPasteText(entry, lowerQuery);

        if (matchesType && matchesQuery) {
          if (skipped >= offset) {
            items.push(entry);
          } else {
            skipped++;
          }
        }
      }
      cursor = await cursor.continue();
    }

    return items;
  },

  /** Get total LOADED item count */
  async count(): Promise<number> {
    const db = await getDb();
    const tx = db.transaction(STORE_NAME, "readonly");
    const stateIndex = tx.store.index("pasteState");
    return stateIndex.count(PasteState.LOADED);
  },
};

/** Extract searchable text from a PasteItem */
function extractItemText(item: PasteItem): string {
  switch (item.type) {
    case "text":
      return item.text;
    case "url":
      return item.url;
    case "html":
      return item.html;
    case "files":
    case "images":
      return item.relativePathList.join(" ");
    case "color":
      return item.color.toString(16);
    case "rtf":
      return item.rtf;
    default:
      return "";
  }
}

/** Check if a PasteData matches a lowercase query string */
function matchPasteText(data: PasteData, lowerQuery: string): boolean {
  // Check source
  if (data.source?.toLowerCase().includes(lowerQuery)) return true;

  // Check type label
  const typeLabel = PASTE_TYPE_FROM_INT[data.pasteType];
  if (typeLabel?.toLowerCase().includes(lowerQuery)) return true;

  // Check appear item
  if (data.pasteAppearItem) {
    if (extractItemText(data.pasteAppearItem).toLowerCase().includes(lowerQuery)) return true;
  }

  // Check collection items
  for (const item of data.pasteCollection.pasteItems) {
    if (extractItemText(item).toLowerCase().includes(lowerQuery)) return true;
  }

  return false;
}

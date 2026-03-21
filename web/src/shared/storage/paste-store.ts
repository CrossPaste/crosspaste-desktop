import { openDB, type IDBPDatabase } from "idb";
import type { PasteData } from "@/shared/models/paste-data";

const DB_NAME = "crosspaste";
const STORE_NAME = "pastes";
const DB_VERSION = 2;
const MAX_ITEMS = 100;
const MAX_ITEM_SIZE = 5 * 1024 * 1024; // 5MB

async function getDb(): Promise<IDBPDatabase> {
  return openDB(DB_NAME, DB_VERSION, {
    upgrade(db, oldVersion) {
      // Drop v1 store (hash-keyed) if upgrading
      if (oldVersion >= 1 && db.objectStoreNames.contains(STORE_NAME)) {
        db.deleteObjectStore(STORE_NAME);
      }
      const store = db.createObjectStore(STORE_NAME, {
        keyPath: "_id",
        autoIncrement: true,
      });
      store.createIndex("hash", "hash", { unique: true });
      store.createIndex("receivedAt", "receivedAt");
    },
  });
}

export const PasteStore = {
  /**
   * Add a paste item if it doesn't already exist (dedupe by hash).
   * If same hash exists, updates receivedAt to bring it to the top.
   * Returns true if a new item was added.
   */
  async addIfNew(data: PasteData): Promise<boolean> {
    if (data.size > MAX_ITEM_SIZE) return false;

    const db = await getDb();
    const tx = db.transaction(STORE_NAME, "readwrite");
    const hashIndex = tx.store.index("hash");
    const existing = await hashIndex.get(data.hash);

    if (existing) {
      // Same content — update timestamp to bring to top
      existing.receivedAt = Date.now();
      await tx.store.put(existing);
      await tx.done;
      return false;
    }

    // New paste
    await tx.store.add({ ...data, receivedAt: Date.now() });
    await tx.done;

    // Evict oldest items if over limit
    const count = await db.count(STORE_NAME);
    if (count > MAX_ITEMS) {
      const evictTx = db.transaction(STORE_NAME, "readwrite");
      const index = evictTx.store.index("receivedAt");
      let cursor = await index.openCursor();
      let toDelete = count - MAX_ITEMS;
      while (cursor && toDelete > 0) {
        await cursor.delete();
        cursor = await cursor.continue();
        toDelete--;
      }
      await evictTx.done;
    }

    return true;
  },

  /** Get items sorted by receivedAt descending */
  async getItems(offset: number, limit: number): Promise<PasteData[]> {
    const db = await getDb();
    const tx = db.transaction(STORE_NAME, "readonly");
    const index = tx.store.index("receivedAt");
    const items: PasteData[] = [];

    let cursor = await index.openCursor(null, "prev");
    let skipped = 0;

    while (cursor && items.length < limit) {
      if (skipped >= offset) {
        items.push(cursor.value as PasteData);
      } else {
        skipped++;
      }
      cursor = await cursor.continue();
    }

    return items;
  },

  /** Delete a single paste by auto-increment ID */
  async deleteById(id: number): Promise<boolean> {
    const db = await getDb();
    const existing = await db.get(STORE_NAME, id);
    if (!existing) return false;
    await db.delete(STORE_NAME, id);
    return true;
  },

  /** Clear all stored pastes */
  async clear(): Promise<void> {
    const db = await getDb();
    await db.clear(STORE_NAME);
  },

  /** Get total item count */
  async count(): Promise<number> {
    const db = await getDb();
    return db.count(STORE_NAME);
  },
};

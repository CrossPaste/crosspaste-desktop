// TODO: When a second consumer needs blob-change notifications (e.g. file preview, audio),
// add an onChange(hash, callback) subscription API here so hooks subscribe directly to
// BlobStore instead of each listening to chrome.runtime.onMessage independently.
import { openDB, type IDBPDatabase } from "idb";

const DB_NAME = "crosspaste-blobs";
const STORE_NAME = "blobs";
const DB_VERSION = 1;

/**
 * Separate IndexedDB database for binary file storage.
 * Stores ArrayBuffer blobs keyed by "{pasteHash}/{filename}".
 */

async function getDb(): Promise<IDBPDatabase> {
  return openDB(DB_NAME, DB_VERSION, {
    upgrade(db) {
      db.createObjectStore(STORE_NAME);
    },
  });
}

function blobKey(pasteHash: string, fileName: string): string {
  return `${pasteHash}/${fileName}`;
}

export const BlobStore = {
  /** Store a file blob */
  async put(pasteHash: string, fileName: string, data: ArrayBuffer): Promise<void> {
    const db = await getDb();
    await db.put(STORE_NAME, data, blobKey(pasteHash, fileName));
  },

  /** Store multiple file blobs for a single paste */
  async putAll(
    pasteHash: string,
    files: Array<{ name: string; data: ArrayBuffer }>,
  ): Promise<void> {
    const db = await getDb();
    const tx = db.transaction(STORE_NAME, "readwrite");
    for (const file of files) {
      await tx.store.put(file.data, blobKey(pasteHash, file.name));
    }
    await tx.done;
  },

  /** Get a single file blob */
  async get(pasteHash: string, fileName: string): Promise<ArrayBuffer | undefined> {
    const db = await getDb();
    return db.get(STORE_NAME, blobKey(pasteHash, fileName));
  },

  /** Get all blobs for a paste (by hash prefix scan) */
  async getAllForPaste(pasteHash: string): Promise<Map<string, ArrayBuffer>> {
    const db = await getDb();
    const prefix = `${pasteHash}/`;
    const result = new Map<string, ArrayBuffer>();
    const tx = db.transaction(STORE_NAME, "readonly");
    let cursor = await tx.store.openCursor(IDBKeyRange.bound(prefix, prefix + "\uffff"));
    while (cursor) {
      const key = cursor.key as string;
      const fileName = key.slice(prefix.length);
      result.set(fileName, cursor.value as ArrayBuffer);
      cursor = await cursor.continue();
    }
    return result;
  },

  /** Delete all blobs for a paste */
  async deleteForPaste(pasteHash: string): Promise<void> {
    const db = await getDb();
    const prefix = `${pasteHash}/`;
    const tx = db.transaction(STORE_NAME, "readwrite");
    let cursor = await tx.store.openCursor(IDBKeyRange.bound(prefix, prefix + "\uffff"));
    while (cursor) {
      await cursor.delete();
      cursor = await cursor.continue();
    }
    await tx.done;
  },

  /** Clear all blobs */
  async clear(): Promise<void> {
    const db = await getDb();
    await db.clear(STORE_NAME);
  },
};

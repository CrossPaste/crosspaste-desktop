import { useState, useEffect, useCallback, useRef } from "react";
import type { PasteData } from "@/shared/models/paste-data";

const PAGE_SIZE = 30;

async function fetchPastes(offset: number, limit: number): Promise<PasteData[]> {
  try {
    const result = (await chrome.runtime.sendMessage({
      type: "GET_PASTES",
      offset,
      limit,
    })) as { items: PasteData[] };
    return result.items ?? [];
  } catch {
    return [];
  }
}

export function usePasteList() {
  const [items, setItems] = useState<PasteData[]>([]);
  const [loading, setLoading] = useState(true);
  const [hasMore, setHasMore] = useState(true);
  const offsetRef = useRef(0);
  const busyRef = useRef(false);

  const loadMore = useCallback(async () => {
    if (busyRef.current || !hasMore) return;
    busyRef.current = true;
    setLoading(true);

    const newItems = await fetchPastes(offsetRef.current, PAGE_SIZE);
    setItems((prev) => [...prev, ...newItems]);
    offsetRef.current += newItems.length;
    if (newItems.length < PAGE_SIZE) setHasMore(false);

    setLoading(false);
    busyRef.current = false;
  }, [hasMore]);

  const reload = useCallback(async () => {
    busyRef.current = true;
    offsetRef.current = 0;

    const newItems = await fetchPastes(0, PAGE_SIZE);

    setItems(newItems);
    offsetRef.current = newItems.length;
    setHasMore(newItems.length >= PAGE_SIZE);

    busyRef.current = false;
  }, []);

  const deletePaste = useCallback(async (pasteId: number) => {
    await chrome.runtime.sendMessage({ type: "DELETE_PASTE", pasteId });
  }, []);

  // Initial load
  useEffect(() => {
    loadMore();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Listen for paste notifications → full reload
  useEffect(() => {
    const listener = (message: Record<string, unknown>) => {
      if (message.type === "PASTE_UPDATED") {
        reload();
      }
    };
    chrome.runtime.onMessage.addListener(listener);
    return () => chrome.runtime.onMessage.removeListener(listener);
  }, [reload]);

  return { items, loading, hasMore, loadMore, deletePaste };
}

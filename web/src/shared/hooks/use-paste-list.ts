import { useState, useEffect, useCallback, useRef } from "react";
import type { PasteData } from "@/shared/models/paste-data";

const PAGE_SIZE = 30;

interface SearchParams {
  query: string;
  pasteType: number | null;
}

async function fetchPastes(
  offset: number,
  limit: number,
  params: SearchParams,
): Promise<PasteData[]> {
  try {
    const result = (await chrome.runtime.sendMessage({
      type: "GET_PASTES",
      offset,
      limit,
      query: params.query,
      pasteType: params.pasteType,
    })) as { items: PasteData[] };
    return result.items ?? [];
  } catch {
    return [];
  }
}

export function usePasteList(searchParams: SearchParams) {
  const [items, setItems] = useState<PasteData[]>([]);
  const [loading, setLoading] = useState(true);
  const [hasMore, setHasMore] = useState(true);
  const offsetRef = useRef(0);
  const busyRef = useRef(false);
  const paramsRef = useRef(searchParams);
  paramsRef.current = searchParams;

  const loadMore = useCallback(async () => {
    if (busyRef.current || !hasMore) return;
    busyRef.current = true;
    setLoading(true);

    const newItems = await fetchPastes(offsetRef.current, PAGE_SIZE, paramsRef.current);
    setItems((prev) => [...prev, ...newItems]);
    offsetRef.current += newItems.length;
    if (newItems.length < PAGE_SIZE) setHasMore(false);

    setLoading(false);
    busyRef.current = false;
  }, [hasMore]);

  const reload = useCallback(async () => {
    busyRef.current = true;
    offsetRef.current = 0;

    const newItems = await fetchPastes(0, PAGE_SIZE, paramsRef.current);

    setItems(newItems);
    offsetRef.current = newItems.length;
    setHasMore(newItems.length >= PAGE_SIZE);

    busyRef.current = false;
  }, []);

  const removeLocal = useCallback((pasteId: number) => {
    setItems((prev) => {
      const next = prev.filter((i) => i._id !== pasteId);
      if (next.length !== prev.length) {
        offsetRef.current = Math.max(0, offsetRef.current - (prev.length - next.length));
      }
      return next;
    });
  }, []);

  const deletePaste = useCallback(
    async (pasteId: number) => {
      removeLocal(pasteId);
      await chrome.runtime.sendMessage({ type: "DELETE_PASTE", pasteId });
    },
    [removeLocal],
  );

  // Reload when search params change
  useEffect(() => {
    setLoading(true);
    offsetRef.current = 0;
    setHasMore(true);
    reload().then(() => setLoading(false));
  }, [searchParams.query, searchParams.pasteType, reload]);

  // Listen for paste notifications
  useEffect(() => {
    const listener = (message: Record<string, unknown>) => {
      if (message.type === "PASTE_UPDATED") {
        reload();
      } else if (message.type === "PASTE_DELETED") {
        removeLocal(message.pasteId as number);
      }
    };
    chrome.runtime.onMessage.addListener(listener);
    return () => chrome.runtime.onMessage.removeListener(listener);
  }, [reload, removeLocal]);

  return { items, loading, hasMore, loadMore, deletePaste };
}

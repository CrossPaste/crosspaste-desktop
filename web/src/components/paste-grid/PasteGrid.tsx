import { useState, useRef, useCallback } from "react";
import { usePasteList } from "@/shared/hooks/use-paste-list";
import { PasteCard } from "./PasteCard";
import { PasteDetailView } from "./PasteDetailView";
import { EmptyState } from "./EmptyState";
import type { PasteData } from "@/shared/models/paste-data";
import { copyPasteData } from "@/shared/clipboard/clipboard-writer";
import { NotificationManager } from "@/shared/notification/notification-manager";

async function copyItem(data: PasteData) {
  try {
    await copyPasteData(data);
    NotificationManager.success("Copied");
  } catch {
    NotificationManager.error("Copy failed");
  }
}

export function PasteGrid() {
  const { items, loading, hasMore, loadMore, deletePaste } = usePasteList();
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const observer = useRef<IntersectionObserver | null>(null);

  const lastItemRef = useCallback(
    (node: HTMLDivElement | null) => {
      if (loading) return;
      if (observer.current) observer.current.disconnect();

      observer.current = new IntersectionObserver((entries) => {
        if (entries[0].isIntersecting && hasMore) {
          loadMore();
        }
      });

      if (node) observer.current.observe(node);
    },
    [loading, hasMore, loadMore],
  );

  // Detail view
  const selectedItem = selectedId
    ? items.find((i) => i._id === selectedId) ?? null
    : null;

  if (selectedItem) {
    return (
      <PasteDetailView
        data={selectedItem}
        onBack={() => setSelectedId(null)}
        onCopy={() => copyItem(selectedItem)}
        onDelete={
          selectedItem._id
            ? () => {
                deletePaste(selectedItem._id!);
                setSelectedId(null);
              }
            : undefined
        }
      />
    );
  }

  // List view
  if (!loading && items.length === 0) {
    return <EmptyState />;
  }

  return (
    <div className="p-2 grid gap-2" style={{ gridTemplateColumns: "repeat(auto-fill, minmax(150px, 1fr))" }}>
      {items.map((item, index) => (
        <div
          key={item._id ?? item.hash}
          ref={index === items.length - 1 ? lastItemRef : undefined}
        >
          <PasteCard
            data={item}
            onClick={() => setSelectedId(item._id ?? null)}
            onDelete={item._id ? () => deletePaste(item._id!) : undefined}
          />
        </div>
      ))}
      {loading && (
        <div className="col-span-full flex justify-center py-4">
          <div className="w-5 h-5 border-2 border-m3-primary border-t-transparent rounded-full animate-spin" />
        </div>
      )}
    </div>
  );
}

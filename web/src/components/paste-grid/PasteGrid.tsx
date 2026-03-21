import { useRef, useCallback } from "react";
import { usePasteList } from "@/shared/hooks/use-paste-list";
import { PasteCard } from "./PasteCard";
import { EmptyState } from "./EmptyState";

export function PasteGrid() {
  const { items, loading, hasMore, loadMore, deletePaste } = usePasteList();
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

  if (!loading && items.length === 0) {
    return <EmptyState />;
  }

  return (
    <div className="p-2" style={{ columns: "160px auto", columnGap: "8px" }}>
      {items.map((item, index) => (
        <div
          key={item._id ?? item.hash}
          ref={index === items.length - 1 ? lastItemRef : undefined}
          style={{ breakInside: "avoid", marginBottom: "8px" }}
        >
          <PasteCard
            data={item}
            onDelete={item._id ? () => deletePaste(item._id!) : undefined}
          />
        </div>
      ))}
      {loading && (
        <div className="flex justify-center py-4" style={{ breakInside: "avoid" }}>
          <div className="w-5 h-5 border-2 border-m3-primary border-t-transparent rounded-full animate-spin" />
        </div>
      )}
    </div>
  );
}

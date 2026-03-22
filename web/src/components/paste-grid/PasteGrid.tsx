import { useState, useRef, useCallback } from "react";
import { usePasteList } from "@/shared/hooks/use-paste-list";
import { PasteCard } from "./PasteCard";
import { PasteDetailView } from "./PasteDetailView";
import { EmptyState } from "./EmptyState";
import { PasteType } from "@/shared/models/paste-item";
import type {
  TextPasteItem,
  UrlPasteItem,
  HtmlPasteItem,
  ImagesPasteItem,
  ColorPasteItem,
} from "@/shared/models/paste-item";
import type { PasteData } from "@/shared/models/paste-data";
import { argbToHex } from "@/shared/utils/color";

function copyItem(data: PasteData) {
  const item = data.pasteAppearItem ?? data.pasteCollection.pasteItems[0];
  if (!item) return;

  if (item.type === PasteType.IMAGE) {
    const dataUrl = (item as ImagesPasteItem).dataUrl;
    if (dataUrl) {
      fetch(dataUrl)
        .then((res) => res.blob())
        .then((blob) =>
          navigator.clipboard.write([new ClipboardItem({ [blob.type]: blob })]),
        )
        .catch(() => {});
    }
    return;
  }

  let text = "";
  switch (item.type) {
    case PasteType.TEXT:
      text = (item as TextPasteItem).text;
      break;
    case PasteType.URL:
      text = (item as UrlPasteItem).url;
      break;
    case PasteType.HTML:
      text = (item as HtmlPasteItem).html;
      break;
    case PasteType.COLOR:
      text = argbToHex((item as ColorPasteItem).color);
      break;
    default:
      return;
  }
  navigator.clipboard.writeText(text);
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
    <div className="p-2 grid gap-2" style={{ gridTemplateColumns: "repeat(auto-fill, minmax(100px, 1fr))" }}>
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

import { useState, useRef, useCallback, useEffect, useMemo } from "react";
import { usePasteList } from "@/shared/hooks/use-paste-list";
import { PasteCard } from "./PasteCard";
import { PasteDetailView } from "./PasteDetailView";
import { EmptyState } from "./EmptyState";
import type { PasteData } from "@/shared/models/paste-data";
import { copyPasteData } from "@/shared/clipboard/clipboard-writer";
import { NotificationManager } from "@/shared/notification/notification-manager";
import { PasteTypeInt } from "@/shared/models/paste-item";
import { useI18n } from "@/shared/i18n/use-i18n";

/** Each entry maps to an i18n key; null value = "all types" */
const TYPE_OPTIONS: ReadonlyArray<{ value: number | null; i18nKey: string }> = [
  { value: null, i18nKey: "all_types" },
  { value: PasteTypeInt.TEXT, i18nKey: "text" },
  { value: PasteTypeInt.URL, i18nKey: "link" },
  { value: PasteTypeInt.HTML, i18nKey: "html" },
  { value: PasteTypeInt.IMAGE, i18nKey: "image" },
  { value: PasteTypeInt.FILE, i18nKey: "file" },
  { value: PasteTypeInt.COLOR, i18nKey: "color" },
];

export function PasteGrid() {
  const t = useI18n();
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedType, setSelectedType] = useState<number | null>(null);
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [typeDropdownOpen, setTypeDropdownOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  // Debounce search input
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedQuery(searchQuery), 300);
    return () => clearTimeout(timer);
  }, [searchQuery]);

  // Close dropdown on outside click
  useEffect(() => {
    if (!typeDropdownOpen) return;
    const handler = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setTypeDropdownOpen(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [typeDropdownOpen]);

  const searchParams = useMemo(
    () => ({ query: debouncedQuery, pasteType: selectedType }),
    [debouncedQuery, selectedType],
  );

  const { items, loading, hasMore, loadMore, deletePaste } = usePasteList(searchParams);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const observer = useRef<IntersectionObserver | null>(null);

  const copyItem = useCallback(
    async (data: PasteData) => {
      try {
        await copyPasteData(data);
        NotificationManager.success(t("copy_successful"));
      } catch {
        NotificationManager.error(t("copy_failed"));
      }
    },
    [t],
  );

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

  const isFiltering = debouncedQuery || selectedType !== null;
  const selectedTypeOption = TYPE_OPTIONS.find((opt) => opt.value === selectedType) ?? TYPE_OPTIONS[0];
  const selectedTypeLabel = t(selectedTypeOption.i18nKey);

  return (
    <div className="flex flex-col h-full">
      {/* Search bar */}
      <div className="flex items-center gap-2 px-2 pt-2 pb-1">
        {/* Search input */}
        <div className="flex-1 relative">
          <svg
            className="absolute left-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-m3-on-surface-variant"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <circle cx="11" cy="11" r="8" />
            <path d="m21 21-4.3-4.3" />
          </svg>
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder={t("search_pasteboard")}
            className="w-full h-8 pl-8 pr-8 text-sm rounded-lg
              bg-m3-surface-container-highest text-m3-on-surface
              placeholder:text-m3-on-surface-variant/50
              border border-m3-outline-variant
              focus:outline-none focus:border-m3-primary
              transition-colors"
          />
          {searchQuery && (
            <button
              onClick={() => setSearchQuery("")}
              className="absolute right-2 top-1/2 -translate-y-1/2 w-4 h-4
                text-m3-on-surface-variant hover:text-m3-on-surface
                transition-colors"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M18 6 6 18M6 6l12 12" />
              </svg>
            </button>
          )}
        </div>

        {/* Type filter dropdown */}
        <div className="relative" ref={dropdownRef}>
          <button
            onClick={() => setTypeDropdownOpen(!typeDropdownOpen)}
            className={`h-8 px-3 text-xs font-medium rounded-lg border
              transition-colors flex items-center gap-1.5 whitespace-nowrap
              ${selectedType !== null
                ? "bg-m3-primary text-m3-on-primary border-m3-primary"
                : "bg-m3-surface-container-highest text-m3-on-surface-variant border-m3-outline-variant hover:border-m3-primary"
              }`}
          >
            {selectedTypeLabel}
            <svg className="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
              <path d="m6 9 6 6 6-6" />
            </svg>
          </button>

          {typeDropdownOpen && (
            <div className="absolute right-0 top-full mt-1 z-50
              bg-m3-surface-container rounded-lg shadow-lg border border-m3-outline-variant
              py-1 min-w-[120px]">
              {TYPE_OPTIONS.map((opt) => (
                <button
                  key={opt.i18nKey}
                  onClick={() => {
                    setSelectedType(opt.value);
                    setTypeDropdownOpen(false);
                  }}
                  className={`w-full text-left px-3 py-1.5 text-xs transition-colors
                    ${opt.value === selectedType
                      ? "bg-m3-primary-container text-m3-on-primary-container font-medium"
                      : "text-m3-on-surface hover:bg-m3-surface-container-highest"
                    }`}
                >
                  {t(opt.i18nKey)}
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Grid */}
      {!loading && items.length === 0 ? (
        isFiltering ? (
          <div className="flex-1 flex items-center justify-center">
            <p className="text-sm text-m3-on-surface-variant">{t("empty")}</p>
          </div>
        ) : (
          <EmptyState />
        )
      ) : (
        <div
          className="flex-1 overflow-y-auto p-2 grid gap-2"
          style={{ gridTemplateColumns: "repeat(auto-fill, minmax(150px, 1fr))", alignContent: "start" }}
        >
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
      )}
    </div>
  );
}

import type { UrlPasteItem } from "@/shared/models/paste-item";

export function UrlPreview({ item }: { item: UrlPasteItem }) {
  return (
    <div className="flex items-start gap-1.5">
      <svg
        className="w-3.5 h-3.5 text-green-500 shrink-0 mt-0.5"
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
        strokeWidth={2}
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1"
        />
      </svg>
      <p className="text-xs text-blue-600 dark:text-blue-400 break-all line-clamp-4">
        {item.url}
      </p>
    </div>
  );
}

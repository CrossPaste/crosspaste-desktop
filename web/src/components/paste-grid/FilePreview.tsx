import type { FilesPasteItem } from "@/shared/models/paste-item";
import { formatSize } from "@/shared/utils/format";

export function FilePreview({ item }: { item: FilesPasteItem }) {
  const fileName = item.relativePathList?.[0] ?? "file";

  return (
    <div className="flex items-center gap-2">
      <svg
        className="w-8 h-8 text-purple-400 shrink-0"
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
        strokeWidth={1.5}
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          d="M19.5 14.25v-2.625a3.375 3.375 0 0 0-3.375-3.375h-1.5A1.125 1.125 0 0 1 13.5 7.125v-1.5a3.375 3.375 0 0 0-3.375-3.375H8.25m2.25 0H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 0 0-9-9Z"
        />
      </svg>
      <div className="min-w-0">
        <p className="text-xs text-gray-700 dark:text-gray-300 truncate">
          {fileName}
        </p>
        <p className="text-[10px] text-gray-400">
          {item.count > 1 ? `${item.count} files · ` : ""}
          {formatSize(item.size)}
        </p>
      </div>
    </div>
  );
}

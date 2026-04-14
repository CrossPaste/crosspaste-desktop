import type { ImagesPasteItem } from "@/shared/models/paste-item";
import { formatSize } from "@/shared/utils/format";
import { useImageItemUrl } from "@/shared/hooks/use-image-url";

export function ImagePreview({ item }: { item: ImagesPasteItem }) {
  const fileName = item.relativePathList?.[0] ?? "image";
  const imageUrl = useImageItemUrl(item);

  if (imageUrl) {
    return (
      <div className="flex flex-col gap-2">
        <img
          src={imageUrl}
          alt={fileName}
          className="w-full max-h-40 object-contain rounded-lg bg-m3-surface"
        />
        <p className="text-[10px] text-m3-on-surface-variant">
          {formatSize(item.size)}
        </p>
      </div>
    );
  }

  return (
    <div className="flex items-center gap-2">
      <svg
        className="w-8 h-8 text-pink-400 shrink-0"
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
        strokeWidth={1.5}
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          d="m2.25 15.75 5.159-5.159a2.25 2.25 0 0 1 3.182 0l5.159 5.159m-1.5-1.5 1.409-1.409a2.25 2.25 0 0 1 3.182 0l2.909 2.909M3.75 21h16.5A2.25 2.25 0 0 0 22.5 18.75V5.25A2.25 2.25 0 0 0 20.25 3H3.75A2.25 2.25 0 0 0 1.5 5.25v13.5A2.25 2.25 0 0 0 3.75 21Z"
        />
      </svg>
      <div className="min-w-0">
        <p className="text-xs text-gray-700 dark:text-gray-300 truncate">
          {fileName}
        </p>
        <p className="text-[10px] text-gray-400">
          {item.count > 1 ? `${item.count} images · ` : ""}
          {formatSize(item.size)}
        </p>
      </div>
    </div>
  );
}

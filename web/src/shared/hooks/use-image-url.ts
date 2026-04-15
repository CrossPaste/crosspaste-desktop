import type { ImagesPasteItem } from "@/shared/models/paste-item";
import { useBlobData } from "./use-blob-data";
import { useObjectUrl } from "./use-object-url";

export function useImageUrl(
  hash: string | undefined,
  fileName: string | undefined,
  dataUrl: string | undefined,
): string | undefined {
  const blobData = useBlobData(dataUrl ? undefined : hash, dataUrl ? undefined : fileName);
  const objectUrl = useObjectUrl(blobData, fileName);
  return dataUrl ?? objectUrl;
}

export function useImageItemUrl(item: ImagesPasteItem): string | undefined {
  return useImageUrl(item.hash, item.relativePathList?.[0], item.dataUrl);
}

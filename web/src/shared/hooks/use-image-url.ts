import { useState, useEffect, useRef } from "react";
import { BlobStore } from "@/shared/storage/blob-store";
import type { ImagesPasteItem } from "@/shared/models/paste-item";

function mimeFromExt(fileName: string): string | undefined {
  const ext = fileName.split(".").pop()?.toLowerCase() ?? "";
  const mimeMap: Record<string, string> = {
    png: "image/png",
    jpg: "image/jpeg",
    jpeg: "image/jpeg",
    gif: "image/gif",
    webp: "image/webp",
    svg: "image/svg+xml",
    bmp: "image/bmp",
    ico: "image/x-icon",
    tiff: "image/tiff",
    tif: "image/tiff",
  };
  return mimeMap[ext];
}

export function useImageUrl(
  hash: string | undefined,
  fileName: string | undefined,
  dataUrl: string | undefined,
): string | undefined {
  const [url, setUrl] = useState<string | undefined>(dataUrl);
  const [blobVersion, setBlobVersion] = useState(0);

  useEffect(() => {
    if (!hash) return;
    const listener = (message: Record<string, unknown>) => {
      if (message.type === "BLOBS_READY" && message.hash === hash) {
        setBlobVersion((v) => v + 1);
      }
    };
    chrome.runtime.onMessage.addListener(listener);
    return () => chrome.runtime.onMessage.removeListener(listener);
  }, [hash]);

  const loadedHashRef = useRef<string>();
  const objectUrlRef = useRef<string>();

  useEffect(() => {
    if (dataUrl) {
      setUrl(dataUrl);
      return;
    }
    if (!hash || !fileName) return;

    if (loadedHashRef.current === hash && objectUrlRef.current) {
      return;
    }

    let cancelled = false;

    BlobStore.get(hash, fileName).then((data) => {
      if (cancelled || !data) return;
      if (objectUrlRef.current) URL.revokeObjectURL(objectUrlRef.current);
      const mime = mimeFromExt(fileName);
      const blob = mime ? new Blob([data], { type: mime }) : new Blob([data]);
      objectUrlRef.current = URL.createObjectURL(blob);
      loadedHashRef.current = hash;
      setUrl(objectUrlRef.current);
    });

    return () => {
      cancelled = true;
    };
  }, [hash, fileName, dataUrl, blobVersion]);

  useEffect(() => {
    return () => {
      if (objectUrlRef.current) URL.revokeObjectURL(objectUrlRef.current);
    };
  }, []);

  return url;
}

export function useImageItemUrl(item: ImagesPasteItem): string | undefined {
  return useImageUrl(item.hash, item.relativePathList?.[0], item.dataUrl);
}

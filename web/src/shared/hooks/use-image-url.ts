import { useState, useEffect } from "react";
import { BlobStore } from "@/shared/storage/blob-store";

function mimeFromExt(fileName: string): string {
  const ext = fileName.split(".").pop()?.toLowerCase() ?? "";
  switch (ext) {
    case "png":
      return "image/png";
    case "gif":
      return "image/gif";
    case "webp":
      return "image/webp";
    case "svg":
      return "image/svg+xml";
    default:
      return "image/jpeg";
  }
}

export function useImageUrl(
  hash: string | undefined,
  fileName: string | undefined,
  dataUrl: string | undefined,
): string | undefined {
  const [url, setUrl] = useState<string | undefined>(dataUrl);
  const [blobVersion, setBlobVersion] = useState(0);

  useEffect(() => {
    const listener = (message: Record<string, unknown>) => {
      if (message.type === "BLOBS_READY" && message.hash === hash) {
        setBlobVersion((v) => v + 1);
      }
    };
    chrome.runtime.onMessage.addListener(listener);
    return () => chrome.runtime.onMessage.removeListener(listener);
  }, [hash]);

  useEffect(() => {
    if (dataUrl) {
      setUrl(dataUrl);
      return;
    }
    if (!hash || !fileName) return;

    let cancelled = false;
    let objectUrl: string | undefined;

    BlobStore.get(hash, fileName).then((data) => {
      if (cancelled || !data) return;
      const blob = new Blob([data], { type: mimeFromExt(fileName) });
      objectUrl = URL.createObjectURL(blob);
      setUrl(objectUrl);
    });

    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [hash, fileName, dataUrl, blobVersion]);

  return url;
}

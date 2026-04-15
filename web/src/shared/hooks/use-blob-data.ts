import { useState, useEffect } from "react";
import { BlobStore } from "@/shared/storage/blob-store";

let bridgeInitialized = false;

function initChromeMessageBridge() {
  if (bridgeInitialized) return;
  bridgeInitialized = true;
  chrome.runtime.onMessage.addListener((message: Record<string, unknown>) => {
    if (message.type === "BLOBS_READY" && typeof message.hash === "string") {
      BlobStore.notifyChange(message.hash);
    }
  });
}

export function useBlobData(
  hash: string | undefined,
  fileName: string | undefined,
): ArrayBuffer | undefined {
  const [data, setData] = useState<ArrayBuffer | undefined>(undefined);

  useEffect(() => {
    initChromeMessageBridge();
  }, []);

  useEffect(() => {
    if (!hash || !fileName) return;

    let cancelled = false;

    const load = () => {
      BlobStore.get(hash, fileName).then((result) => {
        if (!cancelled && result) setData(result);
      });
    };

    load();

    const unsubscribe = BlobStore.subscribe(hash, load);

    return () => {
      cancelled = true;
      unsubscribe();
    };
  }, [hash, fileName]);

  return data;
}

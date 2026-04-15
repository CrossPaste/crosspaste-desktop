import { useState, useEffect, useRef } from "react";

const MIME_MAP: Record<string, string> = {
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

function mimeFromExt(fileName: string): string | undefined {
  const ext = fileName.split(".").pop()?.toLowerCase() ?? "";
  return MIME_MAP[ext];
}

export function useObjectUrl(
  data: ArrayBuffer | undefined,
  fileName: string | undefined,
): string | undefined {
  const [url, setUrl] = useState<string | undefined>(undefined);
  const objectUrlRef = useRef<string | undefined>(undefined);

  useEffect(() => {
    if (!data || !fileName) {
      if (objectUrlRef.current) {
        URL.revokeObjectURL(objectUrlRef.current);
        objectUrlRef.current = undefined;
      }
      setUrl(undefined);
      return;
    }

    if (objectUrlRef.current) URL.revokeObjectURL(objectUrlRef.current);
    const mime = mimeFromExt(fileName);
    const blob = mime ? new Blob([data], { type: mime }) : new Blob([data]);
    objectUrlRef.current = URL.createObjectURL(blob);
    setUrl(objectUrlRef.current);

    return () => {
      if (objectUrlRef.current) {
        URL.revokeObjectURL(objectUrlRef.current);
        objectUrlRef.current = undefined;
      }
    };
  }, [data, fileName]);

  return url;
}

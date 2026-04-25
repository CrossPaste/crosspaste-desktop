import { useState, useEffect, useRef } from "react";
import { imageMimeFromFileName } from "@/shared/utils/mime";

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
    const mime = imageMimeFromFileName(fileName);
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

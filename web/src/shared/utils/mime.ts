export const IMAGE_MIME_MAP: Record<string, string> = {
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

export function imageMimeFromFileName(fileName: string): string | undefined {
  const ext = fileName.split(".").pop()?.toLowerCase() ?? "";
  return IMAGE_MIME_MAP[ext];
}

const SIZE_UNITS = ["B", "KB", "MB", "GB"];

export function formatSize(bytes: number): string {
  let i = 0;
  let size = bytes;
  while (size >= 1024 && i < SIZE_UNITS.length - 1) {
    size /= 1024;
    i++;
  }
  return `${size.toFixed(i === 0 ? 0 : 1)} ${SIZE_UNITS[i]}`;
}

export function truncateText(text: string, maxLength: number): string {
  if (text.length <= maxLength) return text;
  return text.slice(0, maxLength) + "…";
}

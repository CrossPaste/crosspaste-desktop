import {
  PASTE_TYPE_LABELS,
  PASTE_TYPE_FROM_INT,
} from "@/shared/models/paste-item";
import { relativeTime } from "@/shared/utils/date";

interface Props {
  pasteType: number;
  source: string | null;
  receivedAt?: number;
}

const TYPE_COLORS: Record<number, string> = {
  0: "bg-m3-primary-container text-m3-on-primary-container",   // Text
  1: "bg-m3-success-container text-m3-success",                // URL
  2: "bg-m3-warning-container text-m3-warning",                // HTML
  3: "bg-m3-primary-container text-m3-primary",                // File
  4: "bg-m3-error-container text-m3-error",                    // Image
  5: "bg-m3-warning-container text-m3-warning",                // RTF
  6: "bg-m3-warning-container text-m3-warning",                // Color
};

export function PasteCardHeader({ pasteType, source, receivedAt }: Props) {
  const typeValue = PASTE_TYPE_FROM_INT[pasteType];
  const label = typeValue ? PASTE_TYPE_LABELS[typeValue] : "Unknown";
  const colorClass = TYPE_COLORS[pasteType] ?? "bg-m3-surface text-m3-on-surface-variant";

  return (
    <div className="flex items-center justify-between px-3 py-2">
      <span
        className={`text-[10px] font-medium px-1.5 py-0.5 rounded-md ${colorClass}`}
      >
        {label}
      </span>
      <div className="flex items-center gap-1">
        {source && (
          <span className="text-[10px] text-m3-on-surface-variant truncate max-w-[60px]">
            {source}
          </span>
        )}
        {receivedAt && (
          <span className="text-[10px] text-m3-on-surface-variant">
            {relativeTime(receivedAt)}
          </span>
        )}
      </div>
    </div>
  );
}

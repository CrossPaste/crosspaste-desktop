import { useCallback } from "react";
import { ArrowLeft, Copy, Trash2 } from "lucide-react";
import type { PasteData } from "@/shared/models/paste-data";
import { PasteType, PASTE_TYPE_FROM_INT, PASTE_TYPE_LABELS } from "@/shared/models/paste-item";
import type {
  PasteItem,
  TextPasteItem,
  UrlPasteItem,
  HtmlPasteItem,
  ImagesPasteItem,
  FilesPasteItem,
  ColorPasteItem,
  RtfPasteItem,
} from "@/shared/models/paste-item";
import { useI18n } from "@/shared/i18n/use-i18n";
import { argbToHex, argbToComponents } from "@/shared/utils/color";
import { formatSize } from "@/shared/utils/format";
import { relativeTime } from "@/shared/utils/date";

interface Props {
  data: PasteData;
  onBack: () => void;
  onCopy: () => void;
  onDelete?: () => void;
}

const TYPE_COLORS: Record<number, string> = {
  0: "bg-m3-primary-container text-m3-on-primary-container",
  1: "bg-m3-success-container text-m3-success",
  2: "bg-m3-warning-container text-m3-warning",
  3: "bg-m3-primary-container text-m3-primary",
  4: "bg-m3-error-container text-m3-error",
  5: "bg-m3-warning-container text-m3-warning",
  6: "bg-m3-warning-container text-m3-warning",
};

function DetailContent({ item }: { item: PasteItem }) {
  switch (item.type) {
    case PasteType.TEXT:
      return (
        <pre className="text-sm text-m3-on-surface whitespace-pre-wrap break-words font-mono leading-relaxed">
          {(item as TextPasteItem).text}
        </pre>
      );

    case PasteType.URL:
      return (
        <a
          href={(item as UrlPasteItem).url}
          target="_blank"
          rel="noopener noreferrer"
          className="text-sm text-m3-primary break-all hover:underline"
          onClick={(e) => e.stopPropagation()}
        >
          {(item as UrlPasteItem).url}
        </a>
      );

    case PasteType.HTML:
      return (
        <div className="rounded-lg border border-m3-outline-variant overflow-hidden">
          <iframe
            srcDoc={(item as HtmlPasteItem).html}
            className="w-full min-h-[120px] bg-white"
            sandbox=""
            title="HTML preview"
          />
        </div>
      );

    case PasteType.IMAGE: {
      const img = item as ImagesPasteItem;
      if (img.dataUrl) {
        return (
          <img
            src={img.dataUrl}
            alt="clipboard image"
            className="w-full rounded-lg bg-m3-surface-container"
          />
        );
      }
      const fileName = img.relativePathList?.[0] ?? "image";
      return (
        <p className="text-sm text-m3-on-surface-variant">
          {fileName} ({img.count} {img.count > 1 ? "images" : "image"})
        </p>
      );
    }

    case PasteType.FILE: {
      const files = item as FilesPasteItem;
      return (
        <div className="flex flex-col gap-1">
          {files.relativePathList.map((path, i) => (
            <p key={i} className="text-sm text-m3-on-surface font-mono break-all">
              {path}
            </p>
          ))}
        </div>
      );
    }

    case PasteType.RTF:
      return (
        <pre className="text-sm text-m3-on-surface whitespace-pre-wrap break-words font-mono leading-relaxed max-h-[400px] overflow-y-auto">
          {(item as RtfPasteItem).rtf}
        </pre>
      );

    case PasteType.COLOR: {
      const color = (item as ColorPasteItem).color;
      const hex = argbToHex(color);
      const { a, r, g, b } = argbToComponents(color);
      return (
        <div className="flex flex-col gap-3">
          <div
            className="w-full h-24 rounded-xl border border-m3-outline-variant"
            style={{ backgroundColor: hex }}
          />
          <div className="flex flex-col gap-1 text-sm font-mono text-m3-on-surface">
            <p>HEX: {hex}</p>
            <p>RGBA: rgba({r}, {g}, {b}, {(a / 255).toFixed(2)})</p>
          </div>
        </div>
      );
    }

    default:
      return null;
  }
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between px-4 py-2.5">
      <span className="text-xs text-m3-on-surface-variant">{label}</span>
      <span className="text-xs font-medium text-m3-on-surface">{value}</span>
    </div>
  );
}

export function PasteDetailView({ data, onBack, onCopy, onDelete }: Props) {
  const t = useI18n();
  const displayItem = data.pasteAppearItem ?? data.pasteCollection.pasteItems[0];
  const typeValue = PASTE_TYPE_FROM_INT[data.pasteType];
  const typeLabel = typeValue ? PASTE_TYPE_LABELS[typeValue] : "Unknown";
  const colorClass = TYPE_COLORS[data.pasteType] ?? "bg-m3-surface text-m3-on-surface-variant";

  const handleCopy = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      onCopy();
    },
    [onCopy],
  );

  if (!displayItem) return null;

  return (
    <div className="flex flex-col h-full">
      {/* Top bar */}
      <div className="flex items-center justify-between px-4 py-3">
        <div className="flex items-center gap-2">
          <button
            onClick={onBack}
            className="flex items-center justify-center w-8 h-8 rounded-lg hover:bg-m3-surface-container transition-colors"
          >
            <ArrowLeft size={20} className="text-m3-on-surface" />
          </button>
          <span className={`text-[11px] font-medium px-2 py-0.5 rounded-md ${colorClass}`}>
            {typeLabel}
          </span>
        </div>
        <div className="flex items-center gap-1">
          <button
            onClick={handleCopy}
            className="flex items-center justify-center w-8 h-8 rounded-lg hover:bg-m3-surface-container transition-colors"
          >
            <Copy size={18} className="text-m3-on-surface-variant" />
          </button>
          {onDelete && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                onDelete();
              }}
              className="flex items-center justify-center w-8 h-8 rounded-lg hover:bg-m3-error-container/30 transition-colors"
            >
              <Trash2 size={18} className="text-m3-error" />
            </button>
          )}
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto px-5 pb-6">
        <div className="flex flex-col gap-4">
          {/* Full content */}
          <div className="rounded-[14px] bg-m3-surface-container p-4">
            <DetailContent item={displayItem} />
          </div>

          {/* Metadata */}
          <div className="flex flex-col rounded-[14px] bg-m3-surface-container overflow-hidden divide-y divide-m3-outline-variant/30">
            {data.source && (
              <InfoRow label={t("source")} value={data.source} />
            )}
            <InfoRow label={t("size")} value={formatSize(data.size)} />
            {data.receivedAt && (
              <InfoRow label={t("time")} value={relativeTime(data.receivedAt)} />
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

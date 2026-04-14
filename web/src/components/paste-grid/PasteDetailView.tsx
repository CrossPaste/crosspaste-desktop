import { useCallback } from "react";
import { ArrowLeft, Copy, Trash2 } from "lucide-react";
import type { PasteData } from "@/shared/models/paste-data";
import { PasteType, PASTE_TYPE_FROM_INT, PASTE_TYPE_I18N_KEYS } from "@/shared/models/paste-item";
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
import { useImageUrl } from "@/shared/hooks/use-image-url";
import { isDarkColor } from "@/shared/utils/html-color";
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
      // Rendered in a custom container by PasteDetailView (not here)
      return null;

    case PasteType.IMAGE:
      // Rendered in a custom container by PasteDetailView (not here)
      return null;

    case PasteType.FILE:
      // Rendered in a custom container by PasteDetailView (not here)
      return null;

    case PasteType.RTF:
      return (
        <pre className="text-sm text-m3-on-surface whitespace-pre-wrap break-words font-mono leading-relaxed max-h-[400px] overflow-y-auto">
          {(item as RtfPasteItem).rtf}
        </pre>
      );

    case PasteType.COLOR:
      // Rendered in a custom container by PasteDetailView (not here)
      return null;

    default:
      return null;
  }
}

function FileDetailContent({ item }: { item: FilesPasteItem }) {
  const name = item.relativePathList?.[0] ?? "file";
  return (
    <div className="flex-1 min-h-0 rounded-[14px] bg-m3-surface-container flex flex-col items-center justify-center gap-2">
      <svg className="w-12 h-12 text-m3-on-surface-variant" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 0 0-3.375-3.375h-1.5A1.125 1.125 0 0 1 13.5 7.125v-1.5a3.375 3.375 0 0 0-3.375-3.375H8.25m2.25 0H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 0 0-9-9Z" />
      </svg>
      <span className="text-sm text-m3-on-surface-variant truncate max-w-[80%]">{name}</span>
      {item.count > 1 && (
        <span className="text-xs text-m3-outline">{item.count} files</span>
      )}
    </div>
  );
}

function ImageDetailContent({ item }: { item: ImagesPasteItem }) {
  const imageUrl = useImageUrl(item.hash, item.relativePathList?.[0], item.dataUrl);
  if (imageUrl) {
    return (
      <div className="flex-1 min-h-0 rounded-[14px] bg-m3-surface-container flex items-center justify-center overflow-hidden">
        <img
          src={imageUrl}
          alt="clipboard image"
          className="w-full max-h-full object-contain"
        />
      </div>
    );
  }
  const fileName = item.relativePathList?.[0] ?? "image";
  return (
    <div className="flex-1 min-h-0 rounded-[14px] bg-m3-surface-container flex items-center justify-center">
      <p className="text-sm text-m3-on-surface-variant">
        {fileName} ({item.count} {item.count > 1 ? "images" : "image"})
      </p>
    </div>
  );
}

function ColorDetailContent({ item }: { item: ColorPasteItem }) {
  const color = item.color;
  const hex = argbToHex(color);
  const { a, r, g, b } = argbToComponents(color);
  const textColor = isDarkColor(color) ? "rgba(255,255,255,0.9)" : "rgba(0,0,0,0.75)";

  return (
    <div
      className="flex-1 min-h-0 rounded-[14px] flex flex-col items-center justify-center"
      style={{ backgroundColor: hex }}
    >
      <p className="text-lg font-semibold font-mono" style={{ color: textColor }}>
        {hex}
      </p>
      <p className="text-sm font-mono mt-1" style={{ color: textColor, opacity: 0.7 }}>
        rgba({r}, {g}, {b}, {(a / 255).toFixed(2)})
      </p>
    </div>
  );
}

function HtmlDetailContent({ item }: { item: HtmlPasteItem }) {
  const extraInfo = item.extraInfo as { background?: number } | undefined;
  const bgArgb = extraInfo?.background ?? null;
  let bgStyle: string | undefined;
  let textStyle: string | undefined;
  if (bgArgb !== null) {
    const a = (bgArgb >>> 24) & 0xff;
    const r = (bgArgb >> 16) & 0xff;
    const g = (bgArgb >> 8) & 0xff;
    const b = bgArgb & 0xff;
    if (a > 0) {
      bgStyle = `rgba(${r}, ${g}, ${b}, ${a / 255})`;
      textStyle = isDarkColor(bgArgb) ? "#f5f5f5" : "#1a1a1a";
    }
  }
  const bodyStyles = ["margin: 0", "padding: 8px"];
  if (bgStyle) bodyStyles.push(`background-color: ${bgStyle}`);
  if (textStyle) bodyStyles.push(`color: ${textStyle}`);
  const isDark = bgArgb !== null ? isDarkColor(bgArgb) : false;
  const thumbColor = isDark ? "rgba(255,255,255,0.2)" : "rgba(0,0,0,0.2)";
  const thumbHover = isDark ? "rgba(255,255,255,0.35)" : "rgba(0,0,0,0.35)";
  const scrollbarCss = `::-webkit-scrollbar{width:4px;height:4px}::-webkit-scrollbar-track{background:transparent}::-webkit-scrollbar-thumb{background:${thumbColor};border-radius:2px}::-webkit-scrollbar-thumb:hover{background:${thumbHover}}`;
  const srcDoc = `<html><head><style>${scrollbarCss} body { ${bodyStyles.join("; ")} }</style></head><body>${item.html}</body></html>`;

  return (
    <div
      className="flex-1 min-h-0 rounded-[14px] overflow-hidden"
      style={bgStyle ? { backgroundColor: bgStyle } : undefined}
    >
      <iframe
        srcDoc={srcDoc}
        sandbox=""
        className="w-full h-full border-0"
        title="HTML preview"
      />
    </div>
  );
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
  const i18nKey = typeValue ? PASTE_TYPE_I18N_KEYS[typeValue] : null;
  const typeLabel = i18nKey ? t(i18nKey) : "Unknown";
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

      {/* Content + Metadata */}
      <div className="flex-1 flex flex-col min-h-0 px-5 pb-6 gap-4">
        {/* Full content — fills available space */}
        {typeValue === PasteType.HTML ? (
          <HtmlDetailContent item={displayItem as HtmlPasteItem} />
        ) : typeValue === PasteType.COLOR ? (
          <ColorDetailContent item={displayItem as ColorPasteItem} />
        ) : typeValue === PasteType.IMAGE ? (
          <ImageDetailContent item={displayItem as ImagesPasteItem} />
        ) : typeValue === PasteType.FILE ? (
          <FileDetailContent item={displayItem as FilesPasteItem} />
        ) : (
          <div className="flex-1 min-h-0 rounded-[14px] bg-m3-surface-container p-4 overflow-y-auto">
            <DetailContent item={displayItem} />
          </div>
        )}

        {/* Metadata — pinned to bottom */}
        <div className="flex flex-col rounded-[14px] bg-m3-surface-container overflow-hidden divide-y divide-m3-outline-variant/30 shrink-0">
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
  );
}

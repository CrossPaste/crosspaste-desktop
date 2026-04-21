import { useState, useCallback, useEffect, useRef } from "react";
import { Copy, Trash2, Download } from "lucide-react";
import type { PasteData } from "@/shared/models/paste-data";
import { PasteType, PASTE_TYPE_FROM_INT, PASTE_TYPE_I18N_KEYS } from "@/shared/models/paste-item";
import { useI18n } from "@/shared/i18n/use-i18n";
import type {
  PasteItem,
  TextPasteItem,
  UrlPasteItem,
  HtmlPasteItem,
  FilesPasteItem,
  ImagesPasteItem,
  ColorPasteItem,
  RtfPasteItem,
} from "@/shared/models/paste-item";
import { argbToHex, argbToRgba } from "@/shared/utils/color";
import { formatSize } from "@/shared/utils/format";
import { relativeTime } from "@/shared/utils/date";
import { isDarkColor } from "@/shared/utils/html-color";
import { copyPasteData } from "@/shared/clipboard/clipboard-writer";
import { NotificationManager } from "@/shared/notification/notification-manager";
import { useImageItemUrl } from "@/shared/hooks/use-image-url";

const MAX_SIZE = 5 * 1024 * 1024;

interface Props {
  data: PasteData;
  onClick?: () => void;
  onDelete?: () => void;
}

// ─── Type Badge Colors ──────────────────────────────────────────────────

const TYPE_BADGE: Record<number, { bg: string; text: string }> = {
  0: { bg: "bg-m3-primary-container", text: "text-m3-on-primary-container" },
  1: { bg: "bg-m3-success-container", text: "text-m3-success" },
  2: { bg: "bg-m3-warning-container", text: "text-m3-warning" },
  3: { bg: "bg-m3-primary-container", text: "text-m3-primary" },
  4: { bg: "bg-m3-error-container", text: "text-m3-error" },
  5: { bg: "bg-m3-warning-container", text: "text-m3-warning" },
  6: { bg: "bg-m3-warning-container", text: "text-m3-warning" },
};

// ─── Title Bar ──────────────────────────────────────────────────────────

function GridTitle({ data }: { data: PasteData }) {
  const t = useI18n();
  const typeValue = PASTE_TYPE_FROM_INT[data.pasteType];
  const i18nKey = typeValue ? PASTE_TYPE_I18N_KEYS[typeValue] : null;
  const label = i18nKey ? t(i18nKey) : "";
  const badge = TYPE_BADGE[data.pasteType];

  return (
    <div className="flex items-center gap-1.5 px-2.5 py-2 bg-m3-surface-container-high shrink-0">
      {/* Type Badge */}
      {label && badge && (
        <span
          className={`text-[9px] font-semibold px-1.5 py-0.5 rounded ${badge.bg} ${badge.text} truncate`}
        >
          {label}
        </span>
      )}

      {/* Time */}
      {data.receivedAt && (
        <span className="text-[9px] text-m3-on-surface-variant ml-auto shrink-0">
          {relativeTime(data.receivedAt)}
        </span>
      )}
    </div>
  );
}

// ─── Content Renderers ──────────────────────────────────────────────────

function TextContent({ item }: { item: TextPasteItem }) {
  return (
    <div className="p-2.5 overflow-hidden flex-1">
      <p className="text-[11px] text-m3-on-surface whitespace-pre-wrap break-words font-mono leading-relaxed line-clamp-[8]">
        {item.text}
      </p>
    </div>
  );
}

function UrlContent({ item }: { item: UrlPasteItem }) {
  return (
    <div className="p-2.5 overflow-hidden flex-1 flex flex-col">
      <p className="text-[11px] text-m3-primary break-all line-clamp-[8]">
        {item.url}
      </p>
    </div>
  );
}

function ImageContent({ item }: { item: ImagesPasteItem }) {
  const imageUrl = useImageItemUrl(item);
  if (imageUrl) {
    return (
      <div className="flex-1 overflow-hidden">
        <img
          src={imageUrl}
          alt=""
          className="w-full h-full object-cover"
        />
      </div>
    );
  }
  const name = item.relativePathList?.[0] ?? "image";
  return (
    <div className="flex-1 flex flex-col items-center justify-center gap-1 text-m3-on-surface-variant">
      <svg className="w-8 h-8" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
        <path strokeLinecap="round" strokeLinejoin="round" d="m2.25 15.75 5.159-5.159a2.25 2.25 0 0 1 3.182 0l5.159 5.159m-1.5-1.5 1.409-1.409a2.25 2.25 0 0 1 3.182 0l2.909 2.909M3.75 21h16.5A2.25 2.25 0 0 0 22.5 18.75V5.25A2.25 2.25 0 0 0 20.25 3H3.75A2.25 2.25 0 0 0 1.5 5.25v13.5A2.25 2.25 0 0 0 3.75 21Z" />
      </svg>
      <span className="text-[10px] truncate max-w-full px-2">{name}</span>
    </div>
  );
}

function ColorContent({ item }: { item: ColorPasteItem }) {
  const hex = argbToHex(item.color);
  const rgba = argbToRgba(item.color);
  return (
    <div className="flex-1 flex flex-col items-center justify-center gap-2">
      <div
        className="w-12 h-12 rounded-xl border border-m3-outline-variant"
        style={{ backgroundColor: rgba }}
      />
      <div className="flex flex-col items-center gap-0.5">
        <span className="text-[11px] font-semibold font-mono text-m3-on-surface">{hex}</span>
        <span className="text-[9px] font-mono text-m3-on-surface-variant">{rgba}</span>
      </div>
    </div>
  );
}

function HtmlContent({ item }: { item: HtmlPasteItem }) {
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

  const bodyStyles = [
    "margin: 0",
    "padding: 4px",
    "font-size: 11px",
    "overflow: hidden",
  ];
  if (bgStyle) bodyStyles.push(`background-color: ${bgStyle}`);
  if (textStyle) bodyStyles.push(`color: ${textStyle}`);

  const srcDoc = `<html><head><style>body { ${bodyStyles.join("; ")} } ::-webkit-scrollbar { display: none }</style></head><body>${item.html}</body></html>`;

  return (
    <div
      className="flex-1 overflow-hidden"
      style={bgStyle ? { backgroundColor: bgStyle } : undefined}
    >
      <iframe
        srcDoc={srcDoc}
        sandbox=""
        scrolling="no"
        className="w-full h-full border-0 pointer-events-none"
        title="HTML"
      />
    </div>
  );
}

function FileContent({ item }: { item: FilesPasteItem }) {
  const name = item.relativePathList?.[0] ?? "file";
  return (
    <div className="flex-1 flex flex-col items-center justify-center gap-1 text-m3-on-surface-variant">
      <svg className="w-8 h-8" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 0 0-3.375-3.375h-1.5A1.125 1.125 0 0 1 13.5 7.125v-1.5a3.375 3.375 0 0 0-3.375-3.375H8.25m2.25 0H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 0 0-9-9Z" />
      </svg>
      <span className="text-[10px] truncate max-w-full px-2">{name}</span>
      <span className="text-[9px] text-m3-outline">{item.count > 1 ? `${item.count} files` : formatSize(item.size)}</span>
    </div>
  );
}

function renderContent(item: PasteItem) {
  switch (item.type) {
    case PasteType.TEXT:
      return <TextContent item={item as TextPasteItem} />;
    case PasteType.URL:
      return <UrlContent item={item as UrlPasteItem} />;
    case PasteType.IMAGE:
      return <ImageContent item={item as ImagesPasteItem} />;
    case PasteType.COLOR:
      return <ColorContent item={item as ColorPasteItem} />;
    case PasteType.HTML:
      return <HtmlContent item={item as HtmlPasteItem} />;
    case PasteType.FILE:
      return <FileContent item={item as FilesPasteItem} />;
    case PasteType.RTF:
      return <TextContent item={{ ...item, type: PasteType.TEXT, text: (item as RtfPasteItem).rtf } as TextPasteItem} />;
    default:
      return null;
  }
}

// ─── PasteCard ──────────────────────────────────────────────────────────

export function PasteCard({ data, onClick, onDelete }: Props) {
  const t = useI18n();
  const displayItem = data.pasteAppearItem ?? data.pasteCollection.pasteItems[0];
  const [contextMenu, setContextMenu] = useState<{ x: number; y: number } | null>(null);
  const menuRef = useRef<HTMLDivElement>(null);

  const isFileType = data.pasteType === 3; // PasteTypeInt.FILE

  const handleCopy = useCallback(async () => {
    try {
      await copyPasteData(data);
      NotificationManager.success(t("copy_successful"));
    } catch {
      NotificationManager.error(t("copy_failed"));
    }
  }, [data, t]);

  const handleDownload = useCallback(async () => {
    const item = data.pasteAppearItem ?? data.pasteCollection.pasteItems[0];
    if (!item || (item.type !== "files" && item.type !== "images")) return;
    const fileItem = item as FilesPasteItem | ImagesPasteItem;
    const hash = fileItem.hash;
    if (!hash || !fileItem.relativePathList?.length) return;

    for (const fileName of fileItem.relativePathList) {
      try {
        const result = await chrome.runtime.sendMessage({
          type: "DOWNLOAD_FILE",
          hash,
          fileName,
        }) as { success: boolean; error?: string };
        if (result.success) {
          NotificationManager.success(`Downloading ${fileName}`);
        } else {
          NotificationManager.error(result.error ?? "Download failed");
        }
      } catch {
        NotificationManager.error("Download failed");
      }
    }
  }, [data]);

  const handleContextMenu = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    const maxY = window.innerHeight - 90;
    const maxX = window.innerWidth - 160;
    setContextMenu({ x: Math.min(e.clientX, maxX), y: Math.min(e.clientY, maxY) });
  }, []);

  useEffect(() => {
    if (!contextMenu) return;
    const handleClick = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) setContextMenu(null);
    };
    const handleEsc = (e: KeyboardEvent) => {
      if (e.key === "Escape") setContextMenu(null);
    };
    window.addEventListener("mousedown", handleClick);
    window.addEventListener("keydown", handleEsc);
    return () => {
      window.removeEventListener("mousedown", handleClick);
      window.removeEventListener("keydown", handleEsc);
    };
  }, [contextMenu]);

  if (!displayItem) return null;
  if (data.size > MAX_SIZE) return null;

  return (
    <>
      <div
        onClick={onClick}
        onContextMenu={handleContextMenu}
        className="flex flex-col aspect-square rounded-[14px] bg-m3-surface-container overflow-hidden cursor-pointer hover:bg-m3-surface-container-high transition-colors"
      >
        {/* Title Bar */}
        <GridTitle data={data} />

        {/* Content */}
        {renderContent(displayItem)}
      </div>

      {/* Context Menu */}
      {contextMenu && (
        <div
          ref={menuRef}
          className="fixed z-50 min-w-[140px] rounded-xl bg-m3-surface-bright shadow-lg border border-m3-outline-variant/20 py-1"
          style={{ left: contextMenu.x, top: contextMenu.y }}
        >
          <button
            onClick={() => { setContextMenu(null); handleCopy(); }}
            className="flex items-center gap-3 w-full px-4 py-2.5 text-sm text-m3-on-surface hover:bg-m3-surface-container transition-colors"
          >
            <Copy size={16} className="text-m3-on-surface-variant" />
            <span>{t("copy")}</span>
          </button>
          {isFileType && (
            <button
              onClick={() => { setContextMenu(null); handleDownload(); }}
              className="flex items-center gap-3 w-full px-4 py-2.5 text-sm text-m3-on-surface hover:bg-m3-surface-container transition-colors"
            >
              <Download size={16} className="text-m3-on-surface-variant" />
              <span>{t("download")}</span>
            </button>
          )}
          {onDelete && (
            <button
              onClick={() => { setContextMenu(null); onDelete(); }}
              className="flex items-center gap-3 w-full px-4 py-2.5 text-sm text-m3-error hover:bg-m3-error-container/30 transition-colors"
            >
              <Trash2 size={16} />
              <span>{t("delete")}</span>
            </button>
          )}
        </div>
      )}
    </>
  );
}

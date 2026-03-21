import { useState, useCallback, useEffect, useRef } from "react";
import { Copy, Trash2 } from "lucide-react";
import type { PasteData } from "@/shared/models/paste-data";
import { PasteType } from "@/shared/models/paste-item";
import { PasteCardHeader } from "./PasteCardHeader";
import { TextPreview } from "./TextPreview";
import { UrlPreview } from "./UrlPreview";
import { ImagePreview } from "./ImagePreview";
import { FilePreview } from "./FilePreview";
import { ColorPreview } from "./ColorPreview";
import { HtmlPreview } from "./HtmlPreview";
import { DesktopPrompt } from "./DesktopPrompt";
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
import { argbToHex } from "@/shared/utils/color";

const MAX_SIZE = 5 * 1024 * 1024; // 5MB

interface Props {
  data: PasteData;
  onDelete?: () => void;
}

function renderPreview(item: PasteItem) {
  switch (item.type) {
    case PasteType.TEXT:
      return <TextPreview item={item as TextPasteItem} />;
    case PasteType.URL:
      return <UrlPreview item={item as UrlPasteItem} />;
    case PasteType.HTML:
      return <HtmlPreview item={item as HtmlPasteItem} />;
    case PasteType.FILE:
      return <FilePreview item={item as FilesPasteItem} />;
    case PasteType.IMAGE:
      return <ImagePreview item={item as ImagesPasteItem} />;
    case PasteType.RTF:
      return <TextPreview item={{ ...item, type: PasteType.TEXT, text: (item as RtfPasteItem).rtf } as unknown as TextPasteItem} />;
    case PasteType.COLOR:
      return <ColorPreview item={item as ColorPasteItem} />;
    default:
      return <p className="text-xs text-m3-on-surface-variant">Unknown type</p>;
  }
}

export function PasteCard({ data, onDelete }: Props) {
  const t = useI18n();
  const displayItem = data.pasteAppearItem ?? data.pasteCollection.pasteItems[0];
  const [contextMenu, setContextMenu] = useState<{ x: number; y: number } | null>(null);
  const menuRef = useRef<HTMLDivElement>(null);

  const handleCopy = useCallback(async () => {
    if (!displayItem) return;

    // Image with inline data — copy as image blob
    if (displayItem.type === PasteType.IMAGE) {
      const dataUrl = (displayItem as ImagesPasteItem).dataUrl;
      if (dataUrl) {
        try {
          const res = await fetch(dataUrl);
          const blob = await res.blob();
          await navigator.clipboard.write([
            new ClipboardItem({ [blob.type]: blob }),
          ]);
        } catch {
          // Fallback: do nothing
        }
        return;
      }
      return;
    }

    let text = "";
    switch (displayItem.type) {
      case PasteType.TEXT:
        text = (displayItem as TextPasteItem).text;
        break;
      case PasteType.URL:
        text = (displayItem as UrlPasteItem).url;
        break;
      case PasteType.HTML:
        text = (displayItem as HtmlPasteItem).html;
        break;
      case PasteType.COLOR:
        text = argbToHex((displayItem as ColorPasteItem).color);
        break;
      default:
        return;
    }
    await navigator.clipboard.writeText(text);
  }, [displayItem]);

  const handleContextMenu = useCallback(
    (e: React.MouseEvent) => {
      e.preventDefault();
      const maxY = window.innerHeight - 90;
      const maxX = window.innerWidth - 160;
      setContextMenu({
        x: Math.min(e.clientX, maxX),
        y: Math.min(e.clientY, maxY),
      });
    },
    [],
  );

  // Close context menu on click outside or Escape
  useEffect(() => {
    if (!contextMenu) return;
    const handleClick = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setContextMenu(null);
      }
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

  if (data.size > MAX_SIZE) {
    return <DesktopPrompt />;
  }

  return (
    <>
      <div
        onClick={handleCopy}
        onContextMenu={handleContextMenu}
        className="rounded-[14px] bg-m3-surface-container overflow-hidden cursor-pointer hover:bg-m3-surface-container-high transition-colors"
      >
        <PasteCardHeader
          pasteType={data.pasteType}
          source={data.source}
          receivedAt={data.receivedAt}
        />
        <div className="px-3 pb-3">{renderPreview(displayItem)}</div>
      </div>

      {/* Right-click Context Menu */}
      {contextMenu && (
        <div
          ref={menuRef}
          className="fixed z-50 min-w-[140px] rounded-xl bg-m3-surface-bright shadow-lg border border-m3-outline-variant/20 py-1"
          style={{ left: contextMenu.x, top: contextMenu.y }}
        >
          <button
            onClick={() => {
              setContextMenu(null);
              handleCopy();
            }}
            className="flex items-center gap-3 w-full px-4 py-2.5 text-sm text-m3-on-surface hover:bg-m3-surface-container transition-colors"
          >
            <Copy size={16} className="text-m3-on-surface-variant" />
            <span>{t("copy")}</span>
          </button>
          {onDelete && (
            <button
              onClick={() => {
                setContextMenu(null);
                onDelete();
              }}
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

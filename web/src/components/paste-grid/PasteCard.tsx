import { useCallback } from "react";
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

export function PasteCard({ data }: Props) {
  const displayItem = data.pasteAppearItem ?? data.pasteCollection.pasteItems[0];

  const handleCopy = useCallback(async () => {
    if (!displayItem) return;
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
      handleCopy();
    },
    [handleCopy],
  );

  if (!displayItem) return null;

  if (data.size > MAX_SIZE) {
    return <DesktopPrompt />;
  }

  return (
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
  );
}

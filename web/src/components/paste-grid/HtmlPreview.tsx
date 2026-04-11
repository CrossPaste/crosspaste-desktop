import { useMemo } from "react";
import type { HtmlPasteItem } from "@/shared/models/paste-item";
import { isDarkColor } from "@/shared/utils/html-color";

function getBackgroundColor(item: HtmlPasteItem): number | null {
  const extraInfo = item.extraInfo as { background?: number } | undefined;
  return extraInfo?.background ?? null;
}

export function HtmlPreview({ item }: { item: HtmlPasteItem }) {
  const bgColor = useMemo(() => getBackgroundColor(item), [item]);

  // Determine appropriate background and text color
  const { backgroundColor, textColor } = useMemo(() => {
    if (bgColor === null) {
      // No background found — use theme-appropriate defaults
      return { backgroundColor: undefined, textColor: undefined };
    }

    const a = (bgColor >>> 24) & 0xff;
    const r = (bgColor >> 16) & 0xff;
    const g = (bgColor >> 8) & 0xff;
    const b = bgColor & 0xff;

    if (a === 0) {
      return { backgroundColor: undefined, textColor: undefined };
    }

    const bg = `rgba(${r}, ${g}, ${b}, ${a / 255})`;
    const dark = isDarkColor(bgColor);
    const text = dark ? "#f5f5f5" : "#1a1a1a";

    return { backgroundColor: bg, textColor: text };
  }, [bgColor]);

  // Wrap HTML with body styles for proper rendering
  const srcDoc = useMemo(() => {
    const bodyStyles = [
      "margin: 0",
      "padding: 4px",
      "font-size: 12px",
      "overflow: hidden",
    ];
    if (backgroundColor) bodyStyles.push(`background-color: ${backgroundColor}`);
    if (textColor) bodyStyles.push(`color: ${textColor}`);

    return `<html><head><style>body { ${bodyStyles.join("; ")} }</style></head><body>${item.html}</body></html>`;
  }, [item.html, backgroundColor, textColor]);

  return (
    <iframe
      srcDoc={srcDoc}
      sandbox=""
      className="w-full h-24 border-0 rounded-md pointer-events-none"
      style={backgroundColor ? { backgroundColor } : undefined}
      title="HTML preview"
    />
  );
}

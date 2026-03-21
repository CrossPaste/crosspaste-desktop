import type { HtmlPasteItem } from "@/shared/models/paste-item";

export function HtmlPreview({ item }: { item: HtmlPasteItem }) {
  return (
    <iframe
      srcDoc={item.html}
      sandbox=""
      className="w-full h-24 border-0 rounded-md bg-white pointer-events-none"
      title="HTML preview"
    />
  );
}

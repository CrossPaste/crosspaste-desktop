import type { TextPasteItem } from "@/shared/models/paste-item";

export function TextPreview({ item }: { item: TextPasteItem }) {
  return (
    <pre className="text-xs text-m3-on-surface whitespace-pre-wrap break-words line-clamp-6 font-mono leading-relaxed">
      {item.text}
    </pre>
  );
}

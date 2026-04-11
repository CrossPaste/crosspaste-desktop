import type { ColorPasteItem } from "@/shared/models/paste-item";
import { argbToHex, argbToRgba } from "@/shared/utils/color";

export function ColorPreview({ item }: { item: ColorPasteItem }) {
  const hex = argbToHex(item.color);
  const rgba = argbToRgba(item.color);

  return (
    <div className="space-y-2">
      <div
        className="w-full h-16 rounded-md border border-gray-200 dark:border-gray-600"
        style={{ backgroundColor: rgba }}
      />
      <div className="space-y-0.5">
        <p className="text-xs font-mono text-gray-700 dark:text-gray-300">
          {hex}
        </p>
        <p className="text-[10px] font-mono text-gray-400">{rgba}</p>
      </div>
    </div>
  );
}

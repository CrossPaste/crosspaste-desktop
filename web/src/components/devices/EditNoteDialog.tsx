import { useState, useEffect, useRef } from "react";
import { X, Edit } from "lucide-react";

interface Props {
  deviceName: string;
  currentNote: string;
  onConfirm: (note: string) => void;
  onClose: () => void;
}

export function EditNoteDialog({
  deviceName,
  currentNote,
  onConfirm,
  onClose,
}: Props) {
  const [note, setNote] = useState(currentNote);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    inputRef.current?.focus();
    inputRef.current?.select();
  }, []);

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter") {
      onConfirm(note.trim());
    } else if (e.key === "Escape") {
      onClose();
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="w-[300px] rounded-2xl bg-m3-surface p-6 shadow-xl">
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-3">
            <Edit size={18} className="text-m3-primary" />
            <span className="text-lg font-semibold text-m3-on-surface">
              设备备注
            </span>
          </div>
          <button
            onClick={onClose}
            className="p-1 rounded-md hover:bg-m3-surface-container"
          >
            <X size={18} className="text-m3-on-surface-variant" />
          </button>
        </div>

        <p className="text-sm text-m3-on-surface-variant mb-4">
          为{" "}
          <span className="font-medium text-m3-on-surface">{deviceName}</span>{" "}
          添加备注名称
        </p>

        <input
          ref={inputRef}
          type="text"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="输入备注名称"
          className="w-full px-3 py-2.5 text-sm rounded-xl border border-m3-outline-variant bg-m3-surface text-m3-on-surface placeholder:text-m3-outline focus:outline-none focus:ring-2 focus:ring-m3-primary focus:border-transparent mb-5"
        />

        <div className="flex items-center justify-end gap-2">
          <button
            onClick={onClose}
            className="px-4 py-2 text-sm font-medium text-m3-on-surface-variant rounded-xl hover:bg-m3-surface-container transition-colors"
          >
            取消
          </button>
          <button
            onClick={() => onConfirm(note.trim())}
            className="px-4 py-2 text-sm font-medium text-white rounded-xl bg-m3-primary hover:opacity-90 transition-colors"
          >
            确认
          </button>
        </div>
      </div>
    </div>
  );
}

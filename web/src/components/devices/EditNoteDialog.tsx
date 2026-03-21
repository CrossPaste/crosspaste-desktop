import { useState, useEffect, useRef } from "react";
import { X, Edit } from "lucide-react";
import { useI18n } from "@/shared/i18n/use-i18n";

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
  const t = useI18n();
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
              {t("device_note")}
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
          {t("add_note_for", deviceName)}
        </p>

        <input
          ref={inputRef}
          type="text"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={t("enter_note_name")}
          className="w-full px-3 py-2.5 text-sm rounded-xl border border-m3-outline-variant bg-m3-surface text-m3-on-surface placeholder:text-m3-outline focus:outline-none focus:ring-2 focus:ring-m3-primary focus:border-transparent mb-5"
        />

        <div className="flex items-center justify-end gap-2">
          <button
            onClick={onClose}
            className="px-4 py-2 text-sm font-medium text-m3-on-surface-variant rounded-xl hover:bg-m3-surface-container transition-colors"
          >
            {t("cancel")}
          </button>
          <button
            onClick={() => onConfirm(note.trim())}
            className="px-4 py-2 text-sm font-medium text-white rounded-xl bg-m3-primary hover:opacity-90 transition-colors"
          >
            {t("confirm")}
          </button>
        </div>
      </div>
    </div>
  );
}

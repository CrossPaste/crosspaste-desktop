import { useRef, useState } from "react";
import { useI18n } from "@/shared/i18n/use-i18n";

interface Props {
  onComplete: (token: number) => void;
  disabled?: boolean;
}

const DIGIT_COUNT = 6;

export function TokenInput({ onComplete, disabled }: Props) {
  const t = useI18n();
  const [digits, setDigits] = useState<string[]>(Array(DIGIT_COUNT).fill(""));
  const refs = useRef<(HTMLInputElement | null)[]>([]);

  const handleChange = (index: number, value: string) => {
    if (!/^\d*$/.test(value)) return;

    const newDigits = [...digits];
    newDigits[index] = value.slice(-1);
    setDigits(newDigits);

    // Auto-advance to next input
    if (value && index < DIGIT_COUNT - 1) {
      refs.current[index + 1]?.focus();
    }

    // Check if all digits filled
    if (newDigits.every((d) => d !== "")) {
      const token = parseInt(newDigits.join(""), 10);
      onComplete(token);
    }
  };

  const handleKeyDown = (index: number, e: React.KeyboardEvent) => {
    if (e.key === "Backspace" && !digits[index] && index > 0) {
      refs.current[index - 1]?.focus();
    }
  };

  const handlePaste = (e: React.ClipboardEvent) => {
    e.preventDefault();
    const pasted = e.clipboardData.getData("text").replace(/\D/g, "");
    if (pasted.length === DIGIT_COUNT) {
      const newDigits = pasted.split("");
      setDigits(newDigits);
      refs.current[DIGIT_COUNT - 1]?.focus();
      onComplete(parseInt(pasted, 10));
    }
  };

  const reset = () => {
    setDigits(Array(DIGIT_COUNT).fill(""));
    refs.current[0]?.focus();
  };

  return (
    <div className="flex flex-col items-center gap-3">
      <div className="flex gap-2 justify-center">
        {digits.map((digit, i) => (
          <input
            key={i}
            ref={(el) => { refs.current[i] = el; }}
            type="text"
            inputMode="numeric"
            maxLength={1}
            value={digit}
            onChange={(e) => handleChange(i, e.target.value)}
            onKeyDown={(e) => handleKeyDown(i, e)}
            onPaste={handlePaste}
            disabled={disabled}
            className="w-10 h-12 text-center text-lg font-mono rounded-xl border border-m3-outline-variant bg-m3-surface text-m3-on-surface focus:outline-none focus:ring-2 focus:ring-m3-primary focus:border-transparent disabled:opacity-40"
          />
        ))}
      </div>
      {disabled && (
        <button
          onClick={reset}
          className="text-xs text-m3-primary hover:underline"
        >
          {t("re_enter")}
        </button>
      )}
    </div>
  );
}

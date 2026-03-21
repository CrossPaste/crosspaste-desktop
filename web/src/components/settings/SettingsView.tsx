import { Globe, Check } from "lucide-react";
import {
  useI18n,
  useLanguageSettings,
  SUPPORTED_LANGUAGES,
  getLanguageName,
} from "@/shared/i18n/use-i18n";

export function SettingsView() {
  const t = useI18n();
  const { language, setLanguage } = useLanguageSettings();

  return (
    <div className="flex flex-col h-full overflow-y-auto px-5 py-2">
      {/* Language Section */}
      <div className="flex flex-col gap-3">
        <div className="flex items-center gap-2">
          <Globe size={18} className="text-m3-on-surface-variant" />
          <span className="text-base font-semibold text-m3-on-surface">
            {t("language")}
          </span>
        </div>

        <div className="flex flex-col rounded-[14px] bg-m3-surface-container overflow-hidden">
          {SUPPORTED_LANGUAGES.map((code) => {
            const isActive = code === language;
            return (
              <button
                key={code}
                onClick={() => setLanguage(code)}
                className={`flex items-center justify-between px-4 py-3 text-sm transition-colors ${
                  isActive
                    ? "bg-m3-primary-container text-m3-on-primary-container"
                    : "text-m3-on-surface hover:bg-m3-surface-container-high"
                }`}
              >
                <span className="font-medium">{getLanguageName(code)}</span>
                {isActive && <Check size={16} className="text-m3-primary" />}
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}

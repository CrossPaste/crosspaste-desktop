import { useState } from "react";
import {
  Globe,
  Palette,
  Info,
  ChevronDown,
  ChevronRight,
  Check,
  Download,
  ArrowRight,
} from "lucide-react";
import {
  useI18n,
  useLanguageSettings,
  SUPPORTED_LANGUAGES,
  getLanguageName,
} from "@/shared/i18n/use-i18n";
import { useTheme, type ThemeMode } from "@/shared/theme/use-theme";
import { openCrossPasteWebInBrowser } from "@/shared/app/ui-support";
import { APP_VERSION } from "@/shared/app/version.generated";
import { AboutView } from "./AboutView";

// ─── Segmented Control ──────────────────────────────────────────────────

const THEME_OPTIONS: { mode: ThemeMode; labelKey: string }[] = [
  { mode: "light", labelKey: "light" },
  { mode: "system", labelKey: "system" },
  { mode: "dark", labelKey: "dark" },
];

function ThemeSegmentedControl() {
  const t = useI18n();
  const { themeMode, setThemeMode } = useTheme();

  return (
    <div className="flex rounded-[10px] bg-m3-outline-variant/30 p-0.5 gap-0.5">
      {THEME_OPTIONS.map(({ mode, labelKey }) => {
        const isActive = themeMode === mode;
        return (
          <button
            key={mode}
            onClick={(e) => {
              e.stopPropagation();
              setThemeMode(mode);
            }}
            className={`flex-1 flex items-center justify-center gap-1 py-1.5 rounded-[8px] text-xs font-medium transition-colors ${
              isActive
                ? "bg-m3-surface text-m3-on-surface shadow-sm"
                : "text-m3-on-surface-variant hover:text-m3-on-surface"
            }`}
          >
            {isActive && <Check size={12} />}
            <span>{t(labelKey)}</span>
          </button>
        );
      })}
    </div>
  );
}

// ─── Language Dropdown ──────────────────────────────────────────────────

function LanguageDropdown({ onClose }: { onClose: () => void }) {
  const { language, setLanguage } = useLanguageSettings();

  return (
    <div className="flex flex-col">
      {SUPPORTED_LANGUAGES.map((code) => {
        const isActive = code === language;
        return (
          <button
            key={code}
            onClick={() => {
              setLanguage(code);
              onClose();
            }}
            className={`flex items-center justify-between pl-[66px] pr-4 py-2.5 text-sm transition-colors ${
              isActive
                ? "bg-m3-primary-container/50"
                : "hover:bg-m3-surface-container-high"
            }`}
          >
            <span
              className={`font-medium ${isActive ? "text-m3-primary" : "text-m3-on-surface"}`}
            >
              {getLanguageName(code)}
            </span>
            {isActive && <Check size={14} className="text-m3-primary" />}
          </button>
        );
      })}
    </div>
  );
}

// ─── Download Banner ────────────────────────────────────────────────────

function DownloadBanner() {
  const t = useI18n();
  return (
    <button
      onClick={() => {
        void openCrossPasteWebInBrowser("download");
      }}
      className="group relative flex items-center gap-3 w-full overflow-hidden rounded-[14px] px-4 py-3 text-left bg-gradient-to-r from-settings-indigo-bg via-settings-blue-bg to-settings-purple-bg ring-1 ring-settings-indigo/10 hover:ring-settings-indigo/30 hover:shadow-md shadow-sm transition-shadow"
    >
      <div className="flex items-center justify-center w-10 h-10 rounded-[12px] bg-m3-surface/70 shrink-0 shadow-sm">
        <Download size={20} className="text-settings-indigo" />
      </div>
      <div className="flex-1 min-w-0 flex flex-col gap-0.5">
        <span className="text-sm font-semibold text-m3-on-surface truncate">
          {t("get_native_app")}
        </span>
        <span className="text-xs text-m3-on-surface-variant truncate">
          {t("download")} · crosspaste.com
        </span>
      </div>
      <ArrowRight
        size={18}
        className="text-settings-indigo shrink-0 transition-transform group-hover:translate-x-0.5"
      />
    </button>
  );
}

// ─── Divider ────────────────────────────────────────────────────────────

function Divider() {
  return (
    <div className="pl-[66px]">
      <div className="h-px bg-m3-outline-variant/50" />
    </div>
  );
}

// ─── Main View ──────────────────────────────────────────────────────────

interface Props {
  desktopConnected?: boolean;
}

export function SettingsView({ desktopConnected }: Props) {
  const t = useI18n();
  const { language } = useLanguageSettings();
  const [showAbout, setShowAbout] = useState(false);
  const [showLanguages, setShowLanguages] = useState(false);

  if (showAbout) {
    return <AboutView onBack={() => setShowAbout(false)} />;
  }

  return (
    <div className="flex flex-col gap-2 h-full overflow-y-auto px-5 py-4">
      {!desktopConnected && (
        <div className="pb-1">
          <DownloadBanner />
        </div>
      )}

      {/* Section Title */}
      <span className="text-[13px] font-semibold text-m3-on-surface-variant">
        {t("general")}
      </span>

      {/* Settings Card */}
      <div className="flex flex-col rounded-[14px] bg-m3-surface-container overflow-hidden">
        {/* Language */}
        <button
          onClick={() => setShowLanguages(!showLanguages)}
          className="flex items-center gap-3.5 px-4 py-3 hover:bg-m3-surface-container-high transition-colors"
        >
          <div className="flex items-center justify-center w-9 h-9 rounded-[10px] bg-settings-indigo-bg shrink-0">
            <Globe size={18} className="text-settings-indigo" />
          </div>
          <div className="flex-1 min-w-0 flex flex-col gap-0.5 text-left">
            <span className="text-sm font-medium text-m3-on-surface">
              {t("language")}
            </span>
            <span className="text-xs text-m3-on-surface-variant">
              {getLanguageName(language)}
            </span>
          </div>
          <ChevronDown
            size={18}
            className={`text-m3-on-surface-variant transition-transform ${showLanguages ? "rotate-180" : ""}`}
          />
        </button>

        {/* Language dropdown */}
        {showLanguages && (
          <>
            <Divider />
            <LanguageDropdown onClose={() => setShowLanguages(false)} />
          </>
        )}

        <Divider />

        {/* Theme */}
        <div className="flex items-center gap-3.5 px-4 py-3">
          <div className="flex items-center justify-center w-9 h-9 rounded-[10px] bg-settings-purple-bg shrink-0">
            <Palette size={18} className="text-settings-purple" />
          </div>
          <div className="flex-1 min-w-0 flex flex-col gap-1.5">
            <span className="text-sm font-medium text-m3-on-surface">
              {t("theme")}
            </span>
            <ThemeSegmentedControl />
          </div>
        </div>

        <Divider />

        {/* About */}
        <button
          onClick={() => setShowAbout(true)}
          className="flex items-center gap-3.5 px-4 py-3 hover:bg-m3-surface-container-high transition-colors"
        >
          <div className="flex items-center justify-center w-9 h-9 rounded-[10px] bg-settings-blue-bg shrink-0">
            <Info size={18} className="text-settings-blue" />
          </div>
          <div className="flex-1 min-w-0 flex flex-col gap-0.5 text-left">
            <span className="text-sm font-medium text-m3-on-surface">
              {t("about")}
            </span>
            <span className="text-xs text-m3-on-surface-variant">
              v{APP_VERSION}
            </span>
          </div>
          <ChevronRight size={18} className="text-m3-on-surface-variant" />
        </button>
      </div>
    </div>
  );
}

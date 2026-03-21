import {
  Globe,
  Check,
  Sun,
  Moon,
  Monitor,
  Palette,
  Info,
  ExternalLink,
  BookOpen,
  FileText,
  MessageSquare,
  Mail,
} from "lucide-react";
import {
  useI18n,
  useLanguageSettings,
  SUPPORTED_LANGUAGES,
  getLanguageName,
} from "@/shared/i18n/use-i18n";
import { useTheme, type ThemeMode } from "@/shared/theme/use-theme";
import { APP_VERSION } from "@/shared/app/version.generated";

const LINKS = {
  github: "https://github.com/CrossPaste/crosspaste-desktop",
  website: "https://crosspaste.com",
  changelog: "https://crosspaste.com/changelog",
  tutorial: "https://crosspaste.com/tutorial",
  feedback: "https://github.com/CrossPaste/crosspaste-desktop/issues",
  email: "mailto:compile.future@gmail.com",
};

function openUrl(url: string) {
  chrome.tabs.create({ url });
}

// ─── Theme Selector ─────────────────────────────────────────────────────

const THEME_OPTIONS: { mode: ThemeMode; labelKey: string; Icon: typeof Sun }[] = [
  { mode: "light", labelKey: "light", Icon: Sun },
  { mode: "system", labelKey: "system", Icon: Monitor },
  { mode: "dark", labelKey: "dark", Icon: Moon },
];

function ThemeSection() {
  const t = useI18n();
  const { themeMode, setThemeMode } = useTheme();

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center gap-2">
        <Palette size={18} className="text-m3-on-surface-variant" />
        <span className="text-base font-semibold text-m3-on-surface">
          {t("theme")}
        </span>
      </div>

      <div className="flex rounded-[14px] bg-m3-surface-container p-1 gap-1">
        {THEME_OPTIONS.map(({ mode, labelKey, Icon }) => {
          const isActive = themeMode === mode;
          return (
            <button
              key={mode}
              onClick={() => setThemeMode(mode)}
              className={`flex-1 flex items-center justify-center gap-1.5 py-2.5 rounded-xl text-sm font-medium transition-colors ${
                isActive
                  ? "bg-m3-primary text-white"
                  : "text-m3-on-surface-variant hover:text-m3-on-surface"
              }`}
            >
              <Icon size={16} />
              <span>{t(labelKey)}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}

// ─── Language Selector ──────────────────────────────────────────────────

function LanguageSection() {
  const t = useI18n();
  const { language, setLanguage } = useLanguageSettings();

  return (
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
  );
}

// ─── About Section ──────────────────────────────────────────────────────

function LinkItem({
  icon: Icon,
  iconColor,
  label,
  desc,
  url,
}: {
  icon: typeof ExternalLink;
  iconColor: string;
  label: string;
  desc: string;
  url: string;
}) {
  return (
    <button
      onClick={() => openUrl(url)}
      className="flex items-center gap-3 px-4 py-3 text-left text-sm text-m3-on-surface hover:bg-m3-surface-container-high transition-colors"
    >
      <Icon size={18} className={iconColor} />
      <div className="flex-1 min-w-0">
        <p className="font-medium">{label}</p>
        <p className="text-xs text-m3-on-surface-variant">{desc}</p>
      </div>
      <ExternalLink size={14} className="text-m3-outline shrink-0" />
    </button>
  );
}

function AboutSection() {
  const t = useI18n();

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center gap-2">
        <Info size={18} className="text-m3-on-surface-variant" />
        <span className="text-base font-semibold text-m3-on-surface">
          {t("about")}
        </span>
      </div>

      {/* Logo & Version */}
      <div className="flex flex-col items-center gap-3 py-4">
        <img
          src="/public/icons/icon-128.png"
          alt="CrossPaste"
          className="w-16 h-16 rounded-2xl"
        />
        <div className="flex flex-col items-center gap-1">
          <span className="text-lg font-bold text-m3-on-surface">
            CrossPaste
          </span>
          <span className="text-xs text-m3-on-surface-variant">
            {t("app_version")} {APP_VERSION}
          </span>
        </div>
      </div>

      {/* Resources */}
      <div className="flex flex-col gap-1">
        <span className="text-xs font-semibold text-m3-on-surface-variant uppercase tracking-wide px-1">
          {t("resources")}
        </span>
        <div className="flex flex-col rounded-[14px] bg-m3-surface-container overflow-hidden">
          <LinkItem
            icon={Globe}
            iconColor="text-m3-primary"
            label={t("official_website")}
            desc={t("official_website_desc")}
            url={LINKS.website}
          />
          <LinkItem
            icon={BookOpen}
            iconColor="text-m3-success"
            label={t("newbie_tutorial")}
            desc={t("newbie_tutorial_desc")}
            url={LINKS.tutorial}
          />
          <LinkItem
            icon={FileText}
            iconColor="text-m3-warning"
            label={t("change_log")}
            desc={t("change_log_desc")}
            url={LINKS.changelog}
          />
        </div>
      </div>

      {/* Support */}
      <div className="flex flex-col gap-1">
        <span className="text-xs font-semibold text-m3-on-surface-variant uppercase tracking-wide px-1">
          {t("support")}
        </span>
        <div className="flex flex-col rounded-[14px] bg-m3-surface-container overflow-hidden">
          <LinkItem
            icon={MessageSquare}
            iconColor="text-m3-error"
            label={t("feedback")}
            desc={t("feedback_desc")}
            url={LINKS.feedback}
          />
          <LinkItem
            icon={Mail}
            iconColor="text-m3-primary"
            label={t("contact_us")}
            desc={t("contact_us_desc")}
            url={LINKS.email}
          />
        </div>
      </div>

      {/* Footer */}
      <p className="text-center text-[10px] text-m3-outline py-2">
        © 2024 Compile Future
      </p>
    </div>
  );
}

// ─── Main View ──────────────────────────────────────────────────────────

export function SettingsView() {
  return (
    <div className="flex flex-col gap-6 h-full overflow-y-auto px-5 py-4">
      <ThemeSection />
      <LanguageSection />
      <AboutSection />
    </div>
  );
}

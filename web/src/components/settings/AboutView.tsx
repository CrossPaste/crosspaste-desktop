import {
  ArrowLeft,
  Globe,
  BookOpen,
  FileText,
  MessageSquare,
  Mail,
  ExternalLink,
} from "lucide-react";
import { useI18n } from "@/shared/i18n/use-i18n";
import { APP_VERSION } from "@/shared/app/version.generated";

const LINKS = {
  website: "https://crosspaste.com",
  tutorial: "https://crosspaste.com/tutorial",
  changelog: "https://crosspaste.com/changelog",
  feedback: "https://github.com/CrossPaste/crosspaste-desktop/issues",
  email: "mailto:compile.future@gmail.com",
};

function openUrl(url: string) {
  chrome.tabs.create({ url });
}

// ─── Divider ────────────────────────────────────────────────────────────

function Divider() {
  return (
    <div className="pl-[66px]">
      <div className="h-px bg-m3-outline-variant/50" />
    </div>
  );
}

// ─── Link Item ──────────────────────────────────────────────────────────

function LinkItem({
  icon: Icon,
  iconBg,
  iconColor,
  label,
  desc,
  url,
  last,
}: {
  icon: typeof Globe;
  iconBg: string;
  iconColor: string;
  label: string;
  desc: string;
  url: string;
  last?: boolean;
}) {
  return (
    <>
      <button
        onClick={() => openUrl(url)}
        className="flex items-center gap-3.5 px-4 py-3 text-left hover:bg-m3-surface-container-high transition-colors"
      >
        <div
          className={`flex items-center justify-center w-9 h-9 rounded-[10px] shrink-0 ${iconBg}`}
        >
          <Icon size={18} className={iconColor} />
        </div>
        <div className="flex-1 min-w-0 flex flex-col gap-0.5">
          <span className="text-sm font-medium text-m3-on-surface">
            {label}
          </span>
          <span className="text-xs text-m3-on-surface-variant">{desc}</span>
        </div>
        <ExternalLink size={14} className="text-m3-outline shrink-0" />
      </button>
      {!last && <Divider />}
    </>
  );
}

// ─── About View ─────────────────────────────────────────────────────────

interface Props {
  onBack: () => void;
}

export function AboutView({ onBack }: Props) {
  const t = useI18n();

  return (
    <div className="flex flex-col h-full">
      {/* Top bar */}
      <div className="flex items-center gap-2 px-4 py-3">
        <button
          onClick={onBack}
          className="flex items-center justify-center w-8 h-8 rounded-lg hover:bg-m3-surface-container transition-colors"
        >
          <ArrowLeft size={20} className="text-m3-on-surface" />
        </button>
        <span className="text-base font-semibold text-m3-on-surface">
          {t("about")}
        </span>
      </div>

      <div className="flex-1 overflow-y-auto px-5 pb-6">
        <div className="flex flex-col gap-4">
          {/* Logo & Version */}
          <div className="flex flex-col items-center gap-3 py-6">
            <img
              src="/public/icons/icon-128.png"
              alt="CrossPaste"
              className="w-[72px] h-[72px] rounded-2xl"
            />
            <div className="flex flex-col items-center gap-1">
              <span className="text-lg font-bold text-m3-on-surface">
                CrossPaste
              </span>
              <span className="text-xs text-m3-on-surface-variant">
                v{APP_VERSION}
              </span>
            </div>
          </div>

          {/* Resources */}
          <div className="flex flex-col gap-2">
            <span className="text-[13px] font-semibold text-m3-on-surface-variant">
              {t("resources")}
            </span>
            <div className="flex flex-col rounded-[14px] bg-m3-surface-container overflow-hidden">
              <LinkItem
                icon={Globe}
                iconBg="bg-blue-100 dark:bg-blue-900/50"
                iconColor="text-blue-500 dark:text-blue-400"
                label={t("official_website")}
                desc={t("official_website_desc")}
                url={LINKS.website}
              />
              <LinkItem
                icon={BookOpen}
                iconBg="bg-green-100 dark:bg-green-900/50"
                iconColor="text-green-500 dark:text-green-400"
                label={t("newbie_tutorial")}
                desc={t("newbie_tutorial_desc")}
                url={LINKS.tutorial}
              />
              <LinkItem
                icon={FileText}
                iconBg="bg-purple-100 dark:bg-purple-900/50"
                iconColor="text-purple-500 dark:text-purple-400"
                label={t("change_log")}
                desc={t("change_log_desc")}
                url={LINKS.changelog}
                last
              />
            </div>
          </div>

          {/* Support */}
          <div className="flex flex-col gap-2">
            <span className="text-[13px] font-semibold text-m3-on-surface-variant">
              {t("support")}
            </span>
            <div className="flex flex-col rounded-[14px] bg-m3-surface-container overflow-hidden">
              <LinkItem
                icon={MessageSquare}
                iconBg="bg-red-100 dark:bg-red-900/50"
                iconColor="text-red-500 dark:text-red-400"
                label={t("feedback")}
                desc={t("feedback_desc")}
                url={LINKS.feedback}
              />
              <LinkItem
                icon={Mail}
                iconBg="bg-cyan-100 dark:bg-cyan-900/50"
                iconColor="text-cyan-500 dark:text-cyan-400"
                label={t("contact_us")}
                desc={t("contact_us_desc")}
                url={LINKS.email}
                last
              />
            </div>
          </div>

          {/* Footer */}
          <p className="text-center text-[10px] text-m3-outline py-2">
            Made with ❤️ by CrossPaste Team
            <br />© 2024 Compile Future
          </p>
        </div>
      </div>
    </div>
  );
}

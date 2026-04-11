import { Clipboard, MonitorSmartphone, Settings } from "lucide-react";
import { useI18n } from "@/shared/i18n/use-i18n";

export type Tab = "clipboard" | "devices" | "settings";

interface Props {
  activeTab: Tab;
  onTabChange: (tab: Tab) => void;
}

const tabs: { id: Tab; labelKey: string; Icon: typeof Clipboard }[] = [
  { id: "clipboard", labelKey: "clipboard", Icon: Clipboard },
  { id: "devices", labelKey: "devices", Icon: MonitorSmartphone },
  { id: "settings", labelKey: "settings", Icon: Settings },
];

export function Header({ activeTab, onTabChange }: Props) {
  const t = useI18n();

  return (
    <header className="flex items-center justify-center px-4 py-3 bg-m3-surface">
      <div className="flex items-center gap-2 rounded-full bg-m3-surface-container p-1">
        {tabs.map(({ id, labelKey, Icon }) => {
          const isActive = activeTab === id;
          return (
            <button
              key={id}
              onClick={() => onTabChange(id)}
              className={`flex items-center gap-1.5 rounded-full px-4 py-1.5 text-sm font-medium transition-colors ${
                isActive
                  ? "bg-m3-primary text-white"
                  : "text-m3-on-surface-variant hover:text-m3-on-surface"
              }`}
            >
              <Icon size={18} />
              <span>{t(labelKey)}</span>
            </button>
          );
        })}
      </div>
    </header>
  );
}

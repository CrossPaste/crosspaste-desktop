import { Clipboard, MonitorSmartphone } from "lucide-react";

type Tab = "devices" | "clipboard";

interface Props {
  activeTab: Tab;
  onTabChange: (tab: Tab) => void;
}

const tabs: { id: Tab; label: string; Icon: typeof Clipboard }[] = [
  { id: "clipboard", label: "剪贴板", Icon: Clipboard },
  { id: "devices", label: "设备", Icon: MonitorSmartphone },
];

export function Header({ activeTab, onTabChange }: Props) {
  return (
    <header className="flex items-center justify-center px-4 py-3 bg-m3-surface">
      <div className="flex items-center gap-2 rounded-full bg-m3-surface-container p-1">
        {tabs.map(({ id, label, Icon }) => {
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
              <span>{label}</span>
            </button>
          );
        })}
      </div>
    </header>
  );
}

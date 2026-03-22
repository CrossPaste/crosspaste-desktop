import { useState, useCallback } from "react";
import { Header, type Tab } from "@/components/layout/Header";
import { DevicesView } from "@/components/devices/DevicesView";
import { PasteGrid } from "@/components/paste-grid/PasteGrid";
import { SettingsView } from "@/components/settings/SettingsView";
import { useConnection } from "@/shared/hooks/use-connection";
import { I18nProvider } from "@/shared/i18n/use-i18n";
import { ThemeProvider } from "@/shared/theme/use-theme";
import { NotificationHost } from "@/components/notification/NotificationHost";

const TAB_STORAGE_KEY = "ui_active_tab";
const VALID_TABS: Tab[] = ["clipboard", "devices", "settings"];

function usePersistedTab(): [Tab, (tab: Tab) => void] {
  const [tab, setTab] = useState<Tab>(() => {
    const saved = localStorage.getItem(TAB_STORAGE_KEY);
    return saved && VALID_TABS.includes(saved as Tab) ? (saved as Tab) : "devices";
  });

  const setAndPersist = useCallback((newTab: Tab) => {
    setTab(newTab);
    localStorage.setItem(TAB_STORAGE_KEY, newTab);
  }, []);

  return [tab, setAndPersist];
}

export default function App() {
  const { devices, connect, pair, removeDevice, updateNote } = useConnection();
  const [activeTab, setActiveTab] = usePersistedTab();

  return (
    <ThemeProvider>
      <I18nProvider>
        <div className="relative flex flex-col h-screen bg-m3-surface">
          <NotificationHost />
          <Header activeTab={activeTab} onTabChange={setActiveTab} />
          <main className="flex-1 overflow-hidden">
            {activeTab === "devices" ? (
              <DevicesView
                devices={devices}
                onConnect={connect}
                onPair={pair}
                onRemoveDevice={removeDevice}
                onUpdateNote={updateNote}
              />
            ) : activeTab === "clipboard" ? (
              <div className="h-full overflow-y-auto py-2">
                <PasteGrid />
              </div>
            ) : (
              <SettingsView />
            )}
          </main>
        </div>
      </I18nProvider>
    </ThemeProvider>
  );
}

import { useState, useCallback } from "react";
import { Monitor } from "lucide-react";
import { Header, type Tab } from "@/components/layout/Header";
import { DevicesView } from "@/components/devices/DevicesView";
import { PasteGrid } from "@/components/paste-grid/PasteGrid";
import { SettingsView } from "@/components/settings/SettingsView";
import { useConnection } from "@/shared/hooks/use-connection";
import { useDesktopStatus } from "@/shared/hooks/use-desktop-status";
import { useOversizeNoticeListener } from "@/shared/hooks/use-oversize-notice";
import { useI18n, I18nProvider } from "@/shared/i18n/use-i18n";
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

function DesktopActiveBanner() {
  const t = useI18n();
  return (
    <div className="flex justify-center mt-2">
      <div className="inline-flex items-center gap-2 px-3 py-2 rounded-lg bg-m3-warning-container text-m3-warning text-sm flex-wrap justify-center">
        <Monitor size={16} className="shrink-0" />
        <span>{t("desktop_app_active")}</span>
      </div>
    </div>
  );
}

/** Placed inside I18nProvider so hooks that depend on `useI18n` can run. */
function OversizeNoticeRelay() {
  useOversizeNoticeListener();
  return null;
}

export default function App() {
  const { devices, connect, pair, rePair, removeDevice, updateNote } = useConnection();
  const [activeTab, setActiveTab] = usePersistedTab();
  const desktopConnected = useDesktopStatus();

  return (
    <ThemeProvider>
      <I18nProvider>
        <OversizeNoticeRelay />
        <div className="relative flex flex-col h-screen bg-m3-surface">
          <NotificationHost />
          <Header activeTab={activeTab} onTabChange={setActiveTab} />
          {desktopConnected && <DesktopActiveBanner />}
          <main className="flex-1 overflow-hidden">
            {activeTab === "devices" ? (
              <DevicesView
                devices={devices}
                desktopConnected={desktopConnected}
                onConnect={connect}
                onPair={pair}
                onRemoveDevice={removeDevice}
                onUpdateNote={updateNote}
                onRePair={rePair}
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

import { useState } from "react";
import { Header, type Tab } from "@/components/layout/Header";
import { DevicesView } from "@/components/devices/DevicesView";
import { PasteGrid } from "@/components/paste-grid/PasteGrid";
import { SettingsView } from "@/components/settings/SettingsView";
import { useConnection } from "@/shared/hooks/use-connection";
import { I18nProvider } from "@/shared/i18n/use-i18n";
import { ThemeProvider } from "@/shared/theme/use-theme";

export default function App() {
  const { devices, connect, pair, removeDevice, updateNote } = useConnection();
  const [activeTab, setActiveTab] = useState<Tab>("devices");

  return (
    <ThemeProvider>
      <I18nProvider>
        <div className="flex flex-col h-screen bg-m3-surface">
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

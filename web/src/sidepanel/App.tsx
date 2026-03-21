import { useState, useEffect } from "react";
import { Header } from "@/components/layout/Header";
import { DevicesView } from "@/components/devices/DevicesView";
import { PasteGrid } from "@/components/paste-grid/PasteGrid";
import { useConnection } from "@/shared/hooks/use-connection";

type Tab = "devices" | "clipboard";

export default function App() {
  const { devices, connect, pair, removeDevice, updateNote } = useConnection();
  const [darkMode, setDarkMode] = useState(false);
  const [activeTab, setActiveTab] = useState<Tab>("devices");

  useEffect(() => {
    const mq = window.matchMedia("(prefers-color-scheme: dark)");
    setDarkMode(mq.matches);
    const handler = (e: MediaQueryListEvent) => setDarkMode(e.matches);
    mq.addEventListener("change", handler);
    return () => mq.removeEventListener("change", handler);
  }, []);

  useEffect(() => {
    document.documentElement.classList.toggle("dark", darkMode);
  }, [darkMode]);

  return (
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
        ) : (
          <div className="h-full overflow-y-auto py-2">
            <PasteGrid />
          </div>
        )}
      </main>
    </div>
  );
}

import { useState } from "react";
import { TokenInput } from "./TokenInput";
import { DeviceInfo } from "./DeviceInfo";
import type { SyncInfo } from "@/shared/models/sync-info";

type ConnectionStatus = "disconnected" | "connecting" | "connected" | "pairing" | "trusted";

interface ConnectionState {
  status: ConnectionStatus;
  error: string | null;
  syncInfo: SyncInfo | null;
}

interface Props {
  state: ConnectionState;
  onConnect: (host: string, port: number) => Promise<{ success: boolean; syncInfo?: SyncInfo }>;
  onPair: (token: number) => Promise<{ success: boolean; error?: string }>;
  onDisconnect: () => void;
}

export function ConnectionSetup({
  state,
  onConnect,
  onPair,
  onDisconnect,
}: Props) {
  const [host, setHost] = useState("");
  const [port, setPort] = useState("13129");

  const handleConnect = async (e: React.FormEvent) => {
    e.preventDefault();
    await onConnect(host, parseInt(port, 10));
  };

  // Pairing: desktop is showing the token, user enters it here
  if (state.status === "pairing") {
    return (
      <div className="space-y-4">
        {state.syncInfo && <DeviceInfo syncInfo={state.syncInfo} status="pairing" />}

        <div className="bg-white dark:bg-gray-800 rounded-lg p-4 shadow-sm border border-gray-200 dark:border-gray-700">
          <h3 className="text-sm font-medium text-gray-900 dark:text-gray-100 mb-3">
            Enter pairing code
          </h3>
          <p className="text-xs text-gray-500 dark:text-gray-400 mb-3">
            A 6-digit code is shown on the CrossPaste desktop app.
          </p>
          <TokenInput onComplete={(token) => onPair(token)} />
          {state.error && (
            <p className="mt-2 text-xs text-red-500">{state.error}</p>
          )}
        </div>

        <button
          onClick={onDisconnect}
          className="w-full py-2 text-sm text-gray-500 hover:text-red-500 dark:text-gray-400 dark:hover:text-red-400 transition-colors"
        >
          Cancel
        </button>
      </div>
    );
  }

  // Trusted: show as "My Device"
  if (state.status === "trusted") {
    return (
      <div className="space-y-4">
        <h3 className="text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide px-1">
          My Device
        </h3>
        {state.syncInfo && <DeviceInfo syncInfo={state.syncInfo} status="trusted" />}
        <button
          onClick={onDisconnect}
          className="w-full py-2 text-sm text-gray-500 hover:text-red-500 dark:text-gray-400 dark:hover:text-red-400 transition-colors"
        >
          Disconnect
        </button>
      </div>
    );
  }

  // Connected but not yet trusted: show as "Nearby Device"
  if (state.status === "connected") {
    return (
      <div className="space-y-4">
        <h3 className="text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide px-1">
          Nearby Device
        </h3>
        {state.syncInfo && <DeviceInfo syncInfo={state.syncInfo} status="connected" />}
        {state.error && (
          <p className="text-xs text-red-500 text-center">{state.error}</p>
        )}
        <button
          onClick={onDisconnect}
          className="w-full py-2 text-sm text-gray-500 hover:text-red-500 dark:text-gray-400 dark:hover:text-red-400 transition-colors"
        >
          Disconnect
        </button>
      </div>
    );
  }

  // Disconnected / Connecting: show IP+port form
  return (
    <div className="space-y-4">
      <div className="bg-white dark:bg-gray-800 rounded-lg p-4 shadow-sm border border-gray-200 dark:border-gray-700">
        <h3 className="text-sm font-medium text-gray-900 dark:text-gray-100 mb-3">
          Connect to CrossPaste
        </h3>
        <form onSubmit={handleConnect} className="space-y-3">
          <div>
            <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">
              IP Address
            </label>
            <input
              type="text"
              value={host}
              onChange={(e) => setHost(e.target.value)}
              placeholder="192.168.1.100"
              className="w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500"
              required
            />
          </div>
          <div>
            <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">
              Port
            </label>
            <input
              type="number"
              value={port}
              onChange={(e) => setPort(e.target.value)}
              placeholder="13129"
              className="w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500"
              required
            />
          </div>
          <button
            type="submit"
            disabled={state.status === "connecting"}
            className="w-full py-2 bg-blue-600 hover:bg-blue-700 disabled:bg-blue-400 text-white text-sm font-medium rounded-md transition-colors"
          >
            {state.status === "connecting" ? "Connecting…" : "Connect"}
          </button>
          {state.error && (
            <p className="text-xs text-red-500">{state.error}</p>
          )}
        </form>
      </div>
    </div>
  );
}

import { useState, useEffect } from "react";

export function useDesktopStatus(): boolean {
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    chrome.runtime.sendMessage({ type: "GET_DESKTOP_STATUS" }).then((res) => {
      if (res?.desktopConnected) setConnected(true);
    }).catch(() => {});

    const listener = (message: { type: string; connected?: boolean }) => {
      if (message.type === "DESKTOP_STATUS_CHANGED") {
        setConnected(!!message.connected);
      }
    };
    chrome.runtime.onMessage.addListener(listener);
    return () => chrome.runtime.onMessage.removeListener(listener);
  }, []);

  return connected;
}

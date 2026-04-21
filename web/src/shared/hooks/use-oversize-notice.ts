import { useEffect } from "react";
import { NotificationManager } from "@/shared/notification/notification-manager";

interface OversizeNoticeMessage {
  type: "OVERSIZE_NOTICE";
  title: string;
  message: string;
}

/**
 * Listen for PASTE_REJECTED_OVERSIZE events relayed by the service worker
 * and show a persistent in-panel notification (dismissed manually).
 */
export function useOversizeNoticeListener(): void {
  useEffect(() => {
    const listener = (message: { type: string } & Partial<OversizeNoticeMessage>) => {
      if (message.type !== "OVERSIZE_NOTICE") return;
      if (!message.title) return;
      NotificationManager.warning(message.title, message.message, null);
    };
    chrome.runtime.onMessage.addListener(listener);
    return () => chrome.runtime.onMessage.removeListener(listener);
  }, []);
}

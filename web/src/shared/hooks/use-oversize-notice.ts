import { useEffect } from "react";
import { NotificationManager } from "@/shared/notification/notification-manager";
import { openCrossPasteWebInBrowser } from "@/shared/app/ui-support";
import { CrossPasteWebService } from "@/shared/app/cross-paste-web-service";
import { useI18n } from "@/shared/i18n/use-i18n";

interface OversizeNoticeMessage {
  type: "OVERSIZE_NOTICE";
  title: string;
  message: string;
}

/**
 * Listen for PASTE_REJECTED_OVERSIZE events relayed by the service worker
 * and show a persistent in-panel notification (dismissed manually), with a
 * CTA that deep-links the user to the desktop-client download page.
 */
export function useOversizeNoticeListener(): void {
  const t = useI18n();
  useEffect(() => {
    // Warm the locale path map so the CTA can open the correct localized URL
    // (also gracefully falls back to /en/ if the fetch fails or is slow).
    void CrossPasteWebService.refresh();

    const listener = (message: { type: string } & Partial<OversizeNoticeMessage>) => {
      if (message.type !== "OVERSIZE_NOTICE") return;
      if (!message.title) return;
      NotificationManager.warning(message.title, message.message, null, {
        label: t("install_desktop_client"),
        onClick: () => {
          void openCrossPasteWebInBrowser("download");
        },
      });
    };
    chrome.runtime.onMessage.addListener(listener);
    return () => chrome.runtime.onMessage.removeListener(listener);
  }, [t]);
}

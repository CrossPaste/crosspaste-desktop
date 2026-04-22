import { useEffect } from "react";
import { NotificationManager } from "@/shared/notification/notification-manager";
import { openCrossPasteWebInBrowser } from "@/shared/app/ui-support";
import { CrossPasteWebService } from "@/shared/app/cross-paste-web-service";
import { useI18n } from "@/shared/i18n/use-i18n";
import {
  drainOversizeNotices,
  enqueueOversizeNotice,
} from "@/shared/oversize-notice-queue";

/**
 * Drain oversize-paste notices persisted by the service worker and surface
 * them as in-panel notifications with a CTA to download the desktop client.
 *
 * The service worker always enqueues a notice first, then sends a drain ping.
 * We drain on mount (to catch any queued while the panel was closed) and on
 * each drain ping. This sidesteps the MV3 behavior where sendMessage resolves
 * with undefined when the offscreen listener receives but doesn't handle it.
 */
export function useOversizeNoticeListener(): void {
  const t = useI18n();
  useEffect(() => {
    // Warm the locale path map so the CTA can open the correct localized URL
    // (also gracefully falls back to /en/ if the fetch fails or is slow).
    void CrossPasteWebService.refresh();

    const showNotice = (title: string, message: string) => {
      NotificationManager.warning(title, message, null, {
        label: t("install_desktop_client"),
        onClick: () => {
          void openCrossPasteWebInBrowser("download");
        },
      });
    };

    let cancelled = false;
    const drainAndShow = async () => {
      const pending = await drainOversizeNotices();
      if (cancelled) {
        // Unmounted mid-drain — put the already-drained notices back so the
        // next mount can replay them instead of silently losing them.
        for (const notice of pending) {
          await enqueueOversizeNotice(notice);
        }
        return;
      }
      for (const notice of pending) {
        showNotice(notice.title, notice.message);
      }
    };

    void drainAndShow();

    const listener = (message: { type: string }) => {
      if (message.type !== "OVERSIZE_NOTICE_DRAIN") return;
      void drainAndShow();
    };
    chrome.runtime.onMessage.addListener(listener);
    return () => {
      cancelled = true;
      chrome.runtime.onMessage.removeListener(listener);
    };
  }, [t]);
}

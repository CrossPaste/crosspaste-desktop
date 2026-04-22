import { useCallback } from "react";
import { copyPasteData } from "@/shared/clipboard/clipboard-writer";
import { NotificationManager } from "@/shared/notification/notification-manager";
import { useI18n } from "@/shared/i18n/use-i18n";
import type { PasteData } from "@/shared/models/paste-data";

/**
 * Copy a paste item to the system clipboard and surface a success/error toast.
 * The callback reference is stable across renders for the same translator.
 */
export function useCopyWithNotification(): (data: PasteData) => Promise<void> {
  const t = useI18n();
  return useCallback(
    async (data: PasteData) => {
      try {
        await copyPasteData(data);
        NotificationManager.success(t("copy_successful"));
      } catch {
        NotificationManager.error(t("copy_failed"));
      }
    },
    [t],
  );
}

import { CrossPasteWebService } from "./cross-paste-web-service";

/**
 * Extension-side equivalent of the Kotlin [UISupport] interface — only the
 * methods the extension can actually fulfil (no file-system or OS integration).
 */

/** Open [url] in a new browser tab. */
export function openUrlInBrowser(url: string): void {
  if (chrome?.tabs?.create) {
    void chrome.tabs.create({ url }).catch(() => {
      window.open(url, "_blank", "noopener");
    });
  } else {
    window.open(url, "_blank", "noopener");
  }
}

/** Open the localized CrossPaste website — [path] is appended after the locale segment. */
export async function openCrossPasteWebInBrowser(path = ""): Promise<void> {
  const url = await CrossPasteWebService.getLocalizedUrl(path);
  openUrlInBrowser(url);
}

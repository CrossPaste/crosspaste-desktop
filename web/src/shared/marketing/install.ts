import { buildMarketingChrome } from "./chrome-stub";

if (typeof window !== "undefined") {
  const w = window as unknown as { chrome?: typeof chrome };
  if (!w.chrome || !w.chrome.runtime?.id) {
    w.chrome = buildMarketingChrome();
    document.documentElement.dataset.marketing = "true";
  }
}

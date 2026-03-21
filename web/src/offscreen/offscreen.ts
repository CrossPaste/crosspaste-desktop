/**
 * Offscreen document for clipboard reading.
 *
 * This is a "dumb" clipboard reader — it simply reads the current system
 * clipboard text and returns it. All change-detection logic lives in
 * the service worker, which persists state via chrome.storage.session.
 */

const textarea = document.getElementById("clipboard-area") as HTMLTextAreaElement;

function readClipboardText(): string | null {
  textarea.value = "";
  textarea.focus();
  const ok = document.execCommand("paste");
  if (!ok) return null;
  return textarea.value;
}

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message.type === "READ_CLIPBOARD") {
    const text = readClipboardText();
    sendResponse({ text });
    return;
  }
});

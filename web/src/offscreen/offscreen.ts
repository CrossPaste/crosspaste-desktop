/**
 * Offscreen document for clipboard reading.
 *
 * Uses a contenteditable div + paste event to read all clipboard formats:
 * text/plain, text/html, and image/* (as base64 data URL).
 */

const div = document.getElementById("clipboard-area") as HTMLDivElement;

interface ClipboardResult {
  text: string | null;
  html: string | null;
  imageDataUrl: string | null;
}

function readClipboard(): Promise<ClipboardResult> {
  return new Promise((resolve) => {
    const result: ClipboardResult = { text: null, html: null, imageDataUrl: null };
    let settled = false;

    const settle = () => {
      if (settled) return;
      settled = true;
      div.removeEventListener("paste", handlePaste);
      div.innerHTML = "";
      resolve(result);
    };

    const handlePaste = (e: ClipboardEvent) => {
      e.preventDefault();

      const items = e.clipboardData?.items;
      if (!items || items.length === 0) {
        settle();
        return;
      }

      let pending = 0;
      const done = () => {
        if (--pending === 0) settle();
      };

      for (const item of items) {
        if (item.type === "text/plain") {
          pending++;
          item.getAsString((s) => {
            result.text = s;
            done();
          });
        } else if (item.type === "text/html") {
          pending++;
          item.getAsString((s) => {
            result.html = s;
            done();
          });
        } else if (item.type.startsWith("image/")) {
          const blob = item.getAsFile();
          if (blob) {
            pending++;
            const reader = new FileReader();
            reader.onloadend = () => {
              result.imageDataUrl = reader.result as string;
              done();
            };
            reader.readAsDataURL(blob);
          }
        }
      }

      if (pending === 0) settle();
    };

    div.addEventListener("paste", handlePaste);
    div.focus();
    document.execCommand("paste");

    // Timeout fallback
    setTimeout(settle, 1000);
  });
}

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message.type === "READ_CLIPBOARD") {
    readClipboard().then(sendResponse);
    return true; // async response
  }
});

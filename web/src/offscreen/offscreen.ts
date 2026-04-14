/**
 * Offscreen document for clipboard reading.
 *
 * Uses a contenteditable div + paste event to read all clipboard formats:
 * text/plain, text/html, and image/* (as base64 data URL).
 */

import { getHtmlBackgroundColor } from "@/shared/utils/html-color";

const div = document.getElementById("clipboard-area") as HTMLDivElement;

const MAX_FILE_SIZE = 1 * 1024 * 1024; // 1MB per file

interface ClipboardFileInfo {
  name: string;
  size: number;
  mimeType: string;
  dataUrl: string | null; // base64 content, null if file exceeds size limit
}

interface ClipboardResult {
  text: string | null;
  html: string | null;
  htmlBackgroundColor: number | null;
  rtf: string | null;
  imageDataUrl: string | null;
  files: ClipboardFileInfo[] | null;
}

function readClipboard(): Promise<ClipboardResult> {
  return new Promise((resolve) => {
    const result: ClipboardResult = { text: null, html: null, htmlBackgroundColor: null, rtf: null, imageDataUrl: null, files: null };
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
            result.htmlBackgroundColor = getHtmlBackgroundColor(s);
            done();
          });
        } else if (item.type === "text/rtf") {
          pending++;
          item.getAsString((s) => {
            result.rtf = s;
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
        } else if (item.kind === "file") {
          // Non-image file (e.g. copied from Finder)
          const file = item.getAsFile();
          if (file) {
            if (!result.files) result.files = [];
            const fileInfo: ClipboardFileInfo = {
              name: file.name,
              size: file.size,
              mimeType: file.type || "application/octet-stream",
              dataUrl: null,
            };
            result.files.push(fileInfo);
            // Read content for files under 1MB
            if (file.size <= MAX_FILE_SIZE) {
              pending++;
              const reader = new FileReader();
              reader.onloadend = () => {
                fileInfo.dataUrl = reader.result as string;
                done();
              };
              reader.readAsDataURL(file);
            }
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

async function writeClipboard(data: {
  text?: string;
  html?: string;
  imageDataUrl?: string;
}): Promise<boolean> {
  try {
    const blobs: Record<string, Blob> = {};

    if (data.imageDataUrl) {
      const res = await fetch(data.imageDataUrl);
      const blob = await res.blob();
      blobs["image/png"] = blob;
    }

    if (data.html) {
      blobs["text/html"] = new Blob([data.html], { type: "text/html" });
    }

    if (data.text) {
      blobs["text/plain"] = new Blob([data.text], { type: "text/plain" });
    }

    if (Object.keys(blobs).length === 0) return false;

    await navigator.clipboard.write([new ClipboardItem(blobs)]);
    return true;
  } catch (e) {
    console.error("[Offscreen] writeClipboard failed:", e);
    return false;
  }
}

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message.type === "READ_CLIPBOARD") {
    readClipboard().then(sendResponse);
    return true;
  }
  if (message.type === "WRITE_CLIPBOARD") {
    writeClipboard(message.data).then(sendResponse);
    return true;
  }
});

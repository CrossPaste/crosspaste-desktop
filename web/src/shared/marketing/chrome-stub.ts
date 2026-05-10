import { getMarketingDevices } from "./marketing-devices";
import { getMarketingPastes, invalidateMarketingCache } from "./marketing-fixtures";

type Listener = (msg: Record<string, unknown>, sender?: unknown, sendResponse?: (r: unknown) => void) => void | boolean;

const LANGUAGE_STORAGE_KEY = "ui_language";

const messageListeners = new Set<Listener>();

function broadcast(msg: Record<string, unknown>): void {
  for (const fn of messageListeners) {
    try {
      fn(msg);
    } catch {
      /* swallow listener errors so one bad listener doesn't break others */
    }
  }
}

async function handleSendMessage(message: Record<string, unknown>): Promise<unknown> {
  const type = message.type as string;
  switch (type) {
    case "GET_PASTES": {
      const all = await getMarketingPastes();
      const offset = (message.offset as number) ?? 0;
      const limit = (message.limit as number) ?? all.length;
      const query = ((message.query as string) ?? "").trim().toLowerCase();
      const pasteType = message.pasteType as number | null | undefined;
      let filtered = all;
      if (typeof pasteType === "number") {
        filtered = filtered.filter((p) => p.pasteType === pasteType);
      }
      if (query) {
        filtered = filtered.filter((p) => {
          const item = p.pasteAppearItem;
          if (!item) return false;
          if (item.type === "text") return item.text.toLowerCase().includes(query);
          if (item.type === "url") return item.url.toLowerCase().includes(query);
          if (item.type === "html") return item.html.toLowerCase().includes(query);
          return false;
        });
      }
      return { items: filtered.slice(offset, offset + limit) };
    }
    case "DELETE_PASTE":
      return { success: true };
    case "DOWNLOAD_FILE":
      return { success: true };
    case "GET_DEVICES":
      return { devices: getMarketingDevices() };
    case "GET_WS_STATUS": {
      const statuses: Record<string, string> = {};
      for (const d of getMarketingDevices()) {
        statuses[d.targetAppInstanceId] = "ws_connected";
      }
      return { statuses };
    }
    case "GET_DESKTOP_STATUS":
      return { desktopConnected: false };
    case "CONNECT":
    case "PAIR":
    case "REPAIR":
    case "REMOVE_DEVICE":
    case "UPDATE_NOTE":
    case "LOCAL_COPY":
    case "COPY_ITEM":
      return { success: true };
    default:
      return undefined;
  }
}

function makeStorageArea(backing: Storage): chrome.storage.StorageArea {
  const get = (
    keys?: string | string[] | Record<string, unknown> | null,
  ): Promise<Record<string, unknown>> => {
    const result: Record<string, unknown> = {};
    const collect = (k: string, fallback?: unknown) => {
      const raw = backing.getItem(k);
      if (raw !== null) {
        try {
          result[k] = JSON.parse(raw);
        } catch {
          result[k] = raw;
        }
      } else if (fallback !== undefined) {
        result[k] = fallback;
      }
    };
    if (keys == null) {
      for (let i = 0; i < backing.length; i++) {
        const k = backing.key(i);
        if (k) collect(k);
      }
    } else if (typeof keys === "string") {
      collect(keys);
    } else if (Array.isArray(keys)) {
      keys.forEach((k) => collect(k));
    } else {
      Object.entries(keys).forEach(([k, fallback]) => collect(k, fallback));
    }
    return Promise.resolve(result);
  };

  const set = (items: Record<string, unknown>): Promise<void> => {
    let languageChanged = false;
    Object.entries(items).forEach(([k, v]) => {
      if (k === LANGUAGE_STORAGE_KEY && backing === window.localStorage) {
        const prev = backing.getItem(k);
        if (prev !== JSON.stringify(v)) languageChanged = true;
      }
      backing.setItem(k, JSON.stringify(v));
    });
    if (languageChanged) {
      invalidateMarketingCache();
      broadcast({ type: "PASTE_UPDATED" });
    }
    return Promise.resolve();
  };

  const remove = (keys: string | string[]): Promise<void> => {
    const list = Array.isArray(keys) ? keys : [keys];
    list.forEach((k) => backing.removeItem(k));
    return Promise.resolve();
  };

  return { get, set, remove } as unknown as chrome.storage.StorageArea;
}

export function buildMarketingChrome(): typeof chrome {
  const stub = {
    runtime: {
      sendMessage: (message: Record<string, unknown>) => handleSendMessage(message),
      onMessage: {
        addListener: (fn: Listener) => messageListeners.add(fn),
        removeListener: (fn: Listener) => messageListeners.delete(fn),
        hasListener: (fn: Listener) => messageListeners.has(fn),
      },
      getURL: (path: string) => path,
      id: "marketing-mode",
    },
    storage: {
      local: makeStorageArea(window.localStorage),
      session: makeStorageArea(window.sessionStorage),
    },
    tabs: {
      create: (props: { url?: string }) => {
        if (props.url) window.open(props.url, "_blank", "noopener");
        return Promise.resolve({} as chrome.tabs.Tab);
      },
    },
    permissions: {
      contains: () => Promise.resolve(true),
      request: () => Promise.resolve(true),
    },
  };
  return stub as unknown as typeof chrome;
}

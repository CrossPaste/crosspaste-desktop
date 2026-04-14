const NATIVE_HOST_NAME = "com.crosspaste.desktop";
const RECONNECT_INTERVAL_MS = 10_000;

type NativeHostCallbacks = {
  onDesktopConnected: () => void;
  onDesktopDisconnected: () => void;
};

let port: chrome.runtime.Port | null = null;
let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
let callbacks: NativeHostCallbacks | null = null;
let desktopConnected = false;
let initialResolve: ((connected: boolean) => void) | null = null;

export function isDesktopConnected(): boolean {
  return desktopConnected;
}

export function initNativeHost(cbs: NativeHostCallbacks): Promise<boolean> {
  callbacks = cbs;
  return new Promise((resolve) => {
    initialResolve = resolve;
    attemptConnect();
  });
}

function attemptConnect(): void {
  try {
    port = chrome.runtime.connectNative(NATIVE_HOST_NAME);

    port.onMessage.addListener((_msg: unknown) => {
      if (!desktopConnected) {
        desktopConnected = true;
        if (initialResolve) {
          initialResolve(true);
          initialResolve = null;
        }
        callbacks?.onDesktopConnected();
      }
    });

    port.onDisconnect.addListener(() => {
      port = null;
      if (initialResolve) {
        initialResolve(false);
        initialResolve = null;
      } else if (desktopConnected) {
        desktopConnected = false;
        callbacks?.onDesktopDisconnected();
      }
      scheduleReconnect();
    });
  } catch {
    if (initialResolve) {
      initialResolve(false);
      initialResolve = null;
    }
    scheduleReconnect();
  }
}

function scheduleReconnect(): void {
  if (reconnectTimer) clearTimeout(reconnectTimer);
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null;
    attemptConnect();
  }, RECONNECT_INTERVAL_MS);
}

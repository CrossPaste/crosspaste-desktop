export type MessageType = "info" | "success" | "warning" | "error";

export interface Message {
  id: number;
  title: string;
  message?: string;
  type: MessageType;
  /** Auto-dismiss duration in ms. Null = persistent until manually dismissed. */
  duration: number | null;
}

type Listener = (messages: Message[]) => void;

let nextId = 1;
const DEBOUNCE_MS = 300;

let messages: Message[] = [];
let lastContent: string | null = null;
let lastContentTime = 0;
const listeners = new Set<Listener>();

function notify() {
  for (const fn of listeners) fn([...messages]);
}

export const NotificationManager = {
  /** Subscribe to notification list changes */
  subscribe(fn: Listener): () => void {
    listeners.add(fn);
    fn([...messages]);
    return () => listeners.delete(fn);
  },

  /** Show a notification. Debounces identical content within 300ms. */
  show(title: string, type: MessageType = "info", message?: string, duration: number | null = 3000) {
    const contentKey = `${type}:${title}:${message ?? ""}`;
    const now = Date.now();
    if (contentKey === lastContent && now - lastContentTime < DEBOUNCE_MS) return;
    lastContent = contentKey;
    lastContentTime = now;

    const msg: Message = { id: nextId++, title, message, type, duration };
    messages = [...messages, msg];
    notify();

    if (duration !== null) {
      const msgId = msg.id;
      setTimeout(() => {
        NotificationManager.dismiss(msgId);
      }, duration);
    }
  },

  dismiss(id: number) {
    const prev = messages.length;
    messages = messages.filter((m) => m.id !== id);
    if (messages.length !== prev) notify();
  },

  /** Convenience methods */
  info(title: string, message?: string, duration?: number | null) {
    this.show(title, "info", message, duration);
  },
  success(title: string, message?: string, duration?: number | null) {
    this.show(title, "success", message, duration);
  },
  warning(title: string, message?: string, duration?: number | null) {
    this.show(title, "warning", message, duration);
  },
  error(title: string, message?: string, duration?: number | null) {
    this.show(title, "error", message, duration);
  },
};

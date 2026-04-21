export type MessageType = "info" | "success" | "warning" | "error";

export interface MessageAction {
  label: string;
  onClick: () => void;
}

export interface Message {
  id: number;
  title: string;
  message?: string;
  type: MessageType;
  /** Auto-dismiss duration in ms. Null = persistent until manually dismissed. */
  duration: number | null;
  /** Optional call-to-action button rendered inside the notification. */
  action?: MessageAction;
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
  show(
    title: string,
    type: MessageType = "info",
    message?: string,
    duration: number | null = 3000,
    action?: MessageAction,
  ) {
    const contentKey = `${type}:${title}:${message ?? ""}`;
    const now = Date.now();
    if (contentKey === lastContent && now - lastContentTime < DEBOUNCE_MS) return;
    lastContent = contentKey;
    lastContentTime = now;

    const msg: Message = { id: nextId++, title, message, type, duration, action };
    messages = [msg, ...messages];
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
  info(title: string, message?: string, duration?: number | null, action?: MessageAction) {
    this.show(title, "info", message, duration, action);
  },
  success(title: string, message?: string, duration?: number | null, action?: MessageAction) {
    this.show(title, "success", message, duration, action);
  },
  warning(title: string, message?: string, duration?: number | null, action?: MessageAction) {
    this.show(title, "warning", message, duration, action);
  },
  error(title: string, message?: string, duration?: number | null, action?: MessageAction) {
    this.show(title, "error", message, duration, action);
  },
};

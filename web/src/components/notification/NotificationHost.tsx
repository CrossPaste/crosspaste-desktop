import { useState, useEffect, useCallback } from "react";
import { X, Info, CheckCircle, AlertTriangle, AlertCircle } from "lucide-react";
import {
  NotificationManager,
  type Message,
  type MessageType,
} from "@/shared/notification/notification-manager";

const TYPE_STYLES: Record<MessageType, { container: string; icon: string }> = {
  info: {
    container: "bg-blue-100 dark:bg-blue-900 text-blue-900 dark:text-blue-100",
    icon: "text-blue-700 dark:text-blue-300",
  },
  success: {
    container: "bg-green-100 dark:bg-green-900 text-green-900 dark:text-green-100",
    icon: "text-green-700 dark:text-green-300",
  },
  warning: {
    container: "bg-amber-100 dark:bg-amber-900 text-amber-900 dark:text-amber-100",
    icon: "text-amber-700 dark:text-amber-300",
  },
  error: {
    container: "bg-red-100 dark:bg-red-900 text-red-900 dark:text-red-100",
    icon: "text-red-700 dark:text-red-300",
  },
};

const TYPE_ICONS: Record<MessageType, typeof Info> = {
  info: Info,
  success: CheckCircle,
  warning: AlertTriangle,
  error: AlertCircle,
};

function NotificationCard({
  msg,
  onDismiss,
}: {
  msg: Message;
  onDismiss: (id: number) => void;
}) {
  const [visible, setVisible] = useState(false);
  const [exiting, setExiting] = useState(false);

  useEffect(() => {
    // Trigger enter animation
    requestAnimationFrame(() => setVisible(true));
  }, []);

  const handleDismiss = useCallback(() => {
    setExiting(true);
    setTimeout(() => onDismiss(msg.id), 200);
  }, [msg.id, onDismiss]);

  // Auto-dismiss: trigger exit animation before actual removal
  useEffect(() => {
    if (msg.duration === null) return;
    const exitTime = msg.duration - 200;
    if (exitTime <= 0) return;
    const timer = setTimeout(() => setExiting(true), exitTime);
    return () => clearTimeout(timer);
  }, [msg.duration]);

  const styles = TYPE_STYLES[msg.type];
  const Icon = TYPE_ICONS[msg.type];

  return (
    <div
      className={`
        flex items-start gap-2.5 px-3 py-2.5 rounded-xl shadow-lg
        transition-all duration-200 ease-out
        ${styles.container}
        ${visible && !exiting ? "opacity-100 translate-y-0" : "opacity-0 -translate-y-2"}
        ${exiting ? "opacity-0 translate-x-full" : ""}
      `}
    >
      <Icon size={18} className={`${styles.icon} shrink-0 mt-0.5`} />
      <div className="flex-1 min-w-0">
        <p className="text-xs font-medium leading-tight">{msg.title}</p>
        {msg.message && (
          <p className="text-[11px] leading-tight mt-0.5 opacity-70">{msg.message}</p>
        )}
      </div>
      <button
        onClick={handleDismiss}
        className="shrink-0 opacity-50 hover:opacity-100 transition-opacity mt-0.5"
      >
        <X size={14} />
      </button>
    </div>
  );
}

export function NotificationHost() {
  const [messages, setMessages] = useState<Message[]>([]);

  useEffect(() => {
    return NotificationManager.subscribe(setMessages);
  }, []);

  if (messages.length === 0) return null;

  return (
    <div className="absolute top-1 left-3 right-3 z-50 flex flex-col gap-1.5 pointer-events-none">
      {messages.map((msg) => (
        <div key={msg.id} className="pointer-events-auto">
          <NotificationCard
            msg={msg}
            onDismiss={NotificationManager.dismiss}
          />
        </div>
      ))}
    </div>
  );
}

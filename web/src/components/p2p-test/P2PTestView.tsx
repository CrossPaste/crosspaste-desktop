import { useState, useRef, useCallback, useEffect } from "react";
import { ArrowLeft, Copy, Check, Send, Wifi, WifiOff } from "lucide-react";
import { RTCSession, type SessionState } from "@/shared/p2p/rtc-session";
import { formatCode, unformatCode } from "@/shared/p2p/rtc-codec";

// ─── Types ──────────────────────────────────────────────────────────────

type Role = "none" | "offerer" | "answerer";

interface ChatMessage {
  from: "me" | "peer";
  text: string;
  time: number;
}

interface Props {
  onBack: () => void;
}

// ─── Status Badge ───────────────────────────────────────────────────────

const STATE_LABELS: Record<SessionState, string> = {
  idle: "Idle",
  gathering: "Gathering ICE…",
  "waiting-for-peer": "Waiting for peer code",
  connecting: "Connecting…",
  connected: "Connected",
  disconnected: "Disconnected",
  failed: "Failed",
};

const STATE_COLORS: Record<SessionState, string> = {
  idle: "bg-m3-surface-container text-m3-on-surface-variant",
  gathering: "bg-m3-warning-container text-m3-warning",
  "waiting-for-peer": "bg-m3-primary-container text-m3-primary",
  connecting: "bg-m3-warning-container text-m3-warning",
  connected: "bg-m3-success-container text-m3-success",
  disconnected: "bg-m3-error-container text-m3-error",
  failed: "bg-m3-error-container text-m3-error",
};

function StatusBadge({ state }: { state: SessionState }) {
  return (
    <span className={`text-[11px] font-medium px-2 py-0.5 rounded-md ${STATE_COLORS[state]}`}>
      {STATE_LABELS[state]}
    </span>
  );
}

// ─── Code Display ───────────────────────────────────────────────────────

function CodeDisplay({ code, label }: { code: string; label: string }) {
  const [copied, setCopied] = useState(false);

  const handleCopy = useCallback(() => {
    navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }, [code]);

  return (
    <div className="flex flex-col gap-1.5">
      <span className="text-xs font-medium text-m3-on-surface-variant">{label}</span>
      <div className="flex items-start gap-2">
        <div className="flex-1 bg-m3-surface-container-highest rounded-lg p-2.5 font-mono text-[11px] text-m3-on-surface break-all leading-relaxed select-all">
          {formatCode(code)}
        </div>
        <button
          onClick={handleCopy}
          className="shrink-0 flex items-center justify-center w-8 h-8 rounded-lg bg-m3-primary-container text-m3-primary hover:bg-m3-primary/20 transition-colors"
        >
          {copied ? <Check size={14} /> : <Copy size={14} />}
        </button>
      </div>
    </div>
  );
}

// ─── Code Input ─────────────────────────────────────────────────────────

function CodeInput({
  onSubmit,
  label,
  placeholder,
  disabled,
}: {
  onSubmit: (code: string) => void;
  label: string;
  placeholder: string;
  disabled: boolean;
}) {
  const [value, setValue] = useState("");

  const handleSubmit = () => {
    const cleaned = unformatCode(value);
    if (cleaned) onSubmit(cleaned);
  };

  return (
    <div className="flex flex-col gap-1.5">
      <span className="text-xs font-medium text-m3-on-surface-variant">{label}</span>
      <div className="flex gap-2">
        <textarea
          value={value}
          onChange={(e) => setValue(e.target.value)}
          placeholder={placeholder}
          disabled={disabled}
          rows={3}
          className="flex-1 bg-m3-surface-container-highest rounded-lg p-2.5 font-mono text-[11px] text-m3-on-surface placeholder:text-m3-on-surface-variant/40 border border-m3-outline-variant focus:border-m3-primary focus:outline-none resize-none disabled:opacity-50"
        />
        <button
          onClick={handleSubmit}
          disabled={disabled || !value.trim()}
          className="shrink-0 self-end flex items-center justify-center h-8 px-3 rounded-lg bg-m3-primary text-m3-on-primary text-xs font-medium disabled:opacity-40 hover:bg-m3-primary/90 transition-colors"
        >
          OK
        </button>
      </div>
    </div>
  );
}

// ─── Chat Panel ─────────────────────────────────────────────────────────

function ChatPanel({
  messages,
  onSend,
}: {
  messages: ChatMessage[];
  onSend: (msg: string) => void;
}) {
  const [input, setInput] = useState("");
  const listRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    listRef.current?.scrollTo(0, listRef.current.scrollHeight);
  }, [messages.length]);

  const handleSend = () => {
    if (!input.trim()) return;
    onSend(input.trim());
    setInput("");
  };

  return (
    <div className="flex flex-col flex-1 min-h-0 rounded-[14px] bg-m3-surface-container overflow-hidden">
      {/* Messages */}
      <div ref={listRef} className="flex-1 overflow-y-auto p-3 flex flex-col gap-2">
        {messages.length === 0 && (
          <p className="text-xs text-m3-on-surface-variant text-center py-4">
            Connection established! Send a message to test.
          </p>
        )}
        {messages.map((m, i) => (
          <div
            key={i}
            className={`flex ${m.from === "me" ? "justify-end" : "justify-start"}`}
          >
            <div
              className={`max-w-[75%] px-3 py-1.5 rounded-xl text-xs ${
                m.from === "me"
                  ? "bg-m3-primary text-m3-on-primary rounded-br-sm"
                  : "bg-m3-surface-container-highest text-m3-on-surface rounded-bl-sm"
              }`}
            >
              {m.text}
            </div>
          </div>
        ))}
      </div>

      {/* Input */}
      <div className="flex items-center gap-2 px-3 py-2 border-t border-m3-outline-variant/30">
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && handleSend()}
          placeholder="Type a message…"
          className="flex-1 bg-transparent text-sm text-m3-on-surface placeholder:text-m3-on-surface-variant/40 focus:outline-none"
        />
        <button
          onClick={handleSend}
          disabled={!input.trim()}
          className="flex items-center justify-center w-7 h-7 rounded-full bg-m3-primary text-m3-on-primary disabled:opacity-40 transition-colors"
        >
          <Send size={14} />
        </button>
      </div>
    </div>
  );
}

// ─── Main View ──────────────────────────────────────────────────────────

export function P2PTestView({ onBack }: Props) {
  const [role, setRole] = useState<Role>("none");
  const [state, setState] = useState<SessionState>("idle");
  const [localCode, setLocalCode] = useState("");
  const [error, setError] = useState("");
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const sessionRef = useRef<RTCSession | null>(null);

  const getSession = useCallback(() => {
    if (sessionRef.current) return sessionRef.current;
    const s = new RTCSession({
      onStateChange: setState,
      onMessage: (text) =>
        setMessages((prev) => [...prev, { from: "peer", text, time: Date.now() }]),
      onLocalCode: setLocalCode,
      onError: setError,
    });
    sessionRef.current = s;
    return s;
  }, []);

  const handleSend = useCallback(
    (text: string) => {
      const s = sessionRef.current;
      if (s?.send(text)) {
        setMessages((prev) => [...prev, { from: "me", text, time: Date.now() }]);
      }
    },
    [],
  );

  const handleReset = useCallback(() => {
    sessionRef.current?.close();
    sessionRef.current = null;
    setRole("none");
    setState("idle");
    setLocalCode("");
    setError("");
    setMessages([]);
  }, []);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      sessionRef.current?.close();
    };
  }, []);

  const startAsOfferer = async () => {
    setRole("offerer");
    setError("");
    try {
      await getSession().createOffer();
    } catch (e) {
      setError(String(e));
      setState("failed");
    }
  };

  const startAsAnswerer = () => {
    setRole("answerer");
    setError("");
  };

  const handleOfferCode = async (code: string) => {
    try {
      await getSession().acceptOffer(code);
    } catch (e) {
      setError(String(e));
      setState("failed");
    }
  };

  const handleAnswerCode = async (code: string) => {
    try {
      await getSession().acceptAnswer(code);
    } catch (e) {
      setError(String(e));
      setState("failed");
    }
  };

  return (
    <div className="flex flex-col h-full">
      {/* Top bar */}
      <div className="flex items-center justify-between px-4 py-3 shrink-0">
        <div className="flex items-center gap-2">
          <button
            onClick={onBack}
            className="flex items-center justify-center w-8 h-8 rounded-lg hover:bg-m3-surface-container transition-colors"
          >
            <ArrowLeft size={20} className="text-m3-on-surface" />
          </button>
          <span className="text-base font-semibold text-m3-on-surface">
            P2P Test
          </span>
        </div>
        <div className="flex items-center gap-2">
          <StatusBadge state={state} />
          {role !== "none" && (
            <button
              onClick={handleReset}
              className="text-[11px] text-m3-error hover:underline"
            >
              Reset
            </button>
          )}
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 flex flex-col min-h-0 px-5 pb-5 gap-4">
        {/* Error */}
        {error && (
          <div className="bg-m3-error-container rounded-lg px-3 py-2 text-xs text-m3-error">
            {error}
          </div>
        )}

        {/* Role selection */}
        {role === "none" && (
          <div className="flex flex-col gap-3 py-4">
            <p className="text-sm text-m3-on-surface-variant text-center">
              Test WebRTC DataChannel P2P connectivity between two Chrome extensions on the same LAN.
            </p>
            <div className="flex gap-3">
              <button
                onClick={startAsOfferer}
                className="flex-1 flex flex-col items-center gap-2 py-5 rounded-[14px] bg-m3-primary-container hover:bg-m3-primary-container/80 transition-colors"
              >
                <Wifi size={24} className="text-m3-primary" />
                <span className="text-sm font-semibold text-m3-primary">
                  Create (Side A)
                </span>
                <span className="text-[10px] text-m3-on-primary-container/70">
                  Generate code first
                </span>
              </button>
              <button
                onClick={startAsAnswerer}
                className="flex-1 flex flex-col items-center gap-2 py-5 rounded-[14px] bg-m3-surface-container hover:bg-m3-surface-container-high transition-colors"
              >
                <WifiOff size={24} className="text-m3-on-surface-variant" />
                <span className="text-sm font-semibold text-m3-on-surface">
                  Join (Side B)
                </span>
                <span className="text-[10px] text-m3-on-surface-variant">
                  Enter peer's code
                </span>
              </button>
            </div>
          </div>
        )}

        {/* Offerer flow */}
        {role === "offerer" && state !== "connected" && (
          <div className="flex flex-col gap-4">
            {state === "gathering" && (
              <div className="flex items-center justify-center gap-2 py-4">
                <div className="w-4 h-4 border-2 border-m3-primary border-t-transparent rounded-full animate-spin" />
                <span className="text-sm text-m3-on-surface-variant">Gathering ICE candidates…</span>
              </div>
            )}
            {localCode && (
              <>
                <CodeDisplay code={localCode} label="Step 1: Share this code with Side B" />
                <CodeInput
                  label="Step 2: Paste Side B's code here"
                  placeholder="Paste answer code from Side B…"
                  onSubmit={handleAnswerCode}
                  disabled={state === "connecting"}
                />
              </>
            )}
          </div>
        )}

        {/* Answerer flow */}
        {role === "answerer" && state !== "connected" && (
          <div className="flex flex-col gap-4">
            {!localCode && state !== "gathering" && (
              <CodeInput
                label="Step 1: Paste Side A's code here"
                placeholder="Paste offer code from Side A…"
                onSubmit={handleOfferCode}
                disabled={false}
              />
            )}
            {state === "gathering" && (
              <div className="flex items-center justify-center gap-2 py-4">
                <div className="w-4 h-4 border-2 border-m3-primary border-t-transparent rounded-full animate-spin" />
                <span className="text-sm text-m3-on-surface-variant">Generating answer…</span>
              </div>
            )}
            {localCode && (
              <>
                <CodeDisplay code={localCode} label="Step 2: Share this code with Side A" />
                {state === "connecting" && (
                  <div className="flex items-center justify-center gap-2 py-2">
                    <div className="w-4 h-4 border-2 border-m3-primary border-t-transparent rounded-full animate-spin" />
                    <span className="text-sm text-m3-on-surface-variant">Waiting for connection…</span>
                  </div>
                )}
              </>
            )}
          </div>
        )}

        {/* Connected — Chat */}
        {state === "connected" && (
          <ChatPanel messages={messages} onSend={handleSend} />
        )}
      </div>
    </div>
  );
}

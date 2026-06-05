import { useEffect, useMemo, useRef, useState } from "react";
import { Send, X, Trash2, Sparkles, MessageCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { ApiError } from "@/api/client";
import { useMe } from "@/hooks/useAuth";
import { useChatHistory, useClearChat, useSendChat } from "@/hooks/useChat";

const ASSISTANT_NAME = "AI 小助手";

/** Resolve the avatar image from the user's preference (backend already falls back to gender). */
function avatarSrc(avatar?: "male" | "female") {
  return avatar === "female" ? "/assistant/coach-female.png" : "/assistant/coach-male.png";
}

const QUICK_PROMPTS = [
  "今天適合做什麼運動？",
  "幫我看看今天的熱量",
  "晚餐吃什麼比較好？",
];

export function AssistantWidget() {
  const { data: user } = useMe();
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState("");
  const scrollRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const { data: messages = [], isSuccess } = useChatHistory();
  const send = useSendChat();
  const clear = useClearChat();

  const avatar = useMemo(() => avatarSrc(user?.profile?.assistantAvatar), [user?.profile?.assistantAvatar]);
  const pending = send.isPending;

  // Assistant messages already revealed (loaded from history, or finished typing).
  // Only brand-new replies animate; history and re-opens render instantly.
  const revealed = useRef<Set<number>>(new Set());
  const initialized = useRef(false);
  useEffect(() => {
    if (isSuccess && !initialized.current) {
      messages.forEach((m) => revealed.current.add(m.id));
      initialized.current = true;
    }
  }, [isSuccess, messages]);

  const scrollToBottom = () => {
    const el = scrollRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  };

  // Auto-scroll to the newest message whenever the list grows or the panel opens.
  useEffect(() => {
    if (!open) return;
    scrollToBottom();
  }, [messages, pending, open]);

  // Focus the composer when the panel opens so the user can type right away.
  useEffect(() => {
    if (open) inputRef.current?.focus();
  }, [open]);

  function submit(text: string) {
    const trimmed = text.trim();
    if (!trimmed || pending) return;
    setDraft("");
    send.reset();
    // Restore the draft if the send fails so the user doesn't lose their message.
    send.mutate(trimmed, { onError: () => setDraft(trimmed) });
  }

  return (
    <>
      {/* Floating launcher button */}
      {!open && (
        <button
          type="button"
          onClick={() => setOpen(true)}
          aria-label="開啟 AI 小幫手"
          className="fixed z-50 bottom-28 right-5 md:bottom-6 md:right-6 group flex items-center justify-center size-24 rounded-full shadow-xl shadow-violet-500/30 bg-gradient-to-tr from-violet-500 to-indigo-500 ring-4 ring-white/70 dark:ring-slate-950/70 transition-transform duration-300 hover:scale-105 active:scale-95"
        >
          <img src={avatar} alt="" className="size-24 rounded-full object-cover object-top" />
          <span className="absolute top-0.5 right-0.5 flex size-6 items-center justify-center rounded-full bg-emerald-500 text-white shadow ring-2 ring-white/80 dark:ring-slate-950/80">
            <Sparkles className="size-3.5" />
          </span>
        </button>
      )}

      {/* Chat panel */}
      {open && (
        <div className="fixed z-50 inset-x-3 bottom-3 top-20 md:inset-auto md:bottom-6 md:right-6 md:w-[380px] md:h-[620px] md:max-h-[82vh] flex flex-col rounded-3xl border border-violet-200/60 dark:border-violet-900/40 bg-white/90 dark:bg-slate-950/85 backdrop-blur-xl shadow-2xl shadow-violet-500/10 overflow-hidden animate-fade-in">
          {/* Header */}
          <div className="flex items-center gap-3 px-4 py-3 border-b border-slate-100 dark:border-slate-900 bg-gradient-to-r from-violet-500/10 to-indigo-500/5">
            <div className="relative">
              <img src={avatar} alt="" className="size-14 rounded-full object-cover object-top ring-2 ring-violet-300/50 dark:ring-violet-800/50 bg-gradient-to-br from-violet-100 to-indigo-100 dark:from-violet-900/40 dark:to-indigo-900/40" />
              <span className="absolute bottom-0 right-0 size-3 rounded-full bg-emerald-500 ring-2 ring-white dark:ring-slate-950" />
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-bold text-slate-800 dark:text-slate-100">{ASSISTANT_NAME}</p>
              <p className="text-[11px] text-muted-foreground">你的 AI 健身營養小幫手</p>
            </div>
            {messages.length > 0 && (
              <Button
                variant="ghost"
                size="icon"
                onClick={() => clear.mutate()}
                disabled={clear.isPending}
                title="清除對話"
                className="rounded-full text-muted-foreground hover:text-rose-500 hover:bg-rose-500/10"
              >
                <Trash2 className="size-4" />
              </Button>
            )}
            <Button
              variant="ghost"
              size="icon"
              onClick={() => setOpen(false)}
              title="關閉"
              className="rounded-full text-muted-foreground hover:text-foreground"
            >
              <X className="size-5" />
            </Button>
          </div>

          {/* Messages */}
          <div ref={scrollRef} className="flex-1 overflow-y-auto px-4 py-4 space-y-3">
            {messages.length === 0 && !pending && (
              <div className="flex flex-col items-center text-center gap-3 pt-6">
                <img src={avatar} alt="" className="size-40 rounded-2xl object-contain p-2 bg-gradient-to-br from-violet-100 to-indigo-100 dark:from-violet-950/40 dark:to-indigo-950/40 shadow-lg shadow-violet-500/10" />
                <p className="text-sm font-semibold text-slate-700 dark:text-slate-200">
                  嗨{user?.name ? ` ${user.name}` : ""}！我是 {ASSISTANT_NAME} 👋
                </p>
                <p className="text-xs text-muted-foreground max-w-[16rem] leading-relaxed">
                  我專門陪你聊<strong>運動與飲食</strong>。說「<strong>我午餐吃了雞胸肉沙拉</strong>」我幫你記到飲食追蹤；
                  說「<strong>幫我排個練腿菜單</strong>」我幫你產生運動菜單；說「<strong>我今天體重 68 公斤</strong>」我幫你記下體重 🍱💪 下面也有幾個起手式：
                </p>
                <div className="flex flex-col gap-2 w-full mt-1">
                  {QUICK_PROMPTS.map((q) => (
                    <button
                      key={q}
                      type="button"
                      onClick={() => submit(q)}
                      className="text-xs text-left px-3.5 py-2.5 rounded-xl border border-violet-200/70 dark:border-violet-900/50 bg-violet-50/50 dark:bg-violet-950/20 text-violet-700 dark:text-violet-300 hover:bg-violet-100/70 dark:hover:bg-violet-900/30 transition-colors"
                    >
                      {q}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {messages.map((m) => (
              <div key={m.id} className={cn("flex items-end gap-2", m.role === "user" ? "justify-end" : "justify-start")}>
                {m.role === "assistant" && (
                  <img src={avatar} alt="" className="size-7 rounded-full object-cover object-top bg-violet-100 dark:bg-violet-950/40 shrink-0" />
                )}
                <div
                  className={cn(
                    "max-w-[78%] px-3.5 py-2 rounded-2xl text-sm leading-relaxed whitespace-pre-wrap break-words",
                    m.role === "user"
                      ? "bg-gradient-to-tr from-violet-500 to-indigo-500 text-white rounded-br-md shadow-sm"
                      : "bg-slate-100 dark:bg-slate-900 text-slate-800 dark:text-slate-100 rounded-bl-md",
                  )}
                >
                  {m.role === "assistant" ? (
                    <TypingText
                      text={m.content}
                      animate={!revealed.current.has(m.id)}
                      onTick={scrollToBottom}
                      onDone={() => revealed.current.add(m.id)}
                    />
                  ) : (
                    m.content
                  )}
                </div>
              </div>
            ))}

            {pending && (
              <div className="flex items-end gap-2 justify-start">
                <img src={avatar} alt="" className="size-7 rounded-full object-cover shrink-0" />
                <div className="px-4 py-3 rounded-2xl rounded-bl-md bg-slate-100 dark:bg-slate-900">
                  <span className="flex gap-1">
                    <span className="size-2 rounded-full bg-violet-400 animate-bounce [animation-delay:-0.3s]" />
                    <span className="size-2 rounded-full bg-violet-400 animate-bounce [animation-delay:-0.15s]" />
                    <span className="size-2 rounded-full bg-violet-400 animate-bounce" />
                  </span>
                </div>
              </div>
            )}
          </div>

          {/* Send error */}
          {send.isError && (
            <div className="px-4 pt-2 text-[11px] text-rose-500 dark:text-rose-400">
              {send.error instanceof ApiError ? send.error.message : "傳送失敗，請再試一次"}
            </div>
          )}

          {/* Composer */}
          <form
            onSubmit={(e) => {
              e.preventDefault();
              submit(draft);
            }}
            className="flex items-center gap-2 p-3 border-t border-slate-100 dark:border-slate-900 bg-white/60 dark:bg-slate-950/40"
          >
            <input
              ref={inputRef}
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              placeholder={`和 ${ASSISTANT_NAME} 聊聊…`}
              maxLength={1000}
              className="flex-1 h-11 rounded-2xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-900 px-4 text-sm outline-none focus:border-violet-400 dark:focus:border-violet-600 transition-colors"
            />
            <Button
              type="submit"
              size="icon"
              disabled={!draft.trim() || pending}
              className="size-11 shrink-0 rounded-2xl bg-gradient-to-tr from-violet-500 to-indigo-500 hover:opacity-90 text-white shadow-sm disabled:opacity-40"
            >
              {pending ? <MessageCircle className="size-5 animate-pulse" /> : <Send className="size-5" />}
            </Button>
          </form>
        </div>
      )}
    </>
  );
}

/** Reveals text progressively for a typewriter feel; renders instantly when animate=false. */
function TypingText({
  text,
  animate,
  onTick,
  onDone,
}: {
  text: string;
  animate: boolean;
  onTick?: () => void;
  onDone?: () => void;
}) {
  const [count, setCount] = useState(animate ? 0 : text.length);

  useEffect(() => {
    if (!animate) {
      setCount(text.length);
      return;
    }
    setCount(0);
    // Reveal in small chunks so long replies stay snappy (~120 ticks max).
    const step = Math.max(1, Math.ceil(text.length / 120));
    let shown = 0;
    const id = window.setInterval(() => {
      shown = Math.min(text.length, shown + step);
      setCount(shown);
      onTick?.();
      if (shown >= text.length) {
        window.clearInterval(id);
        onDone?.();
      }
    }, 22);
    return () => window.clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [text, animate]);

  const done = count >= text.length;
  return (
    <>
      {text.slice(0, count)}
      {animate && !done && (
        <span className="inline-block w-px h-[1em] ml-0.5 bg-violet-400 animate-pulse align-text-bottom" />
      )}
    </>
  );
}

import { useState } from "react";
import { Bell, CheckCheck } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { useNotifications, useMarkNotificationsRead } from "@/hooks/useNotifications";
import type { NotificationItem, NotificationSeverity } from "@/types/api";

const accent: Record<NotificationSeverity, string> = {
  success: "border-l-emerald-400",
  info: "border-l-sky-400",
  warning: "border-l-amber-400",
};

export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const { data } = useNotifications();
  const markRead = useMarkNotificationsRead();
  const navigate = useNavigate();

  const unread = data?.unreadCount ?? 0;
  const items = data?.items ?? [];

  function toggle() {
    const next = !open;
    setOpen(next);
    // Opening the panel counts as reading — clear the badge.
    if (next && unread > 0) markRead.mutate();
  }

  function go(item: NotificationItem) {
    setOpen(false);
    if (item.actionHref) navigate(item.actionHref);
  }

  return (
    <div className="relative">
      <Button
        variant="ghost"
        size="icon"
        className="rounded-full text-muted-foreground hover:text-foreground relative"
        onClick={toggle}
        title="通知"
        aria-label="通知"
      >
        <Bell className="size-5" />
        {unread > 0 && (
          <span className="absolute -top-0.5 -right-0.5 min-w-[18px] h-[18px] px-1 flex items-center justify-center rounded-full bg-rose-500 text-white text-[10px] font-bold leading-none">
            {unread > 9 ? "9+" : unread}
          </span>
        )}
      </Button>

      {open && (
        <>
          {/* click-outside backdrop */}
          <div className="fixed inset-0 z-30" onClick={() => setOpen(false)} />
          <div className="absolute right-0 mt-2 w-[20rem] max-w-[90vw] z-40 rounded-2xl border border-slate-100 dark:border-slate-800 bg-white dark:bg-slate-950 shadow-xl shadow-slate-200/50 dark:shadow-black/50 overflow-hidden">
            <div className="flex items-center justify-between px-4 py-3 border-b border-slate-100 dark:border-slate-900">
              <span className="text-sm font-bold">通知中心</span>
              <CheckCheck className="size-4 text-muted-foreground" />
            </div>

            <div className="max-h-[60vh] overflow-y-auto divide-y divide-slate-50 dark:divide-slate-900/60">
              {items.length === 0 ? (
                <p className="px-4 py-8 text-center text-xs text-muted-foreground">目前沒有通知 🎉</p>
              ) : (
                items.map((n) => (
                  <button
                    key={n.key}
                    onClick={() => go(n)}
                    className={cn(
                      "w-full text-left flex gap-3 px-4 py-3 border-l-2 hover:bg-slate-50 dark:hover:bg-slate-900/40 transition-colors",
                      accent[n.severity],
                      !n.read && "bg-slate-50/60 dark:bg-slate-900/30",
                    )}
                  >
                    <span className="text-xl leading-none mt-0.5">{n.emoji}</span>
                    <span className="flex-1 min-w-0">
                      <span className="flex items-center gap-1.5">
                        <span className="text-sm font-semibold text-slate-800 dark:text-slate-100 truncate">{n.title}</span>
                        {!n.read && <span className="size-1.5 rounded-full bg-rose-500 shrink-0" />}
                      </span>
                      <span className="block text-xs text-muted-foreground leading-snug mt-0.5">{n.body}</span>
                    </span>
                  </button>
                ))
              )}
            </div>
          </div>
        </>
      )}
    </div>
  );
}

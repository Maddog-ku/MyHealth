import { MonitorSmartphone, Smartphone, Laptop, ShieldCheck, LogOut } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useRevokeOtherSessions, useRevokeSession, useSessions } from "@/hooks/useSessions";
import type { SessionInfo } from "@/types/api";

function deviceIcon(device: string) {
  if (/iPhone|Android/.test(device)) return Smartphone;
  if (/iPad/.test(device)) return MonitorSmartphone;
  return Laptop;
}

/** Short relative label like "剛剛 / 5 分鐘前 / 3 小時前 / 2 天前". */
function relativeTime(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime();
  const min = Math.floor(diffMs / 60_000);
  if (min < 1) return "剛剛";
  if (min < 60) return `${min} 分鐘前`;
  const hr = Math.floor(min / 60);
  if (hr < 24) return `${hr} 小時前`;
  const day = Math.floor(hr / 24);
  if (day < 30) return `${day} 天前`;
  return new Date(iso).toLocaleDateString("zh-TW");
}

function loginDate(iso: string): string {
  return new Date(iso).toLocaleDateString("zh-TW", { year: "numeric", month: "short", day: "numeric" });
}

export function SessionsCard() {
  const sessions = useSessions();
  const revoke = useRevokeSession();
  const revokeOthers = useRevokeOtherSessions();

  const list = sessions.data?.sessions ?? [];
  const hasOthers = list.some((s) => !s.current);

  return (
    <Card className="border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden card-hover-effect">
      <CardHeader className="pb-3">
        <CardTitle className="text-lg font-bold flex items-center gap-2">
          <ShieldCheck className="size-4.5 text-teal-500" />
          登入裝置與安全
        </CardTitle>
        <CardDescription className="text-xs">
          這些是目前登入你帳號的裝置。如果看到不認得的裝置，請登出它並考慮更改密碼。
        </CardDescription>
      </CardHeader>
      <CardContent className="px-6 pb-6 space-y-3">
        {sessions.isLoading ? (
          <Skeleton className="h-24 w-full rounded-2xl" />
        ) : list.length === 0 ? (
          <p className="py-4 text-center text-xs text-muted-foreground">目前沒有其他有效的登入工作階段。</p>
        ) : (
          <>
            <ul className="space-y-2.5">
              {list.map((s) => (
                <SessionRow
                  key={s.id}
                  session={s}
                  onRevoke={() => revoke.mutate(s.id)}
                  revoking={revoke.isPending && revoke.variables === s.id}
                />
              ))}
            </ul>

            {hasOthers && (
              <Button
                type="button"
                variant="outline"
                disabled={revokeOthers.isPending}
                onClick={() => revokeOthers.mutate()}
                className="mt-2 w-full rounded-2xl py-5 gap-1.5 border-rose-500/30 text-rose-600 dark:text-rose-400 hover:bg-rose-500/10 font-semibold text-sm"
              >
                <LogOut className="size-4" />
                {revokeOthers.isPending ? "登出中…" : "登出其他所有裝置"}
              </Button>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}

function SessionRow({
  session,
  onRevoke,
  revoking,
}: {
  session: SessionInfo;
  onRevoke: () => void;
  revoking: boolean;
}) {
  const Icon = deviceIcon(session.device);
  return (
    <li className="flex items-center gap-3 rounded-2xl border border-slate-100 dark:border-slate-900/60 bg-white/50 dark:bg-slate-900/20 p-3.5">
      <div
        className={`flex size-10 shrink-0 items-center justify-center rounded-xl ${
          session.current ? "bg-teal-500/10 text-teal-600 dark:text-teal-400" : "bg-slate-100 dark:bg-slate-900 text-slate-500"
        }`}
      >
        <Icon className="size-5" />
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <p className="truncate text-sm font-semibold text-slate-700 dark:text-slate-200">{session.device}</p>
          {session.current && (
            <Badge className="shrink-0 rounded-full text-[9px] bg-teal-500/10 text-teal-600 dark:text-teal-400 border border-teal-500/15 px-2 py-0.5">
              目前裝置
            </Badge>
          )}
        </div>
        <p className="mt-0.5 text-[10px] text-muted-foreground">
          {session.current ? "使用中" : `最後活動 ${relativeTime(session.lastActiveAt)}`} · 登入於 {loginDate(session.createdAt)}
        </p>
      </div>
      {!session.current && (
        <Button
          type="button"
          variant="ghost"
          size="sm"
          disabled={revoking}
          onClick={onRevoke}
          className="shrink-0 rounded-xl text-xs font-semibold text-slate-400 hover:text-rose-500"
        >
          {revoking ? "登出中…" : "登出"}
        </Button>
      )}
    </li>
  );
}

import { AlertTriangle, Bot, CheckCircle2, Database, Gauge, RefreshCw, Server, XCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useSystemStatus } from "@/hooks/useSystemStatus";
import { useI18n } from "@/i18n/i18n";
import { cn } from "@/lib/utils";
import type { SystemComponentStatus, SystemComponentStatusValue } from "@/types/api";

const componentIcons: Record<string, typeof Server> = {
  backend: Server,
  database: Database,
  ai: Bot,
  rateLimit: Gauge,
};

function statusTone(status: SystemComponentStatusValue) {
  if (status === "UP") {
    return {
      icon: CheckCircle2,
      badge: "bg-emerald-500/10 text-emerald-700 dark:text-emerald-300",
      dot: "bg-emerald-500",
      text: "text-emerald-700 dark:text-emerald-300",
    };
  }
  if (status === "DEGRADED") {
    return {
      icon: AlertTriangle,
      badge: "bg-amber-500/10 text-amber-700 dark:text-amber-300",
      dot: "bg-amber-500",
      text: "text-amber-700 dark:text-amber-300",
    };
  }
  return {
    icon: XCircle,
    badge: "bg-rose-500/10 text-rose-700 dark:text-rose-300",
    dot: "bg-rose-500",
    text: "text-rose-700 dark:text-rose-300",
  };
}

function formatCheckedAt(value?: string) {
  if (!value) return "";
  return new Intl.DateTimeFormat(undefined, {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).format(new Date(value));
}

function ComponentRow({ component }: { component: SystemComponentStatus }) {
  const Icon = componentIcons[component.key] ?? Server;
  const tone = statusTone(component.status);
  const StatusIcon = tone.icon;

  return (
    <div className="flex items-center gap-3 rounded-2xl border border-slate-100/80 bg-white/60 p-3 dark:border-slate-900 dark:bg-slate-900/40">
      <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-slate-50 dark:bg-slate-950">
        <Icon className="size-4.5 text-slate-600 dark:text-slate-300" />
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <p className="truncate text-sm font-semibold">{component.label}</p>
          <span className={cn("inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-bold", tone.badge)}>
            <StatusIcon className="size-3" />
            {component.status}
          </span>
        </div>
        <p className="mt-0.5 truncate text-[11px] text-muted-foreground">{component.detail}</p>
      </div>
    </div>
  );
}

export function SystemStatusCard() {
  const { t } = useI18n();
  const status = useSystemStatus();
  const tone = status.data ? statusTone(status.data.status) : statusTone("DEGRADED");

  return (
    <Card className="border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden card-hover-effect">
      <CardHeader className="pb-3">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <CardTitle className="text-lg font-bold flex items-center gap-2">
              <Server className="size-4.5 text-teal-500" />
              {t("settings.system.title")}
            </CardTitle>
            <CardDescription className="text-xs">{t("settings.system.desc")}</CardDescription>
          </div>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => status.refetch()}
            disabled={status.isFetching}
            className="h-9 rounded-xl gap-1.5 text-xs"
          >
            <RefreshCw className={cn("size-3.5", status.isFetching && "animate-spin")} />
            {t("settings.system.refresh")}
          </Button>
        </div>
      </CardHeader>
      <CardContent className="grid gap-3 px-6 pb-6">
        {status.error ? (
          <div className="rounded-2xl border border-rose-500/20 bg-rose-500/5 p-4 text-sm text-rose-700 dark:text-rose-300">
            {t("settings.system.error")}
          </div>
        ) : (
          <>
            <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-slate-100/80 bg-slate-50/70 p-4 dark:border-slate-900 dark:bg-slate-900/30">
              <div className="flex items-center gap-2">
                <span className={cn("size-2.5 rounded-full", tone.dot)} />
                <span className={cn("text-sm font-bold", tone.text)}>
                  {status.data?.status ?? t("settings.system.loading")}
                </span>
              </div>
              {status.data?.checkedAt && (
                <span className="text-[11px] text-muted-foreground">
                  {t("settings.system.checkedAt")} {formatCheckedAt(status.data.checkedAt)}
                </span>
              )}
            </div>
            <div className="grid gap-2 md:grid-cols-2">
              {(status.data?.components ?? []).map((component) => (
                <ComponentRow key={component.key} component={component} />
              ))}
              {status.isLoading && (
                <div className="rounded-2xl border border-slate-100/80 bg-white/60 p-4 text-xs text-muted-foreground dark:border-slate-900 dark:bg-slate-900/40">
                  {t("settings.system.loading")}
                </div>
              )}
            </div>
          </>
        )}
      </CardContent>
    </Card>
  );
}

import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { AlertTriangle, Moon, Sun, SunMoon, Palette, Trash2, Bot, Check, Type, Languages } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { cn } from "@/lib/utils";
import { ApiError } from "@/api/client";
import { useDeleteAccount, useMe, useUpdateProfile } from "@/hooks/useAuth";
import { useTheme, type ThemeMode } from "@/hooks/useTheme";
import { useFontScale, type FontScale } from "@/hooks/useFontScale";
import { useI18n } from "@/i18n/i18n";
import { LANGS, type Lang } from "@/i18n/dictionaries";
import { ChangePasswordCard } from "@/components/ChangePasswordCard";
import { SessionsCard } from "@/components/SessionsCard";
import { DataExportCard } from "@/components/DataExportCard";

const AVATAR_OPTIONS = [
  { value: "male" as const, labelKey: "settings.avatar.male", descKey: "settings.avatar.maleDesc", src: "/assistant/coach-male.png" },
  { value: "female" as const, labelKey: "settings.avatar.female", descKey: "settings.avatar.femaleDesc", src: "/assistant/coach-female.png" },
];

const FONT_OPTIONS: { value: FontScale; labelKey: string; sample: string }[] = [
  { value: "small", labelKey: "settings.font.small", sample: "text-sm" },
  { value: "normal", labelKey: "settings.font.normal", sample: "text-base" },
  { value: "large", labelKey: "settings.font.large", sample: "text-lg" },
  { value: "xlarge", labelKey: "settings.font.xlarge", sample: "text-xl" },
];

export function SettingsPage() {
  const del = useDeleteAccount();
  const navigate = useNavigate();
  const { mode, setTheme } = useTheme();
  const { scale, setFontScale } = useFontScale();
  const { lang, setLang, t } = useI18n();
  const { data: user } = useMe();
  const updateProfile = useUpdateProfile();
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  function chooseLang(next: Lang) {
    if (next === lang) return;
    setLang(next);
    // Persist to the profile too, so the choice follows the account across devices.
    if (user?.profile) updateProfile.mutate({ ...user.profile, language: next });
  }

  const currentAvatar = user?.profile?.assistantAvatar ?? "male";

  function chooseAvatar(avatar: "male" | "female") {
    if (!user?.profile || avatar === currentAvatar || updateProfile.isPending) return;
    updateProfile.mutate({ ...user.profile, assistantAvatar: avatar });
  }

  async function handleDelete() {
    try {
      await del.mutateAsync();
      navigate("/login", { replace: true });
    } catch {
      // error rendered in the danger zone
    }
  }

  return (
    <section className="grid gap-6 animate-fade-in pb-10">
      {/* Theme Options Card */}
      <Card className="border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden card-hover-effect">
        <CardHeader className="pb-3">
          <CardTitle className="text-lg font-bold flex items-center gap-2">
            <Palette className="size-4.5 text-emerald-500" />
            {t("settings.theme.title")}
          </CardTitle>
          <CardDescription className="text-xs">{t("settings.theme.desc")}</CardDescription>
        </CardHeader>
        <CardContent className="grid grid-cols-1 sm:grid-cols-3 gap-3 px-6 pb-6">
          {(
            [
              { value: "light", label: t("settings.theme.light"), desc: t("settings.theme.lightDesc"), icon: Sun, color: "text-amber-500" },
              { value: "dark", label: t("settings.theme.dark"), desc: t("settings.theme.darkDesc"), icon: Moon, color: "text-indigo-400" },
              { value: "system", label: t("settings.theme.system"), desc: t("settings.theme.systemDesc"), icon: SunMoon, color: "text-emerald-500" },
            ] as { value: ThemeMode; label: string; desc: string; icon: typeof Sun; color: string }[]
          ).map(({ value, label, desc, icon: Icon, color }) => {
            const isActive = mode === value;
            return (
              <button
                key={value}
                type="button"
                onClick={() => setTheme(value)}
                className={cn(
                  "flex items-start gap-3.5 p-4 rounded-2xl border text-left transition-all duration-300",
                  isActive
                    ? "bg-gradient-to-tr from-emerald-500/10 to-teal-500/5 border-emerald-500/40 text-emerald-600 dark:text-emerald-400 font-semibold shadow-sm nav-active-glow"
                    : "bg-white/50 border-slate-100 dark:bg-slate-900/50 dark:border-slate-900 hover:border-slate-200 dark:hover:border-slate-800",
                )}
              >
                <div className={cn(
                  "flex size-10 items-center justify-center rounded-xl transition-all duration-300",
                  isActive ? "bg-emerald-500/10" : "bg-slate-50 dark:bg-slate-900",
                )}>
                  <Icon className={cn("size-5", color)} />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold">{label}</p>
                  <p className="text-[10px] text-muted-foreground mt-0.5 font-normal leading-normal">{desc}</p>
                </div>
              </button>
            );
          })}
        </CardContent>
      </Card>

      {/* Font size */}
      <Card className="border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden card-hover-effect">
        <CardHeader className="pb-3">
          <CardTitle className="text-lg font-bold flex items-center gap-2">
            <Type className="size-4.5 text-sky-500" />
            {t("settings.font.title")}
          </CardTitle>
          <CardDescription className="text-xs">{t("settings.font.desc")}</CardDescription>
        </CardHeader>
        <CardContent className="grid grid-cols-4 gap-3 px-6 pb-6">
          {FONT_OPTIONS.map(({ value, labelKey, sample }) => {
            const isActive = scale === value;
            return (
              <button
                key={value}
                type="button"
                onClick={() => setFontScale(value)}
                className={cn(
                  "flex flex-col items-center justify-center gap-1.5 py-4 rounded-2xl border transition-all duration-300",
                  isActive
                    ? "bg-gradient-to-tr from-sky-500/10 to-cyan-500/5 border-sky-500/40 text-sky-600 dark:text-sky-400 font-semibold shadow-sm"
                    : "bg-white/50 border-slate-100 dark:bg-slate-900/50 dark:border-slate-900 hover:border-slate-200 dark:hover:border-slate-800",
                )}
              >
                <span className={cn("font-bold leading-none", sample)}>A</span>
                <span className="text-[11px] font-medium">{t(labelKey)}</span>
              </button>
            );
          })}
        </CardContent>
      </Card>

      {/* Language */}
      <Card className="border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden card-hover-effect">
        <CardHeader className="pb-3">
          <CardTitle className="text-lg font-bold flex items-center gap-2">
            <Languages className="size-4.5 text-indigo-500" />
            {t("settings.lang.title")}
          </CardTitle>
          <CardDescription className="text-xs">{t("settings.lang.desc")}</CardDescription>
        </CardHeader>
        <CardContent className="grid grid-cols-2 gap-3 px-6 pb-6">
          {LANGS.map(({ value, label }) => {
            const isActive = lang === value;
            return (
              <button
                key={value}
                type="button"
                onClick={() => chooseLang(value)}
                className={cn(
                  "flex items-center justify-center gap-2 py-4 rounded-2xl border text-sm font-semibold transition-all duration-300",
                  isActive
                    ? "bg-gradient-to-tr from-indigo-500/10 to-violet-500/5 border-indigo-500/40 text-indigo-600 dark:text-indigo-400 shadow-sm"
                    : "bg-white/50 border-slate-100 dark:bg-slate-900/50 dark:border-slate-900 hover:border-slate-200 dark:hover:border-slate-800",
                )}
              >
                {isActive && <Check className="size-4" />}
                {label}
              </button>
            );
          })}
        </CardContent>
      </Card>

      {/* AI Assistant avatar selection */}
      <Card className="border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden card-hover-effect">
        <CardHeader className="pb-3">
          <CardTitle className="text-lg font-bold flex items-center gap-2">
            <Bot className="size-4.5 text-violet-500" />
            {t("settings.avatar.title")}
          </CardTitle>
          <CardDescription className="text-xs">
            {t("settings.avatar.desc")}
          </CardDescription>
        </CardHeader>
        <CardContent className="grid grid-cols-2 gap-3 px-6 pb-6">
          {AVATAR_OPTIONS.map(({ value, labelKey, descKey, src }) => {
            const isActive = currentAvatar === value;
            return (
              <button
                key={value}
                type="button"
                onClick={() => chooseAvatar(value)}
                disabled={updateProfile.isPending}
                className={cn(
                  "relative flex flex-col items-center gap-2 p-4 rounded-2xl border text-center transition-all duration-300 disabled:opacity-60",
                  isActive
                    ? "bg-gradient-to-tr from-violet-500/10 to-indigo-500/5 border-violet-500/40 shadow-sm"
                    : "bg-white/50 border-slate-100 dark:bg-slate-900/50 dark:border-slate-900 hover:border-slate-200 dark:hover:border-slate-800",
                )}
              >
                {isActive && (
                  <span className="absolute top-2.5 right-2.5 flex size-5 items-center justify-center rounded-full bg-violet-500 text-white shadow">
                    <Check className="size-3" />
                  </span>
                )}
                <img
                  src={src}
                  alt={t(labelKey)}
                  className={cn(
                    "size-36 rounded-2xl object-contain p-2 bg-gradient-to-br from-violet-100 to-indigo-100 dark:from-violet-950/40 dark:to-indigo-950/40 transition-transform duration-300",
                    isActive ? "ring-2 ring-violet-400/60 scale-[1.02]" : "opacity-90",
                  )}
                />
                <div>
                  <p className={cn("text-sm font-semibold", isActive && "text-violet-600 dark:text-violet-400")}>{t(labelKey)}</p>
                  <p className="text-[10px] text-muted-foreground mt-0.5 leading-normal">{t(descKey)}</p>
                </div>
              </button>
            );
          })}
        </CardContent>
      </Card>

      {/* Change password */}
      <ChangePasswordCard />

      {/* Active login sessions / device management */}
      <SessionsCard />

      {/* Export my data */}
      <DataExportCard />

      {/* Danger Zone — account deletion */}
      <Card className="border border-rose-500/20 dark:border-rose-500/15 bg-rose-500/[0.03] dark:bg-rose-950/10 rounded-3xl overflow-hidden">
        <CardHeader className="pb-3">
          <CardTitle className="text-lg font-bold flex items-center gap-2 text-rose-600 dark:text-rose-400">
            <AlertTriangle className="size-4.5" />
            {t("settings.danger.title")}
          </CardTitle>
          <CardDescription className="text-xs">
            {t("settings.danger.desc")}
          </CardDescription>
        </CardHeader>
        <CardContent className="px-6 pb-6">
          {del.error instanceof ApiError && (
            <Alert variant="destructive" className="mb-4 py-2.5 px-4 rounded-xl border-rose-500/20 bg-rose-500/5 text-rose-600 dark:text-rose-400">
              <AlertTitle className="text-xs font-bold">{t("settings.danger.deleteFailed")}</AlertTitle>
              <AlertDescription className="text-[11px] opacity-90">{del.error.message}</AlertDescription>
            </Alert>
          )}

          {!confirmingDelete ? (
            <Button
              type="button"
              variant="outline"
              onClick={() => setConfirmingDelete(true)}
              className="rounded-2xl py-5 px-6 gap-1.5 border-rose-500/30 text-rose-600 dark:text-rose-400 hover:bg-rose-500/10 hover:text-rose-700 dark:hover:text-rose-300 font-semibold text-sm"
            >
              <Trash2 className="size-4" />
              {t("settings.danger.deleteBtn")}
            </Button>
          ) : (
            <div className="flex flex-col gap-3 rounded-2xl border border-rose-500/20 bg-rose-500/5 p-4">
              <p className="text-xs font-semibold text-rose-700 dark:text-rose-300">
                {t("settings.danger.confirmQuestion")}
              </p>
              <div className="flex flex-wrap items-center gap-2">
                <Button
                  type="button"
                  onClick={handleDelete}
                  disabled={del.isPending}
                  className="rounded-2xl py-5 px-6 gap-1.5 bg-rose-600 hover:bg-rose-500 text-white font-semibold text-sm shadow-sm shadow-rose-500/10"
                >
                  {del.isPending ? (
                    <>
                      <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                      {t("settings.danger.deleting")}
                    </>
                  ) : (
                    <>
                      <Trash2 className="size-4" />
                      {t("settings.danger.confirmBtn")}
                    </>
                  )}
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  onClick={() => setConfirmingDelete(false)}
                  disabled={del.isPending}
                  className="rounded-2xl py-5 px-6 text-sm text-muted-foreground"
                >
                  {t("settings.danger.cancel")}
                </Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>
    </section>
  );
}

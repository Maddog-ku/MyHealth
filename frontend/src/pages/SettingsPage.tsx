import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { AlertTriangle, Moon, Sun, SunMoon, Palette, Trash2, Bot, Check, Type } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { cn } from "@/lib/utils";
import { ApiError } from "@/api/client";
import { useDeleteAccount, useMe, useUpdateProfile } from "@/hooks/useAuth";
import { useTheme, type ThemeMode } from "@/hooks/useTheme";
import { useFontScale, type FontScale } from "@/hooks/useFontScale";
import { SessionsCard } from "@/components/SessionsCard";

const AVATAR_OPTIONS = [
  { value: "male" as const, label: "活力男孩", desc: "陽光開朗的健身夥伴", src: "/assistant/coach-male.png" },
  { value: "female" as const, label: "元氣女孩", desc: "溫暖貼心的健身夥伴", src: "/assistant/coach-female.png" },
];

const FONT_OPTIONS: { value: FontScale; label: string; sample: string }[] = [
  { value: "small", label: "小", sample: "text-sm" },
  { value: "normal", label: "標準", sample: "text-base" },
  { value: "large", label: "大", sample: "text-lg" },
  { value: "xlarge", label: "特大", sample: "text-xl" },
];

export function SettingsPage() {
  const del = useDeleteAccount();
  const navigate = useNavigate();
  const { mode, setTheme } = useTheme();
  const { scale, setFontScale } = useFontScale();
  const { data: user } = useMe();
  const updateProfile = useUpdateProfile();
  const [confirmingDelete, setConfirmingDelete] = useState(false);

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
            外觀視覺主題
          </CardTitle>
          <CardDescription className="text-xs">選擇符合您當前環境與視覺偏好的色彩模式</CardDescription>
        </CardHeader>
        <CardContent className="grid grid-cols-1 sm:grid-cols-3 gap-3 px-6 pb-6">
          {(
            [
              { value: "light", label: "清新淺色", desc: "如晨光般乾淨自然", icon: Sun, color: "text-amber-500" },
              { value: "dark", label: "深沉靜夜", desc: "護眼低光睡眠友善", icon: Moon, color: "text-indigo-400" },
              { value: "system", label: "跟隨系統", desc: "隨日出日落自動調節", icon: SunMoon, color: "text-emerald-500" },
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
            字體大小
          </CardTitle>
          <CardDescription className="text-xs">調整整個介面（含 AI 小助手對話）的文字大小</CardDescription>
        </CardHeader>
        <CardContent className="grid grid-cols-4 gap-3 px-6 pb-6">
          {FONT_OPTIONS.map(({ value, label, sample }) => {
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
                <span className="text-[11px] font-medium">{label}</span>
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
            AI 小幫手人偶
          </CardTitle>
          <CardDescription className="text-xs">
            選擇你想聊天的小幫手形象，預設依你的性別，隨時可自由更換。
          </CardDescription>
        </CardHeader>
        <CardContent className="grid grid-cols-2 gap-3 px-6 pb-6">
          {AVATAR_OPTIONS.map(({ value, label, desc, src }) => {
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
                  alt={label}
                  className={cn(
                    "size-36 rounded-2xl object-contain p-2 bg-gradient-to-br from-violet-100 to-indigo-100 dark:from-violet-950/40 dark:to-indigo-950/40 transition-transform duration-300",
                    isActive ? "ring-2 ring-violet-400/60 scale-[1.02]" : "opacity-90",
                  )}
                />
                <div>
                  <p className={cn("text-sm font-semibold", isActive && "text-violet-600 dark:text-violet-400")}>{label}</p>
                  <p className="text-[10px] text-muted-foreground mt-0.5 leading-normal">{desc}</p>
                </div>
              </button>
            );
          })}
        </CardContent>
      </Card>

      {/* Active login sessions / device management */}
      <SessionsCard />

      {/* Danger Zone — account deletion */}
      <Card className="border border-rose-500/20 dark:border-rose-500/15 bg-rose-500/[0.03] dark:bg-rose-950/10 rounded-3xl overflow-hidden">
        <CardHeader className="pb-3">
          <CardTitle className="text-lg font-bold flex items-center gap-2 text-rose-600 dark:text-rose-400">
            <AlertTriangle className="size-4.5" />
            危險操作區
          </CardTitle>
          <CardDescription className="text-xs">
            刪除帳號將永久清除您的所有資料，包含運動紀錄、飲食紀錄、體重趨勢與個人檔案，且<strong>無法復原</strong>。
          </CardDescription>
        </CardHeader>
        <CardContent className="px-6 pb-6">
          {del.error instanceof ApiError && (
            <Alert variant="destructive" className="mb-4 py-2.5 px-4 rounded-xl border-rose-500/20 bg-rose-500/5 text-rose-600 dark:text-rose-400">
              <AlertTitle className="text-xs font-bold">刪除失敗</AlertTitle>
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
              刪除我的帳號
            </Button>
          ) : (
            <div className="flex flex-col gap-3 rounded-2xl border border-rose-500/20 bg-rose-500/5 p-4">
              <p className="text-xs font-semibold text-rose-700 dark:text-rose-300">
                確定要永久刪除帳號嗎？此動作會立即登出並清除所有資料，無法復原。
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
                      刪除中...
                    </>
                  ) : (
                    <>
                      <Trash2 className="size-4" />
                      確認永久刪除
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
                  取消
                </Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>
    </section>
  );
}

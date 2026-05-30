import { FormEvent, useEffect, useState } from "react";
import { Moon, Save, Sun, SunMoon, Palette, User, ShieldAlert, Sparkles, UserCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { cn } from "@/lib/utils";
import { ApiError } from "@/api/client";
import { useMe, useUpdateProfile } from "@/hooks/useAuth";
import { useTheme, type ThemeMode } from "@/hooks/useTheme";
import type { Profile } from "@/types/api";

export function SettingsPage() {
  const { data: user } = useMe();
  const update = useUpdateProfile();
  const { mode, setTheme } = useTheme();
  const [draft, setDraft] = useState<Profile | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (user?.profile) setDraft({ ...user.profile });
  }, [user]);

  if (!draft) return null;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaved(false);
    try {
      await update.mutateAsync(draft!);
      setSaved(true);
      setTimeout(() => setSaved(false), 2000);
    } catch {
      // error rendered below
    }
  }

  return (
    <section className="grid gap-6 animate-fade-in pb-10">
      {/* Theme Options Card */}
      <Card className="border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden card-hover-effect">
        <CardHeader className="pb-3">
          <CardTitle className="text-md font-bold flex items-center gap-2">
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

      {/* Profile Form Card */}
      <Card className="border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden accent-glow">
        <form onSubmit={submit}>
          <CardHeader className="pb-3 border-b border-slate-50 dark:border-slate-900/30">
            <CardTitle className="text-md font-bold flex items-center gap-2">
              <User className="size-4.5 text-emerald-500" />
              個人生理指標檔案
            </CardTitle>
            <CardDescription className="text-xs">這些生理指標將用於 AI 計算您的基礎代謝 (BMR)、每日熱量上限與最適合您的訓練動作</CardDescription>
          </CardHeader>
          
          <CardContent className="grid gap-4 md:grid-cols-2 p-6">
            <Field label="身高 (cm)" htmlFor="heightCm">
              <Input
                id="heightCm"
                type="number"
                min={50}
                max={250}
                value={draft.heightCm}
                onChange={(e) => setDraft({ ...draft!, heightCm: Number(e.target.value) })}
                className="rounded-2xl border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 py-5 focus-visible:ring-emerald-500 focus-visible:border-emerald-500/40 transition-all duration-300"
              />
            </Field>

            <Field label="體重 (kg)" htmlFor="weightKg">
              <Input
                id="weightKg"
                type="number"
                min={20}
                max={300}
                step="0.1"
                value={draft.weightKg}
                onChange={(e) => setDraft({ ...draft!, weightKg: Number(e.target.value) })}
                className="rounded-2xl border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 py-5 focus-visible:ring-emerald-500 focus-visible:border-emerald-500/40 transition-all duration-300"
              />
            </Field>

            <Field label="生理性別" htmlFor="gender">
              <Select
                value={draft.gender}
                onValueChange={(v) => setDraft({ ...draft!, gender: v as Profile["gender"] })}
              >
                <SelectTrigger id="gender" className="rounded-2xl border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 py-5">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent className="rounded-2xl border-slate-200/80 dark:border-slate-800">
                  <SelectItem value="male" className="rounded-xl">生理男</SelectItem>
                  <SelectItem value="female" className="rounded-xl">生理女</SelectItem>
                  <SelectItem value="other" className="rounded-xl">其他</SelectItem>
                </SelectContent>
              </Select>
            </Field>

            <Field label="年齡" htmlFor="age">
              <Input
                id="age"
                type="number"
                min={1}
                max={120}
                value={draft.age ?? ""}
                onChange={(e) => setDraft({ ...draft!, age: e.target.value ? Number(e.target.value) : undefined })}
                placeholder="未設定"
                className="rounded-2xl border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 py-5 focus-visible:ring-emerald-500 focus-visible:border-emerald-500/40 transition-all duration-300"
              />
            </Field>

            <Field label="健康鍛鍊目標" htmlFor="goal">
              <Select
                value={draft.goal ?? ""}
                onValueChange={(v) => setDraft({ ...draft!, goal: (v || undefined) as Profile["goal"] })}
              >
                <SelectTrigger id="goal" className="rounded-2xl border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 py-5">
                  <SelectValue placeholder="選擇健康目標" />
                </SelectTrigger>
                <SelectContent className="rounded-2xl border-slate-200/80 dark:border-slate-800">
                  <SelectItem value="muscle_gain" className="rounded-xl">增肌 (Muscle Gain)</SelectItem>
                  <SelectItem value="fat_loss" className="rounded-xl">減脂 (Fat Loss)</SelectItem>
                  <SelectItem value="maintain" className="rounded-xl">健康維持 (Maintain)</SelectItem>
                </SelectContent>
              </Select>
            </Field>

            <Field label="健身經驗等級" htmlFor="experience">
              <Select
                value={draft.experience ?? ""}
                onValueChange={(v) => setDraft({ ...draft!, experience: (v || undefined) as Profile["experience"] })}
              >
                <SelectTrigger id="experience" className="rounded-2xl border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 py-5">
                  <SelectValue placeholder="選擇經驗等級" />
                </SelectTrigger>
                <SelectContent className="rounded-2xl border-slate-200/80 dark:border-slate-800">
                  <SelectItem value="beginner" className="rounded-xl">新手入門 (Beginner)</SelectItem>
                  <SelectItem value="intermediate" className="rounded-xl">中等經驗 (Intermediate)</SelectItem>
                  <SelectItem value="advanced" className="rounded-xl">進階訓練 (Advanced)</SelectItem>
                </SelectContent>
              </Select>
            </Field>
          </CardContent>

          {/* Form Actions Footer */}
          <CardContent className="flex items-center justify-between border-t border-slate-50 dark:border-slate-900/30 p-6 bg-slate-50/20 dark:bg-slate-900/5">
            <div>
              {update.error instanceof ApiError && (
                <Alert variant="destructive" className="py-2.5 px-4 rounded-xl border-rose-500/10 bg-rose-500/5 text-rose-600 dark:text-rose-400">
                  <div className="flex items-center gap-1.5">
                    <ShieldAlert className="size-3.5" />
                    <AlertTitle className="text-xs font-bold leading-none mb-0">儲存失敗</AlertTitle>
                  </div>
                  <AlertDescription className="text-[10px] leading-relaxed mt-1 opacity-90">{update.error.message}</AlertDescription>
                </Alert>
              )}
              {saved && (
                <div className="flex items-center gap-1.5 text-xs text-emerald-600 dark:text-emerald-400 font-extrabold animate-fade-in">
                  <UserCheck className="size-4 animate-bounce" />
                  <span>資料已成功同步更新！</span>
                </div>
              )}
            </div>

            <Button
              type="submit"
              disabled={update.isPending}
              className="rounded-2xl py-5 px-6 bg-gradient-to-r from-emerald-600 to-teal-500 hover:from-emerald-500 hover:to-teal-400 text-white font-semibold shadow-md shadow-emerald-500/10 hover:shadow-lg transition-all-smooth gap-1.5"
            >
              {update.isPending ? (
                <>
                  <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                  同步中...
                </>
              ) : (
                <>
                  <Save className="size-4" />
                  儲存變更檔案
                </>
              )}
            </Button>
          </CardContent>
        </form>
      </Card>
    </section>
  );
}

function Field({ label, htmlFor, children }: { label: string; htmlFor: string; children: React.ReactNode }) {
  return (
    <div className="grid gap-1.5">
      <Label htmlFor={htmlFor} className="text-xs font-semibold text-slate-500 px-1">{label}</Label>
      {children}
    </div>
  );
}

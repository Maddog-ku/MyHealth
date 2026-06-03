import { FormEvent, useEffect, useState } from "react";
import { Save, ShieldAlert, User, UserCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { ApiError } from "@/api/client";
import { useMe, useUpdateProfile } from "@/hooks/useAuth";
import type { Profile } from "@/types/api";

export function ProfilePage() {
  const { data: user } = useMe();
  const update = useUpdateProfile();
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
                step="0.1"
                required
                inputMode="decimal"
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
                required
                inputMode="decimal"
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
                step={1}
                inputMode="numeric"
                value={draft.age ?? ""}
                onChange={(e) => setDraft({ ...draft!, age: e.target.value ? Number(e.target.value) : undefined })}
                placeholder="未設定"
                className="rounded-2xl border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 py-5 focus-visible:ring-emerald-500 focus-visible:border-emerald-500/40 transition-all duration-300"
              />
            </Field>

            {/* Body measurements (optional) — feed the body_measurements history / trend */}
            <NumberField label="體脂率 (%)" htmlFor="bodyFatPct" min={1} max={70} value={draft.bodyFatPct}
              onChange={(v) => setDraft({ ...draft!, bodyFatPct: v })} />
            <NumberField label="肌肉量 (kg)" htmlFor="muscleMassKg" min={1} max={150} value={draft.muscleMassKg}
              onChange={(v) => setDraft({ ...draft!, muscleMassKg: v })} />
            <NumberField label="基礎代謝 BMR (kcal)" htmlFor="bmrKcal" min={500} max={5000} step={1} value={draft.bmrKcal}
              onChange={(v) => setDraft({ ...draft!, bmrKcal: v })} />
            <NumberField label="腰圍 (cm)" htmlFor="waistCm" min={30} max={200} value={draft.waistCm}
              onChange={(v) => setDraft({ ...draft!, waistCm: v })} />
            <NumberField label="體水分率 (%)" htmlFor="bodyWaterPct" min={1} max={90} value={draft.bodyWaterPct}
              onChange={(v) => setDraft({ ...draft!, bodyWaterPct: v })} />

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

            <div className="md:col-span-2">
              <Field label="可用器材（以逗號分隔）" htmlFor="equipment">
                <Input
                  id="equipment"
                  value={(draft.equipment ?? []).join("、")}
                  onChange={(e) =>
                    setDraft({
                      ...draft!,
                      equipment: e.target.value
                        .split(/[,，、\n]/)
                        .map((s) => s.trim())
                        .filter(Boolean),
                    })
                  }
                  placeholder="例：啞鈴、瑜珈墊、彈力帶"
                  className="rounded-2xl border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 py-5 focus-visible:ring-emerald-500 focus-visible:border-emerald-500/40 transition-all duration-300"
                />
              </Field>
            </div>
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
                  <UserCheck className="size-4" />
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

function NumberField({
  label,
  htmlFor,
  value,
  onChange,
  min,
  max,
  step = "0.1",
}: {
  label: string;
  htmlFor: string;
  value: number | undefined;
  onChange: (value: number | undefined) => void;
  min: number;
  max: number;
  step?: number | string;
}) {
  return (
    <Field label={label} htmlFor={htmlFor}>
      <Input
        id={htmlFor}
        type="number"
        min={min}
        max={max}
        step={step}
        inputMode="decimal"
        value={value ?? ""}
        placeholder="未設定"
        onChange={(e) => onChange(e.target.value ? Number(e.target.value) : undefined)}
        className="rounded-2xl border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 py-5 focus-visible:ring-emerald-500 focus-visible:border-emerald-500/40 transition-all duration-300"
      />
    </Field>
  );
}

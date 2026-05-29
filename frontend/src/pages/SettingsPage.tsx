import { FormEvent, useEffect, useState } from "react";
import { Moon, Save, Sun, SunMoon } from "lucide-react";
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
    <section className="grid gap-6 animate-fade-in">
      <Card>
        <CardHeader>
          <CardTitle>外觀主題</CardTitle>
          <CardDescription>選擇你偏好的色彩模式</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-wrap gap-2">
          {(
            [
              { value: "light", label: "淺色", icon: Sun },
              { value: "dark", label: "深色", icon: Moon },
              { value: "system", label: "跟隨系統", icon: SunMoon },
            ] as { value: ThemeMode; label: string; icon: typeof Sun }[]
          ).map(({ value, label, icon: Icon }) => (
            <Button
              key={value}
              type="button"
              variant={mode === value ? "default" : "outline"}
              onClick={() => setTheme(value)}
              className={cn("min-w-28 justify-start")}
            >
              <Icon className="size-4" />
              {label}
            </Button>
          ))}
        </CardContent>
      </Card>

      <Card>
        <form onSubmit={submit}>
          <CardHeader>
            <CardTitle>個人資料</CardTitle>
            <CardDescription>用於個人化菜單與熱量目標</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4 md:grid-cols-2">
            <Field label="身高 (cm)" htmlFor="heightCm">
              <Input
                id="heightCm"
                type="number"
                min={50}
                max={250}
                value={draft.heightCm}
                onChange={(e) => setDraft({ ...draft!, heightCm: Number(e.target.value) })}
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
              />
            </Field>
            <Field label="性別" htmlFor="gender">
              <Select
                value={draft.gender}
                onValueChange={(v) => setDraft({ ...draft!, gender: v as Profile["gender"] })}
              >
                <SelectTrigger id="gender"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="male">男</SelectItem>
                  <SelectItem value="female">女</SelectItem>
                  <SelectItem value="other">其他</SelectItem>
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
              />
            </Field>
            <Field label="目標" htmlFor="goal">
              <Select
                value={draft.goal ?? ""}
                onValueChange={(v) => setDraft({ ...draft!, goal: (v || undefined) as Profile["goal"] })}
              >
                <SelectTrigger id="goal"><SelectValue placeholder="未設定" /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="muscle_gain">增肌</SelectItem>
                  <SelectItem value="fat_loss">減脂</SelectItem>
                  <SelectItem value="maintain">維持</SelectItem>
                </SelectContent>
              </Select>
            </Field>
            <Field label="經驗" htmlFor="experience">
              <Select
                value={draft.experience ?? ""}
                onValueChange={(v) => setDraft({ ...draft!, experience: (v || undefined) as Profile["experience"] })}
              >
                <SelectTrigger id="experience"><SelectValue placeholder="未設定" /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="beginner">新手</SelectItem>
                  <SelectItem value="intermediate">中等</SelectItem>
                  <SelectItem value="advanced">進階</SelectItem>
                </SelectContent>
              </Select>
            </Field>
          </CardContent>
          <CardContent className="flex items-center justify-between border-t pt-4">
            <div>
              {update.error instanceof ApiError && (
                <Alert variant="destructive" className="py-2">
                  <AlertTitle className="text-xs">儲存失敗</AlertTitle>
                  <AlertDescription className="text-xs">{update.error.message}</AlertDescription>
                </Alert>
              )}
              {saved && <p className="text-sm text-success">已儲存</p>}
            </div>
            <Button type="submit" disabled={update.isPending}>
              <Save className="size-4" />
              {update.isPending ? "儲存中…" : "儲存變更"}
            </Button>
          </CardContent>
        </form>
      </Card>
    </section>
  );
}

function Field({ label, htmlFor, children }: { label: string; htmlFor: string; children: React.ReactNode }) {
  return (
    <div className="grid gap-2">
      <Label htmlFor={htmlFor}>{label}</Label>
      {children}
    </div>
  );
}

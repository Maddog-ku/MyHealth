import { useState } from "react";
import { KeyRound, Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { ApiError } from "@/api/client";
import { useChangePassword } from "@/hooks/useChangePassword";

const POLICY = /^(?=.*[A-Z])(?=.*[a-z])[A-Za-z0-9]+$/;

function newPasswordError(pw: string): string | null {
  if (pw.length < 8) return "新密碼至少需要 8 個字元";
  if (pw.length > 128) return "新密碼過長";
  if (!POLICY.test(pw)) return "需同時包含大寫與小寫英文字母（僅限英數字）";
  return null;
}

export function ChangePasswordCard() {
  const change = useChangePassword();
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [done, setDone] = useState(false);

  const policyError = next ? newPasswordError(next) : null;
  const mismatch = confirm.length > 0 && next !== confirm;
  const canSubmit =
    current.length > 0 && next.length > 0 && confirm.length > 0 && !policyError && !mismatch && !change.isPending;

  function submit() {
    if (!canSubmit) return;
    setDone(false);
    change.mutate(
      { currentPassword: current, newPassword: next },
      {
        onSuccess: () => {
          setDone(true);
          setCurrent("");
          setNext("");
          setConfirm("");
        },
      },
    );
  }

  const serverError =
    change.error instanceof ApiError
      ? change.error.details?.find((d) => d.field === "newPassword")?.message ?? change.error.message
      : null;

  return (
    <Card className="border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden card-hover-effect">
      <CardHeader className="pb-3">
        <CardTitle className="text-lg font-bold flex items-center gap-2">
          <KeyRound className="size-4.5 text-amber-500" />
          修改密碼
        </CardTitle>
        <CardDescription className="text-xs">
          為了安全，變更密碼後會自動登出其他所有裝置（這台裝置會保持登入）。
        </CardDescription>
      </CardHeader>
      <CardContent className="px-6 pb-6">
        <form
          className="grid gap-4 max-w-md"
          onSubmit={(e) => {
            e.preventDefault();
            submit();
          }}
        >
          <div className="grid gap-1.5">
            <Label htmlFor="current-password" className="text-xs font-semibold text-slate-500">目前密碼</Label>
            <Input
              id="current-password"
              type="password"
              autoComplete="current-password"
              value={current}
              onChange={(e) => setCurrent(e.target.value)}
              className="rounded-2xl"
            />
          </div>

          <div className="grid gap-1.5">
            <Label htmlFor="new-password" className="text-xs font-semibold text-slate-500">新密碼</Label>
            <Input
              id="new-password"
              type="password"
              autoComplete="new-password"
              value={next}
              onChange={(e) => setNext(e.target.value)}
              className="rounded-2xl"
            />
            <p className={`text-[10px] ${policyError ? "text-rose-500" : "text-muted-foreground"}`}>
              {policyError ?? "至少 8 碼，需同時包含大寫與小寫英文字母。"}
            </p>
          </div>

          <div className="grid gap-1.5">
            <Label htmlFor="confirm-password" className="text-xs font-semibold text-slate-500">確認新密碼</Label>
            <Input
              id="confirm-password"
              type="password"
              autoComplete="new-password"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              className="rounded-2xl"
            />
            {mismatch && <p className="text-[10px] text-rose-500">兩次輸入的新密碼不一致</p>}
          </div>

          {done && (
            <Alert className="rounded-2xl border-emerald-500/20 bg-emerald-500/5 text-emerald-700 dark:text-emerald-400">
              <Check className="size-4" />
              <AlertTitle className="text-xs font-bold">密碼已更新</AlertTitle>
              <AlertDescription className="text-[11px] opacity-90">其他裝置已被登出。</AlertDescription>
            </Alert>
          )}

          {serverError && !done && (
            <Alert variant="destructive" className="rounded-2xl border-rose-500/20 bg-rose-500/5 text-rose-600 dark:text-rose-400">
              <AlertTitle className="text-xs font-bold">無法更新密碼</AlertTitle>
              <AlertDescription className="text-[11px] opacity-90">{serverError}</AlertDescription>
            </Alert>
          )}

          <Button
            type="submit"
            disabled={!canSubmit}
            className="rounded-2xl py-5 px-6 gap-1.5 bg-gradient-to-r from-amber-500 to-orange-500 hover:from-amber-400 hover:to-orange-400 text-white font-semibold text-sm w-full sm:w-auto"
          >
            {change.isPending ? (
              <>
                <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                更新中…
              </>
            ) : (
              <>
                <KeyRound className="size-4" />
                更新密碼
              </>
            )}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}

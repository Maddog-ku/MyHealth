import { FormEvent, useState } from "react";
import { AlertCircle, Sparkles, Heart, ArrowRight, Eye, EyeOff } from "lucide-react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { saveAuth, getAccessToken, ApiError } from "@/api/client";
import { useLogin, useRegister } from "@/hooks/useAuth";
import { useQueryClient } from "@tanstack/react-query";
import { qk } from "@/lib/queryClient";
import type { Gender } from "@/types/api";

export function LoginPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const login = useLogin();
  const register = useRegister();
  const [tab, setTab] = useState("login");
  const [registerError, setRegisterError] = useState<string | null>(null);
  const from = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname ?? "/";

  if (getAccessToken()) {
    return <Navigate to={from} replace />;
  }

  async function handleLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    try {
      await login.mutateAsync({
        email: String(form.get("email")).trim(),
        password: String(form.get("password")),
      });
      await qc.invalidateQueries({ queryKey: qk.me });
      navigate(from, { replace: true });
    } catch {
      // mutation error is rendered below
    }
  }

  async function handleRegister(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const password = String(form.get("password"));
    if (password !== String(form.get("confirmPassword"))) {
      setRegisterError("兩次輸入的密碼不一致，請重新確認。");
      return;
    }
    setRegisterError(null);
    const payload = {
      email: String(form.get("email")).trim(),
      password,
      name: String(form.get("name")).trim(),
      gender: String(form.get("gender")) as Gender,
      heightCm: Number(form.get("heightCm")),
      weightKg: Number(form.get("weightKg")),
    };
    try {
      await register.mutateAsync(payload);
      const tokens = await login.mutateAsync({ email: payload.email, password: payload.password });
      saveAuth(tokens);
      await qc.invalidateQueries({ queryKey: qk.me });
      navigate("/", { replace: true });
    } catch {
      // rendered below
    }
  }

  const pendingError = registerError ?? login.error ?? register.error;

  return (
    <main className="relative grid min-h-screen place-items-center overflow-hidden bg-gradient-to-tr from-slate-50 via-slate-100 to-emerald-50/30 px-4 py-16 dark:from-[#070b13] dark:via-[#0c1322] dark:to-[#08151f]">
      {/* Decorative ambient blobs for aesthetics */}
      <div className="absolute top-1/4 left-1/4 -z-10 size-72 rounded-full bg-emerald-300/10 blur-3xl dark:bg-emerald-500/5 animate-pulse" style={{ animationDuration: '8s' }}></div>
      <div className="absolute bottom-1/4 right-1/4 -z-10 size-80 rounded-full bg-teal-300/10 blur-3xl dark:bg-teal-500/5 animate-pulse" style={{ animationDuration: '10s' }}></div>

      <div className="w-full max-w-[440px] space-y-6 animate-fade-in z-10">
        {/* App Logo / Brand */}
        <div className="flex flex-col items-center text-center space-y-2">
          <img src="/logo.svg" alt="" className="size-16 drop-shadow-md" />
          <h1 className="text-3xl font-extrabold tracking-tight bg-gradient-to-r from-emerald-600 to-teal-500 bg-clip-text text-transparent dark:from-emerald-400 dark:to-teal-300">
            MyHealth
          </h1>
          <p className="text-sm text-muted-foreground max-w-xs flex items-center justify-center gap-1.5 font-medium">
            <Heart className="size-3.5 text-emerald-500 fill-emerald-500/20" />
            AI 賦能的個人健康與飲食管家
          </p>
        </div>

        {/* Auth Glass Card */}
        <Card className="glass-panel border border-white/40 dark:border-white/5 shadow-2xl rounded-3xl overflow-hidden accent-glow">
          <CardHeader className="pb-3 text-center">
            <CardTitle className="text-xl font-bold">歡迎回來</CardTitle>
            <CardDescription className="text-xs">登入或註冊以追蹤你的健身、飲食與AI健康規劃</CardDescription>
          </CardHeader>
          <CardContent className="px-6 pb-6">
            <Tabs value={tab} onValueChange={(v) => { setRegisterError(null); setTab(v); }} className="w-full">
              <TabsList className="grid w-full grid-cols-2 p-1 bg-slate-100/80 dark:bg-slate-900/60 rounded-2xl mb-5">
                <TabsTrigger
                  value="login"
                  className="rounded-xl py-2 text-xs font-semibold data-[state=active]:bg-white dark:data-[state=active]:bg-slate-800 data-[state=active]:text-emerald-600 dark:data-[state=active]:text-emerald-400 data-[state=active]:shadow-sm transition-all duration-300"
                >
                  會員登入
                </TabsTrigger>
                <TabsTrigger
                  value="register"
                  className="rounded-xl py-2 text-xs font-semibold data-[state=active]:bg-white dark:data-[state=active]:bg-slate-800 data-[state=active]:text-emerald-600 dark:data-[state=active]:text-emerald-400 data-[state=active]:shadow-sm transition-all duration-300"
                >
                  免費註冊
                </TabsTrigger>
              </TabsList>

              {/* Login Form */}
              <TabsContent value="login" className="outline-none mt-0">
                <form onSubmit={handleLogin} className="grid gap-4">
                  <Field name="email" label="電子郵件" type="email" required maxLength={254} autoComplete="email" placeholder="demo@example.com" />
                  {/* Login intentionally does not enforce the register-time policy regex:
                      existing accounts may have been registered under an older policy and
                      must still be able to type their real password. Server BCrypt check
                      is the source of truth. */}
                  <PasswordField name="password" label="密碼" required maxLength={128} autoComplete="current-password" placeholder="請輸入密碼" />
                  
                  {pendingError && <ErrorBox error={pendingError} />}
                  
                  <Button
                    type="submit"
                    disabled={login.isPending}
                    className="w-full mt-2 py-6 rounded-2xl bg-gradient-to-r from-emerald-600 to-teal-500 hover:from-emerald-500 hover:to-teal-400 text-white font-semibold shadow-md shadow-emerald-500/10 hover:shadow-lg hover:scale-[1.01] transition-all-smooth"
                  >
                    {login.isPending ? (
                      <span className="flex items-center gap-2">
                        <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                        安全登入中...
                      </span>
                    ) : (
                      <span className="flex items-center gap-1.5">
                        開始使用 <ArrowRight className="size-4" />
                      </span>
                    )}
                  </Button>
                </form>
              </TabsContent>

              {/* Register Form */}
              <TabsContent value="register" className="outline-none mt-0">
                <form onSubmit={handleRegister} className="grid gap-4.5">
                  <Field name="name" label="姓名 / 暱稱" required maxLength={100} pattern="[\p{L}\p{N}\s._-]+" autoComplete="name" placeholder="如何稱呼您" />
                  <Field name="email" label="電子郵件" type="email" required maxLength={254} autoComplete="email" placeholder="demo@example.com" />
                  <PasswordField name="password" label="設定密碼" minLength={8} maxLength={128} pattern="(?=.*[A-Z])(?=.*[a-z])[A-Za-z0-9]+" title="密碼需至少 8 字，且只能使用半形英文與數字，並至少包含 1 個大寫英文與 1 個小寫英文。" required autoComplete="new-password" placeholder="至少 8 字，含大小寫英文" />
                  <PasswordField name="confirmPassword" label="確認密碼" minLength={8} maxLength={128} required autoComplete="new-password" placeholder="再次輸入相同密碼" />
                  
                  <div className="grid gap-1.5">
                    <Label htmlFor="gender" className="text-xs font-semibold text-slate-600 dark:text-slate-400 px-1">生理性別</Label>
                    <Select name="gender" defaultValue="other">
                      <SelectTrigger id="gender" className="rounded-2xl border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 py-5">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent className="rounded-2xl border-slate-200/80 dark:border-slate-800">
                        <SelectItem value="male" className="rounded-xl">生理男</SelectItem>
                        <SelectItem value="female" className="rounded-xl">生理女</SelectItem>
                        <SelectItem value="other" className="rounded-xl">其他 / 不透露</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                  
                  <div className="grid grid-cols-2 gap-3.5">
                    <Field name="heightCm" label="身高 (cm)" type="number" min={50} max={250} step="0.1" required placeholder="170" />
                    <Field name="weightKg" label="體重 (kg)" type="number" min={20} max={300} step="0.1" required placeholder="65" />
                  </div>

                  <label className="flex items-start gap-2.5 p-3 rounded-2xl bg-amber-500/5 border border-amber-500/10 text-[11px] text-amber-700 dark:text-amber-300 leading-normal select-none">
                    <input type="checkbox" required className="mt-0.5 rounded border-amber-300 text-amber-600 focus:ring-amber-500" />
                    <span>我已理解 AI 估算與運動建議僅供一般健康參考，不應視作專業醫療或營養診斷處方。</span>
                  </label>

                  {pendingError && <ErrorBox error={pendingError} />}

                  <Button
                    type="submit"
                    disabled={register.isPending || login.isPending}
                    className="w-full mt-2 py-6 rounded-2xl bg-gradient-to-r from-emerald-600 to-teal-500 hover:from-emerald-500 hover:to-teal-400 text-white font-semibold shadow-md shadow-emerald-500/10 hover:shadow-lg hover:scale-[1.01] transition-all-smooth"
                  >
                    {register.isPending || login.isPending ? (
                      <span className="flex items-center gap-2">
                        <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                        帳號建立中...
                      </span>
                    ) : (
                      <span className="flex items-center gap-1.5">
                        註冊並建立帳號 <ArrowRight className="size-4" />
                      </span>
                    )}
                  </Button>
                </form>
              </TabsContent>
            </Tabs>
          </CardContent>
        </Card>

        {/* Bottom notes */}
        <div className="flex flex-col items-center space-y-1 text-center text-[11px] text-muted-foreground">
          <p className="flex items-center gap-1">
            <Sparkles className="size-3 text-emerald-500" />
            資料儲存於本地 PostgreSQL，未上傳至任何雲端第三方，隱私安全無虞。
          </p>
        </div>
      </div>
    </main>
  );
}

function Field({
  name,
  label,
  ...props
}: { name: string; label: string } & React.InputHTMLAttributes<HTMLInputElement>) {
  const id = `field-${name}`;
  return (
    <div className="grid gap-1.5">
      <Label htmlFor={id} className="text-xs font-semibold text-slate-600 dark:text-slate-400 px-1">{label}</Label>
      <Input
        id={id}
        name={name}
        {...props}
        className="rounded-2xl border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 py-5 focus-visible:ring-emerald-500 focus-visible:border-emerald-500/40 transition-all duration-300"
      />
    </div>
  );
}

function PasswordField({
  name,
  label,
  ...props
}: { name: string; label: string } & React.InputHTMLAttributes<HTMLInputElement>) {
  const [show, setShow] = useState(false);
  const id = `field-${name}`;
  return (
    <div className="grid gap-1.5">
      <Label htmlFor={id} className="text-xs font-semibold text-slate-600 dark:text-slate-400 px-1">{label}</Label>
      <div className="relative">
        <Input
          id={id}
          name={name}
          {...props}
          type={show ? "text" : "password"}
          className="rounded-2xl border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 py-5 pr-11 focus-visible:ring-emerald-500 focus-visible:border-emerald-500/40 transition-all duration-300"
        />
        <button
          type="button"
          onClick={() => setShow((v) => !v)}
          aria-label={show ? "隱藏密碼" : "顯示密碼"}
          aria-pressed={show}
          tabIndex={-1}
          className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-emerald-600 dark:hover:text-emerald-400 transition-colors"
        >
          {show ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
        </button>
      </div>
    </div>
  );
}

function ErrorBox({ error }: { error: unknown }) {
  const message =
    typeof error === "string"
      ? error
      : error instanceof ApiError ? error.message : error instanceof Error ? error.message : "操作失敗，請稍後重試";
  return (
    <Alert variant="destructive" className="rounded-2xl py-3 border-destructive/20 bg-destructive/5 text-destructive-foreground">
      <AlertCircle className="size-4 text-destructive" />
      <AlertTitle className="text-xs font-bold leading-none mb-1">無法完成請求</AlertTitle>
      <AlertDescription className="text-[11px] leading-relaxed opacity-90">{message}</AlertDescription>
    </Alert>
  );
}

import { FormEvent, useState } from "react";
import { Activity, AlertCircle } from "lucide-react";
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
  const from = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname ?? "/";

  if (getAccessToken()) {
    return <Navigate to={from} replace />;
  }

  async function handleLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    try {
      await login.mutateAsync({
        email: String(form.get("email")),
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
    const payload = {
      email: String(form.get("email")),
      password: String(form.get("password")),
      name: String(form.get("name")),
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

  const pendingError = login.error ?? register.error;

  return (
    <main className="grid min-h-full place-items-center px-4 py-10">
      <div className="w-full max-w-md space-y-6 animate-fade-in">
        <div className="flex items-center justify-center gap-3 text-2xl font-bold">
          <Activity className="size-7 text-primary" aria-hidden />
          MyHealth
        </div>

        <Card>
          <CardHeader className="pb-2">
            <CardTitle>歡迎</CardTitle>
            <CardDescription>登入或建立帳號開始追蹤你的健身與飲食</CardDescription>
          </CardHeader>
          <CardContent>
            <Tabs value={tab} onValueChange={setTab}>
              <TabsList className="grid w-full grid-cols-2">
                <TabsTrigger value="login">登入</TabsTrigger>
                <TabsTrigger value="register">註冊</TabsTrigger>
              </TabsList>

              <TabsContent value="login">
                <form onSubmit={handleLogin} className="grid gap-4 pt-4">
                  <Field name="email" label="Email" type="email" required defaultValue="demo@example.com" />
                  <Field name="password" label="密碼" type="password" required defaultValue="secret123" />
                  {pendingError && <ErrorBox error={pendingError} />}
                  <Button type="submit" disabled={login.isPending}>
                    {login.isPending ? "登入中…" : "登入"}
                  </Button>
                </form>
              </TabsContent>

              <TabsContent value="register">
                <form onSubmit={handleRegister} className="grid gap-4 pt-4">
                  <Field name="name" label="姓名" required defaultValue="Demo" />
                  <Field name="email" label="Email" type="email" required defaultValue="demo@example.com" />
                  <Field name="password" label="密碼（至少 8 字）" type="password" minLength={8} required defaultValue="secret123" />
                  <div className="grid gap-2">
                    <Label htmlFor="gender">性別</Label>
                    <Select name="gender" defaultValue="other">
                      <SelectTrigger id="gender"><SelectValue /></SelectTrigger>
                      <SelectContent>
                        <SelectItem value="male">男</SelectItem>
                        <SelectItem value="female">女</SelectItem>
                        <SelectItem value="other">其他</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <Field name="heightCm" label="身高 (cm)" type="number" min={50} max={250} required defaultValue="170" />
                    <Field name="weightKg" label="體重 (kg)" type="number" min={20} max={300} step="0.1" required defaultValue="65" />
                  </div>
                  <label className="flex items-start gap-2 text-xs text-muted-foreground">
                    <input type="checkbox" required className="mt-0.5" />
                    <span>我理解 AI 建議僅供一般健康參考，非醫療或營養處方。</span>
                  </label>
                  {pendingError && <ErrorBox error={pendingError} />}
                  <Button type="submit" disabled={register.isPending || login.isPending}>
                    {register.isPending || login.isPending ? "建立中…" : "建立帳號"}
                  </Button>
                </form>
              </TabsContent>
            </Tabs>
          </CardContent>
        </Card>

        <p className="text-center text-xs text-muted-foreground">
          資料儲存於你的本地 MyHealth 後端，未上傳第三方雲端。
        </p>
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
    <div className="grid gap-2">
      <Label htmlFor={id}>{label}</Label>
      <Input id={id} name={name} {...props} />
    </div>
  );
}

function ErrorBox({ error }: { error: unknown }) {
  const message = error instanceof ApiError ? error.message : error instanceof Error ? error.message : "操作失敗";
  return (
    <Alert variant="destructive">
      <AlertCircle className="size-4" />
      <AlertTitle>無法完成</AlertTitle>
      <AlertDescription>{message}</AlertDescription>
    </Alert>
  );
}

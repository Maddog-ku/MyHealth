import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { AlertTriangle, Check, Mail, Trash2, UserRound } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { ApiError } from "@/api/client";
import { useDeleteAccount, useMe, useUpdateAccount } from "@/hooks/useAuth";
import { useI18n } from "@/i18n/i18n";
import { ChangePasswordCard } from "@/components/ChangePasswordCard";
import { SessionsCard } from "@/components/SessionsCard";
import { DataExportCard } from "@/components/DataExportCard";

/**
 * User account hub, opened from the sidebar profile card. Gathers everything tied to the
 * account itself: editable display name, password, login devices/security, data & privacy
 * (export + deletion) — kept out of the app-preferences "Settings" page.
 */
export function AccountPage() {
  const { data: user } = useMe();
  const updateAccount = useUpdateAccount();
  const del = useDeleteAccount();
  const navigate = useNavigate();
  const { t } = useI18n();

  const [name, setName] = useState("");
  const [saved, setSaved] = useState(false);
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  useEffect(() => {
    if (user?.name) setName(user.name);
  }, [user?.name]);

  const dirty = user != null && name.trim().length > 0 && name.trim() !== user.name;

  async function saveName() {
    if (!dirty) return;
    try {
      await updateAccount.mutateAsync(name.trim());
      setSaved(true);
      setTimeout(() => setSaved(false), 2500);
    } catch {
      // error surfaced below
    }
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
      {/* Account info */}
      <Card className="border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden card-hover-effect">
        <CardHeader className="pb-3">
          <CardTitle className="text-lg font-bold flex items-center gap-2">
            <UserRound className="size-4.5 text-emerald-500" />
            帳戶資訊
          </CardTitle>
          <CardDescription className="text-xs">更新你的顯示名稱；Email 為登入帳號，無法在此變更。</CardDescription>
        </CardHeader>
        <CardContent className="px-6 pb-6 space-y-4">
          <div className="flex items-center gap-3">
            <div className="flex size-12 items-center justify-center rounded-full bg-emerald-100 dark:bg-emerald-950/50 text-emerald-600 dark:text-emerald-400">
              <UserRound className="size-6" />
            </div>
            <div className="flex items-center gap-1.5 text-sm text-muted-foreground min-w-0">
              <Mail className="size-4 shrink-0" />
              <span className="truncate">{user?.email}</span>
            </div>
          </div>

          <div className="grid gap-1.5">
            <Label htmlFor="account-name" className="text-xs font-semibold text-slate-500 px-1">顯示名稱</Label>
            <div className="flex flex-col sm:flex-row gap-2">
              <Input
                id="account-name"
                value={name}
                maxLength={60}
                onChange={(e) => {
                  setName(e.target.value);
                  setSaved(false);
                }}
                placeholder="你的名稱"
                className="rounded-2xl border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 py-5 focus-visible:ring-emerald-500"
              />
              <Button
                type="button"
                onClick={saveName}
                disabled={!dirty || updateAccount.isPending}
                className="rounded-2xl py-5 px-6 bg-gradient-to-r from-emerald-600 to-teal-500 hover:from-emerald-500 hover:to-teal-400 text-white font-semibold shrink-0"
              >
                {updateAccount.isPending ? "儲存中…" : saved ? <><Check className="size-4 mr-1.5" />已儲存</> : "儲存"}
              </Button>
            </div>
            {updateAccount.error instanceof ApiError && (
              <p className="text-[11px] text-rose-500 px-1">{updateAccount.error.message}</p>
            )}
          </div>
        </CardContent>
      </Card>

      {/* Security: password */}
      <ChangePasswordCard />

      {/* Security: login devices */}
      <SessionsCard />

      {/* Data & privacy: export */}
      <DataExportCard />

      {/* Data & privacy: account deletion */}
      <Card className="border border-rose-500/20 dark:border-rose-500/15 bg-rose-500/[0.03] dark:bg-rose-950/10 rounded-3xl overflow-hidden">
        <CardHeader className="pb-3">
          <CardTitle className="text-lg font-bold flex items-center gap-2 text-rose-600 dark:text-rose-400">
            <AlertTriangle className="size-4.5" />
            {t("settings.danger.title")}
          </CardTitle>
          <CardDescription className="text-xs">{t("settings.danger.desc")}</CardDescription>
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
              <p className="text-xs font-semibold text-rose-700 dark:text-rose-300">{t("settings.danger.confirmQuestion")}</p>
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

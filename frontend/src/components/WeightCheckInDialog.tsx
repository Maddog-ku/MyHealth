import { useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Activity, ArrowLeft, Check, Ruler, Scale } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiError } from "@/api/client";
import { useUpdateProfile } from "@/hooks/useAuth";
import type { Profile } from "@/types/api";

type Step = "input" | "confirm";

export function WeightCheckInDialog({
  open,
  onClose,
  profile,
}: {
  open: boolean;
  onClose: () => void;
  profile: Profile;
}) {
  const update = useUpdateProfile();
  const qc = useQueryClient();
  // Inputs start EMPTY (placeholder shows the current value). An empty field
  // means "leave unchanged"; the user may fill either, both, or neither.
  const [step, setStep] = useState<Step>("input");
  const [weight, setWeight] = useState("");
  const [height, setHeight] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      setStep("input");
      setWeight("");
      setHeight("");
      setError(null);
    }
  }, [open]);

  const weightEntered = weight.trim() !== "";
  const heightEntered = height.trim() !== "";
  const finalWeight = weightEntered ? Number(weight) : Number(profile.weightKg);
  const finalHeight = heightEntered ? Number(height) : Number(profile.heightCm);

  function proceedToConfirm() {
    if (weightEntered && (!Number.isFinite(finalWeight) || finalWeight < 20 || finalWeight > 300)) {
      setError("體重請介於 20 ~ 300 kg。");
      return;
    }
    if (heightEntered && (!Number.isFinite(finalHeight) || finalHeight < 50 || finalHeight > 250)) {
      setError("身高請介於 50 ~ 250 cm。");
      return;
    }
    setError(null);
    setStep("confirm");
  }

  async function applyChanges() {
    // Nothing was entered → genuinely no change; just resolve without a write.
    if (!weightEntered && !heightEntered) {
      onClose();
      return;
    }
    try {
      await update.mutateAsync({ ...profile, weightKg: finalWeight, heightCm: finalHeight });
      // Profile update appends a body_measurement on the backend; refresh the
      // dashboard's daily + range stats so the weight trend updates immediately.
      await qc.invalidateQueries({ queryKey: ["stats"] });
      onClose();
    } catch (e) {
      // Surface the failure and send the user back to the input step to retry.
      setError(e instanceof ApiError ? e.message : "更新失敗，請稍後再試。");
      setStep("input");
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!next) onClose(); }}>
      <DialogContent>
        {step === "input" ? (
          <>
            <DialogHeader>
              <div className="flex size-11 items-center justify-center rounded-2xl bg-gradient-to-tr from-emerald-500/15 to-teal-500/10 text-emerald-600 dark:text-emerald-400 mb-1">
                <Activity className="size-5" />
              </div>
              <DialogTitle>今日身體狀況記錄</DialogTitle>
              <DialogDescription>
                填寫想更新的身高或體重即可（可擇一、都填，或都留空代表不變更）。按「確定」後會再請您確認一次。
              </DialogDescription>
            </DialogHeader>

            <div className="grid gap-4 sm:grid-cols-2 pt-1">
              <div className="grid gap-1.5">
                <Label htmlFor="checkin-weight" className="text-xs font-semibold text-slate-500 flex items-center gap-1.5">
                  <Scale className="size-3.5 text-emerald-500" />
                  體重 (kg)
                </Label>
                <Input
                  id="checkin-weight"
                  type="number"
                  min={20}
                  max={300}
                  step="0.1"
                  inputMode="decimal"
                  value={weight}
                  placeholder={`目前 ${profile.weightKg}`}
                  onChange={(e) => { setWeight(e.target.value); setError(null); }}
                  className="rounded-2xl border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 py-5 focus-visible:ring-emerald-500"
                />
              </div>
              <div className="grid gap-1.5">
                <Label htmlFor="checkin-height" className="text-xs font-semibold text-slate-500 flex items-center gap-1.5">
                  <Ruler className="size-3.5 text-emerald-500" />
                  身高 (cm)
                </Label>
                <Input
                  id="checkin-height"
                  type="number"
                  min={50}
                  max={250}
                  step="0.1"
                  inputMode="decimal"
                  value={height}
                  placeholder={`目前 ${profile.heightCm}`}
                  onChange={(e) => { setHeight(e.target.value); setError(null); }}
                  className="rounded-2xl border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 py-5 focus-visible:ring-emerald-500"
                />
              </div>
            </div>

            {error && <p className="text-[11px] font-medium text-rose-600 dark:text-rose-400 px-1">{error}</p>}

            <DialogFooter className="pt-1">
              <Button
                type="button"
                onClick={proceedToConfirm}
                className="rounded-2xl text-sm gap-1.5 bg-gradient-to-r from-emerald-600 to-teal-500 hover:from-emerald-500 hover:to-teal-400 text-white font-semibold shadow-sm shadow-emerald-500/10"
              >
                確定
              </Button>
            </DialogFooter>
          </>
        ) : (
          <>
            <DialogHeader>
              <div className="flex size-11 items-center justify-center rounded-2xl bg-gradient-to-tr from-emerald-500/15 to-teal-500/10 text-emerald-600 dark:text-emerald-400 mb-1">
                <Check className="size-5" />
              </div>
              <DialogTitle>確認您的身體數據</DialogTitle>
              <DialogDescription>請確認以下資料無誤後再送出；若需修改，可返回上一步。</DialogDescription>
            </DialogHeader>

            <div className="grid gap-2.5 pt-1">
              <ConfirmRow icon={<Ruler className="size-4 text-emerald-500" />} label="身高" value={`${finalHeight} cm`} unchanged={!heightEntered} />
              <ConfirmRow icon={<Scale className="size-4 text-emerald-500" />} label="體重" value={`${finalWeight} kg`} unchanged={!weightEntered} />
            </div>

            {!weightEntered && !heightEntered && (
              <p className="text-[11px] text-muted-foreground px-1">您未變更任何數據，按「確認變更」將直接關閉。</p>
            )}

            <DialogFooter className="pt-1">
              <Button
                type="button"
                variant="ghost"
                onClick={() => setStep("input")}
                disabled={update.isPending}
                className="rounded-2xl text-sm gap-1.5 text-muted-foreground"
              >
                <ArrowLeft className="size-4" />
                上一步
              </Button>
              <Button
                type="button"
                onClick={applyChanges}
                disabled={update.isPending}
                className="rounded-2xl text-sm gap-1.5 bg-gradient-to-r from-emerald-600 to-teal-500 hover:from-emerald-500 hover:to-teal-400 text-white font-semibold shadow-sm shadow-emerald-500/10"
              >
                {update.isPending ? (
                  <>
                    <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                    儲存中...
                  </>
                ) : (
                  "確認變更"
                )}
              </Button>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}

function ConfirmRow({
  icon,
  label,
  value,
  unchanged,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  unchanged: boolean;
}) {
  return (
    <div className="flex items-center justify-between rounded-2xl border border-slate-100 dark:border-slate-900/50 bg-slate-50/50 dark:bg-slate-900/20 px-4 py-3">
      <span className="flex items-center gap-2 text-xs font-semibold text-muted-foreground">
        {icon}
        {label}
      </span>
      <span className="flex items-baseline gap-1.5">
        <span className="text-base font-extrabold text-slate-800 dark:text-slate-100">{value}</span>
        {unchanged && <span className="text-[11px] font-medium text-muted-foreground">（未變更）</span>}
      </span>
    </div>
  );
}

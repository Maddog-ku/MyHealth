import { useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Scale } from "lucide-react";
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

/**
 * Lightweight, weight-only quick edit triggered from the dashboard's "目前體重"
 * metric. Saving PUTs the profile (which appends a body_measurement on the
 * backend) and invalidates stats so today's value + trend update immediately.
 */
export function QuickWeightDialog({
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
  const [weight, setWeight] = useState(String(profile.weightKg));
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      setWeight(String(profile.weightKg));
      setError(null);
    }
  }, [open, profile.weightKg]);

  async function save() {
    const w = Number(weight);
    if (!Number.isFinite(w) || w < 20 || w > 300) {
      setError("體重請介於 20 ~ 300 kg。");
      return;
    }
    try {
      await update.mutateAsync({ ...profile, weightKg: w });
      await qc.invalidateQueries({ queryKey: ["stats"] });
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "更新失敗，請稍後再試。");
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!next) onClose(); }}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <div className="flex size-11 items-center justify-center rounded-2xl bg-gradient-to-tr from-indigo-500/15 to-sky-500/10 text-indigo-600 dark:text-indigo-400 mb-1">
            <Scale className="size-5" />
          </div>
          <DialogTitle>快速更新體重</DialogTitle>
          <DialogDescription>輸入目前體重，將立即記錄為今日數據並更新趨勢圖。</DialogDescription>
        </DialogHeader>

        <div className="grid gap-1.5 pt-1">
          <Label htmlFor="quick-weight" className="text-xs font-semibold text-slate-500 flex items-center gap-1.5">
            <Scale className="size-3.5 text-indigo-500" />
            體重 (kg)
          </Label>
          <Input
            id="quick-weight"
            type="number"
            min={20}
            max={300}
            step="0.1"
            autoFocus
            inputMode="decimal"
            value={weight}
            disabled={update.isPending}
            onChange={(e) => { setWeight(e.target.value); setError(null); }}
            onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); save(); } }}
            className="rounded-2xl border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 py-5 text-center text-lg font-bold focus-visible:ring-indigo-500"
          />
        </div>

        {error && <p className="text-[11px] font-medium text-rose-600 dark:text-rose-400 px-1">{error}</p>}

        <DialogFooter className="pt-1">
          <Button
            type="button"
            variant="ghost"
            onClick={onClose}
            disabled={update.isPending}
            className="rounded-2xl text-sm text-muted-foreground"
          >
            取消
          </Button>
          <Button
            type="button"
            onClick={save}
            disabled={update.isPending}
            className="rounded-2xl text-sm gap-1.5 bg-gradient-to-r from-indigo-600 to-sky-500 hover:from-indigo-500 hover:to-sky-400 text-white font-semibold shadow-sm shadow-indigo-500/10"
          >
            {update.isPending ? (
              <>
                <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                儲存中...
              </>
            ) : (
              "儲存"
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

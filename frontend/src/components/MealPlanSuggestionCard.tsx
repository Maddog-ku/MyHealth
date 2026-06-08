import { Lightbulb, Plus } from "lucide-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { mealSlotLabel } from "@/lib/mealSlots";
import type { FoodSuggestion, FoodSuggestions, HealthPlan } from "@/types/api";

export function MealPlanSuggestionCard({
  plan,
  loading,
  selectedSlot,
  suggestions,
  onPickFood,
}: {
  plan: HealthPlan | null;
  loading: boolean;
  selectedSlot: string;
  suggestions?: FoodSuggestions | null;
  /** Called when a recommended food chip is clicked, to prefill the add-meal form. */
  onPickFood?: (food: FoodSuggestion) => void;
}) {
  if (loading) {
    return <Skeleton className="h-28 w-full rounded-3xl" />;
  }
  if (!plan) {
    return null;
  }

  const protein = plan.nutrition.macros.find((macro) => macro.name === "protein");
  const proteinGap = protein ? Math.max(0, protein.targetG - protein.consumedG) : 0;
  const remaining = plan.nutrition.remainingKcal;
  const targetKcal = plan.nutrition.over ? 250 : Math.max(250, Math.min(700, remaining));
  const proteinTarget = Math.max(20, Math.min(45, proteinGap || Math.round((targetKcal * 0.25) / 4)));

  const headline = plan.nutrition.over
    ? "今日熱量已超過預算"
    : protein && protein.pct < 70
      ? "下一餐優先補蛋白質"
      : remaining < 400
        ? "下一餐保持輕量"
        : "下一餐維持均衡";
  const detail = plan.nutrition.over
    ? "選擇低油、高纖、足量蛋白的組合，避免再堆高熱量。"
    : protein && protein.pct < 70
      ? `目前蛋白質約達成 ${protein.pct}%，建議下一餐抓 ${proteinTarget}g 蛋白質。`
      : remaining < 400
        ? "今日可用熱量不多，下一餐以蔬菜、湯品與瘦蛋白為主。"
        : `今日還有約 ${remaining} kcal，可安排一份主食、一份蛋白質與蔬菜。`;

  return (
    <Card className="border border-emerald-500/10 bg-emerald-500/5 dark:bg-emerald-950/10 backdrop-blur-xl shadow-xl shadow-emerald-100/40 dark:shadow-none rounded-3xl overflow-hidden">
      <CardHeader className="pb-3">
        <CardTitle className="text-lg font-bold flex items-center gap-2">
          <Lightbulb className="size-4.5 text-emerald-500" />
          下一餐建議
        </CardTitle>
        <CardDescription className="text-xs">
          依今日健康計畫與目前選擇的{mealSlotLabel(selectedSlot)}時段調整
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-3 px-6 pb-6 md:grid-cols-[1fr_auto] md:items-center">
        <div>
          <p className="text-sm font-extrabold text-slate-800 dark:text-slate-100">{headline}</p>
          <p className="mt-1 text-xs leading-relaxed text-slate-600 dark:text-slate-300">{detail}</p>
        </div>
        <div className="grid grid-cols-2 gap-2 text-center sm:flex">
          <SuggestionPill label="建議熱量" value={`${targetKcal}`} unit="kcal" warn={plan.nutrition.over} />
          <SuggestionPill label="蛋白質" value={`${proteinTarget}`} unit="g" />
        </div>
        {suggestions?.items && suggestions.items.length > 0 && (
          <div className="md:col-span-2 space-y-1.5">
            <p className="text-[11px] font-semibold text-muted-foreground">
              食物資料庫推薦{onPickFood ? "（點選帶入紀錄）" : ""}
            </p>
            <div className="flex flex-wrap gap-1.5">
              {suggestions.items.map((food) => {
                const content = (
                  <>
                    {onPickFood && <Plus className="size-3 text-emerald-500 shrink-0" />}
                    <span className="font-semibold text-slate-700 dark:text-slate-200">{food.name}</span>
                    <span className="text-muted-foreground">
                      {food.grams}g · {food.kcal}kcal · 蛋白 {food.protein}g
                    </span>
                  </>
                );
                const className =
                  "inline-flex items-baseline gap-1 rounded-full border border-emerald-500/20 bg-white/70 dark:bg-slate-950/30 px-2.5 py-1 text-[11px]";
                return onPickFood ? (
                  <button
                    key={food.id}
                    type="button"
                    title={`${food.reason}（點選帶入新增餐點）`}
                    onClick={() => onPickFood(food)}
                    className={`${className} items-center transition-colors hover:border-emerald-500/50 hover:bg-emerald-500/10`}
                  >
                    {content}
                  </button>
                ) : (
                  <span key={food.id} title={food.reason} className={className}>
                    {content}
                  </span>
                );
              })}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function SuggestionPill({ label, value, unit, warn }: { label: string; value: string; unit: string; warn?: boolean }) {
  return (
    <div className={`rounded-2xl border px-4 py-3 min-w-28 ${
      warn
        ? "border-amber-500/20 bg-amber-500/10"
        : "border-emerald-500/15 bg-white/60 dark:bg-slate-950/30"
    }`}>
      <p className="text-[10px] font-semibold text-muted-foreground">{label}</p>
      <p className="mt-0.5 flex items-baseline justify-center gap-1">
        <span className={`text-xl font-extrabold ${warn ? "text-amber-600 dark:text-amber-400" : "text-emerald-600 dark:text-emerald-400"}`}>{value}</span>
        <span className="text-[9px] font-bold text-muted-foreground">{unit}</span>
      </p>
    </div>
  );
}

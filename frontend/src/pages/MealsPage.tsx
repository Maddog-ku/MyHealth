import { useMemo, useState } from "react";
import { Apple, Pencil, Save, Sparkles, Star, Trash2, UtensilsCrossed } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { AddMealForm, type AddMealPreviewRequest } from "@/components/AddMealForm";
import { AiGenerationPanel } from "@/components/AiGenerationPanel";
import {
  EditableFoodRows,
  cleanFoodRows,
  emptyFoodItem,
  summarizeItems,
  toEditableFoodItem,
  type EditableFoodItem,
} from "@/components/EditableFoodRows";
import { MealPlanSuggestionCard } from "@/components/MealPlanSuggestionCard";
import { QuickReusePanel } from "@/components/QuickReusePanel";
import {
  useCopyFavoriteMeal,
  useCopyMeal,
  useConfirmMeal,
  useDeleteFavoriteMeal,
  useDeleteMeal,
  useFavoriteMeal,
  useFavoriteMeals,
  useMeals,
  usePreviewMeal,
  useRecentMeals,
  useUpdateMeal,
} from "@/hooks/useMeals";
import { useHealthPlan } from "@/hooks/useHealthPlan";
import { ApiError } from "@/api/client";
import { todayLocalISO } from "@/lib/date";
import { mealSlotLabel } from "@/lib/mealSlots";
import type { FoodItem, Meal, MealPreview } from "@/types/api";

export function MealsPage() {
  const today = useMemo(() => todayLocalISO(), []);
  const [slot, setSlot] = useState("lunch");
  const [editingId, setEditingId] = useState<number | null>(null);
  const [preview, setPreview] = useState<MealPreview | null>(null);
  const [previewFile, setPreviewFile] = useState<File | null>(null);
  const [formResetKey, setFormResetKey] = useState(0);

  const meals = useMeals(today);
  const recentMeals = useRecentMeals(today);
  const favoriteMeals = useFavoriteMeals();
  const previewMeal = usePreviewMeal();
  const confirmMeal = useConfirmMeal(today);
  const copyMeal = useCopyMeal(today);
  const copyFavoriteMeal = useCopyFavoriteMeal(today);
  const favoriteMeal = useFavoriteMeal();
  const deleteFavoriteMeal = useDeleteFavoriteMeal();
  const deleteMeal = useDeleteMeal(today);
  const healthPlan = useHealthPlan(today);

  async function requestMealPreview({ slot, description, imageFile }: AddMealPreviewRequest) {
    const form = new FormData();
    form.set("date", today);
    form.set("slot", slot);
    if (description) form.set("description", description);
    if (imageFile) form.set("image", imageFile);
    try {
      const result = await previewMeal.mutateAsync(form);
      setPreview(result);
      setPreviewFile(imageFile);
    } catch {
      // shown via previewMeal.error
    }
  }

  async function confirmPreview(items: FoodItem[]) {
    if (!preview) return;
    const form = new FormData();
    form.set("date", preview.date);
    form.set("slot", preview.slot);
    if (preview.description) form.set("description", preview.description);
    if (previewFile) form.set("image", previewFile);
    form.set("items", JSON.stringify(items));
    if (preview.aiSuggestion) form.set("aiSuggestion", preview.aiSuggestion);
    try {
      await confirmMeal.mutateAsync(form);
      setPreview(null);
      setPreviewFile(null);
      setFormResetKey((value) => value + 1);
    } catch {
      // shown via confirmMeal.error
    }
  }

  function clearPreview() {
    setPreview(null);
    setPreviewFile(null);
  }

  return (
    <section className="grid gap-6 animate-fade-in pb-10">
      <MealPlanSuggestionCard plan={healthPlan.data ?? null} loading={healthPlan.isLoading} selectedSlot={slot} />

      <AddMealForm
        slot={slot}
        resetKey={formResetKey}
        previewPending={previewMeal.isPending}
        confirmPending={confirmMeal.isPending}
        previewError={previewMeal.error}
        confirmError={confirmMeal.error}
        onSlotChange={setSlot}
        onPreview={requestMealPreview}
      />

      {preview && (
        <MealPreviewCard
          preview={preview}
          pending={confirmMeal.isPending}
          onConfirm={confirmPreview}
          onCancel={clearPreview}
        />
      )}

      <QuickReusePanel
        selectedSlot={slot}
        recent={recentMeals.data?.data ?? []}
        favorites={favoriteMeals.data ?? []}
        loading={recentMeals.isLoading || favoriteMeals.isLoading}
        copying={copyMeal.isPending || copyFavoriteMeal.isPending}
        deletingFavorite={deleteFavoriteMeal.isPending}
        onCopyRecent={(id) => copyMeal.mutate({ id, slot })}
        onCopyFavorite={(id) => copyFavoriteMeal.mutate({ id, slot })}
        onDeleteFavorite={(id) => deleteFavoriteMeal.mutate(id)}
      />

      {/* Meals History List */}
      <div className="space-y-4">
        <h2 className="text-sm font-bold text-slate-500 dark:text-slate-400 px-1 tracking-wider uppercase">今日餐點日誌</h2>

        {(previewMeal.isPending || confirmMeal.isPending) && <AiGenerationPanel kind="meal" />}
        
        {meals.isLoading ? (
          <Skeleton className="h-40 w-full rounded-3xl" />
        ) : meals.data && meals.data.data.length > 0 ? (
          meals.data.data.map((meal) => (
            <Card key={meal.id} className="border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden card-hover-effect">
              <CardHeader className="flex flex-row items-center justify-between gap-4 pb-3">
                <div className="flex items-center gap-3">
                  <div className="flex size-9 items-center justify-center rounded-xl bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
                    <Apple className="size-5" />
                  </div>
                  <div>
                    <CardTitle className="text-sm font-bold">
                      {mealSlotLabel(meal.slot)}
                    </CardTitle>
                    <CardDescription className="text-[10px] mt-0.5">
                      紀錄於 {new Date(meal.createdAt).toLocaleTimeString("zh-TW", { hour: "2-digit", minute: "2-digit" })}
                    </CardDescription>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <Badge className="rounded-full text-[10px] bg-slate-500/10 text-slate-600 dark:text-slate-400 border border-slate-500/5 px-2.5 py-0.5">
                    {meal.items.length > 0 && meal.items.every((i) => i.confidence === 1) ? "已手動修正" : "AI 估算"}
                  </Badge>
                  {editingId !== meal.id && (
                    <>
                      <Button
                        variant="ghost"
                        size="icon"
                        disabled={favoriteMeal.isPending}
                        onClick={() => favoriteMeal.mutate({ id: meal.id })}
                        className="rounded-full text-muted-foreground hover:text-amber-500 hover:bg-amber-500/5 size-8 transition-colors duration-300"
                        aria-label="收藏此餐為常用餐點"
                      >
                        <Star className="size-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => setEditingId(meal.id)}
                        className="rounded-full text-muted-foreground hover:text-emerald-600 hover:bg-emerald-500/5 size-8 transition-colors duration-300"
                        aria-label="手動修正此餐紀錄"
                      >
                        <Pencil className="size-4" />
                      </Button>
                    </>
                  )}
                  <Button
                    variant="ghost"
                    size="icon"
                    disabled={deleteMeal.isPending}
                    onClick={() => deleteMeal.mutate(meal.id)}
                    className="rounded-full text-muted-foreground hover:text-rose-500 hover:bg-rose-500/5 size-8 transition-colors duration-300"
                    aria-label="刪除此餐紀錄"
                  >
                    <Trash2 className="size-4" />
                  </Button>
                </div>
              </CardHeader>

              <CardContent className="px-6 pb-6 space-y-4">
                {editingId === meal.id ? (
                  <MealEditor meal={meal} date={today} onClose={() => setEditingId(null)} />
                ) : (
                <>
                {/* Micro Nutrients Summary Pill system - User Oriented */}
                <div className="grid grid-cols-4 gap-2">
                  <NutrientBadge label="總卡路里" value={meal.totalKcal} unit="kcal" type="kcal" />
                  <NutrientBadge label="蛋白質" value={meal.totalProtein} unit="g" type="protein" />
                  <NutrientBadge label="總脂肪" value={meal.totalFat} unit="g" type="fat" />
                  <NutrientBadge label="碳水化合物" value={meal.totalCarb} unit="g" type="carb" />
                </div>

                {/* Optional Image and Text Description */}
                <div className="p-4 rounded-2xl bg-slate-50/50 dark:bg-slate-900/30 border border-slate-100 dark:border-slate-900/40 space-y-2">
                  {meal.description && (
                    <div>
                      <span className="text-[10px] text-muted-foreground font-semibold uppercase tracking-wider block mb-0.5">使用者描述</span>
                      <p className="text-xs leading-relaxed font-medium">{meal.description}</p>
                    </div>
                  )}
                  {meal.imageUrl && (
                    <div className="mt-2 rounded-xl overflow-hidden max-w-xs border border-slate-100 dark:border-slate-900 shadow-sm">
                      <img src={meal.imageUrl} alt="餐點照片" className="w-full h-auto object-cover max-h-48" />
                    </div>
                  )}
                </div>

                {/* Items Breakdown list */}
                {meal.items && meal.items.length > 0 && (
                  <div>
                    <span className="text-[10px] text-muted-foreground font-semibold uppercase tracking-wider block mb-1.5 px-1">智能估算成分明細</span>
                    <ul className="divide-y divide-slate-50 dark:divide-slate-900/60 rounded-2xl border border-slate-100 dark:border-slate-900/50 bg-white/40 dark:bg-slate-950/20 overflow-hidden text-xs">
                      {meal.items.map((item, idx) => (
                        <li key={`${item.name}-${idx}`} className="flex items-center justify-between p-3 transition-colors hover:bg-slate-50 dark:hover:bg-slate-900/10">
                          <span className="font-semibold text-slate-700 dark:text-slate-300">{item.name}</span>
                          <div className="flex items-center gap-4 text-muted-foreground font-semibold">
                            <span>{item.grams}g</span>
                            <span className="text-emerald-600 dark:text-emerald-400 font-extrabold">{item.kcal} kcal</span>
                          </div>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}

                {/* AI Suggestions with wellness quote look */}
                {meal.aiSuggestion && (
                  <div className="relative p-4 rounded-2xl bg-gradient-to-tr from-emerald-500/5 to-teal-500/5 border border-emerald-500/10 dark:border-emerald-500/5 text-xs text-slate-700 dark:text-slate-300 leading-relaxed shadow-sm">
                    <div className="flex items-center gap-1.5 mb-1.5">
                      <Sparkles className="size-4 text-emerald-500" />
                      <span className="font-bold text-emerald-700 dark:text-emerald-400">AI 膳食教練評估</span>
                    </div>
                    <p className="font-medium italic pl-1">{meal.aiSuggestion}</p>
                  </div>
                )}
                </>
                )}
              </CardContent>
            </Card>
          ))
        ) : !previewMeal.isPending && !confirmMeal.isPending ? (
          <Card className="border border-dashed border-slate-200 dark:border-slate-800 bg-white/40 dark:bg-slate-950/10 rounded-3xl overflow-hidden py-12 text-center">
            <CardContent className="flex flex-col items-center gap-3">
              <div className="flex size-14 items-center justify-center rounded-full bg-slate-100 dark:bg-slate-900 text-slate-400">
                <UtensilsCrossed className="size-6" />
              </div>
              <p className="text-sm text-slate-500 dark:text-slate-400 font-medium">今天尚未建立任何飲食紀錄</p>
              <p className="text-xs text-muted-foreground max-w-xs leading-normal">選好餐點時段並輸入您的餐食，讓 AI 為您追蹤今日的熱量與營養素平衡吧！</p>
            </CardContent>
          </Card>
        ) : null}
      </div>
    </section>
  );
}

function MealPreviewCard({
  preview,
  pending,
  onConfirm,
  onCancel,
}: {
  preview: MealPreview;
  pending: boolean;
  onConfirm: (items: FoodItem[]) => void;
  onCancel: () => void;
}) {
  const [rows, setRows] = useState<EditableFoodItem[]>(
    preview.items.length > 0 ? preview.items.map(toEditableFoodItem) : [emptyFoodItem()],
  );
  const [error, setError] = useState<string | null>(null);

  function confirm() {
    const cleaned = cleanFoodRows(rows);
    if (cleaned.some((r) => [r.grams, r.kcal, r.protein, r.fat, r.carb].some((n) => !Number.isFinite(n) || n < 0))) {
      setError("份量與營養素必須為 0 以上的數值。");
      return;
    }
    setError(null);
    onConfirm(cleaned);
  }

  const totals = summarizeItems(rows);

  return (
    <Card className="border border-sky-500/15 bg-sky-500/5 dark:bg-sky-950/10 backdrop-blur-xl shadow-xl shadow-sky-100/40 dark:shadow-none rounded-3xl overflow-hidden">
      <CardHeader className="pb-3">
        <CardTitle className="text-lg font-bold flex items-center gap-2">
          <Sparkles className="size-4.5 text-sky-500" />
          AI 餐點預覽
        </CardTitle>
        <CardDescription className="text-xs">確認候選項後才會寫入今日餐點日誌</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4 px-6 pb-6">
        <div className="grid grid-cols-4 gap-2">
          <NutrientBadge label="總卡路里" value={totals.kcal} unit="kcal" type="kcal" />
          <NutrientBadge label="蛋白質" value={totals.protein} unit="g" type="protein" />
          <NutrientBadge label="總脂肪" value={totals.fat} unit="g" type="fat" />
          <NutrientBadge label="碳水化合物" value={totals.carb} unit="g" type="carb" />
        </div>

        {preview.items.length === 0 && (
          <div className="rounded-2xl border border-dashed border-slate-200 dark:border-slate-800 p-4 text-center text-xs text-muted-foreground">
            AI 沒有產生可靠候選項。可先在下方新增食物項目，或確認建立只有描述的餐點。
          </div>
        )}

        <EditableFoodRows
          rows={rows}
          setRows={setRows}
          disabled={pending}
          title="確認成分明細"
          keepOneRowOnRemove
          rowClassName="bg-white/50 dark:bg-slate-950/20"
          onError={setError}
        />

        {error && (
          <Alert variant="destructive" className="rounded-2xl border-amber-500/20 bg-amber-500/5 text-amber-700 dark:text-amber-400">
            <AlertDescription className="text-[11px]">{error}</AlertDescription>
          </Alert>
        )}

        {preview.aiSuggestion && (
          <p className="rounded-2xl border border-sky-500/10 bg-white/50 p-3 text-xs leading-relaxed text-slate-600 dark:bg-slate-950/20 dark:text-slate-300">
            {preview.aiSuggestion}
          </p>
        )}

        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="ghost" disabled={pending} onClick={onCancel} className="rounded-2xl">
            回到輸入修改
          </Button>
          <Button
            type="button"
            disabled={pending}
            onClick={confirm}
            className="rounded-2xl bg-gradient-to-r from-sky-600 to-emerald-500 px-5 font-semibold text-white hover:from-sky-500 hover:to-emerald-400"
          >
            {pending ? "儲存中..." : "確認儲存"}
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

function MealEditor({ meal, date, onClose }: { meal: Meal; date: string; onClose: () => void }) {
  const update = useUpdateMeal(date);
  const [rows, setRows] = useState<EditableFoodItem[]>(
    meal.items.length > 0 ? meal.items.map(toEditableFoodItem) : [emptyFoodItem()],
  );
  const [error, setError] = useState<string | null>(null);

  async function save() {
    const cleaned = cleanFoodRows(rows);
    if (cleaned.length === 0) {
      setError("請至少保留一個有名稱的食物項目，或直接刪除整筆紀錄。");
      return;
    }
    if (cleaned.some((r) => [r.grams, r.kcal, r.protein, r.fat, r.carb].some((n) => !Number.isFinite(n) || n < 0))) {
      setError("份量與營養素必須為 0 以上的數值。");
      return;
    }
    setError(null);
    try {
      await update.mutateAsync({ id: meal.id, items: cleaned, aiSuggestion: null });
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "儲存失敗，請稍後再試。");
    }
  }

  const totalKcal = rows.reduce((s, r) => s + (Number.isFinite(r.kcal) ? r.kcal : 0), 0);

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between px-1">
        <span className="text-[10px] text-muted-foreground font-semibold uppercase tracking-wider">
          手動修正成分明細
        </span>
        <span className="text-[10px] text-muted-foreground font-semibold">
          總計約 <span className="text-emerald-600 dark:text-emerald-400 font-extrabold">{totalKcal}</span> kcal · {rows.length}/5 項
        </span>
      </div>

      <EditableFoodRows
        rows={rows}
        setRows={setRows}
        disabled={update.isPending}
        title="成分明細"
        onError={setError}
      />

      {error && (
        <Alert variant="destructive" className="rounded-2xl border-amber-500/20 bg-amber-500/5 text-amber-700 dark:text-amber-400">
          <AlertDescription className="text-[11px]">{error}</AlertDescription>
        </Alert>
      )}

      <p className="text-[10px] text-muted-foreground px-1 leading-relaxed">
        手動修正後此餐會標記為使用者確認值（信心度 100%），並清除原本的 AI 飲食建議。
      </p>

      <div className="flex items-center justify-end gap-2 pt-0.5">
        <Button
          type="button"
          variant="ghost"
          onClick={onClose}
          disabled={update.isPending}
          className="rounded-2xl text-xs text-muted-foreground"
        >
          取消
        </Button>
        <Button
          type="button"
          onClick={save}
          disabled={update.isPending}
          className="rounded-2xl text-xs gap-1.5 bg-emerald-600 hover:bg-emerald-500 text-white shadow-sm shadow-emerald-500/10"
        >
          {update.isPending ? (
            <>
              <span className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-white border-t-transparent" />
              儲存中...
            </>
          ) : (
            <>
              <Save className="size-4" />
              儲存修正
            </>
          )}
        </Button>
      </div>
    </div>
  );
}

function NutrientBadge({
  label,
  value,
  unit,
  type,
}: {
  label: string;
  value: number;
  unit: string;
  type: "kcal" | "protein" | "fat" | "carb";
}) {
  const typeMap = {
    kcal: "bg-emerald-500/5 text-emerald-600 dark:text-emerald-400 border-emerald-500/10",
    protein: "bg-rose-500/5 text-rose-600 dark:text-rose-400 border-rose-500/10",
    fat: "bg-amber-500/5 text-amber-600 dark:text-amber-400 border-amber-500/10",
    carb: "bg-sky-500/5 text-sky-600 dark:text-sky-400 border-sky-500/10",
  };

  return (
    <div className={`flex flex-col items-center justify-center p-2 rounded-2xl border text-center ${typeMap[type]}`}>
      <span className="text-[9px] text-muted-foreground uppercase font-semibold">{label}</span>
      <div className="flex items-baseline mt-0.5 gap-0.5">
        <span className="text-sm font-extrabold">{value}</span>
        <span className="text-[9px] opacity-80">{unit}</span>
      </div>
    </div>
  );
}

import { FormEvent, useMemo, useRef, useState } from "react";
import { Apple, Image as ImageIcon, Plus, Trash2, X, Sparkles, ChevronRight, UtensilsCrossed } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { useCreateMeal, useDeleteMeal, useMeals } from "@/hooks/useMeals";
import { ApiError } from "@/api/client";
import { todayLocalISO } from "@/lib/date";

const SLOTS = [
  { value: "breakfast", label: "早餐", time: "上午 06:00 - 09:00" },
  { value: "lunch", label: "午餐", time: "中午 11:30 - 13:30" },
  { value: "dinner", label: "晚餐", time: "晚上 17:30 - 20:00" },
  { value: "snack", label: "點心", time: "全天輕食紀錄" },
];

export function MealsPage() {
  const today = useMemo(() => todayLocalISO(), []);
  const [slot, setSlot] = useState("lunch");
  const [description, setDescription] = useState("");
  const [imageFile, setImageFile] = useState<File | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const meals = useMeals(today);
  const createMeal = useCreateMeal(today);
  const deleteMeal = useDeleteMeal(today);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!description.trim() && !imageFile) return;
    const form = new FormData();
    form.set("date", today);
    form.set("slot", slot);
    if (description.trim()) form.set("description", description.trim());
    if (imageFile) form.set("image", imageFile);
    try {
      await createMeal.mutateAsync(form);
      setDescription("");
      setImageFile(null);
      if (fileInputRef.current) fileInputRef.current.value = "";
    } catch {
      // shown via createMeal.error
    }
  }

  return (
    <section className="grid gap-6 animate-fade-in pb-10">
      {/* Add Meal Form */}
      <Card className="border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden accent-glow">
        <CardHeader className="pb-3">
          <CardTitle className="text-md font-bold flex items-center gap-2">
            <UtensilsCrossed className="size-4.5 text-emerald-500" />
            新增今日餐點紀錄
          </CardTitle>
          <CardDescription className="text-xs">輸入飲食內容描述，或上傳餐點照片，AI 將自動辨識並估算熱量與三大營養素</CardDescription>
        </CardHeader>
        <CardContent className="px-6 pb-6">
          <form onSubmit={submit} className="grid gap-5">
            {/* Slot selector pills - User Oriented */}
            <div className="grid gap-2">
              <Label className="text-xs font-semibold text-slate-500 px-1">選擇餐點時段</Label>
              <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
                {SLOTS.map((s) => (
                  <button
                    key={s.value}
                    type="button"
                    onClick={() => setSlot(s.value)}
                    className={`flex flex-col items-center justify-center p-3 rounded-2xl border text-center transition-all duration-300 ${
                      slot === s.value
                        ? "bg-gradient-to-tr from-emerald-500/10 to-teal-500/5 border-emerald-500/40 text-emerald-600 dark:text-emerald-400 font-semibold shadow-sm shadow-emerald-500/5"
                        : "bg-white/50 border-slate-100 dark:bg-slate-900/50 dark:border-slate-900 hover:border-slate-200 dark:hover:border-slate-800"
                    }`}
                  >
                    <span className="text-sm">{s.label}</span>
                    <span className="text-[9px] text-muted-foreground mt-0.5 font-normal tracking-tight hidden sm:inline">{s.time}</span>
                  </button>
                ))}
              </div>
            </div>

            {/* Description Text Input */}
            <div className="grid gap-1.5">
              <Label htmlFor="description" className="text-xs font-semibold text-slate-500 px-1">餐點明細描述</Label>
              <Input
                id="description"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="例：水煮雞胸肉 150克、水煮蛋一顆、地瓜一條，或是簡述所吃的事物..."
                className="rounded-2xl border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 py-5 focus-visible:ring-emerald-500 focus-visible:border-emerald-500/40 transition-all duration-300"
              />
            </div>

            {/* Upload Zone & Submit Buttons */}
            <div className="flex flex-wrap items-center justify-between gap-4 pt-1 border-t border-dashed border-slate-100 dark:border-slate-900/60">
              <div className="flex items-center gap-3">
                <input
                  ref={fileInputRef}
                  id="meal-image"
                  type="file"
                  accept="image/*"
                  className="hidden"
                  onChange={(e) => setImageFile(e.target.files?.[0] ?? null)}
                />
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => fileInputRef.current?.click()}
                  className="rounded-2xl border-slate-200/80 hover:bg-slate-50 dark:border-slate-800 dark:hover:bg-slate-900/80 gap-2 text-xs py-5 px-4 font-medium transition-all-smooth"
                >
                  <ImageIcon className="size-4 text-emerald-500" />
                  {imageFile ? "更換餐點照片" : "上傳餐點照片"}
                </Button>
                
                {imageFile && (
                  <Badge variant="secondary" className="rounded-xl gap-1.5 py-1 px-2.5 bg-emerald-500/5 border border-emerald-500/10 text-emerald-600 dark:text-emerald-400 font-semibold text-[10px]">
                    <span className="truncate max-w-[120px]">{imageFile.name}</span>
                    <button
                      type="button"
                      onClick={() => { setImageFile(null); if (fileInputRef.current) fileInputRef.current.value = ""; }}
                      className="rounded-full hover:bg-emerald-500/10 p-0.5"
                    >
                      <X className="size-3" />
                    </button>
                  </Badge>
                )}
                {!imageFile && <span className="text-[10px] text-muted-foreground font-medium">照片或描述擇一輸入即可</span>}
              </div>

              <Button
                type="submit"
                disabled={createMeal.isPending || (!description.trim() && !imageFile)}
                className="rounded-2xl py-5 px-6 bg-gradient-to-r from-emerald-600 to-teal-500 hover:from-emerald-500 hover:to-teal-400 text-white font-semibold shadow-md shadow-emerald-500/10 hover:shadow-lg transition-all-smooth gap-1.5"
              >
                {createMeal.isPending ? (
                  <>
                    <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                    AI 分析辨識中...
                  </>
                ) : (
                  <>
                    <Plus className="size-4" />
                    送出 AI 分析
                  </>
                )}
              </Button>
            </div>

            {createMeal.error instanceof ApiError && createMeal.error.status === 503 && (
              <Alert variant="destructive" className="rounded-2xl border-rose-500/20 bg-rose-500/5 text-rose-600 dark:text-rose-400">
                <AlertTitle className="text-xs font-bold">AI 服務暫時離線</AlertTitle>
                <AlertDescription className="text-[11px] opacity-90">請保留您的描述文字，待引擎連線後重新進行分析。</AlertDescription>
              </Alert>
            )}
          </form>
        </CardContent>
      </Card>

      {/* Meals History List */}
      <div className="space-y-4">
        <h2 className="text-sm font-bold text-slate-500 dark:text-slate-400 px-1 tracking-wider uppercase">今日餐點日誌</h2>
        
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
                      {SLOTS.find((s) => s.value === meal.slot)?.label ?? meal.slot}
                    </CardTitle>
                    <CardDescription className="text-[10px] mt-0.5">
                      紀錄於 {new Date(meal.createdAt).toLocaleTimeString("zh-TW", { hour: "2-digit", minute: "2-digit" })}
                    </CardDescription>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <Badge className="rounded-full text-[10px] bg-slate-500/10 text-slate-600 dark:text-slate-400 border border-slate-500/5 px-2.5 py-0.5">
                    AI 估算
                  </Badge>
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
              </CardContent>
            </Card>
          ))
        ) : (
          <Card className="border border-dashed border-slate-200 dark:border-slate-800 bg-white/40 dark:bg-slate-950/10 rounded-3xl overflow-hidden py-12 text-center">
            <CardContent className="flex flex-col items-center gap-3">
              <div className="flex size-14 items-center justify-center rounded-full bg-slate-100 dark:bg-slate-900 text-slate-400">
                <UtensilsCrossed className="size-6" />
              </div>
              <p className="text-sm text-slate-500 dark:text-slate-400 font-medium">今天尚未建立任何飲食紀錄</p>
              <p className="text-xs text-muted-foreground max-w-xs leading-normal">選好餐點時段並輸入您的餐食，讓 AI 為您追蹤今日的熱量與營養素平衡吧！</p>
            </CardContent>
          </Card>
        )}
      </div>
    </section>
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

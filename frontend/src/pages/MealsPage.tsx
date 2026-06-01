import { FormEvent, useMemo, useRef, useState } from "react";
import { Apple, Image as ImageIcon, Pencil, Plus, Save, Trash2, X, Sparkles, ChevronRight, UtensilsCrossed } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { AiGenerationPanel } from "@/components/AiGenerationPanel";
import { useCreateMeal, useDeleteMeal, useMeals, useUpdateMeal } from "@/hooks/useMeals";
import { ApiError } from "@/api/client";
import { todayLocalISO } from "@/lib/date";
import type { FoodItem, Meal } from "@/types/api";

const SLOTS = [
  { value: "breakfast", label: "早餐", time: "上午 06:00 - 09:00" },
  { value: "lunch", label: "午餐", time: "中午 11:30 - 13:30" },
  { value: "dinner", label: "晚餐", time: "晚上 17:30 - 20:00" },
  { value: "snack", label: "點心", time: "全天輕食紀錄" },
];

const FOOD_HINT =
  /(早餐|午餐|晚餐|宵夜|點心|餐點|便當|飯|米飯|白飯|糙米|麵|麵包|吐司|粥|湯|沙拉|壽司|水餃|雞|雞胸|牛|牛肉|豬|豬肉|魚|鮭魚|蝦|蛋|豆腐|起司|乳酪|優格|牛奶|豆漿|咖啡|茶|果汁|水|蔬菜|青菜|花椰菜|地瓜|馬鈴薯|玉米|水果|香蕉|蘋果|燕麥|堅果|蛋白|碳水|脂肪|熱量|卡路里|kcal|calorie|rice|noodle|bread|toast|oat|chicken|beef|pork|fish|salmon|shrimp|egg|tofu|cheese|yogurt|milk|coffee|tea|juice|salad|vegetable|banana|apple|potato|meal|breakfast|lunch|dinner|snack)/i;

const PROMPT_INJECTION_HINT =
  /(忽略.*規則|忽略.*指示|系統提示|開發者訊息|prompt|system prompt|developer message|ignore previous|ignore above|json schema|扮演|角色扮演)/i;

function mealDescriptionWarning(value: string) {
  const trimmed = value.trim().replace(/\s+/g, " ");
  if (!trimmed) return null;
  if (trimmed.length > 300) return "餐點描述請控制在 300 字內，並只填寫食物、飲品與份量。";
  if (PROMPT_INJECTION_HINT.test(trimmed)) return "請只輸入餐點內容，不要輸入指令、角色扮演或系統提示文字。";
  if (!FOOD_HINT.test(trimmed)) return "請確認輸入內容是否為飲食或餐點描述，例如「雞胸肉 150g、白飯一碗」。";
  return null;
}

export function MealsPage() {
  const today = useMemo(() => todayLocalISO(), []);
  const [slot, setSlot] = useState("lunch");
  const [description, setDescription] = useState("");
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [inputWarning, setInputWarning] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const meals = useMeals(today);
  const createMeal = useCreateMeal(today);
  const deleteMeal = useDeleteMeal(today);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmedDescription = description.trim().replace(/\s+/g, " ");
    if (!trimmedDescription && !imageFile) return;
    const warning = mealDescriptionWarning(trimmedDescription);
    if (warning) {
      setInputWarning(warning);
      return;
    }
    setInputWarning(null);
    const form = new FormData();
    form.set("date", today);
    form.set("slot", slot);
    if (trimmedDescription) form.set("description", trimmedDescription);
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
                    disabled={createMeal.isPending}
                    onClick={() => setSlot(s.value)}
                    className={`flex flex-col items-center justify-center p-3 rounded-2xl border text-center transition-all duration-300 disabled:cursor-not-allowed disabled:opacity-60 ${
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
                onChange={(e) => {
                  setDescription(e.target.value);
                  setInputWarning(null);
                }}
                disabled={createMeal.isPending}
                maxLength={300}
                aria-invalid={!!inputWarning}
                placeholder="例：水煮雞胸肉 150克、水煮蛋一顆、地瓜一條，或是簡述所吃的食物..."
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
                  disabled={createMeal.isPending}
                  className="hidden"
                  onChange={(e) => setImageFile(e.target.files?.[0] ?? null)}
                />
                <Button
                  type="button"
                  disabled={createMeal.isPending}
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

            {(inputWarning || (createMeal.error instanceof ApiError && createMeal.error.status === 400)) && (
              <Alert variant="destructive" className="rounded-2xl border-amber-500/20 bg-amber-500/5 text-amber-700 dark:text-amber-400">
                <AlertTitle className="text-xs font-bold">請確認餐點內容</AlertTitle>
                <AlertDescription className="text-[11px] opacity-90">
                  {inputWarning ?? (createMeal.error instanceof ApiError ? createMeal.error.message : "請重新確認輸入內容。")}
                </AlertDescription>
              </Alert>
            )}

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

        {createMeal.isPending && <AiGenerationPanel kind="meal" />}
        
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
                    {meal.items.length > 0 && meal.items.every((i) => i.confidence === 1) ? "已手動修正" : "AI 估算"}
                  </Badge>
                  {editingId !== meal.id && (
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => setEditingId(meal.id)}
                      className="rounded-full text-muted-foreground hover:text-emerald-600 hover:bg-emerald-500/5 size-8 transition-colors duration-300"
                      aria-label="手動修正此餐紀錄"
                    >
                      <Pencil className="size-4" />
                    </Button>
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
        ) : !createMeal.isPending ? (
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

function emptyFoodItem(): FoodItem {
  return { name: "", grams: 0, kcal: 0, protein: 0, fat: 0, carb: 0, confidence: 1 };
}

function MealEditor({ meal, date, onClose }: { meal: Meal; date: string; onClose: () => void }) {
  const update = useUpdateMeal(date);
  const [rows, setRows] = useState<FoodItem[]>(
    meal.items.length > 0 ? meal.items.map((i) => ({ ...i })) : [emptyFoodItem()],
  );
  const [error, setError] = useState<string | null>(null);

  function setField(idx: number, field: keyof FoodItem, raw: string) {
    setRows((prev) =>
      prev.map((r, i) => (i === idx ? { ...r, [field]: field === "name" ? raw : Number(raw) } : r)),
    );
  }
  function addRow() {
    setRows((prev) => (prev.length >= 5 ? prev : [...prev, emptyFoodItem()]));
  }
  function removeRow(idx: number) {
    setRows((prev) => prev.filter((_, i) => i !== idx));
  }

  async function save() {
    const cleaned = rows
      .map((r) => ({ ...r, name: r.name.trim(), confidence: 1 }))
      .filter((r) => r.name.length > 0);
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

      <div className="space-y-2">
        {rows.map((row, idx) => (
          <div
            key={idx}
            className="rounded-2xl border border-slate-100 dark:border-slate-900/50 bg-white/40 dark:bg-slate-950/20 p-3 space-y-2"
          >
            <div className="flex items-center gap-2">
              <Input
                value={row.name}
                maxLength={80}
                onChange={(e) => setField(idx, "name", e.target.value)}
                placeholder="食物名稱"
                className="flex-1 rounded-xl text-xs py-4 border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 focus-visible:ring-emerald-500"
              />
              <Button
                type="button"
                variant="ghost"
                size="icon"
                onClick={() => removeRow(idx)}
                aria-label="刪除此項目"
                className="rounded-full text-muted-foreground hover:text-rose-500 hover:bg-rose-500/5 size-8 shrink-0"
              >
                <Trash2 className="size-4" />
              </Button>
            </div>
            <div className="grid grid-cols-5 gap-1.5">
              <NumField label="克 (g)" value={row.grams} onChange={(v) => setField(idx, "grams", v)} />
              <NumField label="熱量" value={row.kcal} onChange={(v) => setField(idx, "kcal", v)} />
              <NumField label="蛋白" value={row.protein} onChange={(v) => setField(idx, "protein", v)} />
              <NumField label="脂肪" value={row.fat} onChange={(v) => setField(idx, "fat", v)} />
              <NumField label="碳水" value={row.carb} onChange={(v) => setField(idx, "carb", v)} />
            </div>
          </div>
        ))}
      </div>

      {error && (
        <Alert variant="destructive" className="rounded-2xl border-amber-500/20 bg-amber-500/5 text-amber-700 dark:text-amber-400">
          <AlertDescription className="text-[11px]">{error}</AlertDescription>
        </Alert>
      )}

      <p className="text-[10px] text-muted-foreground px-1 leading-relaxed">
        手動修正後此餐會標記為使用者確認值（信心度 100%），並清除原本的 AI 飲食建議。
      </p>

      <div className="flex items-center justify-between gap-2 pt-0.5">
        <Button
          type="button"
          variant="outline"
          onClick={addRow}
          disabled={rows.length >= 5 || update.isPending}
          className="rounded-2xl text-xs gap-1.5 border-slate-200/80 dark:border-slate-800 disabled:opacity-50"
        >
          <Plus className="size-4 text-emerald-500" />
          新增項目
        </Button>
        <div className="flex items-center gap-2">
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
    </div>
  );
}

function NumField({ label, value, onChange }: { label: string; value: number; onChange: (v: string) => void }) {
  return (
    <div className="grid gap-1">
      <span className="text-[9px] text-muted-foreground font-semibold text-center uppercase tracking-wide">{label}</span>
      <Input
        type="number"
        min={0}
        inputMode="decimal"
        value={Number.isFinite(value) ? value : 0}
        onChange={(e) => onChange(e.target.value)}
        className="rounded-xl text-center text-xs py-3 px-1 border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 focus-visible:ring-emerald-500"
      />
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

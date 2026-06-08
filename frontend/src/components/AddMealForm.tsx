import { FormEvent, useEffect, useRef, useState } from "react";
import { Image as ImageIcon, Plus, UtensilsCrossed, X } from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiError } from "@/api/client";
import { MEAL_SLOTS } from "@/lib/mealSlots";

const FOOD_HINT =
  /(早餐|午餐|晚餐|宵夜|點心|餐點|便當|飯|米飯|白飯|糙米|麵|麵包|吐司|粥|湯|沙拉|壽司|水餃|雞|雞胸|牛|牛肉|豬|豬肉|魚|鮭魚|蝦|蛋|豆腐|起司|乳酪|優格|牛奶|豆漿|咖啡|茶|果汁|水|蔬菜|青菜|花椰菜|地瓜|馬鈴薯|玉米|水果|香蕉|蘋果|燕麥|堅果|蛋白|碳水|脂肪|熱量|卡路里|kcal|calorie|rice|noodle|bread|toast|oat|chicken|beef|pork|fish|salmon|shrimp|egg|tofu|cheese|yogurt|milk|coffee|tea|juice|salad|vegetable|banana|apple|potato|meal|breakfast|lunch|dinner|snack)/i;

const PROMPT_INJECTION_HINT =
  /(忽略.*規則|忽略.*指示|系統提示|開發者訊息|prompt|system prompt|developer message|ignore previous|ignore above|json schema|扮演|角色扮演)/i;

export type AddMealPreviewRequest = {
  slot: string;
  description: string;
  imageFile: File | null;
};

export function AddMealForm({
  slot,
  resetKey,
  previewPending,
  confirmPending,
  previewError,
  confirmError,
  onSlotChange,
  onPreview,
}: {
  slot: string;
  resetKey: number;
  previewPending: boolean;
  confirmPending: boolean;
  previewError: unknown;
  confirmError: unknown;
  onSlotChange: (slot: string) => void;
  onPreview: (request: AddMealPreviewRequest) => Promise<void>;
}) {
  const [description, setDescription] = useState("");
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [inputWarning, setInputWarning] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const disabled = previewPending || confirmPending;

  useEffect(() => {
    setDescription("");
    setImageFile(null);
    setInputWarning(null);
    if (fileInputRef.current) fileInputRef.current.value = "";
  }, [resetKey]);

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
    await onPreview({ slot, description: trimmedDescription, imageFile });
  }

  return (
    <Card className="border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden accent-glow">
      <CardHeader className="pb-3">
        <CardTitle className="text-lg font-bold flex items-center gap-2">
          <UtensilsCrossed className="size-4.5 text-emerald-500" />
          新增今日餐點紀錄
        </CardTitle>
        <CardDescription className="text-xs">輸入飲食內容描述，或上傳餐點照片，AI 將自動辨識並估算熱量與三大營養素</CardDescription>
      </CardHeader>
      <CardContent className="px-6 pb-6">
        <form onSubmit={submit} className="grid gap-5">
          <div className="grid gap-2">
            <Label className="text-xs font-semibold text-slate-500 px-1">選擇餐點時段</Label>
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
              {MEAL_SLOTS.map((mealSlot) => (
                <button
                  key={mealSlot.value}
                  type="button"
                  disabled={disabled}
                  onClick={() => onSlotChange(mealSlot.value)}
                  className={`flex flex-col items-center justify-center p-3 rounded-2xl border text-center transition-all duration-300 disabled:cursor-not-allowed disabled:opacity-60 ${
                    slot === mealSlot.value
                      ? "bg-gradient-to-tr from-emerald-500/10 to-teal-500/5 border-emerald-500/40 text-emerald-600 dark:text-emerald-400 font-semibold shadow-sm shadow-emerald-500/5"
                      : "bg-white/50 border-slate-100 dark:bg-slate-900/50 dark:border-slate-900 hover:border-slate-200 dark:hover:border-slate-800"
                  }`}
                >
                  <span className="text-sm">{mealSlot.label}</span>
                  <span className="text-[9px] text-muted-foreground mt-0.5 font-normal tracking-tight hidden sm:inline">{mealSlot.time}</span>
                </button>
              ))}
            </div>
          </div>

          <div className="grid gap-1.5">
            <Label htmlFor="description" className="text-xs font-semibold text-slate-500 px-1">餐點明細描述</Label>
            <Input
              id="description"
              value={description}
              onChange={(e) => {
                setDescription(e.target.value);
                setInputWarning(null);
              }}
              disabled={disabled}
              maxLength={300}
              aria-invalid={!!inputWarning}
              placeholder="例：水煮雞胸肉 150克、水煮蛋一顆、地瓜一條，或是簡述所吃的食物..."
              className="rounded-2xl border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 py-5 focus-visible:ring-emerald-500 focus-visible:border-emerald-500/40 transition-all duration-300"
            />
          </div>

          <div className="flex flex-wrap items-center justify-between gap-4 pt-1 border-t border-dashed border-slate-100 dark:border-slate-900/60">
            <div className="flex items-center gap-3">
              <input
                ref={fileInputRef}
                id="meal-image"
                type="file"
                accept="image/*"
                disabled={disabled}
                className="hidden"
                onChange={(e) => setImageFile(e.target.files?.[0] ?? null)}
              />
              <Button
                type="button"
                disabled={disabled}
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
                    onClick={() => {
                      setImageFile(null);
                      if (fileInputRef.current) fileInputRef.current.value = "";
                    }}
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
              disabled={disabled || (!description.trim() && !imageFile)}
              className="rounded-2xl py-5 px-6 bg-gradient-to-r from-emerald-600 to-teal-500 hover:from-emerald-500 hover:to-teal-400 text-white font-semibold shadow-md shadow-emerald-500/10 hover:shadow-lg transition-all-smooth gap-1.5"
            >
              {previewPending ? (
                <>
                  <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                  AI 預覽分析中...
                </>
              ) : (
                <>
                  <Plus className="size-4" />
                  預覽 AI 分析
                </>
              )}
            </Button>
          </div>

          {(inputWarning || (previewError instanceof ApiError && previewError.status === 400)) && (
            <Alert variant="destructive" className="rounded-2xl border-amber-500/20 bg-amber-500/5 text-amber-700 dark:text-amber-400">
              <AlertTitle className="text-xs font-bold">請確認餐點內容</AlertTitle>
              <AlertDescription className="text-[11px] opacity-90">
                {inputWarning ?? (previewError instanceof ApiError ? previewError.message : "請重新確認輸入內容。")}
              </AlertDescription>
            </Alert>
          )}

          {previewError instanceof ApiError && previewError.status === 503 && (
            <Alert variant="destructive" className="rounded-2xl border-rose-500/20 bg-rose-500/5 text-rose-600 dark:text-rose-400">
              <AlertTitle className="text-xs font-bold">AI 服務暫時離線</AlertTitle>
              <AlertDescription className="text-[11px] opacity-90">請保留您的描述文字，待引擎連線後重新進行分析。</AlertDescription>
            </Alert>
          )}

          {confirmError instanceof ApiError && (
            <Alert variant="destructive" className="rounded-2xl border-rose-500/20 bg-rose-500/5 text-rose-600 dark:text-rose-400">
              <AlertTitle className="text-xs font-bold">餐點儲存失敗</AlertTitle>
              <AlertDescription className="text-[11px] opacity-90">{confirmError.message}</AlertDescription>
            </Alert>
          )}
        </form>
      </CardContent>
    </Card>
  );
}

function mealDescriptionWarning(value: string) {
  const trimmed = value.trim().replace(/\s+/g, " ");
  if (!trimmed) return null;
  if (trimmed.length > 300) return "餐點描述請控制在 300 字內，並只填寫食物、飲品與份量。";
  if (PROMPT_INJECTION_HINT.test(trimmed)) return "請只輸入餐點內容，不要輸入指令、角色扮演或系統提示文字。";
  if (!FOOD_HINT.test(trimmed)) return "請確認輸入內容是否為飲食或餐點描述，例如「雞胸肉 150g、白飯一碗」。";
  return null;
}

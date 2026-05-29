import { FormEvent, useMemo, useRef, useState } from "react";
import { Apple, Image as ImageIcon, Plus, Trash2, X } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { useCreateMeal, useDeleteMeal, useMeals } from "@/hooks/useMeals";
import { ApiError } from "@/api/client";
import { todayLocalISO } from "@/lib/date";

const SLOTS = [
  { value: "breakfast", label: "早餐" },
  { value: "lunch", label: "午餐" },
  { value: "dinner", label: "晚餐" },
  { value: "snack", label: "點心" },
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
    <section className="grid gap-6 animate-fade-in">
      <Card>
        <CardHeader>
          <CardTitle>新增餐點</CardTitle>
          <CardDescription>輸入描述或上傳照片，AI 會估算熱量與三大營養素</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={submit} className="grid gap-4">
            <div className="grid gap-4 md:grid-cols-[1fr_2fr]">
              <div className="grid gap-2">
                <Label htmlFor="slot">時段</Label>
                <Select value={slot} onValueChange={setSlot}>
                  <SelectTrigger id="slot"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {SLOTS.map((s) => <SelectItem key={s.value} value={s.value}>{s.label}</SelectItem>)}
                  </SelectContent>
                </Select>
              </div>
              <div className="grid gap-2">
                <Label htmlFor="description">描述</Label>
                <Input
                  id="description"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="例：雞胸肉沙拉、糙米飯一碗"
                />
              </div>
            </div>
            <div className="flex flex-wrap items-center gap-3">
              <input
                ref={fileInputRef}
                id="meal-image"
                type="file"
                accept="image/*"
                className="hidden"
                onChange={(e) => setImageFile(e.target.files?.[0] ?? null)}
              />
              <Button type="button" variant="outline" onClick={() => fileInputRef.current?.click()}>
                <ImageIcon className="size-4" />
                {imageFile ? "更換照片" : "選擇照片"}
              </Button>
              {imageFile && (
                <Badge variant="secondary" className="gap-1">
                  {imageFile.name}
                  <button type="button" onClick={() => { setImageFile(null); if (fileInputRef.current) fileInputRef.current.value = ""; }}>
                    <X className="size-3" />
                  </button>
                </Badge>
              )}
              <span className="text-xs text-muted-foreground">至少擇一輸入</span>
              <Button
                type="submit"
                className="ml-auto"
                disabled={createMeal.isPending || (!description.trim() && !imageFile)}
              >
                <Plus className="size-4" />
                {createMeal.isPending ? "分析中…" : "新增"}
              </Button>
            </div>
            {createMeal.error instanceof ApiError && createMeal.error.status === 503 && (
              <Alert variant="destructive">
                <AlertTitle>AI 暫時不可用</AlertTitle>
                <AlertDescription>請稍後重試，或保留描述待 AI 恢復後重新分析。</AlertDescription>
              </Alert>
            )}
          </form>
        </CardContent>
      </Card>

      {meals.isLoading ? (
        <Skeleton className="h-32 w-full" />
      ) : meals.data && meals.data.data.length > 0 ? (
        meals.data.data.map((meal) => (
          <Card key={meal.id}>
            <CardHeader className="flex flex-row items-start justify-between gap-4">
              <div>
                <CardTitle className="flex items-center gap-2">
                  <Apple className="size-4 text-primary" />
                  {SLOTS.find((s) => s.value === meal.slot)?.label ?? meal.slot}
                </CardTitle>
                <CardDescription>
                  {meal.totalKcal} kcal · {meal.totalProtein}g 蛋白 · {meal.totalFat}g 脂肪 · {meal.totalCarb}g 碳水
                </CardDescription>
              </div>
              <div className="flex items-center gap-2">
                <Badge variant="muted">估算</Badge>
                <Button
                  variant="ghost"
                  size="icon"
                  disabled={deleteMeal.isPending}
                  onClick={() => deleteMeal.mutate(meal.id)}
                  aria-label="刪除餐點"
                >
                  <Trash2 className="size-4" />
                </Button>
              </div>
            </CardHeader>
            <CardContent className="space-y-3">
              {meal.description && <p className="text-sm">{meal.description}</p>}
              <ul className="grid gap-1 text-sm">
                {meal.items.map((item, idx) => (
                  <li key={`${item.name}-${idx}`} className="grid grid-cols-[1fr_auto_auto] gap-3 border-t py-1.5 first:border-t-0">
                    <span>{item.name}</span>
                    <span className="text-muted-foreground">{item.grams}g</span>
                    <span className="text-muted-foreground">{item.kcal} kcal</span>
                  </li>
                ))}
              </ul>
              {meal.aiSuggestion && (
                <Alert variant="info">
                  <AlertTitle>AI 建議</AlertTitle>
                  <AlertDescription>{meal.aiSuggestion}</AlertDescription>
                </Alert>
              )}
            </CardContent>
          </Card>
        ))
      ) : (
        <Card>
          <CardContent className="grid place-items-center gap-3 p-10 text-center">
            <Apple className="size-8 text-muted-foreground" />
            <p className="text-sm text-muted-foreground">今天還沒有飲食紀錄。</p>
          </CardContent>
        </Card>
      )}
    </section>
  );
}

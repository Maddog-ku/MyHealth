import { useState, type Dispatch, type SetStateAction } from "react";
import { Plus, Search, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useFoodSearch } from "@/hooks/useMeals";
import type { FoodCatalogItem, FoodItem } from "@/types/api";

type NutritionBasis = Pick<FoodItem, "grams" | "kcal" | "protein" | "fat" | "carb">;

export type EditableFoodItem = FoodItem & { nutritionBasis?: NutritionBasis };

export function EditableFoodRows({
  rows,
  setRows,
  disabled,
  title,
  keepOneRowOnRemove,
  rowClassName,
  onError,
}: {
  rows: EditableFoodItem[];
  setRows: Dispatch<SetStateAction<EditableFoodItem[]>>;
  disabled: boolean;
  title: string;
  keepOneRowOnRemove?: boolean;
  rowClassName?: string;
  onError: (message: string | null) => void;
}) {
  function setField(idx: number, field: keyof FoodItem, raw: string) {
    setRows((prev) => prev.map((r, i) => (i === idx ? updateFoodRowField(r, field, raw) : r)));
    onError(null);
  }

  function addRow() {
    setRows((prev) => (prev.length >= 5 ? prev : [...prev, emptyFoodItem()]));
    onError(null);
  }

  function removeRow(idx: number) {
    setRows((prev) => {
      if (keepOneRowOnRemove && prev.length <= 1) {
        return [emptyFoodItem()];
      }
      return prev.filter((_, i) => i !== idx);
    });
    onError(null);
  }

  function addCatalogFood(food: FoodCatalogItem) {
    const item = catalogFoodToItem(food);
    setRows((prev) => {
      const idx = prev.findIndex((row) => !row.name.trim());
      if (idx >= 0) {
        onError(null);
        return prev.map((row, i) => (i === idx ? item : row));
      }
      if (prev.length >= 5) {
        onError("最多只能保留 5 個食物項目。");
        return prev;
      }
      onError(null);
      return [...prev, item];
    });
  }

  return (
    <div className="space-y-3">
      <FoodSearchPanel disabled={disabled} onSelect={addCatalogFood} />

      <div className="space-y-2">
        <div className="flex items-center justify-between gap-3 px-1">
          <div>
            <span className="text-[10px] text-muted-foreground font-semibold uppercase tracking-wider">{title}</span>
            <span className="ml-2 text-[10px] text-muted-foreground font-semibold">{rows.filter((r) => r.name.trim()).length}/5 項</span>
          </div>
          <Button
            type="button"
            variant="outline"
            disabled={disabled || rows.length >= 5}
            onClick={addRow}
            className="h-8 rounded-2xl px-3 text-xs gap-1.5 border-slate-200/80 dark:border-slate-800 disabled:opacity-50"
          >
            <Plus className="size-4 text-sky-500" />
            新增項目
          </Button>
        </div>

        {rows.map((row, idx) => (
          <div
            key={idx}
            className={`rounded-2xl border border-slate-100 dark:border-slate-900/50 p-3 space-y-2 ${rowClassName ?? "bg-white/40 dark:bg-slate-950/20"}`}
          >
            <div className="flex items-center gap-2">
              <Input
                value={row.name}
                maxLength={80}
                onChange={(e) => setField(idx, "name", e.target.value)}
                placeholder="食物名稱"
                disabled={disabled}
                className="flex-1 rounded-xl text-xs py-4 border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 focus-visible:ring-sky-500"
              />
              <Button
                type="button"
                variant="ghost"
                size="icon"
                disabled={disabled}
                onClick={() => removeRow(idx)}
                aria-label="刪除此項目"
                className="rounded-full text-muted-foreground hover:text-rose-500 hover:bg-rose-500/5 size-8 shrink-0"
              >
                <Trash2 className="size-4" />
              </Button>
            </div>
            <div className="grid grid-cols-5 gap-1.5">
              <NumField label="克 (g)" value={row.grams} onChange={(v) => setField(idx, "grams", v)} disabled={disabled} />
              <NumField label="熱量" value={row.kcal} onChange={(v) => setField(idx, "kcal", v)} disabled={disabled} />
              <NumField label="蛋白" value={row.protein} onChange={(v) => setField(idx, "protein", v)} disabled={disabled} />
              <NumField label="脂肪" value={row.fat} onChange={(v) => setField(idx, "fat", v)} disabled={disabled} />
              <NumField label="碳水" value={row.carb} onChange={(v) => setField(idx, "carb", v)} disabled={disabled} />
            </div>
            {row.nutritionBasis && (
              <ServingPresetControls
                basisGrams={row.nutritionBasis.grams}
                disabled={disabled}
                onSelect={(grams) => setField(idx, "grams", String(grams))}
              />
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

function FoodSearchPanel({ disabled, onSelect }: { disabled: boolean; onSelect: (food: FoodCatalogItem) => void }) {
  const [query, setQuery] = useState("");
  const foods = useFoodSearch(query);

  function select(food: FoodCatalogItem) {
    onSelect(food);
    setQuery("");
  }

  return (
    <div className="space-y-2 rounded-2xl border border-sky-500/10 bg-white/45 p-3 dark:bg-slate-950/20">
      <Label className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
        食物資料庫
      </Label>
      <div className="relative">
        <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          disabled={disabled}
          placeholder="搜尋雞胸肉、白飯、地瓜..."
          aria-label="食物資料庫"
          className="rounded-xl border-slate-200/80 bg-white/60 py-4 pl-9 text-xs dark:border-slate-800 dark:bg-slate-900/50 focus-visible:ring-sky-500"
        />
      </div>
      {query.trim() && (
        <div className="grid gap-2 sm:grid-cols-2">
          {foods.isFetching ? (
            <div className="rounded-xl border border-dashed border-slate-200 p-3 text-xs text-muted-foreground dark:border-slate-800">
              搜尋中...
            </div>
          ) : foods.data && foods.data.length > 0 ? (
            foods.data.map((food) => (
              <button
                key={food.id}
                type="button"
                disabled={disabled}
                onClick={() => select(food)}
                className="rounded-xl border border-slate-100 bg-white/70 p-3 text-left transition-colors hover:border-sky-500/30 hover:bg-sky-500/5 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-900/60 dark:bg-slate-950/30"
                aria-label={`加入${food.name}`}
              >
                <span className="block text-xs font-bold text-slate-700 dark:text-slate-300">{food.name}</span>
                <span className="mt-1 block text-[10px] font-medium text-muted-foreground">
                  {food.category} · {food.servingGrams}g · {food.kcal} kcal
                </span>
              </button>
            ))
          ) : (
            <div className="rounded-xl border border-dashed border-slate-200 p-3 text-xs text-muted-foreground dark:border-slate-800">
              找不到符合的食物
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function ServingPresetControls({
  basisGrams,
  disabled,
  onSelect,
}: {
  basisGrams: number;
  disabled?: boolean;
  onSelect: (grams: number) => void;
}) {
  const presets = [
    { label: "0.5 份", multiplier: 0.5 },
    { label: "1 份", multiplier: 1 },
    { label: "1.5 份", multiplier: 1.5 },
    { label: "2 份", multiplier: 2 },
  ];
  return (
    <div className="flex flex-wrap items-center gap-1.5 pt-1">
      <span className="text-[9px] font-semibold text-muted-foreground">常用份量</span>
      {presets.map((preset) => {
        const grams = Math.round(basisGrams * preset.multiplier);
        return (
          <button
            key={preset.label}
            type="button"
            disabled={disabled}
            onClick={() => onSelect(grams)}
            aria-label={`套用${preset.label}`}
            className="rounded-full border border-slate-200/80 bg-white/60 px-2.5 py-1 text-[10px] font-semibold text-slate-600 transition-colors hover:border-sky-500/30 hover:bg-sky-500/5 hover:text-sky-600 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-800 dark:bg-slate-900/40 dark:text-slate-300"
          >
            {preset.label}
          </button>
        );
      })}
    </div>
  );
}

function NumField({
  label,
  value,
  onChange,
  disabled,
}: {
  label: string;
  value: number;
  onChange: (v: string) => void;
  disabled?: boolean;
}) {
  return (
    <div className="grid gap-1">
      <span className="text-[9px] text-muted-foreground font-semibold text-center uppercase tracking-wide">{label}</span>
      <Input
        type="number"
        min={0}
        inputMode="decimal"
        value={Number.isFinite(value) ? value : 0}
        onChange={(e) => onChange(e.target.value)}
        disabled={disabled}
        className="rounded-xl text-center text-xs py-3 px-1 border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 focus-visible:ring-emerald-500"
      />
    </div>
  );
}

export function emptyFoodItem(): EditableFoodItem {
  return { name: "", grams: 0, kcal: 0, protein: 0, fat: 0, carb: 0, confidence: 1 };
}

export function toEditableFoodItem(item: FoodItem): EditableFoodItem {
  return { ...item };
}

export function cleanFoodRows(rows: EditableFoodItem[]): FoodItem[] {
  return rows
    .map((r) => ({
      name: r.name.trim(),
      grams: r.grams,
      kcal: r.kcal,
      protein: r.protein,
      fat: r.fat,
      carb: r.carb,
      confidence: 1,
    }))
    .filter((r) => r.name.length > 0);
}

export function summarizeItems(items: FoodItem[]) {
  return {
    kcal: items.reduce((s, r) => s + (Number.isFinite(r.kcal) ? r.kcal : 0), 0),
    protein: Number(items.reduce((s, r) => s + (Number.isFinite(r.protein) ? r.protein : 0), 0).toFixed(2)),
    fat: Number(items.reduce((s, r) => s + (Number.isFinite(r.fat) ? r.fat : 0), 0).toFixed(2)),
    carb: Number(items.reduce((s, r) => s + (Number.isFinite(r.carb) ? r.carb : 0), 0).toFixed(2)),
  };
}

function catalogFoodToItem(food: FoodCatalogItem): EditableFoodItem {
  return {
    name: food.name,
    grams: food.servingGrams,
    kcal: food.kcal,
    protein: food.protein,
    fat: food.fat,
    carb: food.carb,
    confidence: 1,
    nutritionBasis: {
      grams: food.servingGrams,
      kcal: food.kcal,
      protein: food.protein,
      fat: food.fat,
      carb: food.carb,
    },
  };
}

function updateFoodRowField(row: EditableFoodItem, field: keyof FoodItem, raw: string): EditableFoodItem {
  if (field === "name") {
    return { ...row, name: raw };
  }
  const value = Number(raw);
  if (field === "grams" && row.nutritionBasis && Number.isFinite(value)) {
    return scaleFoodRow(row, value);
  }
  const next = { ...row, [field]: value };
  if (field === "kcal" || field === "protein" || field === "fat" || field === "carb") {
    delete next.nutritionBasis;
  }
  return next;
}

function scaleFoodRow(row: EditableFoodItem, grams: number): EditableFoodItem {
  const basis = row.nutritionBasis;
  if (!basis || !Number.isFinite(grams) || basis.grams <= 0) {
    return { ...row, grams };
  }
  const ratio = grams / basis.grams;
  return {
    ...row,
    grams,
    kcal: Math.round(basis.kcal * ratio),
    protein: roundMacro(basis.protein * ratio),
    fat: roundMacro(basis.fat * ratio),
    carb: roundMacro(basis.carb * ratio),
  };
}

function roundMacro(value: number): number {
  return Number(value.toFixed(2));
}

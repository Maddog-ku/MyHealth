import { Copy, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { mealSlotLabel } from "@/lib/mealSlots";
import type { FavoriteMeal, RecentMeal } from "@/types/api";

export function QuickReusePanel({
  selectedSlot,
  recent,
  favorites,
  loading,
  copying,
  deletingFavorite,
  onCopyRecent,
  onCopyFavorite,
  onDeleteFavorite,
}: {
  selectedSlot: string;
  recent: RecentMeal[];
  favorites: FavoriteMeal[];
  loading: boolean;
  copying: boolean;
  deletingFavorite: boolean;
  onCopyRecent: (id: number) => void;
  onCopyFavorite: (id: number) => void;
  onDeleteFavorite: (id: number) => void;
}) {
  if (loading) {
    return <Skeleton className="h-32 w-full rounded-3xl" />;
  }

  if (recent.length === 0 && favorites.length === 0) {
    return null;
  }

  return (
    <Card className="border border-slate-100/80 dark:border-slate-900/60 bg-white/60 dark:bg-slate-950/35 backdrop-blur-xl shadow-xl shadow-slate-100/40 dark:shadow-none rounded-3xl overflow-hidden">
      <CardHeader className="pb-3">
        <CardTitle className="text-sm font-bold flex items-center gap-2">
          <Copy className="size-4 text-sky-500" />
          快速重用
        </CardTitle>
        <CardDescription className="text-xs">
          複製後會加入今日{mealSlotLabel(selectedSlot)}，不重新執行 AI 分析
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4 px-6 pb-6 lg:grid-cols-2">
        <ReuseColumn
          title="常用餐點"
          empty="尚未收藏常用餐點"
          items={favorites.map((favorite) => ({
            id: favorite.id,
            title: favorite.name,
            meta: `${mealSlotLabel(favorite.slot)} · ${favorite.totalKcal} kcal`,
            description: favorite.description,
            removable: true,
          }))}
          copying={copying}
          deleting={deletingFavorite}
          onCopy={onCopyFavorite}
          onDelete={onDeleteFavorite}
        />
        <ReuseColumn
          title="最近餐點"
          empty="沒有可重用的歷史餐點"
          items={recent.map((meal) => ({
            id: meal.id,
            title: meal.displayName,
            meta: `${new Date(`${meal.date}T00:00:00`).toLocaleDateString("zh-TW", {
              month: "numeric",
              day: "numeric",
            })} · ${mealSlotLabel(meal.slot)} · ${meal.totalKcal} kcal`,
            description: meal.description,
            removable: false,
          }))}
          copying={copying}
          deleting={false}
          onCopy={onCopyRecent}
        />
      </CardContent>
    </Card>
  );
}

function ReuseColumn({
  title,
  empty,
  items,
  copying,
  deleting,
  onCopy,
  onDelete,
}: {
  title: string;
  empty: string;
  items: Array<{ id: number; title: string; meta: string; description?: string; removable: boolean }>;
  copying: boolean;
  deleting: boolean;
  onCopy: (id: number) => void;
  onDelete?: (id: number) => void;
}) {
  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between px-1">
        <span className="text-[10px] text-muted-foreground font-semibold uppercase tracking-wider">{title}</span>
        <span className="text-[10px] text-muted-foreground font-semibold">{items.length}</span>
      </div>
      {items.length > 0 ? (
        <div className="space-y-2">
          {items.map((item) => (
            <div
              key={`${title}-${item.id}`}
              className="flex items-center justify-between gap-3 rounded-2xl border border-slate-100 dark:border-slate-900/50 bg-white/45 dark:bg-slate-950/20 p-3"
            >
              <div className="min-w-0">
                <p className="truncate text-xs font-bold text-slate-700 dark:text-slate-300">{item.title}</p>
                <p className="mt-0.5 truncate text-[10px] font-medium text-muted-foreground">{item.meta}</p>
                {item.description && <p className="mt-1 truncate text-[10px] text-muted-foreground">{item.description}</p>}
              </div>
              <div className="flex shrink-0 items-center gap-1">
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  disabled={copying}
                  onClick={() => onCopy(item.id)}
                  aria-label={`複製${item.title}到今日`}
                  className="size-8 rounded-full text-muted-foreground hover:bg-sky-500/5 hover:text-sky-500"
                >
                  <Copy className="size-4" />
                </Button>
                {item.removable && onDelete && (
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    disabled={deleting}
                    onClick={() => onDelete(item.id)}
                    aria-label={`移除常用餐點${item.title}`}
                    className="size-8 rounded-full text-muted-foreground hover:bg-rose-500/5 hover:text-rose-500"
                  >
                    <X className="size-4" />
                  </Button>
                )}
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="rounded-2xl border border-dashed border-slate-200 dark:border-slate-800 p-4 text-center text-xs text-muted-foreground">
          {empty}
        </div>
      )}
    </div>
  );
}

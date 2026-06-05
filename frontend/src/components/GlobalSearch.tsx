import { useEffect, useState } from "react";
import { Search, Salad, Dumbbell, Loader2 } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { useSearch } from "@/hooks/useSearch";
import type { SearchResult } from "@/types/api";

export function GlobalSearch() {
  const [open, setOpen] = useState(false);
  const [q, setQ] = useState("");
  const [debounced, setDebounced] = useState("");
  const navigate = useNavigate();

  useEffect(() => {
    const t = setTimeout(() => setDebounced(q), 250);
    return () => clearTimeout(t);
  }, [q]);

  // Reset when the dialog closes so it opens fresh next time.
  useEffect(() => {
    if (!open) {
      setQ("");
      setDebounced("");
    }
  }, [open]);

  const { data, isFetching } = useSearch(debounced);
  const term = debounced.trim();
  const results = data?.results ?? [];

  function go(r: SearchResult) {
    setOpen(false);
    navigate(r.type === "MEAL" ? "/meals" : "/workouts");
  }

  return (
    <>
      <Button
        variant="ghost"
        size="icon"
        className="rounded-full text-muted-foreground hover:text-foreground"
        onClick={() => setOpen(true)}
        title="搜尋"
        aria-label="搜尋"
      >
        <Search className="size-5" />
      </Button>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-w-lg p-0 gap-0 overflow-hidden">
          <div className="flex items-center gap-2 px-4 py-3 border-b border-slate-100 dark:border-slate-900">
            <Search className="size-4 text-muted-foreground shrink-0" />
            <input
              autoFocus
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="搜尋餐點、運動…（例：雞胸肉、腹肌）"
              className="flex-1 bg-transparent text-sm outline-none placeholder:text-muted-foreground"
            />
            {isFetching && <Loader2 className="size-4 animate-spin text-muted-foreground" />}
          </div>

          <div className="max-h-[60vh] overflow-y-auto">
            {term.length === 0 ? (
              <p className="px-4 py-8 text-center text-xs text-muted-foreground">輸入關鍵字搜尋你的飲食與運動紀錄</p>
            ) : results.length === 0 && !isFetching ? (
              <p className="px-4 py-8 text-center text-xs text-muted-foreground">找不到「{term}」的相關紀錄</p>
            ) : (
              <ul className="divide-y divide-slate-50 dark:divide-slate-900/60">
                {results.map((r) => (
                  <li key={`${r.type}-${r.id}`}>
                    <button
                      onClick={() => go(r)}
                      className="w-full text-left flex items-center gap-3 px-4 py-3 hover:bg-slate-50 dark:hover:bg-slate-900/40 transition-colors"
                    >
                      <span
                        className={`flex size-9 items-center justify-center rounded-xl shrink-0 ${
                          r.type === "MEAL"
                            ? "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400"
                            : "bg-indigo-500/10 text-indigo-600 dark:text-indigo-400"
                        }`}
                      >
                        {r.type === "MEAL" ? <Salad className="size-4.5" /> : <Dumbbell className="size-4.5" />}
                      </span>
                      <span className="flex-1 min-w-0">
                        <span className="block text-sm font-semibold text-slate-800 dark:text-slate-100 truncate">{r.title}</span>
                        <span className="block text-xs text-muted-foreground">
                          {r.subtitle} · {new Date(r.date).toLocaleDateString("zh-TW", { month: "numeric", day: "numeric" })}
                        </span>
                      </span>
                      <span className="text-xs font-bold text-muted-foreground shrink-0">{r.kcal} kcal</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}

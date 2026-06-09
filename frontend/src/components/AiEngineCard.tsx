import { Cpu, Sparkles } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useAiStatus } from "@/hooks/useAiStatus";

/** Local AI inference engine status: provider, models, and the idle release-on-use behaviour. */
export function AiEngineCard() {
  const ai = useAiStatus();

  return (
    <Card id="ai-engine" className="scroll-mt-24 border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden card-hover-effect flex flex-col justify-between">
      <div>
        <CardHeader className="pb-3">
          <CardTitle className="text-lg font-bold flex items-center gap-2">
            <Cpu className="size-4.5 text-emerald-500" />
            AI 核心智能引擎
          </CardTitle>
          <CardDescription className="text-xs">管理本機推論引擎與深度分析狀態</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {ai.isLoading ? (
            <div className="space-y-3">
              <Skeleton className="h-4 w-full rounded-md" />
              <Skeleton className="h-4 w-[80%] rounded-md" />
              <Skeleton className="h-4 w-[60%] rounded-md" />
            </div>
          ) : ai.data ? (
            <div className="space-y-3.5 pt-1">
              <div className="flex items-center justify-between p-3 rounded-2xl bg-slate-50/50 dark:bg-slate-900/20 border border-slate-100 dark:border-slate-900/50">
                <span className="text-xs text-muted-foreground font-medium">引擎狀態</span>
                <div className="flex items-center gap-2">
                  <span className="relative flex h-2 w-2">
                    <span className={`relative inline-flex rounded-full h-2 w-2 ${ai.data.loaded ? "bg-emerald-500" : "bg-amber-500"}`}></span>
                  </span>
                  <Badge
                    variant="secondary"
                    className={`rounded-xl text-[10px] font-bold px-2 py-0.5 border ${
                      ai.data.loaded
                        ? "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/10"
                        : "bg-amber-500/10 text-amber-600 dark:text-amber-400 border-amber-500/10"
                    }`}
                  >
                    {ai.data.loaded ? "運作中 / 已載入" : "閒置等待中"}
                  </Badge>
                </div>
              </div>

              <div className="grid gap-2.5 text-xs">
                <div className="flex items-center justify-between py-1 border-b border-slate-50 dark:border-slate-900/30">
                  <span className="text-muted-foreground">推理主機</span>
                  <span className="font-semibold">{ai.data.provider}</span>
                </div>
                <div className="flex items-center justify-between py-1 border-b border-slate-50 dark:border-slate-900/30">
                  <span className="text-muted-foreground">主語言模型</span>
                  <span className="font-mono text-[10px] bg-slate-100 dark:bg-slate-900 px-2 py-0.5 rounded-md font-semibold truncate max-w-[150px]" title={ai.data.textModel}>
                    {ai.data.textModel}
                  </span>
                </div>
                <div className="flex items-center justify-between py-1 border-b border-slate-50 dark:border-slate-900/30">
                  <span className="text-muted-foreground">視覺辨識模型</span>
                  <span className="font-mono text-[10px] bg-slate-100 dark:bg-slate-900 px-2 py-0.5 rounded-md font-semibold truncate max-w-[150px]" title={ai.data.visionModel}>
                    {ai.data.visionModel}
                  </span>
                </div>
              </div>
            </div>
          ) : (
            <div className="grid h-24 place-items-center rounded-2xl bg-slate-50/50 dark:bg-slate-900/20 border border-dashed border-slate-200 dark:border-slate-800 text-xs text-muted-foreground text-center p-6 leading-relaxed select-none">
              智慧引擎離線，無法取得即時分析服務。
            </div>
          )}
        </CardContent>
      </div>

      <CardContent className="pt-2">
        {ai.data && (
          <div className="p-3 rounded-2xl bg-amber-500/5 border border-amber-500/10 text-[10px] text-amber-700 dark:text-amber-300 flex items-start gap-2 leading-relaxed">
            <Sparkles className="size-4 shrink-0 text-amber-500 mt-0.5" />
            <span>
              本機推論優化啟動中：當閒置超過 <strong>{ai.data.idleTimeoutSec}秒</strong>，將自動釋放顯存 (VRAM) 以降低功耗並釋放記憶體。
            </span>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

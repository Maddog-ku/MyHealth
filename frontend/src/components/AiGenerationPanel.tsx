import { Activity, Brain, Camera, Dumbbell, Lightbulb, Loader2, Sparkles, UtensilsCrossed } from "lucide-react";
import type { ReactNode } from "react";
import { useMemo } from "react";
import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";

type GenerationKind = "workout" | "meal";

const COPY: Record<GenerationKind, {
  icon: ReactNode;
  title: string;
  description: string;
  steps: string[];
  preview: string[];
  tipsTitle: string;
  tips: string[];
}> = {
  workout: {
    icon: <Dumbbell className="size-5" />,
    title: "AI 正在規劃訓練菜單",
    description: "本地模型正在依照分類、時長與強度產生安全的動作組合。",
    steps: ["讀取訓練條件", "套用安全限制", "整理動作與休息時間"],
    preview: ["動作名稱", "組數與次數", "休息秒數", "預估消耗"],
    tipsTitle: "運動小知識",
    tips: [
      "阻力訓練不一定要練到力竭，保留 1 到 3 次餘裕通常更容易維持動作品質。",
      "同一肌群給足恢復時間，通常比每天硬練更有利於穩定進步。",
      "熱身的目的不是把自己操累，而是讓關節活動度和神經反應先醒過來。",
      "深蹲與弓箭步先控制下放速度，比一開始追求重量更能降低代償。",
      "訓練紀錄最有價值的是趨勢：重量、次數、休息時間都比單次表現更重要。",
    ],
  },
  meal: {
    icon: <UtensilsCrossed className="size-5" />,
    title: "AI 正在分析餐點",
    description: "本地模型正在辨識照片或文字，並用保守規則估算營養。",
    steps: ["讀取餐點資訊", "辨識可見食物", "計算熱量與營養素"],
    preview: ["食物項目", "份量估算", "三大營養素", "可信度"],
    tipsTitle: "飲食小知識",
    tips: [
      "同一份餐點先估蛋白質來源，再估主食與油脂，通常比直接猜總熱量更穩。",
      "醬料、油煎與堅果很容易被低估；拍照時讓它們入鏡會讓紀錄更接近現實。",
      "蔬菜熱量通常不高，但能增加飽足感，也讓餐點體積更容易被判斷。",
      "外食便當的白飯份量差異很大，半碗、一碗、滿盒會讓熱量落差很明顯。",
      "AI 估算適合看長期趨勢，單餐誤差可以透過手動修正慢慢校準。",
    ],
  },
};

export function AiGenerationPanel({ kind, className }: { kind: GenerationKind; className?: string }) {
  const copy = COPY[kind];
  const tip = useMemo(() => copy.tips[Math.floor(Math.random() * copy.tips.length)], [copy.tips]);

  return (
    <Card
      className={cn(
        "relative overflow-hidden rounded-3xl border border-emerald-500/20 bg-white/75 shadow-xl shadow-emerald-500/10 backdrop-blur-xl dark:bg-slate-950/50 dark:shadow-none",
        className,
      )}
      aria-live="polite"
      aria-busy="true"
    >
      <div className="absolute inset-x-0 top-0 h-1 overflow-hidden bg-emerald-500/10">
        <div className="h-full w-1/2 animate-[generation-slide_1.45s_ease-in-out_infinite] bg-gradient-to-r from-transparent via-emerald-400 to-transparent" />
      </div>

      <CardContent className="grid gap-5 p-6 md:grid-cols-[1fr_1.1fr] md:items-center">
        <div className="flex items-start gap-4">
          <div className="relative flex size-14 shrink-0 items-center justify-center rounded-2xl bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
            <span className="absolute inline-flex size-full animate-ping rounded-2xl bg-emerald-400/20" />
            <span className="relative">{copy.icon}</span>
          </div>

          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h3 className="text-sm font-extrabold text-slate-800 dark:text-slate-100">{copy.title}</h3>
              <Loader2 className="size-4 animate-spin text-emerald-500" />
            </div>
            <p className="mt-1 max-w-xl text-xs leading-relaxed text-slate-500 dark:text-slate-400">
              {copy.description}
            </p>
            <div className="mt-4 grid gap-2">
              {copy.steps.map((step, index) => (
                <div key={step} className="flex items-center gap-2 text-[11px] font-semibold text-slate-500 dark:text-slate-400">
                  <span
                    className="flex size-5 items-center justify-center rounded-full bg-emerald-500/10 text-[10px] text-emerald-600 dark:text-emerald-400"
                    style={{ animationDelay: `${index * 180}ms` }}
                  >
                    {index + 1}
                  </span>
                  <span>{step}</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="grid gap-3 rounded-2xl border border-slate-100 bg-slate-50/70 p-4 dark:border-slate-900 dark:bg-slate-900/30">
          <div className="flex items-center justify-between text-[10px] font-bold uppercase tracking-wider text-slate-400">
            <span className="flex items-center gap-1.5">
              {kind === "meal" ? <Camera className="size-3.5" /> : <Brain className="size-3.5" />}
              生成預覽
            </span>
            <Sparkles className="size-3.5 animate-pulse text-emerald-500" />
          </div>
          {copy.preview.map((label, index) => (
            <div key={label} className="grid grid-cols-[7rem_1fr] items-center gap-3">
              <span className="text-[11px] font-semibold text-slate-500 dark:text-slate-400">{label}</span>
              <span
                className="h-2.5 animate-pulse rounded-full bg-gradient-to-r from-emerald-500/20 via-teal-400/25 to-sky-400/20"
                style={{ width: `${92 - index * 12}%`, animationDelay: `${index * 120}ms` }}
              />
            </div>
          ))}
          <div className="mt-1 flex items-center gap-2 text-[11px] font-medium text-emerald-700 dark:text-emerald-400">
            <Activity className="size-3.5 animate-pulse" />
            本地 AI 推理中，完成後會自動更新列表
          </div>
          <div className="rounded-xl border border-amber-500/10 bg-amber-500/5 p-3 text-[11px] leading-relaxed text-amber-800 dark:text-amber-200">
            <div className="mb-1.5 flex items-center gap-1.5 font-extrabold text-amber-700 dark:text-amber-300">
              <Lightbulb className="size-3.5" />
              {copy.tipsTitle}
            </div>
            <p>{tip}</p>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

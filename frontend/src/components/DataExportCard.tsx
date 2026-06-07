import { useMutation } from "@tanstack/react-query";
import { Download, ShieldCheck, Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { ApiError, api } from "@/api/client";
import { todayLocalISO } from "@/lib/date";

export function DataExportCard() {
  const exportData = useMutation({
    mutationFn: () => api.exportData(),
    onSuccess: (data) => {
      // Build the download entirely in the browser so the JWT-protected fetch is reused
      // and nothing leaves the device beyond the user's own machine.
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `myhealth-export-${todayLocalISO()}.json`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    },
  });

  return (
    <Card className="border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden card-hover-effect">
      <CardHeader className="pb-3">
        <CardTitle className="text-lg font-bold flex items-center gap-2">
          <ShieldCheck className="size-4.5 text-sky-500" />
          資料與隱私
        </CardTitle>
        <CardDescription className="text-xs">
          下載你在 MyHealth 的所有資料（個人檔案、體重量測、運動、餐點、目標、習慣）成一份 JSON 檔，全程在你的裝置完成。
        </CardDescription>
      </CardHeader>
      <CardContent className="px-6 pb-6 space-y-3">
        {exportData.isSuccess && (
          <Alert className="rounded-2xl border-emerald-500/20 bg-emerald-500/5 text-emerald-700 dark:text-emerald-400">
            <Check className="size-4" />
            <AlertTitle className="text-xs font-bold">已開始下載</AlertTitle>
            <AlertDescription className="text-[11px] opacity-90">檔案已存到你的下載資料夾。</AlertDescription>
          </Alert>
        )}
        {exportData.error instanceof ApiError && (
          <Alert variant="destructive" className="rounded-2xl border-rose-500/20 bg-rose-500/5 text-rose-600 dark:text-rose-400">
            <AlertTitle className="text-xs font-bold">匯出失敗</AlertTitle>
            <AlertDescription className="text-[11px] opacity-90">{exportData.error.message}</AlertDescription>
          </Alert>
        )}
        <Button
          type="button"
          variant="outline"
          disabled={exportData.isPending}
          onClick={() => exportData.mutate()}
          className="rounded-2xl py-5 px-6 gap-1.5 border-sky-500/30 text-sky-600 dark:text-sky-400 hover:bg-sky-500/10 font-semibold text-sm"
        >
          {exportData.isPending ? (
            <>
              <span className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
              準備中…
            </>
          ) : (
            <>
              <Download className="size-4" />
              匯出我的資料
            </>
          )}
        </Button>
      </CardContent>
    </Card>
  );
}

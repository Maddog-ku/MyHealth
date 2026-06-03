import { Activity, Dumbbell, LogOut, Salad, Settings as SettingsIcon, UserRound, Sparkles, Moon, Sun, Flame } from "lucide-react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { useLogout, useMe } from "@/hooks/useAuth";
import { AssistantWidget } from "@/components/AssistantWidget";

const navItems = [
  { to: "/", label: "儀表板", icon: Activity, end: true },
  { to: "/workouts", label: "運動菜單", icon: Dumbbell },
  { to: "/meals", label: "飲食追蹤", icon: Salad },
  { to: "/profile", label: "生理指標", icon: UserRound },
  { to: "/settings", label: "系統設定", icon: SettingsIcon },
];

function getGreeting() {
  const hr = new Date().getHours();
  if (hr >= 5 && hr < 12) return "早安，開啟美好活力的一天";
  if (hr >= 12 && hr < 17) return "午安，保持專注與健康節奏";
  if (hr >= 17 && hr < 22) return "傍晚好，享受健康的放鬆時刻";
  return "夜深了，讓身體好好充電休息";
}

export function AppShell() {
  const { data: user } = useMe();
  const logout = useLogout();
  const navigate = useNavigate();

  async function handleLogout() {
    await logout.mutateAsync();
    navigate("/login", { replace: true });
  }

  const greeting = getGreeting();
  const todayString = new Date().toLocaleDateString("zh-TW", {
    month: "long",
    day: "numeric",
    weekday: "long",
  });

  return (
    <div className="flex h-screen w-full flex-col bg-[#fafafc] text-foreground dark:bg-[#090d16] md:flex-row overflow-hidden transition-colors duration-500">
      {/* Sidebar - Desktop Floating Panel */}
      <aside className="hidden md:flex flex-col w-[260px] p-6 h-full border-r border-slate-100 dark:border-slate-900 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl z-20 transition-all duration-300">
        {/* Brand */}
        <div className="flex items-center gap-3 px-3 py-4 mb-8">
          <img src="/logo.svg" alt="" className="size-10 drop-shadow-sm" />
          <div>
            <span className="text-lg font-bold tracking-tight bg-gradient-to-r from-emerald-600 to-teal-500 bg-clip-text text-transparent dark:from-emerald-400 dark:to-teal-300">
              MyHealth
            </span>
            <p className="text-[10px] text-muted-foreground uppercase tracking-widest font-semibold">Intelligence</p>
          </div>
        </div>

        {/* Navigation */}
        <nav className="flex flex-col gap-1.5 flex-1">
          {navItems.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                cn(
                  "flex items-center gap-3 px-4 py-3 rounded-2xl text-sm font-medium text-muted-foreground hover:bg-slate-50 dark:hover:bg-slate-900/50 hover:text-foreground transition-all duration-300 group",
                  isActive &&
                    "bg-gradient-to-r from-emerald-500/10 to-teal-500/5 text-emerald-600 dark:text-emerald-400 shadow-sm border border-emerald-500/10 nav-active-glow",
                )
              }
            >
              {({ isActive }) => (
                <>
                  <Icon
                    className={cn(
                      "size-5 transition-transform duration-300 group-hover:scale-110",
                      isActive ? "text-emerald-500" : "text-muted-foreground group-hover:text-emerald-500",
                    )}
                  />
                  <span>{label}</span>
                </>
              )}
            </NavLink>
          ))}
        </nav>

        {/* User Card & Logout bottom */}
        <div className="mt-auto pt-6 border-t border-slate-100 dark:border-slate-900/60">
          <div className="flex items-center gap-3 p-3 rounded-2xl bg-slate-50/50 dark:bg-slate-900/20 mb-3">
            <div className="flex size-10 items-center justify-center rounded-full bg-emerald-100 dark:bg-emerald-950/50 text-emerald-600 dark:text-emerald-400">
              <UserRound className="size-5" />
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-semibold truncate">{user?.name || "健康行者"}</p>
              <p className="text-xs text-muted-foreground truncate">{user?.email}</p>
            </div>
          </div>

          <Button
            variant="ghost"
            className="w-full justify-start gap-3 rounded-2xl text-muted-foreground hover:text-destructive hover:bg-destructive/5 py-6 transition-all-smooth"
            onClick={handleLogout}
          >
            <LogOut className="size-5" />
            <span>帳號登出</span>
          </Button>
        </div>
      </aside>

      {/* Main Content Area */}
      <div className="flex-1 flex flex-col min-w-0 h-full overflow-hidden">
        {/* Header - Frosted Glass Glassmorphism */}
        <header className="sticky top-0 z-10 flex items-center justify-between gap-4 border-b border-slate-100/80 dark:border-slate-900/40 bg-white/70 dark:bg-slate-950/40 backdrop-blur-md px-6 py-4 transition-all duration-300">
          <div>
            <p className="text-xs font-medium text-muted-foreground tracking-wide">{todayString}</p>
            <div className="flex items-center gap-2 mt-0.5">
              <Sparkles className="size-4 text-emerald-500 dark:text-emerald-400" />
              <h1 className="text-lg md:text-xl font-bold tracking-tight text-slate-800 dark:text-slate-100">
                {user?.name ? `${user.name}，${greeting}` : greeting}
              </h1>
            </div>
          </div>

          <div className="flex items-center gap-3">
            {/* Quick profile info badge */}
            <div className="hidden sm:flex items-center gap-2 px-3.5 py-1.5 rounded-full border border-slate-100 dark:border-slate-900 bg-slate-50/50 dark:bg-slate-900/20 text-xs font-semibold">
              <span className="relative flex h-2 w-2">
                <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
              </span>
              <span className="text-muted-foreground">AI 引擎已連線</span>
            </div>

            <Button
              variant="ghost"
              size="icon"
              className="md:hidden rounded-full text-muted-foreground hover:text-destructive hover:bg-destructive/5"
              onClick={handleLogout}
              title="登出"
            >
              <LogOut className="size-5" />
            </Button>
          </div>
        </header>

        {/* Page Content Panel */}
        <main className="flex-1 overflow-y-auto p-4 md:p-8 pb-24 md:pb-8 bg-[#fafafc] dark:bg-[#070b13] transition-colors duration-500">
          <div className="max-w-6xl mx-auto space-y-6">
            <Outlet />
          </div>
        </main>
      </div>

      {/* Floating Bottom Navigation Bar (Mobile / RWD Focus) */}
      <div className="md:hidden fixed bottom-6 left-4 right-4 z-40">
        <nav className="flex items-center justify-around p-2.5 rounded-3xl bg-white/85 dark:bg-slate-950/80 border border-slate-200/50 dark:border-slate-900/50 backdrop-blur-xl shadow-xl shadow-slate-200/40 dark:shadow-black/50 transition-all duration-300">
          {navItems.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                cn(
                  "flex flex-col items-center justify-center flex-1 py-1.5 rounded-2xl text-[10px] font-medium text-muted-foreground transition-all duration-300 group relative",
                  isActive && "text-emerald-500 dark:text-emerald-400 font-semibold",
                )
              }
            >
              {({ isActive }) => (
                <>
                  <div
                    className={cn(
                      "flex size-9 items-center justify-center rounded-xl transition-all duration-300 mb-0.5",
                      isActive
                        ? "bg-emerald-500/10 text-emerald-500 shadow-sm border border-emerald-500/10"
                        : "group-hover:bg-slate-100 dark:group-hover:bg-slate-900",
                    )}
                  >
                    <Icon className="size-4.5" />
                  </div>
                  <span>{label.substring(0, 3)}</span>
                  {isActive && (
                    <span className="absolute bottom-0 w-1.5 h-1.5 rounded-full bg-emerald-500 dark:bg-emerald-400"></span>
                  )}
                </>
              )}
            </NavLink>
          ))}
        </nav>
      </div>

      {/* Floating AI assistant — present on every page */}
      <AssistantWidget />
    </div>
  );
}

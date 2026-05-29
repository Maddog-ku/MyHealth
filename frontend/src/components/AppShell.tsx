import { Activity, Dumbbell, LogOut, Salad, Settings as SettingsIcon, UserRound } from "lucide-react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { useLogout, useMe } from "@/hooks/useAuth";

const navItems = [
  { to: "/", label: "儀表板", icon: Activity, end: true },
  { to: "/workouts", label: "運動", icon: Dumbbell },
  { to: "/meals", label: "飲食", icon: Salad },
  { to: "/settings", label: "設定", icon: SettingsIcon },
];

export function AppShell() {
  const { data: user } = useMe();
  const logout = useLogout();
  const navigate = useNavigate();

  async function handleLogout() {
    await logout.mutateAsync();
    navigate("/login", { replace: true });
  }

  return (
    <div className="grid min-h-full grid-cols-1 md:grid-cols-[240px_1fr]">
      <aside className="flex flex-col gap-5 border-b border-border bg-card p-4 md:border-b-0 md:border-r md:p-5">
        <div className="flex items-center gap-2 text-lg font-bold">
          <Activity className="size-6 text-primary" aria-hidden />
          <span>MyHealth</span>
        </div>
        <nav className="flex gap-1 overflow-x-auto md:flex-col md:gap-1.5">
          {navItems.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                cn(
                  "flex min-h-10 items-center gap-2.5 rounded-md px-3 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground",
                  isActive && "bg-secondary text-foreground",
                )
              }
            >
              <Icon className="size-4" aria-hidden />
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>
        <Button
          variant="ghost"
          className="mt-auto hidden justify-start gap-2 text-muted-foreground md:flex"
          onClick={handleLogout}
        >
          <LogOut className="size-4" aria-hidden />
          登出
        </Button>
      </aside>

      <main className="flex min-h-0 flex-col">
        <header className="flex items-center justify-between gap-3 border-b border-border bg-background/80 px-6 py-4 backdrop-blur">
          <div>
            <p className="text-xs text-muted-foreground">{new Date().toLocaleDateString("zh-TW", { weekday: "long" })}</p>
            <h1 className="text-xl font-semibold tracking-tight">{user?.name ? `${user.name} 的健康` : "MyHealth"}</h1>
          </div>
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <UserRound className="size-4" aria-hidden />
            <span className="hidden sm:inline">{user?.email}</span>
            <Button variant="ghost" size="sm" className="md:hidden" onClick={handleLogout}>
              <LogOut className="size-4" aria-hidden />
            </Button>
          </div>
        </header>
        <div className="flex-1 overflow-y-auto p-6">
          <Outlet />
        </div>
      </main>
    </div>
  );
}

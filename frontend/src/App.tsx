import { lazy, Suspense, useEffect } from "react";
import { Route, Routes } from "react-router-dom";
import { AppShell } from "@/components/AppShell";
import { RequireAuth } from "@/components/RequireAuth";
import { useTheme } from "@/hooks/useTheme";
import { useFontScale } from "@/hooks/useFontScale";

const DashboardPage = lazy(() => import("@/pages/DashboardPage").then((m) => ({ default: m.DashboardPage })));
const AccountPage = lazy(() => import("@/pages/AccountPage").then((m) => ({ default: m.AccountPage })));
const LoginPage = lazy(() => import("@/pages/LoginPage").then((m) => ({ default: m.LoginPage })));
const MealsPage = lazy(() => import("@/pages/MealsPage").then((m) => ({ default: m.MealsPage })));
const ProgressPage = lazy(() => import("@/pages/ProgressPage").then((m) => ({ default: m.ProgressPage })));
const ProfilePage = lazy(() => import("@/pages/ProfilePage").then((m) => ({ default: m.ProfilePage })));
const SettingsPage = lazy(() => import("@/pages/SettingsPage").then((m) => ({ default: m.SettingsPage })));
const WorkoutsPage = lazy(() => import("@/pages/WorkoutsPage").then((m) => ({ default: m.WorkoutsPage })));

export function App() {
  useTheme();
  useFontScale();

  // Block dragging images out of the app (e.g. into a new tab). CSS user-drag covers
  // WebKit/Chromium; this guard also covers Firefox, which ignores that property.
  useEffect(() => {
    const blockImageDrag = (e: DragEvent) => {
      if (e.target instanceof HTMLImageElement) e.preventDefault();
    };
    document.addEventListener("dragstart", blockImageDrag);
    return () => document.removeEventListener("dragstart", blockImageDrag);
  }, []);

  return (
    <Suspense fallback={<div className="p-6 text-sm text-muted-foreground">載入中…</div>}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          element={
            <RequireAuth>
              <AppShell />
            </RequireAuth>
          }
        >
          <Route index element={<DashboardPage />} />
          <Route path="/workouts" element={<WorkoutsPage />} />
          <Route path="/meals" element={<MealsPage />} />
          <Route path="/progress" element={<ProgressPage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/account" element={<AccountPage />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Route>
      </Routes>
    </Suspense>
  );
}

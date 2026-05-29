import { Route, Routes } from "react-router-dom";
import { AppShell } from "@/components/AppShell";
import { RequireAuth } from "@/components/RequireAuth";
import { useTheme } from "@/hooks/useTheme";
import { DashboardPage } from "@/pages/DashboardPage";
import { LoginPage } from "@/pages/LoginPage";
import { MealsPage } from "@/pages/MealsPage";
import { SettingsPage } from "@/pages/SettingsPage";
import { WorkoutsPage } from "@/pages/WorkoutsPage";

export function App() {
  useTheme();

  return (
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
        <Route path="/settings" element={<SettingsPage />} />
      </Route>
    </Routes>
  );
}

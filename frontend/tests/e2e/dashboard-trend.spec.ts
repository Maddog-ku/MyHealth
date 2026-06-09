import { expect, test, type Page } from "@playwright/test";

// Mocked-backend e2e (same pattern as auth.spec.ts).

const USER = {
  id: 1, email: "demo@example.com", name: "Demo", role: "USER",
  profile: { gender: "female", heightCm: 165, weightKg: 55, equipment: [], theme: "system", language: "zh-TW" },
  createdAt: "2026-05-30T00:00:00Z",
};
function today(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

async function seedAuth(page: Page) {
  await page.addInitScript((key) => {
    localStorage.setItem("accessToken", "t");
    localStorage.setItem("refreshToken", "t");
    localStorage.setItem(key as string, "done"); // suppress daily check-in dialog
  }, `weightCheckIn:${today()}`);
}

test("the trend chart switches metric, and shows an empty state for one without data", async ({ page }) => {
  await seedAuth(page);

  // Series has weight but no body-fat measurements.
  const series = [
    { date: "2026-05-31", intakeKcal: 0, burnKcal: 0, weightKg: 55, bodyFatPct: null, muscleMassKg: null, waistCm: null, bodyWaterPct: null },
    { date: today(), intakeKcal: 0, burnKcal: 0, weightKg: 54.6, bodyFatPct: null, muscleMassKg: null, waistCm: null, bodyWaterPct: null },
  ];

  await page.route("**/api/v1/me", (r) => r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(USER) }));
  await page.route("**/api/v1/stats/daily**", (r) => r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ date: today(), intakeKcal: 0, burnKcal: 0, netKcal: 0, protein: 0, fat: 0, carb: 0, weightKg: 54.6, goalKcal: 1700, workoutsDone: 0, workoutsPlanned: 0 }) }));
  await page.route("**/api/v1/stats/range**", (r) => r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ from: "", to: "", series }) }));
  await page.route("**/api/v1/ai/status**", (r) => r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ provider: "local", textModel: "x", visionModel: "x", loaded: false, idleTimeoutSec: 60 }) }));
  await page.route("**/api/v1/habits/daily**", (r) => r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ date: today(), completed: 0, total: 4, items: [] }) }));
  // Other Progress-page cards: empty/minimal so they don't hit the network.
  await page.route("**/api/v1/weight-goal**", (r) => r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ progress: null }) }));
  await page.route("**/api/v1/workout-goal**", (r) => r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ progress: null }) }));
  await page.route("**/api/v1/streak**", (r) => r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ mealStreak: { current: 0, longest: 0, lastActiveDate: null }, workoutStreak: { current: 0, longest: 0, lastActiveDate: null }, overallStreak: { current: 0, longest: 0, lastActiveDate: null }, achievements: [], newlyUnlocked: [] }) }));
  await page.route("**/api/v1/reports/weekly**", (r) => r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ weekStart: today(), weekEnd: today(), summary: { totalIntakeKcal: 0, avgIntakeKcal: 0, totalBurnKcal: 0, netKcal: 0, goalKcal: 1700, weightStart: null, weightEnd: null, weightDelta: null, workoutsDone: 0, mealsLogged: 0, daysCovered: 0 }, adherence: { caloriePct: 0, proteinPct: 0, workoutPct: 0, workoutTarget: null, loggingPct: 0, daysLogged: 0, daysCovered: 0 }, trends: [], narrative: null, generatedAt: null }) }));

  await page.goto("/progress");

  // Default metric = 體重, default range = 30 days: data present → no empty state.
  await expect(page.getByText("近 30 日身體量測趨勢")).toBeVisible();
  await expect(page.getByText(/目前尚無/)).toBeHidden();

  // Switch the range to 7 days → the title reflects the new window.
  await page.getByRole("button", { name: "7 天", exact: true }).click();
  await expect(page.getByText("近 7 日身體量測趨勢")).toBeVisible();

  // Switch to 體脂率 (no data) → empty state names that metric.
  await page.getByRole("button", { name: "體脂率", exact: true }).click();
  await expect(page.getByText("目前尚無「體脂率」的量測數據。請至「生理指標」更新身體數據，趨勢將同步於此呈現。")).toBeVisible();

  // Back to 體重 → empty state goes away.
  await page.getByRole("button", { name: "體重", exact: true }).click();
  await expect(page.getByText(/目前尚無/)).toBeHidden();
});

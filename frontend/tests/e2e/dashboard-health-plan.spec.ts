import { expect, test, type Page } from "@playwright/test";

const USER = {
  id: 1,
  email: "demo@example.com",
  name: "Demo",
  role: "USER",
  profile: { gender: "female", heightCm: 165, weightKg: 76, goal: "fat_loss", equipment: [], theme: "system", language: "zh-TW" },
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
    localStorage.setItem(key as string, "done");
  }, `weightCheckIn:${today()}`);
}

test("dashboard shows the integrated health plan summary and next action", async ({ page }) => {
  await seedAuth(page);

  await page.route("**/api/v1/me", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(USER) }),
  );
  await page.route("**/api/v1/stats/daily**", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        date: today(),
        intakeKcal: 1200,
        burnKcal: 250,
        netKcal: 950,
        protein: 67,
        fat: 30,
        carb: 120,
        weightKg: 76,
        goalKcal: 1700,
        workoutsDone: 1,
        workoutsPlanned: 1,
      }),
    }),
  );
  await page.route("**/api/v1/stats/range**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ from: "", to: "", series: [] }) }),
  );
  await page.route("**/api/v1/ai/status**", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ provider: "local", textModel: "x", visionModel: "x", loaded: false, idleTimeoutSec: 60 }),
    }),
  );
  await page.route("**/api/v1/habits/daily**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ date: today(), completed: 0, total: 4, items: [] }) }),
  );
  await page.route("**/api/v1/health-plan/settings", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        primaryGoal: "fat_loss",
        currentWeightKg: 76,
        weightGoal: {
          targetWeightKg: 70,
          startWeightKg: 76,
          currentWeightKg: 76,
          startDate: today(),
          targetDate: "2026-07-01",
          remainingKg: -6,
          changeSoFarKg: 0,
          progressPct: 25,
          ratePerWeekKg: null,
          projectedDate: null,
          onTrack: null,
          achieved: false,
          createdAt: "2026-06-08T00:00:00Z",
        },
        workoutGoal: {
          targetSessionsPerWeek: 4,
          completedThisWeek: 2,
          remaining: 2,
          progressPct: 50,
          weekStart: today(),
          achieved: false,
          createdAt: "2026-06-08T00:00:00Z",
        },
      }),
    }),
  );
  await page.route(/.*\/api\/v1\/health-plan(?:\?.*)?$/, (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        date: today(),
        primaryGoal: "減脂",
        readinessScore: 78,
        nutrition: {
          goalKcal: 1700,
          budgetKcal: 1950,
          intakeKcal: 1200,
          burnKcal: 250,
          remainingKcal: 750,
          consumedPct: 62,
          over: false,
          macros: [{ name: "protein", targetG: 149, consumedG: 67, pct: 45 }],
        },
        weight: {
          configured: true,
          currentWeightKg: 76,
          targetWeightKg: 70,
          remainingKg: -6,
          progressPct: 25,
          targetDate: "2026-07-01",
          projectedDate: "2026-08-31",
          onTrack: false,
          achieved: false,
        },
        workout: {
          configured: true,
          workoutsDoneToday: 1,
          workoutsPlannedToday: 1,
          targetSessionsPerWeek: 4,
          completedThisWeek: 2,
          remainingThisWeek: 2,
          progressPct: 50,
          achievedThisWeek: false,
        },
        streak: { current: 3, longest: 8, lastActiveDate: today() },
        nextActions: [
          { type: "PLAN_WORKOUT", title: "安排下一次訓練", detail: "本週還差 2 次訓練，建議先排入行事曆。", priority: 70, href: "/workouts" },
        ],
      }),
    }),
  );

  await page.goto("/");

  await expect(page.getByText("今日健康計畫")).toBeVisible();
  await expect(page.getByText(`減脂 · ${today()}`)).toBeVisible();
  await expect(page.getByText("準備度")).toBeVisible();
  await expect(page.getByText("78")).toBeVisible();
  await expect(page.getByText("剩餘熱量")).toBeVisible();
  await expect(page.getByText("750")).toBeVisible();
  await expect(page.getByText("本週訓練")).toBeVisible();
  await expect(page.getByText("2/4")).toBeVisible();
  await expect(page.getByText("體重目標").first()).toBeVisible();
  await expect(page.getByText("25").first()).toBeVisible();
  await expect(page.getByText("連續紀錄")).toBeVisible();
  await expect(page.getByText("安排下一次訓練")).toBeVisible();
  await expect(page.getByRole("link", { name: /安排下一次訓練/ })).toHaveAttribute("href", "/workouts");
});

test("dashboard health plan settings saves an integrated goal payload", async ({ page }) => {
  await seedAuth(page);
  let savedBody: Record<string, unknown> | null = null;

  await page.route("**/api/v1/me", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(USER) }),
  );
  await page.route("**/api/v1/stats/daily**", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ date: today(), intakeKcal: 0, burnKcal: 0, netKcal: 0, protein: 0, fat: 0, carb: 0, weightKg: 76, goalKcal: 1700, workoutsDone: 0, workoutsPlanned: 0 }),
    }),
  );
  await page.route("**/api/v1/stats/range**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ from: "", to: "", series: [] }) }),
  );
  await page.route("**/api/v1/ai/status**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ provider: "local", textModel: "x", visionModel: "x", loaded: false, idleTimeoutSec: 60 }) }),
  );
  await page.route("**/api/v1/habits/daily**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ date: today(), completed: 0, total: 4, items: [] }) }),
  );
  await page.route("**/api/v1/health-plan/settings", async (route) => {
    if (route.request().method() === "PUT") {
      savedBody = JSON.parse(route.request().postData() ?? "{}");
    }
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        primaryGoal: savedBody ? "muscle_gain" : "fat_loss",
        currentWeightKg: 76,
        weightGoal: savedBody
          ? { targetWeightKg: 82, startWeightKg: 76, currentWeightKg: 76, startDate: today(), targetDate: "2026-09-01", remainingKg: 6, changeSoFarKg: 0, progressPct: 0, ratePerWeekKg: null, projectedDate: null, onTrack: null, achieved: false, createdAt: "2026-06-08T00:00:00Z" }
          : null,
        workoutGoal: savedBody
          ? { targetSessionsPerWeek: 4, completedThisWeek: 0, remaining: 4, progressPct: 0, weekStart: today(), achieved: false, createdAt: "2026-06-08T00:00:00Z" }
          : null,
      }),
    });
  });
  await page.route(/.*\/api\/v1\/health-plan(?:\?.*)?$/, (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        date: today(),
        primaryGoal: "減脂",
        readinessScore: 50,
        nutrition: { goalKcal: 1700, budgetKcal: 1700, intakeKcal: 0, burnKcal: 0, remainingKcal: 1700, consumedPct: 0, over: false, macros: [] },
        weight: { configured: false, currentWeightKg: 76, targetWeightKg: null, remainingKg: null, progressPct: 0, targetDate: null, projectedDate: null, onTrack: null, achieved: false },
        workout: { configured: false, workoutsDoneToday: 0, workoutsPlannedToday: 0, targetSessionsPerWeek: null, completedThisWeek: null, remainingThisWeek: null, progressPct: null, achievedThisWeek: false },
        streak: { current: 0, longest: 0, lastActiveDate: null },
        nextActions: [],
      }),
    }),
  );

  await page.goto("/settings");

  await page.getByText("健康計畫設定").waitFor();
  await page.getByRole("combobox").first().click();
  await page.getByRole("option", { name: "增肌" }).click();
  await page.getByLabel("體重目標").check();
  await page.getByRole("spinbutton").fill("82");
  await page.locator('input[type="date"]').fill("2026-09-01");
  await page.getByLabel("每週訓練目標").check();
  await page.getByRole("combobox").nth(1).click();
  await page.getByRole("option", { name: "4 次 / 週" }).click();
  await page.getByRole("button", { name: "儲存計畫" }).click();

  expect(savedBody).toEqual({
    primaryGoal: "muscle_gain",
    weightGoal: { enabled: true, targetWeightKg: 82, targetDate: "2026-09-01" },
    workoutGoal: { enabled: true, targetSessionsPerWeek: 4 },
  });
  await expect(page.getByText("健康計畫已更新。")).toBeVisible();
});

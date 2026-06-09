import { expect, test, type Page } from "@playwright/test";

// Mocked-backend e2e for the weekly workout-goal card on /workouts.

const USER = {
  id: 1, email: "demo@example.com", name: "Demo", role: "USER",
  profile: { gender: "male", heightCm: 178, weightKg: 72, equipment: [], theme: "system", language: "zh-TW" },
  createdAt: "2026-05-30T00:00:00Z",
};

function progress(target: number, completed: number) {
  return {
    progress: {
      targetSessionsPerWeek: target,
      completedThisWeek: completed,
      remaining: Math.max(0, target - completed),
      progressPct: Math.min(100, Math.round((completed / target) * 100)),
      weekStart: "2026-06-01",
      achieved: completed >= target,
      createdAt: "2026-06-01T00:00:00Z",
    },
  };
}

async function seed(page: Page, getGoal: () => Record<string, unknown>) {
  await page.addInitScript(() => {
    localStorage.setItem("accessToken", "t");
    localStorage.setItem("refreshToken", "t");
  });
  await page.route("**/api/v1/me", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(USER) }),
  );
  await page.route("**/api/v1/ai/**", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ loaded: true }) }),
  );
  await page.route("**/api/v1/workouts?**", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ data: [], page: 0, size: 20, total: 0 }) }),
  );
  await page.route("**/api/v1/workout-schedules", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ data: [], page: 0, size: 20, total: 0 }) }),
  );
  await page.route("**/api/v1/workouts/volume**", (r) =>
    r.fulfill({
      status: 200, contentType: "application/json",
      body: JSON.stringify({ from: "2026-05-18", to: "2026-06-07", weeks: 4, totalSessions: 0, totalSets: 0, totalKcal: 0, activeDays: 0, avgSessionsPerWeek: 0, byCategory: [], series: [] }),
    }),
  );
  await page.route("**/api/v1/workout-goal", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(getGoal()) }),
  );
  // Other cards on the Progress page — empty/minimal so they don't hit the network.
  await page.route("**/api/v1/stats/range**", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ from: "", to: "", series: [] }) }),
  );
  await page.route("**/api/v1/weight-goal**", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ progress: null }) }),
  );
  await page.route("**/api/v1/streak**", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ mealStreak: { current: 0, longest: 0, lastActiveDate: null }, workoutStreak: { current: 0, longest: 0, lastActiveDate: null }, overallStreak: { current: 0, longest: 0, lastActiveDate: null }, achievements: [], newlyUnlocked: [] }) }),
  );
  await page.route("**/api/v1/reports/weekly**", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ weekStart: "2026-06-01", weekEnd: "2026-06-07", summary: { totalIntakeKcal: 0, avgIntakeKcal: 0, totalBurnKcal: 0, netKcal: 0, goalKcal: 1700, weightStart: null, weightEnd: null, weightDelta: null, workoutsDone: 0, mealsLogged: 0, daysCovered: 0 }, adherence: { caloriePct: 0, proteinPct: 0, workoutPct: 0, workoutTarget: null, loggingPct: 0, daysLogged: 0, daysCovered: 0 }, trends: [], narrative: null, generatedAt: null }) }),
  );
}

test("sets a weekly workout goal and shows live progress", async ({ page }) => {
  let goal: Record<string, unknown> = { progress: null };
  let putBody: Record<string, unknown> | null = null;
  await seed(page, () => goal);

  await page.route("**/api/v1/workout-goal", async (route) => {
    const req = route.request();
    if (req.method() === "PUT") {
      putBody = req.postDataJSON();
      goal = progress(4, 1); // user has already done 1 session this week
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(goal) });
      return;
    }
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(goal) });
  });

  await page.goto("/progress");

  await expect(page.getByText("每週訓練目標")).toBeVisible();

  // No goal yet → the form is shown. Pick a target of 4 (the goal card's select is the
  // only one labelled "次 / 週").
  await page.getByRole("combobox").filter({ hasText: "次 / 週" }).click();
  await page.getByRole("option", { name: "4 次 / 週" }).click();
  await page.getByRole("button", { name: "設定目標" }).click();

  // Progress view appears with the new target and this week's completed count.
  await expect(page.getByText("/ 4 次")).toBeVisible();
  await expect(page.getByText("還差 3 次")).toBeVisible();
  expect(putBody).toEqual({ targetSessionsPerWeek: 4 });
});

test("shows an achieved badge when the weekly target is met", async ({ page }) => {
  await seed(page, () => progress(3, 3));
  await page.goto("/progress");

  await expect(page.getByText("每週訓練目標")).toBeVisible();
  await expect(page.getByText("本週達標")).toBeVisible();
  await expect(page.getByText("/ 3 次")).toBeVisible();
});

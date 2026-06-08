import { expect, test, type Page } from "@playwright/test";

// Mocked-backend e2e for the weekly/monthly schedule planner on /workouts.

const USER = {
  id: 1, email: "demo@example.com", name: "Demo", role: "USER",
  profile: { gender: "male", heightCm: 178, weightKg: 72, goal: "muscle_gain", equipment: [], theme: "system", language: "zh-TW" },
  createdAt: "2026-05-30T00:00:00Z",
};

// A 4-day split starting Monday 2026-06-01, two weeks.
function schedule(): Record<string, unknown> {
  return {
    id: 7, goal: "增肌", startDate: "2026-06-01", weeks: 2, daysPerWeek: 4, intensity: "medium",
    days: [
      { weekday: 1, rest: false, category: "legs", durationMin: 40, focus: "下肢肌力" },
      { weekday: 2, rest: true, category: null, durationMin: 0, focus: "休息與恢復" },
      { weekday: 3, rest: false, category: "chest", durationMin: 35, focus: "胸與三頭" },
      { weekday: 4, rest: true, category: null, durationMin: 0, focus: "休息與恢復" },
      { weekday: 5, rest: false, category: "back", durationMin: 35, focus: "背與二頭" },
      { weekday: 6, rest: false, category: "arms", durationMin: 30, focus: "手臂與肩穩定" },
      { weekday: 7, rest: true, category: null, durationMin: 0, focus: "休息與恢復" },
    ],
    createdAt: "2026-06-01T00:00:00Z",
  };
}

async function seed(page: Page, hasSchedule: () => boolean) {
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
  await page.route("**/api/v1/health-plan/settings", (r) =>
    r.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        primaryGoal: "muscle_gain",
        currentWeightKg: 72,
        weightGoal: null,
        workoutGoal: {
          targetSessionsPerWeek: 4,
          completedThisWeek: 1,
          remaining: 3,
          progressPct: 25,
          weekStart: "2026-06-01",
          achieved: false,
          createdAt: "2026-06-01T00:00:00Z",
        },
      }),
    }),
  );
  // The day's plain workout list — keep it empty so only the planner is exercised.
  await page.route("**/api/v1/workouts?**", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ data: [], page: 0, size: 20, total: 0 }) }),
  );
  // GET list reflects whether a schedule has been generated yet.
  await page.route("**/api/v1/workout-schedules", (r) =>
    r.fulfill({
      status: 200, contentType: "application/json",
      body: JSON.stringify({ data: hasSchedule() ? [schedule()] : [], page: 0, size: 20, total: hasSchedule() ? 1 : 0 }),
    }),
  );
}

test("generates a weekly split and adds a training day to a date", async ({ page }) => {
  let generated = false;
  let generateBody: Record<string, unknown> | null = null;
  let applyBody: Record<string, unknown> | null = null;
  await seed(page, () => generated);

  await page.route("**/api/v1/workout-schedules/generate", async (route) => {
    generateBody = route.request().postDataJSON();
    generated = true;
    await route.fulfill({ status: 201, contentType: "application/json", body: JSON.stringify(schedule()) });
  });
  await page.route("**/api/v1/workout-schedules/*/apply", async (route) => {
    applyBody = route.request().postDataJSON();
    await route.fulfill({
      status: 201, contentType: "application/json",
      body: JSON.stringify({ id: 99, date: applyBody!.date, category: "legs", items: [], totalKcal: 200, burnedKcal: null, done: false, createdAt: "2026-06-01T00:00:00Z" }),
    });
  });

  await page.goto("/workouts");

  // Empty state until a schedule is generated.
  await expect(page.getByText("還沒有週期課表")).toBeVisible();
  await expect(page.getByText("依健康計畫預設 4 天/週")).toBeVisible();

  await page.getByRole("button", { name: "產生週期課表" }).click();
  expect(generateBody).toMatchObject({ daysPerWeek: 4, weeks: 4, intensity: "medium" });

  // The split renders: a Monday leg day and at least one rest day.
  await expect(page.getByText("目標 · 增肌")).toBeVisible();
  await expect(page.getByText("腿部肌群")).toBeVisible();
  await expect(page.getByText("休息日").first()).toBeVisible();

  // Add Monday's leg session to its date.
  await page.getByRole("button", { name: "加入當天" }).first().click();

  await expect(page.getByRole("button", { name: "已加入" })).toBeVisible();
  expect(applyBody).toEqual({ date: "2026-06-01", weekday: 1 });
});

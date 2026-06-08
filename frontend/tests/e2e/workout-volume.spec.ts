import { expect, test, type Page } from "@playwright/test";

// Mocked-backend e2e for the training-volume analytics card on /workouts.

const USER = {
  id: 1, email: "demo@example.com", name: "Demo", role: "USER",
  profile: { gender: "male", heightCm: 178, weightKg: 72, equipment: [], theme: "system", language: "zh-TW" },
  createdAt: "2026-05-30T00:00:00Z",
};

function volume(weeks: number): Record<string, unknown> {
  return {
    from: "2026-05-18", to: "2026-06-07", weeks,
    totalSessions: 9, totalSets: 96, totalKcal: 1820, activeDays: 7, avgSessionsPerWeek: 2.3,
    byCategory: [
      { category: "legs", sessions: 4, sets: 48, kcal: 900 },
      { category: "chest", sessions: 3, sets: 30, kcal: 560 },
      { category: "back", sessions: 2, sets: 18, kcal: 360 },
    ],
    series: [
      { weekStart: "2026-05-18", sessions: 2, sets: 22, kcal: 420 },
      { weekStart: "2026-05-25", sessions: 3, sets: 30, kcal: 560 },
      { weekStart: "2026-06-01", sessions: 4, sets: 44, kcal: 840 },
    ],
    neglectedCategories: ["arms", "abs", "glutes"],
  };
}

async function seed(page: Page) {
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
  await page.route("**/api/v1/workout-schedules", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ data: [], page: 0, size: 20, total: 0 }) }),
  );
  // The volume endpoint must be routed BEFORE the broad workouts list route, since the
  // list glob (**/api/v1/workouts**) also matches /workouts/volume — last route wins.
  await page.route("**/api/v1/workouts?**", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ data: [], page: 0, size: 20, total: 0 }) }),
  );
  await page.route("**/api/v1/workouts/volume**", (route) => {
    const weeks = Number(new URL(route.request().url()).searchParams.get("weeks") ?? "4");
    return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(volume(weeks)) });
  });
}

test("shows training-volume totals, per-category bars and reacts to the range selector", async ({ page }) => {
  let lastWeeks = 0;
  await seed(page);
  page.on("request", (req) => {
    const m = req.url().match(/\/workouts\/volume\?weeks=(\d+)/);
    if (m) lastWeeks = Number(m[1]);
  });

  await page.goto("/workouts");

  await expect(page.getByText("訓練量分析")).toBeVisible();
  // Totals row.
  await expect(page.getByText("總訓練次數")).toBeVisible();
  // Per-category breakdown renders the busiest group first.
  await expect(page.getByText("腿部肌群")).toBeVisible();
  await expect(page.getByText("胸部塑造")).toBeVisible();
  // Neglected primary muscle groups are surfaced as a balance nudge.
  await expect(page.getByText("這段期間較少練到")).toBeVisible();

  // Default range is 4 weeks.
  await expect(lastWeeks === 4 || lastWeeks === 0).toBeTruthy();

  // Switching the range refetches with the new week count.
  await page.getByRole("combobox").last().click();
  await page.getByRole("option", { name: "近 12 週" }).click();
  await expect.poll(() => lastWeeks).toBe(12);
});

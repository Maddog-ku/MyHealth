import { expect, test, type Page } from "@playwright/test";

// Mocked-backend e2e (same pattern as auth.spec.ts). Workouts are now performed
// in a timed player rather than checked off, so we drive the countdown with
// Playwright's clock API instead of waiting in real time.

const USER = {
  id: 1, email: "demo@example.com", name: "Demo", role: "USER",
  profile: { gender: "female", heightCm: 165, weightKg: 55, equipment: [], theme: "system", language: "zh-TW" },
  createdAt: "2026-05-30T00:00:00Z",
};

// Single exercise, single set, 12s of work and no trailing rest — one work phase.
function singleExercisePlan(): Record<string, unknown> {
  return {
    id: 42, date: "2026-06-01", category: "abs",
    items: [
      { name: "捲腹", sets: 1, reps: "12", restSec: 30, durationSec: 12, kcal: 40, note: "下背貼地", alt: [] },
    ],
    totalKcal: 40, burnedKcal: null, done: false, createdAt: "2026-05-30T03:00:00Z",
  };
}

async function seedAuth(page: Page) {
  await page.clock.install();
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
}

test("finishing every timed set records actual kcal and marks the plan done", async ({ page }) => {
  await seedAuth(page);
  let plan = singleExercisePlan();
  let completeBody: Record<string, unknown> | null = null;

  await page.route("**/api/v1/workouts**", async (route) => {
    const req = route.request();
    if (req.method() === "POST" && req.url().includes("/complete")) {
      completeBody = req.postDataJSON();
      plan = { ...plan, done: true, burnedKcal: 40 };
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(plan) });
      return;
    }
    if (req.method() === "GET") {
      await route.fulfill({
        status: 200, contentType: "application/json",
        body: JSON.stringify({ data: [plan], page: 0, size: 20, total: 1 }),
      });
      return;
    }
    await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
  });

  await page.goto("/workouts");

  // Launch the timed player.
  await page.getByRole("button", { name: "開始訓練" }).click();
  await expect(page.getByRole("heading", { name: "捲腹" })).toBeVisible();
  await expect(page.getByText("第 1/1 組")).toBeVisible();
  await expect(page.getByText("0:12")).toBeVisible();

  // Fast-forward through the 12s work countdown → session ends.
  await page.clock.runFor(13_000);

  await expect(page.getByRole("heading", { name: "訓練結束" })).toBeVisible();
  const record = page.getByRole("button", { name: "完成並記錄" });
  await expect(record).toBeVisible();
  expect(completeBody).toBeNull(); // nothing sent until the user confirms

  await record.click();

  // POST carried the earned kcal, and the card reflects the completed state.
  expect(completeBody).toEqual({ actualKcal: 40 });
  await expect(page.getByText("已完成 · 消耗 40 kcal")).toBeVisible();
});

test("skipping a work set leaves the exercise uncounted", async ({ page }) => {
  await seedAuth(page);
  const plan = singleExercisePlan();
  let completeCalled = false;

  await page.route("**/api/v1/workouts**", async (route) => {
    const req = route.request();
    if (req.method() === "POST" && req.url().includes("/complete")) {
      completeCalled = true;
    }
    await route.fulfill({
      status: 200, contentType: "application/json",
      body: JSON.stringify({ data: [plan], page: 0, size: 20, total: 1 }),
    });
  });

  await page.goto("/workouts");
  await page.getByRole("button", { name: "開始訓練" }).click();

  // Skip the only work set without finishing the seconds → it must not count.
  await page.getByRole("button", { name: "跳過" }).click();

  await expect(page.getByRole("heading", { name: "訓練結束" })).toBeVisible();
  await expect(page.getByText("0/1 組 · 未完成")).toBeVisible();

  // No "record" path when nothing was completed; closing sends no POST.
  await page.getByRole("button", { name: /未完成任何動作/ }).click();
  expect(completeCalled).toBe(false);
});

test("pausing freezes the countdown", async ({ page }) => {
  await seedAuth(page);
  const plan = singleExercisePlan();

  await page.route("**/api/v1/workouts**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ data: [plan], page: 0, size: 20, total: 1 }) }),
  );

  await page.goto("/workouts");
  await page.getByRole("button", { name: "開始訓練" }).click();

  // Let 3s elapse, then pause.
  await page.clock.runFor(3_000);
  await expect(page.getByText("0:09")).toBeVisible();
  await page.getByRole("button", { name: "暫停" }).click();
  await expect(page.getByText("已暫停")).toBeVisible();

  // Time advances but the countdown is frozen while paused.
  await page.clock.runFor(5_000);
  await expect(page.getByText("0:09")).toBeVisible();

  // Resume keeps counting down.
  await page.getByRole("button", { name: "繼續" }).click();
  await expect(page.getByText("已暫停")).toBeHidden();
});

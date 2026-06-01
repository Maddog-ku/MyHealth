import { expect, test, type Page } from "@playwright/test";

// Mocked-backend e2e (same pattern as auth.spec.ts).

const USER = {
  id: 1, email: "demo@example.com", name: "Demo", role: "USER",
  profile: { gender: "female", heightCm: 165, weightKg: 55, equipment: [], theme: "system", language: "zh-TW" },
  createdAt: "2026-05-30T00:00:00Z",
};

async function seedAuth(page: Page) {
  await page.addInitScript(() => {
    localStorage.setItem("accessToken", "t");
    localStorage.setItem("refreshToken", "t");
  });
}

test("workout 打卡 is gated behind ticking every exercise on the page", async ({ page }) => {
  await seedAuth(page);

  let plan: Record<string, unknown> = {
    id: 42, date: "2026-06-01", category: "abs",
    items: [
      { name: "捲腹", sets: 4, reps: "15", restSec: 45, kcal: 40, note: "下背貼地", alt: [] },
      { name: "棒式", sets: 3, reps: "45s", restSec: 45, kcal: 35, note: "一直線", alt: [] },
    ],
    totalKcal: 75, done: false, createdAt: "2026-05-30T03:00:00Z",
  };
  let completeCalled = false;

  await page.route("**/api/v1/me", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(USER) }),
  );
  await page.route("**/api/v1/workouts**", async (route) => {
    const req = route.request();
    if (req.method() === "POST" && req.url().includes("/complete")) {
      completeCalled = true;
      plan = { ...plan, done: true };
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

  // Before any exercise is ticked, 打卡 is disabled and shows 0/2 progress.
  const gate = page.getByRole("button", { name: "完成進度 0/2" });
  await expect(gate).toBeVisible();
  await expect(gate).toBeDisabled();

  // Tick the first exercise → progress advances but still locked.
  await page.getByRole("button", { name: /捲腹/ }).click();
  await expect(page.getByRole("button", { name: "完成進度 1/2" })).toBeDisabled();

  // Tick the second → now 打卡 unlocks.
  await page.getByRole("button", { name: /棒式/ }).click();
  const ready = page.getByRole("button", { name: "完成打卡" });
  await expect(ready).toBeEnabled();
  expect(completeCalled).toBe(false); // not until the user actually clicks

  await ready.click();

  // POST fired, and after refetch the plan reads as checked-in.
  await expect(page.getByRole("button", { name: "已打卡完成" })).toBeVisible();
  expect(completeCalled).toBe(true);
});

test("a single click cannot complete a workout without ticking exercises", async ({ page }) => {
  await seedAuth(page);
  let completeCalled = false;
  const plan = {
    id: 7, date: "2026-06-01", category: "legs",
    items: [{ name: "深蹲", sets: 4, reps: "12", restSec: 60, kcal: 70, note: "膝蓋朝腳尖", alt: [] }],
    totalKcal: 70, done: false, createdAt: "2026-05-30T03:00:00Z",
  };
  await page.route("**/api/v1/me", (r) => r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(USER) }));
  await page.route("**/api/v1/workouts**", async (route) => {
    if (route.request().method() === "POST") { completeCalled = true; }
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ data: [plan], page: 0, size: 20, total: 1 }) });
  });

  await page.goto("/workouts");

  // The gate button is present but disabled — clicking it does nothing.
  const gate = page.getByRole("button", { name: "完成進度 0/1" });
  await expect(gate).toBeDisabled();
  await gate.click({ force: true }).catch(() => {});
  expect(completeCalled).toBe(false);
});

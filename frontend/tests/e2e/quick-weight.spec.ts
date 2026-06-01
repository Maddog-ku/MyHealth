import { expect, test, type Page } from "@playwright/test";

// Mocked-backend e2e (same pattern as auth.spec.ts).

const PROFILE = { gender: "female", heightCm: 165, weightKg: 55, equipment: [], theme: "system", language: "zh-TW" };
const USER = { id: 1, email: "demo@example.com", name: "Demo", role: "USER", profile: PROFILE, createdAt: "2026-05-30T00:00:00Z" };

function todayLocalISO(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

async function seedAuth(page: Page) {
  const key = `weightCheckIn:${todayLocalISO()}`;
  await page.addInitScript((flagKey) => {
    localStorage.setItem("accessToken", "test-access-token");
    localStorage.setItem("refreshToken", "test-refresh-token");
    // Suppress the daily auto check-in dialog so it doesn't cover the metric.
    localStorage.setItem(flagKey as string, "done");
  }, key);
}

async function mockDashboard(page: Page, onProfilePut?: (body: any) => void) {
  await page.route("**/api/v1/me/profile", async (route) => {
    const body = JSON.parse(route.request().postData() ?? "{}");
    onProfilePut?.(body);
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
  });
  await page.route("**/api/v1/me", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(USER) }),
  );
  await page.route("**/api/v1/stats/daily**", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        date: todayLocalISO(), intakeKcal: 0, burnKcal: 0, netKcal: 0,
        protein: 0, fat: 0, carb: 0, weightKg: 55, goalKcal: 1700, workoutsDone: 0, workoutsPlanned: 0,
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
      body: JSON.stringify({ provider: "local", textModel: "gemma4:e4b", visionModel: "gemma4:e4b", loaded: false, idleTimeoutSec: 60 }),
    }),
  );
}

test("tapping the current-weight metric opens a quick editor that logs a new weight", async ({ page }) => {
  await seedAuth(page);
  let putBody: any = null;
  await mockDashboard(page, (b) => { putBody = b; });

  await page.goto("/");

  // The current-weight metric is an interactive button.
  const weightMetric = page.getByRole("button", { name: /目前體重/ });
  await expect(weightMetric).toBeVisible();
  await weightMetric.click();

  // Quick weight dialog opens, prefilled with the current value.
  await expect(page.getByText("快速更新體重")).toBeVisible();
  const input = page.getByLabel("體重 (kg)");
  await expect(input).toHaveValue("55");

  await input.fill("58.2");
  await page.getByRole("button", { name: "儲存" }).click();

  // Dialog closes and the profile was PUT with the new weight (which the backend
  // records as today's body_measurement → counts toward the daily trend).
  await expect(page.getByText("快速更新體重")).toBeHidden();
  expect(putBody).not.toBeNull();
  expect(putBody.weightKg).toBe(58.2);
  expect(putBody.heightCm).toBe(165); // unchanged fields preserved
  expect(putBody.gender).toBe("female");
});

test("the quick weight editor can be cancelled without writing", async ({ page }) => {
  await seedAuth(page);
  let putCalled = false;
  await mockDashboard(page, () => { putCalled = true; });

  await page.goto("/");
  await page.getByRole("button", { name: /目前體重/ }).click();
  await expect(page.getByText("快速更新體重")).toBeVisible();

  await page.getByRole("button", { name: "取消" }).click();

  await expect(page.getByText("快速更新體重")).toBeHidden();
  expect(putCalled).toBe(false);
});

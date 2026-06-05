import { expect, test, type Page } from "@playwright/test";

// Mocked-backend e2e (same pattern as auth.spec.ts): no real server, auth seeded
// via localStorage, every dashboard endpoint stubbed with page.route.

const PROFILE = { gender: "female", heightCm: 165, weightKg: 55, equipment: [], theme: "system", language: "zh-TW" };

const USER = {
  id: 1,
  email: "demo@example.com",
  name: "Demo",
  role: "USER",
  profile: PROFILE,
  createdAt: "2026-05-30T00:00:00Z",
};

function todayLocalISO(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

async function seedAuth(page: Page, opts: { dismissedToday?: boolean } = {}) {
  const dismissed = opts.dismissedToday ?? false;
  const key = `weightCheckIn:${todayLocalISO()}`;
  await page.addInitScript(
    ([dismissedFlag, flagKey]) => {
      localStorage.setItem("accessToken", "test-access-token");
      localStorage.setItem("refreshToken", "test-refresh-token");
      if (dismissedFlag) localStorage.setItem(flagKey as string, "done");
    },
    [dismissed, key] as const,
  );
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
  await page.route("**/api/v1/habits/daily**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ date: todayLocalISO(), completed: 0, total: 4, items: [] }) }),
  );
}

test("filling only one field carries the other through as unchanged in step 2", async ({ page }) => {
  await seedAuth(page);
  let putBody: any = null;
  await mockDashboard(page, (b) => { putBody = b; });

  await page.goto("/");

  // Dialog pops automatically; inputs start empty (placeholder = current value).
  await expect(page.getByText("今日身體狀況記錄")).toBeVisible();
  const height = page.getByLabel("身高 (cm)");
  await expect(height).toHaveValue("");

  // Enter only height; leave weight blank.
  await height.fill("177");
  await page.getByRole("button", { name: "確定" }).click();

  // Step 2 lists what will be applied: height 177, weight unchanged (original 55).
  await expect(page.getByText("確認您的身體數據")).toBeVisible();
  await expect(page.getByText("177 cm")).toBeVisible();
  await expect(page.getByText("55 kg")).toBeVisible();
  await expect(page.getByText("（未變更）")).toBeVisible();

  await page.getByRole("button", { name: "確認變更" }).click();

  // The blank field was carried through as the original value.
  await expect(page.getByText("確認您的身體數據")).toBeHidden();
  expect(putBody).not.toBeNull();
  expect(putBody.heightCm).toBe(177);
  expect(putBody.weightKg).toBe(55);
  expect(putBody.gender).toBe("female"); // required fields preserved

  const flag = await page.evaluate((k) => localStorage.getItem(k), `weightCheckIn:${todayLocalISO()}`);
  expect(flag).toBe("done");
});

test("the step-2 back button returns to the input step preserving entries", async ({ page }) => {
  await seedAuth(page);
  await mockDashboard(page);

  await page.goto("/");
  await expect(page.getByText("今日身體狀況記錄")).toBeVisible();

  await page.getByLabel("體重 (kg)").fill("60");
  await page.getByRole("button", { name: "確定" }).click();

  await expect(page.getByText("確認您的身體數據")).toBeVisible();
  await page.getByRole("button", { name: "上一步" }).click();

  // Back on the input step with the typed value still there.
  await expect(page.getByText("今日身體狀況記錄")).toBeVisible();
  await expect(page.getByLabel("體重 (kg)")).toHaveValue("60");
});

test("leaving both fields blank confirms as unchanged and writes nothing", async ({ page }) => {
  await seedAuth(page);
  let putCalled = false;
  await mockDashboard(page, () => { putCalled = true; });

  await page.goto("/");
  await expect(page.getByText("今日身體狀況記錄")).toBeVisible();

  // Change nothing, just confirm through both steps.
  await page.getByRole("button", { name: "確定" }).click();

  // Both rows show（未變更）and a hint explains no write will happen.
  await expect(page.getByText("確認您的身體數據")).toBeVisible();
  await expect(page.getByText(/您未變更任何數據/)).toBeVisible();
  expect(await page.getByText("（未變更）").count()).toBe(2);

  await page.getByRole("button", { name: "確認變更" }).click();

  await expect(page.getByText("確認您的身體數據")).toBeHidden();
  expect(putCalled).toBe(false);
  const flag = await page.evaluate((k) => localStorage.getItem(k), `weightCheckIn:${todayLocalISO()}`);
  expect(flag).toBe("done");
});

test("check-in does not auto-prompt again once dismissed today", async ({ page }) => {
  await seedAuth(page, { dismissedToday: true });
  await mockDashboard(page);

  await page.goto("/");

  // Dashboard content is up, but the dialog stays closed.
  await expect(page.getByText("AI 核心智能引擎")).toBeVisible();
  await expect(page.getByText("今日身體狀況記錄")).toBeHidden();
});

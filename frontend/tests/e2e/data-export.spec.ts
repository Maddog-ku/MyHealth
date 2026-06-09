import { expect, test, type Page } from "@playwright/test";

// Mocked-backend e2e for "export my data" on /settings.

const USER = {
  id: 1, email: "demo@example.com", name: "Demo", role: "USER",
  profile: { gender: "female", heightCm: 165, weightKg: 55, equipment: [], theme: "system", language: "zh-TW" },
  createdAt: "2026-05-30T00:00:00Z",
};

const EXPORT = {
  exportedAt: "2026-06-07T00:00:00Z",
  account: { id: 1, email: "demo@example.com", name: "Demo", role: "USER", profile: null, createdAt: "2026-05-30T00:00:00Z" },
  bodyMeasurements: [], workouts: [], meals: [], weightGoal: null, favoriteMeals: [], habits: [],
};

async function seed(page: Page) {
  await page.addInitScript(() => {
    localStorage.setItem("accessToken", "test-access");
    localStorage.setItem("refreshToken", "raw-current");
  });
  await page.route("**/api/v1/me", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(USER) }),
  );
  await page.route("**/api/v1/me/sessions", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ sessions: [] }) }),
  );
  await page.route("**/api/v1/me/export", (r) =>
    r.fulfill({
      status: 200,
      contentType: "application/json",
      headers: { "Content-Disposition": 'attachment; filename="myhealth-export-2026-06-07.json"' },
      body: JSON.stringify(EXPORT),
    }),
  );
}

test("exporting downloads a JSON file of the user's data", async ({ page }) => {
  await seed(page);
  await page.goto("/account");

  await expect(page.getByText("資料與隱私")).toBeVisible();

  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: "匯出我的資料" }).click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toMatch(/^myhealth-export-\d{4}-\d{2}-\d{2}\.json$/);
  await expect(page.getByText("已開始下載")).toBeVisible();
});

import { expect, test, type Page } from "@playwright/test";

// Mocked-backend e2e for the language switch (i18n) on /settings + the nav shell.

const USER = {
  id: 1, email: "demo@example.com", name: "Demo", role: "USER",
  profile: { gender: "female", heightCm: 165, weightKg: 55, equipment: [], theme: "system", language: "zh-TW" },
  createdAt: "2026-05-30T00:00:00Z",
};

async function seed(page: Page) {
  await page.addInitScript(() => {
    localStorage.setItem("accessToken", "test-access");
    localStorage.setItem("refreshToken", "raw-current");
  });
  await page.route("**/api/v1/me", (r) => {
    if (r.request().method() === "PUT") {
      // updateProfile when language changes
      return r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(USER.profile) });
    }
    return r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(USER) });
  });
  await page.route("**/api/v1/me/profile", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(USER.profile) }),
  );
  await page.route("**/api/v1/me/sessions", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ sessions: [] }) }),
  );
}

test("switching to English translates the nav and settings, and persists", async ({ page }) => {
  await seed(page);
  await page.goto("/settings");

  // Defaults to Traditional Chinese.
  await expect(page.getByRole("link", { name: "儀表板" })).toBeVisible();
  await expect(page.getByText("外觀視覺主題")).toBeVisible();

  // Switch to English.
  await page.getByRole("button", { name: "English" }).click();

  // Nav + settings cards are now in English.
  await expect(page.getByRole("link", { name: "Dashboard" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Settings" })).toBeVisible();
  await expect(page.getByText("Appearance")).toBeVisible();
  await expect(page.getByText("Font size")).toBeVisible();

  // Choice is persisted and applied to <html lang>.
  expect(await page.evaluate(() => localStorage.getItem("lang"))).toBe("en");
  expect(await page.evaluate(() => document.documentElement.lang)).toBe("en");

  // Survives a reload.
  await page.reload();
  await expect(page.getByRole("link", { name: "Dashboard" })).toBeVisible();
});

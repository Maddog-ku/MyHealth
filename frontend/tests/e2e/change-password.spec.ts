import { expect, test, type Page } from "@playwright/test";

// Mocked-backend e2e for changing the password on /settings.

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
  await page.route("**/api/v1/me", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(USER) }),
  );
  await page.route("**/api/v1/me/sessions", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ sessions: [] }) }),
  );
}

test("changing the password submits and confirms other devices were logged out", async ({ page }) => {
  let body: Record<string, unknown> | null = null;
  let sentHeader: string | null = null;
  await seed(page);

  await page.route("**/api/v1/me/password", async (route) => {
    body = route.request().postDataJSON();
    sentHeader = route.request().headers()["x-refresh-token"] ?? null;
    await route.fulfill({ status: 204, body: "" });
  });

  await page.goto("/account");

  await expect(page.getByText("修改密碼")).toBeVisible();

  await page.locator("#current-password").fill("OldPass1");
  await page.locator("#new-password").fill("NewPass2");
  await page.locator("#confirm-password").fill("NewPass2");

  await page.getByRole("button", { name: "更新密碼" }).click();

  await expect(page.getByText("密碼已更新")).toBeVisible();
  expect(body).toEqual({ currentPassword: "OldPass1", newPassword: "NewPass2" });
  expect(sentHeader).toBe("raw-current"); // current device preserved via header
});

test("submit stays disabled until the form is valid", async ({ page }) => {
  await seed(page);
  await page.route("**/api/v1/me/password", (r) => r.fulfill({ status: 204, body: "" }));

  await page.goto("/account");

  const submit = page.getByRole("button", { name: "更新密碼" });
  await expect(submit).toBeDisabled();

  // New password long enough but missing an uppercase letter → policy hint, stays disabled.
  await page.locator("#current-password").fill("OldPass1");
  await page.locator("#new-password").fill("weakpass");
  await page.locator("#confirm-password").fill("weakpass");
  await expect(page.getByText("需同時包含大寫與小寫英文字母（僅限英數字）")).toBeVisible();
  await expect(submit).toBeDisabled();

  // Mismatch keeps it disabled.
  await page.locator("#new-password").fill("NewPass2");
  await page.locator("#confirm-password").fill("NewPass3");
  await expect(page.getByText("兩次輸入的新密碼不一致")).toBeVisible();
  await expect(submit).toBeDisabled();

  // Matching, policy-compliant → enabled.
  await page.locator("#confirm-password").fill("NewPass2");
  await expect(submit).toBeEnabled();
});

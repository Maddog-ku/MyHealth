import { expect, test, type Page } from "@playwright/test";

// Mocked-backend e2e (same pattern as auth.spec.ts): no real server, auth seeded
// via localStorage, every endpoint stubbed with page.route.

const USER = {
  id: 1,
  email: "demo@example.com",
  name: "Demo",
  role: "USER",
  profile: { gender: "female", heightCm: 165, weightKg: 55, equipment: [], theme: "system", language: "zh-TW" },
  createdAt: "2026-05-30T00:00:00Z",
};

async function seedAuth(page: Page) {
  await page.addInitScript(() => {
    localStorage.setItem("accessToken", "test-access-token");
    localStorage.setItem("refreshToken", "test-refresh-token");
  });
}

test("user can delete their account from settings and is returned to login", async ({ page }) => {
  await seedAuth(page);

  let deleteCalled = false;

  await page.route("**/api/v1/me", async (route) => {
    const request = route.request();
    if (request.method() === "DELETE") {
      deleteCalled = true;
      await route.fulfill({ status: 204, body: "" });
      return;
    }
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(USER) });
  });

  await page.goto("/account");

  // Danger zone is present and requires an explicit confirmation step.
  await expect(page.getByText("危險操作區")).toBeVisible();
  await page.getByRole("button", { name: "刪除我的帳號" }).click();
  await expect(page.getByText(/確定要永久刪除帳號嗎/)).toBeVisible();

  await page.getByRole("button", { name: "確認永久刪除" }).click();

  // DELETE /me fired and the app routed back to the public login page.
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole("heading", { name: "MyHealth" })).toBeVisible();
  expect(deleteCalled).toBe(true);

  // Tokens were cleared as part of the deletion flow.
  const accessToken = await page.evaluate(() => localStorage.getItem("accessToken"));
  expect(accessToken).toBeNull();
});

test("user can cancel out of the account-deletion confirmation", async ({ page }) => {
  await seedAuth(page);

  let deleteCalled = false;
  await page.route("**/api/v1/me", async (route) => {
    if (route.request().method() === "DELETE") {
      deleteCalled = true;
      await route.fulfill({ status: 204, body: "" });
      return;
    }
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(USER) });
  });

  await page.goto("/account");

  await page.getByRole("button", { name: "刪除我的帳號" }).click();
  await expect(page.getByText(/確定要永久刪除帳號嗎/)).toBeVisible();
  await page.getByRole("button", { name: "取消" }).click();

  // Back to the resting state, still on the account page, nothing deleted.
  await expect(page.getByText(/確定要永久刪除帳號嗎/)).toBeHidden();
  await expect(page).toHaveURL(/\/account$/);
  expect(deleteCalled).toBe(false);
});

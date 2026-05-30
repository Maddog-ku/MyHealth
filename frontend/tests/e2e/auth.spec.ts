import { expect, test } from "@playwright/test";

test("login page renders and surfaces API errors", async ({ page }) => {
  await page.route("**/api/v1/auth/login", async (route) => {
    await route.fulfill({
      status: 401,
      contentType: "application/json",
      body: JSON.stringify({
        status: 401,
        error: "UNAUTHORIZED",
        message: "帳號或密碼錯誤",
      }),
    });
  });

  await page.goto("/login");

  await expect(page.getByRole("heading", { name: "MyHealth" })).toBeVisible();
  // Fields are no longer pre-filled, so type credentials to get past the
  // required-field validation and actually trigger the (mocked) login call.
  await page.getByLabel("電子郵件").fill("user@example.com");
  await page.getByLabel("密碼", { exact: true }).fill("whatever123");
  await page.getByRole("button", { name: /開始使用/ }).click();
  await expect(page.getByText("帳號或密碼錯誤")).toBeVisible();
});

test("registration password field rejects symbols and accepts letters plus digits", async ({ page }) => {
  await page.goto("/login");
  await page.getByRole("tab", { name: "免費註冊" }).click();

  const password = page.getByLabel("設定密碼");
  await password.fill("Password!");
  const rejectsSymbols = await password.evaluate((element) => (element as HTMLInputElement).validity.patternMismatch);
  expect(rejectsSymbols).toBe(true);

  await password.fill("Secret123");
  const isValid = await password.evaluate((element) => (element as HTMLInputElement).validity.valid);
  expect(isValid).toBe(true);
});

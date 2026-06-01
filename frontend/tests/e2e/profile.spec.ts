import { expect, test, type Page } from "@playwright/test";

// Mocked-backend e2e (same pattern as auth.spec.ts): no real server, auth seeded
// via localStorage, endpoints stubbed with page.route.

const PROFILE = {
  gender: "female", heightCm: 165, weightKg: 55, age: 28,
  goal: "fat_loss", experience: "intermediate", equipment: [], theme: "system", language: "zh-TW",
};

const USER = { id: 1, email: "demo@example.com", name: "Demo", role: "USER", profile: PROFILE, createdAt: "2026-05-30T00:00:00Z" };

async function seedAuth(page: Page) {
  await page.addInitScript(() => {
    localStorage.setItem("accessToken", "test-access-token");
    localStorage.setItem("refreshToken", "test-refresh-token");
    // Skip the dashboard's daily check-in dialog (irrelevant to this page).
    const d = new Date();
    const key = `weightCheckIn:${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
    localStorage.setItem(key, "done");
  });
}

test("the physiological profile lives on its own /profile page and saves edits", async ({ page }) => {
  await seedAuth(page);
  let putBody: any = null;

  await page.route("**/api/v1/me/profile", async (route) => {
    putBody = JSON.parse(route.request().postData() ?? "{}");
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(putBody) });
  });
  await page.route("**/api/v1/me", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(USER) }),
  );

  await page.goto("/profile");

  // The profile form was extracted out of Settings into its own page.
  await expect(page.getByText("個人生理指標檔案")).toBeVisible();
  const weight = page.getByLabel("體重 (kg)");
  await expect(weight).toHaveValue("55");

  await weight.fill("70");
  await page.getByRole("button", { name: "儲存變更檔案" }).click();

  await expect(page.getByText("資料已成功同步更新！")).toBeVisible();
  expect(putBody).not.toBeNull();
  expect(putBody.weightKg).toBe(70);
  expect(putBody.gender).toBe("female"); // required fields preserved
});

test("settings no longer contains the physiological profile form", async ({ page }) => {
  await seedAuth(page);
  await page.route("**/api/v1/me", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(USER) }),
  );

  await page.goto("/settings");

  // Settings keeps appearance + danger zone, but the body-metrics form moved out.
  await expect(page.getByText("外觀視覺主題")).toBeVisible();
  await expect(page.getByText("危險操作區")).toBeVisible();
  await expect(page.getByText("個人生理指標檔案")).toHaveCount(0);

  // And the nav exposes the dedicated profile entry.
  await expect(page.getByRole("link", { name: "生理指標" }).first()).toBeVisible();
});

test("body-measurement fields and equipment are sent on save", async ({ page }) => {
  await seedAuth(page);
  let putBody: any = null;

  await page.route("**/api/v1/me/profile", async (route) => {
    putBody = JSON.parse(route.request().postData() ?? "{}");
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(putBody) });
  });
  await page.route("**/api/v1/me", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(USER) }),
  );

  await page.goto("/profile");

  await page.getByLabel("體脂率 (%)").fill("22.5");
  await page.getByLabel("腰圍 (cm)").fill("70");
  await page.getByLabel("基礎代謝 BMR (kcal)").fill("1450");
  await page.getByLabel("可用器材（以逗號分隔）").fill("啞鈴、瑜珈墊");

  await page.getByRole("button", { name: "儲存變更檔案" }).click();
  await expect(page.getByText("資料已成功同步更新！")).toBeVisible();

  expect(putBody.bodyFatPct).toBe(22.5);
  expect(putBody.waistCm).toBe(70);
  expect(putBody.bmrKcal).toBe(1450);
  expect(putBody.equipment).toEqual(["啞鈴", "瑜珈墊"]);
  expect(putBody.weightKg).toBe(55); // untouched required field preserved
});

import { expect, test, type Page } from "@playwright/test";

// These e2e specs follow the existing project pattern (see auth.spec.ts): the
// real backend is NOT started — every API call is mocked with page.route, and
// auth is satisfied by seeding tokens into localStorage so RequireAuth + the
// /me query pass.

const USER = {
  id: 1,
  email: "demo@example.com",
  name: "Demo",
  role: "USER",
  profile: { gender: "female", heightCm: 165, weightKg: 55, equipment: [], theme: "system", language: "zh-TW" },
  createdAt: "2026-05-30T00:00:00Z",
};

function num(value: unknown): number {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

async function seedAuth(page: Page) {
  await page.addInitScript(() => {
    localStorage.setItem("accessToken", "test-access-token");
    localStorage.setItem("refreshToken", "test-refresh-token");
  });
}

test("user can manually correct an AI meal estimate and totals update", async ({ page }) => {
  await seedAuth(page);

  // Mutable server-side state the mocked endpoints read/write, so the PUT is
  // reflected by the subsequent refetch — exactly like the real backend.
  let meal: Record<string, unknown> = {
    id: 1,
    date: "2026-06-01",
    slot: "lunch",
    description: "雞胸肉沙拉",
    items: [{ name: "雞胸肉", grams: 150, kcal: 248, protein: 46.5, fat: 5.4, carb: 0, confidence: 0.9 }],
    totalKcal: 248,
    totalProtein: 46.5,
    totalFat: 5.4,
    totalCarb: 0,
    aiSuggestion: "蛋白足夠，可補充蔬菜",
    createdAt: "2026-05-30T03:00:00Z",
  };
  let lastPutBody: any = null;

  await page.route("**/api/v1/me", async (route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(USER) });
  });

  await page.route("**/api/v1/meals**", async (route) => {
    const request = route.request();
    const url = request.url();
    if (request.method() === "GET" && url.includes("/meals/favorites")) {
      await route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
      return;
    }
    if (request.method() === "GET" && url.includes("/meals/recent")) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: [], page: 0, size: 20, total: 0 }),
      });
      return;
    }
    if (request.method() === "GET") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: [meal], page: 0, size: 20, total: 1 }),
      });
      return;
    }
    if (request.method() === "PUT") {
      const body = JSON.parse(request.postData() ?? "{}");
      lastPutBody = body;
      const items = body.items as Array<Record<string, unknown>>;
      meal = {
        ...meal,
        items,
        aiSuggestion: body.aiSuggestion,
        totalKcal: items.reduce((s, i) => s + num(i.kcal), 0),
        totalProtein: items.reduce((s, i) => s + num(i.protein), 0),
        totalFat: items.reduce((s, i) => s + num(i.fat), 0),
        totalCarb: items.reduce((s, i) => s + num(i.carb), 0),
      };
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(meal) });
      return;
    }
    await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
  });

  await page.goto("/meals");

  // The AI-estimated meal is shown with the "AI 估算" badge before any edit.
  await expect(page.getByText("AI 估算")).toBeVisible();

  // Enter edit mode and bump this item's calories to 300.
  await page.getByRole("button", { name: "手動修正此餐紀錄" }).click();
  await expect(page.getByText("手動修正成分明細")).toBeVisible();

  // NumField order per row: grams, kcal, protein, fat, carb → kcal is index 1.
  const kcalInput = page.getByRole("spinbutton").nth(1);
  await kcalInput.fill("300");

  await page.getByRole("button", { name: "儲存修正" }).click();

  // After the PUT + refetch, the meal is now a user-confirmed value (confidence
  // 1 for every item), so the badge flips and the recomputed total shows 300.
  await expect(page.getByText("已手動修正")).toBeVisible();
  await expect(page.getByText("300", { exact: false }).first()).toBeVisible();

  // The request the frontend sent matches the documented contract: confidence
  // forced to 1, aiSuggestion cleared to null.
  expect(lastPutBody).not.toBeNull();
  expect(lastPutBody.items[0].kcal).toBe(300);
  expect(lastPutBody.items[0].confidence).toBe(1);
  expect(lastPutBody.aiSuggestion).toBeNull();
});

test("user can add a food item manually when the AI returned nothing", async ({ page }) => {
  await seedAuth(page);

  let meal: Record<string, unknown> = {
    id: 2,
    date: "2026-06-01",
    slot: "dinner",
    description: "看不清楚的照片",
    items: [],
    totalKcal: 0,
    totalProtein: 0,
    totalFat: 0,
    totalCarb: 0,
    aiSuggestion: "AI 無法可靠辨識，請重新拍攝或手動輸入。",
    createdAt: "2026-05-30T11:00:00Z",
  };
  let lastPutBody: any = null;

  await page.route("**/api/v1/me", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(USER) }),
  );

  await page.route("**/api/v1/meals**", async (route) => {
    const request = route.request();
    const url = request.url();
    if (request.method() === "GET" && url.includes("/meals/favorites")) {
      await route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
      return;
    }
    if (request.method() === "GET" && url.includes("/meals/recent")) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: [], page: 0, size: 20, total: 0 }),
      });
      return;
    }
    if (request.method() === "GET") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: [meal], page: 0, size: 20, total: 1 }),
      });
      return;
    }
    if (request.method() === "PUT") {
      const body = JSON.parse(request.postData() ?? "{}");
      lastPutBody = body;
      const items = body.items as Array<Record<string, unknown>>;
      meal = { ...meal, items, aiSuggestion: body.aiSuggestion, totalKcal: items.reduce((s, i) => s + num(i.kcal), 0) };
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(meal) });
      return;
    }
    await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
  });

  await page.goto("/meals");

  await page.getByRole("button", { name: "手動修正此餐紀錄" }).click();

  // Empty meal seeds one blank row; fill it in.
  await page.getByPlaceholder("食物名稱").fill("地瓜");
  await page.getByRole("spinbutton").nth(0).fill("120"); // grams
  await page.getByRole("spinbutton").nth(1).fill("110"); // kcal

  await page.getByRole("button", { name: "儲存修正" }).click();

  await expect(page.getByText("已手動修正")).toBeVisible();
  expect(lastPutBody.items).toHaveLength(1);
  expect(lastPutBody.items[0].name).toBe("地瓜");
  expect(lastPutBody.items[0].kcal).toBe(110);
});

test("user can reuse a recent meal and save today's meal as a favorite", async ({ page }) => {
  await seedAuth(page);

  let meals: Array<Record<string, any>> = [
    {
      id: 1,
      date: "2026-06-01",
      slot: "lunch",
      description: "雞胸肉沙拉",
      items: [{ name: "雞胸肉", grams: 150, kcal: 248, protein: 46.5, fat: 5.4, carb: 0, confidence: 0.9 }],
      totalKcal: 248,
      totalProtein: 46.5,
      totalFat: 5.4,
      totalCarb: 0,
      aiSuggestion: "蛋白足夠",
      createdAt: "2026-05-30T03:00:00Z",
    },
  ];
  let favorites: Array<Record<string, unknown>> = [];
  let copyBody: Record<string, unknown> | null = null;
  let favoriteCalled = false;

  await page.route("**/api/v1/me", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(USER) }),
  );

  await page.route("**/api/v1/meals**", async (route) => {
    const request = route.request();
    const url = request.url();
    if (request.method() === "GET" && url.includes("/meals/favorites")) {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(favorites) });
      return;
    }
    if (request.method() === "GET" && url.includes("/meals/recent")) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: [
            {
              id: 2,
              date: "2026-05-31",
              displayName: "鮭魚飯",
              slot: "dinner",
              description: "鮭魚與白飯",
              items: [{ name: "鮭魚", grams: 120, kcal: 240, protein: 26, fat: 14, carb: 0, confidence: 1 }],
              totalKcal: 420,
              totalProtein: 30,
              totalFat: 15,
              totalCarb: 48,
              createdAt: "2026-05-31T10:00:00Z",
            },
          ],
          page: 0,
          size: 20,
          total: 1,
        }),
      });
      return;
    }
    if (request.method() === "POST" && url.includes("/meals/2/copy")) {
      copyBody = JSON.parse(request.postData() ?? "{}");
      meals = [
        {
          id: 3,
          date: String(copyBody.date),
          slot: String(copyBody.slot),
          description: "鮭魚與白飯",
          items: [{ name: "鮭魚", grams: 120, kcal: 240, protein: 26, fat: 14, carb: 0, confidence: 1 }],
          totalKcal: 420,
          totalProtein: 30,
          totalFat: 15,
          totalCarb: 48,
          aiSuggestion: null,
          createdAt: "2026-06-01T05:00:00Z",
        },
        ...meals,
      ];
      await route.fulfill({ status: 201, contentType: "application/json", body: JSON.stringify(meals[0]) });
      return;
    }
    if (request.method() === "POST" && url.includes("/meals/1/favorite")) {
      favoriteCalled = true;
      favorites = [
        {
          id: 9,
          name: "雞胸肉",
          slot: "lunch",
          description: "雞胸肉沙拉",
          items: meals[0].items,
          totalKcal: 248,
          totalProtein: 46.5,
          totalFat: 5.4,
          totalCarb: 0,
          aiSuggestion: null,
          createdAt: "2026-06-01T06:00:00Z",
        },
      ];
      await route.fulfill({ status: 201, contentType: "application/json", body: JSON.stringify(favorites[0]) });
      return;
    }
    if (request.method() === "GET") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: meals, page: 0, size: 20, total: meals.length }),
      });
      return;
    }
    await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
  });

  await page.goto("/meals");

  await expect(page.getByText("快速重用")).toBeVisible();
  await page.getByRole("button", { name: "晚餐" }).click();
  await page.getByRole("button", { name: "複製鮭魚飯到今日" }).click();

  expect(copyBody).not.toBeNull();
  expect(copyBody!.slot).toBe("dinner");
  await expect.poll(async () => page.getByText("鮭魚與白飯").count()).toBeGreaterThanOrEqual(2);

  await page.getByRole("button", { name: "收藏此餐為常用餐點" }).last().click();
  expect(favoriteCalled).toBe(true);
  await expect.poll(async () => page.getByText("雞胸肉", { exact: true }).count()).toBeGreaterThanOrEqual(2);
});

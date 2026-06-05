import { expect, test, type Page } from "@playwright/test";

const USER = {
  id: 1,
  email: "demo@example.com",
  name: "Demo",
  role: "USER",
  profile: { gender: "female", heightCm: 165, weightKg: 55, equipment: [], theme: "system", language: "zh-TW" },
  createdAt: "2026-05-30T00:00:00Z",
};

function today(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

async function seedAuth(page: Page) {
  await page.addInitScript((key) => {
    localStorage.setItem("accessToken", "t");
    localStorage.setItem("refreshToken", "t");
    localStorage.setItem(key as string, "done");
  }, `weightCheckIn:${today()}`);
}

test("user can mark a daily habit complete from the dashboard", async ({ page }) => {
  await seedAuth(page);
  let toggleBody: Record<string, unknown> | null = null;
  let completed = false;

  const habits = () => ({
    date: today(),
    completed: completed ? 1 : 0,
    total: 4,
    items: [
      { type: "WATER", title: "喝水", description: "今天至少補足 6 杯水", completed, completedAt: completed ? "2026-06-06T00:00:00Z" : null },
      { type: "STRETCH", title: "伸展", description: "完成 5 分鐘伸展或活動度練習", completed: false, completedAt: null },
      { type: "PROTEIN", title: "蛋白質", description: "每餐都有蛋白質來源", completed: false, completedAt: null },
      { type: "SLEEP", title: "睡眠", description: "睡眠或休息安排達標", completed: false, completedAt: null },
    ],
  });

  await page.route("**/api/v1/me", (r) => r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(USER) }));
  await page.route("**/api/v1/stats/daily**", (r) => r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ date: today(), intakeKcal: 0, burnKcal: 0, netKcal: 0, protein: 0, fat: 0, carb: 0, weightKg: 55, goalKcal: 1700, workoutsDone: 0, workoutsPlanned: 0 }) }));
  await page.route("**/api/v1/stats/range**", (r) => r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ from: "", to: "", series: [] }) }));
  await page.route("**/api/v1/ai/status**", (r) => r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ provider: "local", textModel: "x", visionModel: "x", loaded: false, idleTimeoutSec: 60 }) }));
  await page.route("**/api/v1/habits/daily**", (r) => r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(habits()) }));
  await page.route("**/api/v1/habits/WATER/toggle", async (route) => {
    toggleBody = JSON.parse(route.request().postData() ?? "{}");
    completed = Boolean(toggleBody.completed);
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(habits()) });
  });

  await page.goto("/");

  await expect(page.getByText("今日習慣")).toBeVisible();
  await expect(page.getByText("0%")).toBeVisible();

  await page.getByRole("button", { name: "喝水 今天至少補足 6 杯水" }).click();

  expect(toggleBody).toEqual({ date: today(), completed: true });
  await expect(page.getByText("25%")).toBeVisible();
});

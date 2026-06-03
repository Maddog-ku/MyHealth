import { expect, test, type Page } from "@playwright/test";

// Management mode: cancel individual exercises (single or batch) and/or delete
// whole plans, via checkboxes + one action bar. Mocked-backend e2e.

const USER = {
  id: 1, email: "demo@example.com", name: "Demo", role: "USER",
  profile: { gender: "male", heightCm: 175, weightKg: 70, equipment: [], theme: "light", language: "zh-TW" },
  createdAt: "2026-05-30T00:00:00Z",
};

function plan() {
  return {
    id: 42, date: "2026-06-03", category: "abs", burnedKcal: null, done: false,
    createdAt: "2026-06-03T03:00:00Z", totalKcal: 130,
    items: [
      { name: "捲腹", sets: 4, reps: "15", restSec: 45, durationSec: 45, kcal: 40, note: "下背貼地", alt: [] },
      { name: "棒式", sets: 3, reps: "45s", restSec: 45, durationSec: 45, kcal: 35, note: "一直線", alt: [] },
      { name: "登山者", sets: 3, reps: "30s", restSec: 60, durationSec: 30, kcal: 55, note: "收核心", alt: [] },
    ],
  };
}

async function seed(page: Page, handlers: {
  onRemove?: (body: Record<string, unknown>) => void;
  onDelete?: (url: string) => void;
}) {
  await page.addInitScript(() => {
    localStorage.setItem("accessToken", "t");
    localStorage.setItem("refreshToken", "t");
  });
  await page.route("**/api/v1/me", (r) => r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(USER) }));
  await page.route("**/api/v1/ai/**", (r) => r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ loaded: true }) }));
  await page.route("**/api/v1/workouts**", async (route) => {
    const req = route.request();
    if (req.method() === "POST" && req.url().includes("/items/remove")) {
      handlers.onRemove?.(req.postDataJSON());
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(plan()) });
      return;
    }
    if (req.method() === "DELETE") {
      handlers.onDelete?.(req.url());
      await route.fulfill({ status: 204, body: "" });
      return;
    }
    await route.fulfill({
      status: 200, contentType: "application/json",
      body: JSON.stringify({ data: [plan()], page: 0, size: 20, total: 1 }),
    });
  });
}

test("cancel a single exercise from a plan", async ({ page }) => {
  let removeBody: Record<string, unknown> | null = null;
  await seed(page, { onRemove: (b) => (removeBody = b) });

  await page.goto("/workouts");
  await page.getByRole("button", { name: "管理" }).click();

  // Tick just the second exercise (index 1).
  await page.getByRole("button", { name: /棒式/ }).click();
  await expect(page.getByText("已選 1 個動作")).toBeVisible();

  // Step 1: opening the action does NOT cancel — it opens a confirmation dialog.
  await page.getByRole("button", { name: "取消所選" }).click();
  const dialog = page.getByRole("dialog", { name: "確認取消運動" });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByText("棒式")).toBeVisible(); // the ticked option is shown
  expect(removeBody).toBeNull();

  // Step 2: confirming in the dialog actually fires the request.
  await dialog.getByRole("button", { name: "確認取消" }).click();
  expect(removeBody).toEqual({ indices: [1] });
});

test("backing out of the confirmation dialog cancels nothing", async ({ page }) => {
  let removeBody: Record<string, unknown> | null = null;
  await seed(page, { onRemove: (b) => (removeBody = b) });

  await page.goto("/workouts");
  await page.getByRole("button", { name: "管理" }).click();
  await page.getByRole("button", { name: /棒式/ }).click();
  await page.getByRole("button", { name: "取消所選" }).click();

  const dialog = page.getByRole("dialog", { name: "確認取消運動" });
  await dialog.getByRole("button", { name: "返回" }).click();

  await expect(dialog).toBeHidden();
  expect(removeBody).toBeNull(); // nothing was cancelled
  await expect(page.getByText("已選 1 個動作")).toBeVisible(); // selection preserved
});

test("batch-cancel multiple exercises at once", async ({ page }) => {
  let removeBody: { indices: number[] } | null = null;
  await seed(page, { onRemove: (b) => (removeBody = b as { indices: number[] }) });

  await page.goto("/workouts");
  await page.getByRole("button", { name: "管理" }).click();

  await page.getByRole("button", { name: /捲腹/ }).click();
  await page.getByRole("button", { name: /登山者/ }).click();
  await expect(page.getByText("已選 2 個動作")).toBeVisible();

  await page.getByRole("button", { name: "取消所選" }).click();
  const dialog = page.getByRole("dialog", { name: "確認取消運動" });
  await expect(dialog.getByText("捲腹")).toBeVisible();
  await expect(dialog.getByText("登山者")).toBeVisible();
  await dialog.getByRole("button", { name: "確認取消" }).click();

  expect(removeBody).not.toBeNull();
  expect([...removeBody!.indices].sort()).toEqual([0, 2]);
});

test("cancelling every exercise removes the whole plan card", async ({ page }) => {
  let emptied = false;
  await page.addInitScript(() => {
    localStorage.setItem("accessToken", "t");
    localStorage.setItem("refreshToken", "t");
  });
  await page.route("**/api/v1/me", (r) => r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(USER) }));
  await page.route("**/api/v1/ai/**", (r) => r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ loaded: true }) }));
  await page.route("**/api/v1/workouts**", async (route) => {
    const req = route.request();
    if (req.method() === "POST" && req.url().includes("/items/remove")) {
      emptied = true; // backend deletes the now-empty plan
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(plan()) });
      return;
    }
    await route.fulfill({
      status: 200, contentType: "application/json",
      body: JSON.stringify({ data: emptied ? [] : [plan()], page: 0, size: 20, total: emptied ? 0 : 1 }),
    });
  });

  await page.goto("/workouts");
  await page.getByRole("button", { name: "管理" }).click();
  for (const name of ["捲腹", "棒式", "登山者"]) {
    await page.getByRole("button", { name: new RegExp(name) }).click();
  }
  await page.getByRole("button", { name: "取消所選" }).click();
  await page.getByRole("dialog", { name: "確認取消運動" }).getByRole("button", { name: "確認取消" }).click();

  // The plan card is gone (card list and empty state are mutually exclusive),
  // so the empty state — not an orphaned 0-exercise card — is what shows.
  await expect(page.getByText("今天尚未產生任何運動菜單")).toBeVisible();
  await expect(page.getByRole("button", { name: "開始訓練" })).toHaveCount(0);
});

test("selecting a whole plan deletes it instead of its items", async ({ page }) => {
  let removeCalled = false;
  let deletedUrl: string | null = null;
  await seed(page, { onRemove: () => (removeCalled = true), onDelete: (u) => (deletedUrl = u) });

  await page.goto("/workouts");
  await page.getByRole("button", { name: "管理" }).click();

  await page.getByRole("checkbox", { name: /選取整份菜單/ }).click();
  await expect(page.getByText("1 份菜單")).toBeVisible();
  // Whole plan selected → its per-exercise checkboxes disappear.
  await expect(page.getByRole("button", { name: /棒式/ })).toHaveCount(0);

  await page.getByRole("button", { name: "取消所選" }).click();
  const dialog = page.getByRole("dialog", { name: "確認取消運動" });
  await expect(dialog.getByText("刪除整份菜單（1）")).toBeVisible();
  await dialog.getByRole("button", { name: "確認取消" }).click();

  expect(deletedUrl).toContain("/workouts/42");
  expect(removeCalled).toBe(false);
});

import { expect, test } from "@playwright/test";

// The header "AI engine connected" badge should deep-link to the AI engine card in Settings.

const USER = {
  id: 1, email: "demo@example.com", name: "Demo", role: "USER",
  profile: { gender: "male", heightCm: 178, weightKg: 72, equipment: [], theme: "system", language: "zh-TW" },
  createdAt: "2026-05-30T00:00:00Z",
};

test("clicking the header AI badge jumps to the AI engine card in Settings", async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem("accessToken", "t");
    localStorage.setItem("refreshToken", "t");
  });
  await page.route("**/api/v1/me", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(USER) }),
  );
  await page.route("**/api/v1/ai/status**", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ provider: "local", textModel: "gemma4:e4b", visionModel: "gemma4:e4b", loaded: true, idleTimeoutSec: 60 }) }),
  );

  // Start on a page without an auto-opening dialog so the header stays interactive.
  await page.goto("/profile");

  await page.getByRole("button", { name: "AI 引擎已連線" }).click();

  await expect(page).toHaveURL(/\/settings#ai-engine$/);
  await expect(page.getByText("AI 核心智能引擎")).toBeVisible();
});

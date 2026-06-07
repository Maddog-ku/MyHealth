import { expect, test, type Page } from "@playwright/test";

// Mocked-backend e2e for device/session management on /settings.

const USER = {
  id: 1, email: "demo@example.com", name: "Demo", role: "USER",
  profile: { gender: "female", heightCm: 165, weightKg: 55, equipment: [], theme: "system", language: "zh-TW" },
  createdAt: "2026-05-30T00:00:00Z",
};

function session(id: number, device: string, current: boolean) {
  return {
    id, device, current,
    createdAt: "2026-06-01T00:00:00Z",
    lastActiveAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(), // 2h ago
    expiresAt: "2026-07-01T00:00:00Z",
  };
}

async function seed(page: Page) {
  await page.addInitScript(() => {
    localStorage.setItem("accessToken", "test-access");
    localStorage.setItem("refreshToken", "raw-current");
  });
  await page.route("**/api/v1/me", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(USER) }),
  );
}

test("lists login devices, flags the current one, and logs out another device", async ({ page }) => {
  let otherRevoked = false;
  let sentRefreshHeader: string | null = null;
  let deletedId: number | null = null;
  await seed(page);

  await page.route("**/api/v1/me/sessions/*", async (route) => {
    // DELETE /me/sessions/{id}
    deletedId = Number(route.request().url().split("/").pop());
    otherRevoked = true;
    await route.fulfill({ status: 204, body: "" });
  });
  await page.route("**/api/v1/me/sessions", async (route) => {
    if (route.request().method() === "GET") {
      sentRefreshHeader = route.request().headers()["x-refresh-token"] ?? null;
      const sessions = otherRevoked
        ? [session(1, "Chrome · macOS", true)]
        : [session(1, "Chrome · macOS", true), session(2, "Safari · iPhone", false)];
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ sessions }) });
      return;
    }
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ sessions: [] }) });
  });

  await page.goto("/settings");

  await expect(page.getByText("登入裝置與安全")).toBeVisible();
  await expect(page.getByText("Chrome · macOS")).toBeVisible();
  await expect(page.getByText("Safari · iPhone")).toBeVisible();
  await expect(page.getByText("目前裝置")).toBeVisible();

  // The current refresh token was sent so the backend can flag "this device".
  expect(sentRefreshHeader).toBe("raw-current");

  // Log out the other (non-current) device.
  await page.getByRole("button", { name: "登出", exact: true }).click();

  await expect(page.getByText("Safari · iPhone")).toBeHidden();
  expect(deletedId).toBe(2);
  // The current device remains and has no revoke button.
  await expect(page.getByText("Chrome · macOS")).toBeVisible();
});

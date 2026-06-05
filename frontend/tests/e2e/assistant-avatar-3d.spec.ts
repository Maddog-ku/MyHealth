import { expect, test, type Locator, type Page } from "@playwright/test";

const USER = {
  id: 1,
  email: "demo@example.com",
  name: "Demo",
  role: "USER",
  profile: {
    gender: "female",
    heightCm: 165,
    weightKg: 55,
    equipment: [],
    theme: "system",
    language: "zh-TW",
    assistantAvatar: "female",
  },
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

async function mockShell(page: Page) {
  await page.route("**/api/v1/me", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(USER) }),
  );
  await page.route("**/api/v1/stats/daily**", (r) =>
    r.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        date: today(),
        intakeKcal: 0,
        burnKcal: 0,
        netKcal: 0,
        protein: 0,
        fat: 0,
        carb: 0,
        weightKg: 55,
        goalKcal: 1700,
        workoutsDone: 0,
        workoutsPlanned: 0,
      }),
    }),
  );
  await page.route("**/api/v1/stats/range**", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ from: "", to: "", series: [] }) }),
  );
  await page.route("**/api/v1/ai/status**", (r) =>
    r.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ provider: "local", textModel: "x", visionModel: "x", loaded: false, idleTimeoutSec: 60 }),
    }),
  );
  await page.route("**/api/v1/ai/chat/history", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ messages: [] }) }),
  );
  await page.route("**/api/v1/habits/daily**", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ date: today(), completed: 0, total: 4, items: [] }) }),
  );
}

async function expectCanvasPainted(locator: Locator) {
  await expect(locator.locator("canvas")).toHaveCount(1);
  await expect
    .poll(
      async () =>
        locator.locator("canvas").evaluate((canvas) => {
          const el = canvas as HTMLCanvasElement;
          const gl = el.getContext("webgl2") ?? el.getContext("webgl");
          if (!gl || el.width === 0 || el.height === 0) return 0;
          const pixels = new Uint8Array(el.width * el.height * 4);
          gl.readPixels(0, 0, el.width, el.height, gl.RGBA, gl.UNSIGNED_BYTE, pixels);
          let painted = 0;
          for (let i = 0; i < pixels.length; i += 4) {
            if (pixels[i + 3] > 8 && pixels[i] + pixels[i + 1] + pixels[i + 2] > 32) painted += 1;
          }
          return painted;
        }),
      { timeout: 8_000 },
    )
    .toBeGreaterThan(80);
}

async function openDashboard(page: Page) {
  await seedAuth(page);
  await mockShell(page);
  await page.goto("/");
  await expect(page.getByLabel("開啟 AI 小幫手")).toBeVisible();
}

test("floating AI assistant renders a nonblank animated 3D avatar", async ({ page }) => {
  let originalAvatarRequested = false;
  page.on("request", (request) => {
    if (request.url().includes("/assistant/coach-female.png")) originalAvatarRequested = true;
  });

  await openDashboard(page);

  const launcherAvatar = page.getByTestId("assistant-avatar-3d");
  await expect(launcherAvatar).toHaveCount(1);
  await expect(launcherAvatar).toHaveAttribute("data-rig", "layered-2.5d");
  await expectCanvasPainted(launcherAvatar);
  expect(originalAvatarRequested).toBe(true);

  await page.getByLabel("開啟 AI 小幫手").click();
  await expect(page.getByText("你的 AI 健身營養小幫手")).toBeVisible();

  const panelAvatars = page.getByTestId("assistant-avatar-3d");
  await expect(panelAvatars).toHaveCount(2);
  await expectCanvasPainted(panelAvatars.nth(0));
  await expectCanvasPainted(panelAvatars.nth(1));
});

test("3D assistant stays framed on mobile", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await openDashboard(page);

  const launcherAvatar = page.getByTestId("assistant-avatar-3d");
  await expect(launcherAvatar).toHaveCount(1);
  await expect(launcherAvatar).toHaveAttribute("data-rig", "layered-2.5d");
  await expectCanvasPainted(launcherAvatar);

  const box = await launcherAvatar.boundingBox();
  expect(box?.width).toBeGreaterThan(80);
  expect(box?.height).toBeGreaterThan(80);
});

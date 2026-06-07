import { expect, test } from "@playwright/test";

// PWA wiring. The service worker only registers in production builds (so it never disrupts
// the Vite dev server these e2e tests run against), but the manifest, theme-color and the
// offline fallback page are part of the served app and verifiable here.

test("the app links a valid web manifest and theme colour", async ({ page }) => {
  await page.goto("/login");

  await expect(page.locator('link[rel="manifest"]')).toHaveAttribute("href", "/manifest.webmanifest");
  await expect(page.locator('meta[name="theme-color"]')).toHaveAttribute("content", "#10b981");
  await expect(page.locator('link[rel="apple-touch-icon"]')).toHaveCount(1);
});

test("the web manifest is reachable and well-formed", async ({ page }) => {
  const res = await page.request.get("/manifest.webmanifest");
  expect(res.ok()).toBeTruthy();

  const manifest = JSON.parse(await res.text());
  expect(manifest.name).toContain("MyHealth");
  expect(manifest.short_name).toBe("MyHealth");
  expect(manifest.display).toBe("standalone");
  expect(manifest.start_url).toBe("/");
  expect(Array.isArray(manifest.icons)).toBeTruthy();
  expect(manifest.icons.length).toBeGreaterThan(0);
});

test("the offline fallback page is served and explains the offline state", async ({ page }) => {
  const res = await page.request.get("/offline.html");
  expect(res.ok()).toBeTruthy();

  const html = await res.text();
  expect(html).toContain("無法連線");
  expect(html).toContain("重新連線");
});

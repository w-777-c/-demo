const { test, expect } = require("@playwright/test");
let pageErrors;
async function open(page) {
  await page.goto("/duel.html");
  await expect(page.locator("#connection-label")).toHaveText("已连接");
}
async function join(page, name) {
  await page.getByLabel("昵称", { exact: true }).fill(name);
  await page.getByRole("button", { name: "加入对战", exact: true }).click();
  await expect(page.locator("#identity")).toContainText(name);
}
async function start(page, context) {
  const other = await context.newPage();
  await open(other);
  await join(page, "青锋");
  await join(other, "赤刃");
  await page.locator("#ready").click();
  await expect(page.locator("#ready")).toBeDisabled();
  await expect(page.locator("#duel-scene")).toHaveAttribute("data-phase", "WAITING");
  await other.locator("#ready").click();
  await expect(page.locator("#duel-scene")).toHaveAttribute("data-phase", "FIGHTING");
  await expect(other.locator("#duel-scene")).toHaveAttribute("data-phase", "FIGHTING");
  return other;
}
async function act(page, action) {
  const revision = await page.locator("#duel-scene").getAttribute("data-revision");
  await page.locator(`[data-action="${action}"]`).click();
  await expect(page.locator("#duel-scene")).not.toHaveAttribute("data-revision", revision);
  await expect(page.locator("#action-status")).not.toHaveText("操作确认中");
}
async function equalHealth(a, b) {
  const health = await a.locator(".fighter-hp").allTextContents();
  await expect(b.locator(".fighter-hp")).toHaveText(health);
}
async function canvasPixels(page) {
  return page.locator("#duel-scene").evaluate((canvas) => {
    const pixels = canvas.getContext("2d").getImageData(0, 0, canvas.width, canvas.height).data;
    let green = 0, coral = 0, hash = 2166136261;
    for (let i = 0; i < pixels.length; i += 4) {
      if (pixels[i + 1] > pixels[i] * 1.2 && pixels[i + 1] > 90) green++;
      if (pixels[i] > pixels[i + 1] * 1.2 && pixels[i] > 150) coral++;
      hash = Math.imul(hash ^ pixels[i], 16777619);
    }
    return { green, coral, hash: hash >>> 0 };
  });
}
test.beforeEach(async ({ page, context }) => {
  pageErrors = [];
  page.on("pageerror", (error) => pageErrors.push(error.message));
  context.on("page", (newPage) => newPage.on("pageerror", (error) => pageErrors.push(error.message)));
  await open(page);
});
test.afterEach(async ({ context }) => {
  for (const page of context.pages()) {
    if (!page.url().includes("duel.html")) continue;
    if (await page.locator("#leave").isVisible()) {
      await page.locator("#leave").click();
      await page.locator("#confirm-command").click();
      await expect(page.locator("#identity")).toHaveText("观战中");
    }
  }
  expect(pageErrors).toEqual([]);
});

test("readiness, turn gating and shared skills update both screens", async ({ page, context }, testInfo) => {
  const other = await start(page, context);
  await expect(page.locator('[data-action="POTION"]')).toBeDisabled();
  await expect(other.locator('[data-action="ATTACK"]')).toBeDisabled();
  await act(page, "DEFEND");
  await expect(page.locator("#fighter-0 .guard-status")).toHaveText("防御姿态");
  await act(other, "POWER_STRIKE");
  await expect(page.locator(".fighter-hp")).toHaveText(["154 / 180", "170 / 180"]);
  await act(page, "DRAIN");
  await expect(page.locator(".fighter-hp")).toHaveText(["171 / 180", "136 / 180"]);
  await equalHealth(page, other);
  await expect(page.locator("#drain-meta")).toContainText("冷却 2");
  await expect(page.locator("#duel-log")).toContainText("生命汲取");
  const pixels = await canvasPixels(page);
  expect(pixels.green).toBeGreaterThan(500);
  expect(pixels.coral).toBeGreaterThan(500);
  await expect.poll(async () => (await canvasPixels(page)).hash).not.toBe(pixels.hash);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  expect(await page.locator("img").evaluateAll((images) => images.every((image) => image.complete && image.naturalWidth > 0))).toBe(true);
  await page.screenshot({ path: testInfo.outputPath("online-duel.png"), fullPage: true });
});

test("plays a complete fight and starts a rematch with the other first player", async ({ page, context }) => {
  const other = await start(page, context);
  for (let turn = 1; turn <= 13; turn++) await act(turn % 2 ? page : other, "ATTACK");
  await expect(page.locator("#phase-label")).toHaveText("青锋获胜");
  await expect(other.locator("#phase-label")).toHaveText("青锋获胜");
  await expect(other.locator("#fighter-1 .fighter-hp")).toHaveText("0 / 180");
  await expect(page.locator('[data-action="ATTACK"]')).toBeDisabled();
  const matchId = await page.locator("#duel-scene").getAttribute("data-match-id");
  await page.locator("#rematch").click();
  await expect(page.locator("#rematch")).toBeDisabled();
  await other.locator("#rematch").click();
  await expect(page.locator("#duel-scene")).not.toHaveAttribute("data-match-id", matchId);
  await expect(page.locator(".fighter-hp")).toHaveText(["180 / 180", "180 / 180"]);
  await expect(other.locator('[data-action="ATTACK"]')).toBeEnabled();
  await expect(page.locator('[data-action="ATTACK"]')).toBeDisabled();
});

test("refresh and network reconnection preserve the seat and authoritative turn", async ({ page, context }) => {
  let route, upstream;
  await page.routeWebSocket("**/ws/duel", (current) => { route = current; upstream = current.connectToServer(); });
  await page.reload();
  await expect(page.locator("#connection-label")).toHaveText("已连接");
  const other = await start(page, context);
  await act(page, "ATTACK");
  await page.reload();
  await expect(page.locator("#identity")).toHaveText("P1 · 青锋");
  await expect(page.locator("#fighter-1 .fighter-hp")).toHaveText("152 / 180");
  const oldRoute = route, oldUpstream = upstream;
  await oldRoute.close({ code: 1012, reason: "Test interruption" });
  await oldUpstream.close();
  await expect(other.locator("#phase-label")).toContainText("等待断线玩家重连");
  await expect(other.locator('[data-action="ATTACK"]')).toBeDisabled();
  await expect(page.locator("#connection-label")).toHaveText("已连接");
  await expect(page.locator("#identity")).toHaveText("P1 · 青锋");
  await expect(other.locator('[data-action="ATTACK"]')).toBeEnabled();
  await act(other, "ATTACK");
  await equalHealth(other, page);
});

test("spectators cannot act and resignation confirmation can be cancelled", async ({ page, context }) => {
  await page.getByLabel("昵称", { exact: true }).fill("<script>");
  await page.getByRole("button", { name: "加入对战", exact: true }).click();
  await expect(page.locator("#duel-error")).toBeVisible();
  const other = await start(page, context);
  const spectator = await context.newPage();
  await open(spectator);
  await expect(spectator.locator("#identity")).toHaveText("观战中");
  await expect(spectator.locator("#join")).toBeDisabled();
  await expect(spectator.locator('[data-action="ATTACK"]')).toBeDisabled();
  await other.locator("#resign").click();
  await other.getByRole("button", { name: "取消", exact: true }).click();
  await expect(other.locator("#duel-scene")).toHaveAttribute("data-phase", "FIGHTING");
  await other.locator("#resign").click();
  await other.keyboard.press("Escape");
  await expect(other.locator("#confirm-dialog")).not.toBeVisible();
  await other.locator("#resign").click();
  await other.locator("#confirm-command").click();
  await expect(spectator.locator("#phase-label")).toHaveText("青锋获胜");
});

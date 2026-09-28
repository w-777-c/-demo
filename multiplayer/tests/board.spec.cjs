const { test, expect } = require("@playwright/test");

async function openBoard(page) {
  await page.goto("/board.html");
  await expect(page.locator("#board-status-text")).toHaveText("已连接");
}

async function clearBoard(page) {
  const previous = await page.locator("#board").getAttribute("data-seq");
  await page.getByRole("button", { name: "清空画布", exact: true }).click();
  await page.locator("#confirm-clear").click();
  await expect(page.locator("#board")).not.toHaveAttribute("data-seq", previous);
  await expect(page.locator("#sync-status")).toHaveText("已同步");
}

async function pixels(page) {
  return page.locator("#board").evaluate((canvas) => {
    const bytes = canvas.getContext("2d").getImageData(0, 0, canvas.width, canvas.height).data;
    let hash = 2166136261, ink = 0;
    for (let i = 0; i < bytes.length; i += 4) {
      if (bytes[i] !== 255 || bytes[i + 1] !== 255 || bytes[i + 2] !== 255) ink++;
      hash = Math.imul(hash ^ bytes[i], 16777619);
      hash = Math.imul(hash ^ bytes[i + 1], 16777619);
      hash = Math.imul(hash ^ bytes[i + 2], 16777619);
    }
    return { hash: hash >>> 0, ink };
  });
}

async function stroke(page, points) {
  const box = await page.locator("#board").boundingBox();
  const at = ([x, y]) => [box.x + x / 1200 * box.width, box.y + y / 720 * box.height];
  await page.mouse.move(...at(points[0]));
  await page.mouse.down();
  for (const point of points.slice(1)) await page.mouse.move(...at(point), { steps: 5 });
  await page.mouse.up();
  await expect(page.locator("#sync-status")).toHaveText("已同步");
}

test.beforeEach(async ({ page }) => { await openBoard(page); await clearBoard(page); });

test("shares exact canvas pixels with peers and late joiners", async ({ page, context }, testInfo) => {
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  const peer = await context.newPage();
  await openBoard(peer);
  await expect(page.locator("#online-count")).toHaveText("2");
  await page.getByRole("button", { name: "松绿", exact: true }).click();
  await stroke(page, [[150, 520], [400, 210], [635, 520], [150, 520]]);
  await page.getByRole("button", { name: "湖蓝", exact: true }).click();
  await stroke(page, [[460, 520], [690, 275], [940, 520], [460, 520]]);
  await peer.getByRole("button", { name: "金黄", exact: true }).click();
  await stroke(peer, Array.from({ length: 19 }, (_, i) => [875 + 55 * Math.cos(i / 18 * Math.PI * 2), 180 + 55 * Math.sin(i / 18 * Math.PI * 2)]));
  await expect.poll(async () => (await pixels(page)).ink).toBeGreaterThan(7000);
  const expected = await pixels(page);
  await expect.poll(() => pixels(peer)).toEqual(expected);
  const late = await context.newPage();
  await openBoard(late);
  await expect.poll(() => pixels(late)).toEqual(expected);
  await expect(page.locator("#online-count")).toHaveText("3");
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  expect(await page.locator("img").evaluateAll((items) => items.every((item) => item.complete && item.naturalWidth > 0))).toBe(true);
  await page.screenshot({ path: testInfo.outputPath("shared-board.png"), fullPage: true });
  await late.setViewportSize({ width: 760, height: 900 });
  expect(await pixels(late)).toEqual(expected);
  expect(errors).toEqual([]);
  await late.close();
  await peer.close();
  await expect(page.locator("#online-count")).toHaveText("1");
});

test("eraser, clear cancellation and confirmed clear stay synchronized", async ({ page, context }) => {
  const peer = await context.newPage();
  await openBoard(peer);
  await page.getByLabel("粗细", { exact: true }).fill("24");
  await stroke(page, [[200, 360], [1000, 360]]);
  await peer.getByRole("button", { name: "橡皮", exact: true }).click();
  await peer.getByLabel("粗细", { exact: true }).fill("32");
  await stroke(peer, [[600, 250], [600, 450]]);
  await expect.poll(() => page.locator("#board").evaluate((canvas) => Array.from(canvas.getContext("2d").getImageData(600, 360, 1, 1).data))).toEqual([255, 255, 255, 255]);
  const expected = await pixels(peer);
  expect(expected.ink).toBeGreaterThan(0);
  await expect.poll(() => pixels(page)).toEqual(expected);
  await page.getByRole("button", { name: "清空画布", exact: true }).click();
  await page.getByRole("button", { name: "取消", exact: true }).click();
  expect(await pixels(page)).toEqual(expected);
  await page.getByRole("button", { name: "清空画布", exact: true }).click();
  await page.keyboard.press("Escape");
  await expect(page.locator("#clear-dialog")).not.toBeVisible();
  expect(await pixels(page)).toEqual(expected);
  await clearBoard(peer);
  await expect.poll(async () => (await pixels(page)).ink).toBe(0);
  await expect.poll(async () => (await pixels(peer)).ink).toBe(0);
  await peer.close();
});

test("disconnected users cannot draw and reconnect restores missed work", async ({ page, context }) => {
  const peer = await context.newPage();
  await openBoard(peer);
  await page.getByRole("button", { name: "断开连接", exact: true }).click();
  await expect(page.locator("#board")).toHaveAttribute("aria-disabled", "true");
  await expect(page.getByRole("button", { name: "清空画布", exact: true })).toBeDisabled();
  const box = await page.locator("#board").boundingBox();
  await page.mouse.click(box.x + 10, box.y + 10);
  expect((await pixels(page)).ink).toBe(0);
  await stroke(peer, [[250, 220], [750, 500]]);
  const expected = await pixels(peer);
  await page.getByRole("button", { name: "重新连接", exact: true }).click();
  await expect(page.locator("#board-status-text")).toHaveText("已连接");
  await expect.poll(() => pixels(page)).toEqual(expected);
  await page.reload();
  await expect(page.locator("#board-status-text")).toHaveText("已连接");
  await expect.poll(() => pixels(page)).toEqual(expected);
  await peer.close();
});

test("supports taps or touch strokes and exports a PNG", async ({ page, context }, testInfo) => {
  const box = await page.locator("#board").boundingBox();
  if (testInfo.project.use.isMobile) {
    const cdp = await context.newCDPSession(page);
    await cdp.send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints: [{ x: box.x + 30, y: box.y + 35 }] });
    await cdp.send("Input.dispatchTouchEvent", { type: "touchMove", touchPoints: [{ x: box.x + 130, y: box.y + 95 }] });
    await cdp.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] });
    await cdp.detach();
  } else {
    await page.mouse.click(box.x + 30, box.y + 35);
  }
  await expect.poll(async () => (await pixels(page)).ink).toBeGreaterThan(0);
  const downloadEvent = page.waitForEvent("download");
  await page.getByRole("button", { name: "导出 PNG", exact: true }).click();
  const download = await downloadEvent;
  const file = testInfo.outputPath("canvas.png");
  await download.saveAs(file);
  const bytes = require("node:fs").readFileSync(file);
  expect(bytes.subarray(0, 8).toString("hex")).toBe("89504e470d0a1a0a");
  expect(bytes.readUInt32BE(16)).toBe(1200);
  expect(bytes.readUInt32BE(20)).toBe(720);
});

test("resynchronizes a missing event and automatically reconnects after interruption", async ({ page, context }) => {
  let route, server, connections = 0, dropNextDraw = false;
  await page.routeWebSocket("**/ws/board", (current) => {
    connections++;
    route = current;
    const upstream = current.connectToServer();
    server = upstream;
    upstream.onMessage((message) => {
      if (dropNextDraw && JSON.parse(message).type === "draw") { dropNextDraw = false; return; }
      current.send(message);
    });
  });
  await page.reload();
  await expect(page.locator("#board-status-text")).toHaveText("已连接");
  const peer = await context.newPage();
  await openBoard(peer);
  dropNextDraw = true;
  await stroke(peer, [[200, 250], [800, 400]]);
  const expected = await pixels(peer);
  expect(dropNextDraw).toBe(false);
  await expect.poll(() => pixels(page)).toEqual(expected);
  await expect(page.locator("#board-status-text")).toHaveText("已连接");
  await route.close({ code: 1012, reason: "Test interruption" });
  await server.close();
  await expect.poll(() => connections).toBeGreaterThan(1);
  await expect(page.locator("#board-status-text")).toHaveText("已连接");
  await expect.poll(() => pixels(page)).toEqual(expected);
  await peer.close();
});

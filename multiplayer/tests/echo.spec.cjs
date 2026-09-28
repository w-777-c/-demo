const { test, expect } = require("@playwright/test");

async function connect(page) {
  await page.getByRole("button", { name: "连接", exact: true }).click();
  await expect(page.locator("#status-text")).toHaveText("已连接");
}

async function send(page, text) {
  await page.getByLabel("消息内容").fill(text);
  await page.getByRole("button", { name: "发送消息", exact: true }).click();
}

test("echoes exact text safely and clears the log", async ({ page }, testInfo) => {
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto("/");
  await expect(page.getByRole("button", { name: "发送消息", exact: true })).toBeDisabled();
  await connect(page);
  const content = "你好，竞技场！\n  <img src=x onerror=alert(1)>  ";
  await send(page, content);
  await expect(page.locator("#received-count")).toHaveText("1");
  expect(await page.locator('[data-kind="received"] .message-body').textContent()).toBe(content);
  await expect(page.locator("#messages img")).toHaveCount(0);
  await send(page, "第二条消息 / echo #2");
  await expect(page.locator("#received-count")).toHaveText("2");
  await expect(page.locator("#sent-count")).toHaveText("2");
  await expect(page.locator("#latency")).toHaveText(/^\d+\.\d$/);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  expect(await page.locator("img").evaluateAll((images) => images.every((image) => image.complete && image.naturalWidth > 0))).toBe(true);
  await page.screenshot({ path: testInfo.outputPath("echo-console.png"), fullPage: true });
  await page.getByRole("button", { name: "清空记录" }).click();
  await expect(page.locator("#messages li")).toHaveCount(0);
  await expect(page.locator("#empty-log")).toBeVisible();
  await expect(page.locator("#sent-count")).toHaveText("2");
  expect(errors).toEqual([]);
});

test("counts UTF-8 bytes and allows empty text and the exact limit", async ({ page }) => {
  await page.goto("/");
  await connect(page);
  await send(page, "");
  await expect(page.locator("#received-count")).toHaveText("1");
  expect(await page.locator('[data-kind="received"] .message-body').textContent()).toBe("");
  await page.getByLabel("消息内容").fill("汉".repeat(1366));
  await expect(page.locator("#byte-count")).toHaveText("4098 / 4096 字节");
  await expect(page.locator("#message-error")).toBeVisible();
  await expect(page.getByRole("button", { name: "发送消息", exact: true })).toBeDisabled();
  await send(page, "x".repeat(4096));
  await expect(page.locator("#received-count")).toHaveText("2");
  expect(await page.locator('[data-kind="received"] .message-body').last().textContent()).toBe("x".repeat(4096));
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test("keeps simultaneous browser connections isolated", async ({ page, context }) => {
  const other = await context.newPage();
  await page.goto("/");
  await other.goto("/");
  await connect(page);
  await connect(other);
  await send(page, "client A");
  await send(other, "client B");
  await expect(page.locator('[data-kind="received"] .message-body')).toHaveText(["client A"]);
  await expect(other.locator('[data-kind="received"] .message-body')).toHaveText(["client B"]);
  await other.close();
});

test("recovers from invalid addresses, failed connections and rapid reconnects", async ({ page }) => {
  await page.goto("/");
  const address = await page.getByLabel("服务地址").inputValue();
  await page.getByLabel("服务地址").fill("https://example.com");
  await page.getByRole("button", { name: "连接", exact: true }).click();
  await expect(page.locator("#connection-error")).toContainText("ws://");
  await page.getByLabel("服务地址").fill(address.replace("/ws/echo", "/ws/missing"));
  await page.getByRole("button", { name: "连接", exact: true }).click();
  await expect(page.locator("#status-text")).toHaveText("连接失败");
  await expect(page.locator("#connection-error")).toBeVisible();
  await page.getByLabel("服务地址").fill(address);
  await connect(page);
  await expect(page.locator("#connection-error")).toBeHidden();
  // Trigger both actions in one turn to exercise callbacks from the old socket.
  await page.evaluate(() => { document.getElementById("connect").click(); document.getElementById("connect").click(); });
  await expect(page.locator("#status-text")).toHaveText("已连接");
  await send(page, "reconnected");
  await expect(page.locator('[data-kind="received"] .message-body')).toHaveText(["reconnected"]);
  await page.getByRole("button", { name: "断开", exact: true }).click();
  await expect(page.locator("#status-text")).toHaveText("未连接");
  await expect(page.getByRole("button", { name: "发送消息", exact: true })).toBeDisabled();
});

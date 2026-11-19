// Optional real-browser OpenRouter regression against an in-memory synthetic snapshot.
// PLAYWRIGHT_MODULE=/absolute/path/to/playwright node web/tests/openrouter-review.cjs
const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");

(async () => {
  const root = path.resolve(__dirname, "..");
  const artifacts = path.resolve(root, "../dist/ui-feedback");
  fs.mkdirSync(artifacts, { recursive: true });
  const snapshot = {
    schemaVersion: 3,
    generatedAt: "2026-11-19T12:00:00+08:00",
    sources: [
      { id: "openrouter", name: "OpenRouter", url: "https://openrouter.ai/rankings" },
    ],
    boards: [
      { slug: "overall", name: "综合能力", dimension: "overall", score_type: "aa_index" },
      {
        slug: "openrouter-weekly-usage",
        name: "OpenRouter 近期使用趋势",
        dimension: "usage",
        score_type: "openrouter_usage",
        source_id: "openrouter",
        url: "https://openrouter.ai/rankings",
        model_count: 10,
        last_success_at: "2026-11-19T04:00:00+00:00",
        health: {
          latestAttemptAt: "2026-11-19T04:00:00+00:00",
          latestAttemptStatus: "ok",
          lastSuccessAt: "2026-11-19T04:00:00+00:00",
          stale: false,
          staleAfterHours: 48,
        },
        metric: {
          name: "OpenRouter 周 token 使用量",
          description: "OpenRouter 官方近期 Top 10 模型的每周 token 处理量，反映该聚合平台的使用趋势；不代表模型能力、质量、市场份额或全球调用量。",
          unit: "万亿 tokens/周",
          higherIsBetter: true,
          comparableAcrossBoards: false,
        },
        source: { id: "openrouter", name: "OpenRouter", url: "https://openrouter.ai/rankings" },
        evaluation: { version: null, versionSource: null, upstreamUpdatedAt: "2026-11-18" },
      },
    ],
    entriesByBoard: {
      overall: [
        {slug:"astra-max",display_name:"GPT-6.2 Astra (max)",vendor:"OpenAI",score:96.1,rank:3},
        {slug:"sol-max",display_name:"GPT-6.2 Sol (max)",vendor:"OpenAI",score:88.7,rank:13},
      ],
      "openrouter-weekly-usage": [
        ["DeepSeek V4.1 Flash 1017", "DeepSeek", 62.1, "+17%"],
        ["Hy4 flash", "Unknown", 46.6, "new"],
        ["DeepSeek V4.1 Flash", "DeepSeek", 16.0, "-2%"],
        ["Hy4", "Unknown", 13.0, "-36%"],
        ["GLM 5.3 Flash", "Z.AI", 10.9, "-9%"],
        ["GPT-6 Luna", "OpenAI", 8.62, "+20%"],
        ["Claude Opus 5.5", "Anthropic", 7.63, "+15%"],
        ["Step 5 Preview", "StepFun", 6.39, "-7%"],
        ["MiMo-V2.6-Flash", "Xiaomi", 4.05, "+25%"],
        ["MiniMax M3.1 Flash", "MiniMax", 3.67, "-4%"],
      ].map(([name, vendor, score, change], index) => ({
        slug: `model-${index + 1}`,
        display_name: name,
        vendor,
        score,
        rank: index + 1,
        weekly_change: change,
      })),
    },
    models: {},
  };
  const executablePath = process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH;
  const browser = await chromium.launch({
    headless: true,
    ...(executablePath ? { executablePath } : {}),
  });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    const errors = [];
    page.on("pageerror", (error) => errors.push(error.message));
    await page.route("http://rankboard.test/**", async (route) => {
      const relative = new URL(route.request().url()).pathname.replace(/^\//, "") || "index.html";
      const target = path.resolve(root, relative);
      if (!target.startsWith(root + path.sep) || !fs.existsSync(target)) {
        return route.fulfill({ status: 404, body: "" });
      }
      const contentType = relative.endsWith(".json")
        ? "application/json"
        : /\.(mjs|js)$/.test(relative)
          ? "text/javascript"
          : relative.endsWith(".css")
            ? "text/css"
            : "text/html";
      await route.fulfill({
        contentType,
        body: relative === "leaderboards.json" ? JSON.stringify(snapshot) : fs.readFileSync(target),
      });
    });
    await page.goto("http://rankboard.test/");
    await page.locator('#search-input').waitFor();
    assert.equal(await page.locator('[data-dimension="usage"]').count(), 0);
    await page.locator('#search-input').fill('GPT6.2');
    await page.waitForTimeout(250);
    assert.equal(await page.locator('#table-body tr').count(), 2);
    assert.match(await page.locator('#table-body').innerText(), /Astra/);
    assert.match(await page.locator('#table-body').innerText(), /Sol/);
    await page.locator('a[href="#/trends"]').first().click();
    await page.locator(".usage-board-panel").waitFor();
    assert.equal(await page.locator(".usage-rank-row").count(), 10);
    assert.equal(await page.locator('[data-dimension]').count(), 0);
    assert.match(await page.locator(".board-context-facts").innerText(), /2026-11-18（\d+ 天前）/);
    assert.match(await page.locator(".usage-board-panel").innerText(), /62\.1/);
    assert.match(await page.locator(".usage-board-panel").innerText(), /\+17%/);
    assert.equal(await page.locator(".data-table").count(), 0);
    await page.locator('#usage-search-input').fill('GPT6');
    assert.equal(await page.locator('.usage-rank-row').count(), 1);
    await page.locator('#usage-search-input').fill('');
    assert.equal(await page.locator('.usage-rank-row').count(), 10);
    await page.locator('.usage-rank-row').first().click();
    await page.locator('#model-modal').waitFor({state:'visible'});
    assert.equal(await page.locator('.detail-radar').count(), 0);
    await page.keyboard.press('Escape');
    await page.locator('a[href="#/trends"]').first().click();
    await page.evaluate(() => window.scrollTo({top:0, behavior:'instant'}));
    await page.waitForTimeout(100);
    await page.screenshot({ path: path.join(artifacts, "openrouter-usage-desktop.png"), fullPage: true });
    for (const width of [390, 320]) {
      await page.setViewportSize({ width, height: 844 });
      await page.waitForTimeout(50);
      await page.evaluate(() => window.scrollTo({top:0, behavior:'instant'}));
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false, `${width}px page overflow`);
      assert.equal(await page.locator(".usage-board-panel").evaluate((node) => node.scrollWidth > node.clientWidth + 1), false, `${width}px panel overflow`);
      for (const node of await page.locator(".usage-rank-row").all()) {
        assert.equal(await node.evaluate((row) => row.scrollWidth > row.clientWidth + 1), false, `${width}px row overflow`);
      }
      await page.screenshot({ path: path.join(artifacts, `openrouter-usage-${width}.png`), fullPage: true });
    }
    assert.deepEqual(errors, []);
    console.log("PASS browser: OpenRouter usage ranking, upstream age, no data-table leakage, 1440/390/320 no overflow");
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});

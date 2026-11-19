// Optional real-browser HELM regression against an in-memory synthetic snapshot.
// No server, deployment, or snapshot writes.
// PLAYWRIGHT_MODULE=/absolute/path/to/playwright node web/tests/helm-review.cjs
const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");

(async () => {
  const root = path.resolve(__dirname, "..");
  const artifacts = path.resolve(root, "../dist/ui-feedback");
  fs.mkdirSync(artifacts, { recursive: true });
  const sourceSnapshot = JSON.parse(fs.readFileSync(path.join(root, "leaderboards.json"), "utf8"));
  const snapshot = {
    ...sourceSnapshot,
    generatedAt: "2026-11-19T08:00:00+08:00",
    sources: [
      ...(sourceSnapshot.sources || []),
      {
        id: "helm",
        name: "Stanford HELM Classic",
        url: "https://crfm.stanford.edu/helm/classic/latest/",
        license: "Apache-2.0",
      },
      {
        id: "terminal_bench",
        name: "Terminal-Bench",
        url: "https://hub.harborframework.com/datasets/terminal-bench/terminal-bench/latest?tab=leaderboard&leaderboard=4-1-0",
        license: "Apache-2.0",
      },
    ],
    boards: [
      {
        slug: "helm-classic-accuracy",
        name: "HELM Classic 综合",
        dimension: "overall",
        score_type: "helm_classic",
        source_id: "helm",
        source: { id: "helm", name: "Stanford HELM Classic" },
        metric: {
          name: "HELM Mean win rate",
          unit: "%",
          description:
            "HELM Classic Mean win rate：按 Accuracy 表相关任务计算模型两两胜出的平均比例并转为百分数，不是任务准确率或跨榜综合分；该历史版本不持续重跑最新模型。",
        },
        evaluation: {
          version: "HELM Classic v0.4.0 (2023-11-17)",
          upstreamUpdatedAt: "2023-11-17",
        },
        last_success_at: "2026-11-19T08:00:00+08:00",
        health: {
          lastSuccessAt: "2026-11-19T08:00:00+08:00",
          latestAttemptStatus: "ok",
          stale: false,
        },
        model_count: 3,
        url: "https://crfm.stanford.edu/helm/classic/latest/",
      },
      {
        slug: "terminal-bench-4-1",
        name: "Terminal-Bench 4.1",
        dimension: "agent",
        score_type: "terminal_bench",
        source_id: "terminal_bench",
        source: { id: "terminal_bench", name: "Terminal-Bench" },
        metric: { name: "task resolution", unit: "%" },
        evaluation: { version: "Terminal-Bench 4.1", upstreamUpdatedAt: null },
        last_success_at: "2026-11-19T08:00:00+08:00",
        health: {
          lastSuccessAt: "2026-11-19T08:00:00+08:00",
          latestAttemptStatus: "ok",
          stale: false,
        },
        model_count: 1,
        url: "https://hub.harborframework.com/datasets/terminal-bench/terminal-bench/latest?tab=leaderboard&leaderboard=4-1-0",
      },
      ...(sourceSnapshot.boards || []).filter(
        (board) => !["helm-classic-accuracy", "terminal-bench-4-1"].includes(board.slug),
      ),
    ],
    entriesByBoard: {
      ...sourceSnapshot.entriesByBoard,
      "helm-classic-accuracy": [
        {
          slug: "synthetic-alpha",
          display_name: "Synthetic Alpha",
          vendor: "Example Lab",
          score: 75,
          rank: 1,
          source_url: "https://crfm.stanford.edu/helm/classic/latest/",
          fetched_at: "2026-11-19T08:00:00+08:00",
        },
        {
          slug: "synthetic-beta",
          display_name: "Synthetic Beta",
          vendor: "Example Lab",
          score: 62.5,
          rank: 2,
          source_url: "https://crfm.stanford.edu/helm/classic/latest/",
          fetched_at: "2026-11-19T08:00:00+08:00",
        },
        {
          slug: "synthetic-gamma",
          display_name: "Synthetic Gamma",
          vendor: "Other Lab",
          score: 7.5,
          rank: 3,
          source_url: "https://crfm.stanford.edu/helm/classic/latest/",
          fetched_at: "2026-11-19T08:00:00+08:00",
        },
      ],
      "terminal-bench-4-1": [
        {
          slug: "synthetic-agent",
          display_name: "Synthetic Agent",
          vendor: "Example Lab",
          score: 54.2,
          rank: 1,
          source_url: "https://hub.harborframework.com/datasets/terminal-bench/terminal-bench/latest?tab=leaderboard&leaderboard=4-1-0",
          fetched_at: "2026-11-19T08:00:00+08:00",
        },
      ],
    },
  };

  const browser = await chromium.launch({ headless: true });
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
      const body = relative === "leaderboards.json" ? JSON.stringify(snapshot) : fs.readFileSync(target);
      await route.fulfill({ contentType, body });
    });

    await page.goto("http://rankboard.test/");
    await page.locator('[data-dimension="overall"]').waitFor();
    await page.locator('[data-dimension="overall"]').click();
    const helmChip = page.locator('[data-board="helm-classic-accuracy"]');
    await helmChip.waitFor();
    await helmChip.click();
    await page.locator(".board-context").waitFor();

    assert.match(await page.locator(".board-context-source").innerText(), /Stanford HELM Classic/);
    assert.match(await page.locator(".board-context-facts").innerText(), /%/);
    assert.match(await page.locator(".board-context-facts").innerText(), /v0\.4\.0 \(2023-11-17\)/);
    assert.match(await page.locator(".board-context-note").innerText(), /Mean win rate/);
    assert.equal(await page.locator(".data-table tbody tr").count(), 3);
    assert.match(await page.locator(".data-table tbody tr").first().innerText(), /75\.0/);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
    await page.locator(".board-context").scrollIntoViewIfNeeded();
    await page.screenshot({ path: path.join(artifacts, "helm-context-desktop.png"), fullPage: true });

    for (const width of [390, 320]) {
      await page.setViewportSize({ width, height: 844 });
      await page.locator(".board-context").scrollIntoViewIfNeeded();
      assert.equal(
        await page.evaluate(() => document.documentElement.scrollWidth > innerWidth),
        false,
        `${width}px must not overflow horizontally`,
      );
      assert.equal(
        await page.locator(".board-context h2").evaluate((node) => node.scrollWidth > node.clientWidth + 1),
        false,
      );
      for (const child of await page.locator(".board-context-facts dd").all()) {
        assert.equal(await child.evaluate((node) => node.scrollWidth > node.clientWidth + 1), false);
      }
      await page.screenshot({ path: path.join(artifacts, `helm-context-${width}.png`), fullPage: true });
    }

    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.locator('[data-dimension="agent"]').click();
    await page.locator('[data-board="terminal-bench-4-1"]').waitFor();
    await page.locator('[data-board="terminal-bench-4-1"]').click();
    assert.match(await page.locator(".board-context-source").innerText(), /Terminal-Bench/);
    assert.equal(await page.locator(".data-table tbody tr").count(), 1);

    await page.setViewportSize({ width: 320, height: 844 });
    await page.locator('[data-dimension="overall"]').click();
    await page.locator('[data-board="helm-classic-accuracy"]').click();
    await page.locator("#source-select").selectOption({ label: "Stanford HELM Classic · 1" });
    await page.locator(".board-context").waitFor();
    assert.match(await page.locator(".board-context-source").innerText(), /Stanford HELM Classic/);
    assert.equal(await page.locator("#source-select").inputValue(), "helm");
    assert.ok((await page.locator("#source-select").locator("option").allInnerTexts()).some((text) => text === "Stanford HELM Classic · 1"));
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);

    assert.deepEqual(errors, []);
    console.log(
      "PASS browser: synthetic HELM source/filter/unit/history, Terminal-Bench context, 1440/390/320 no overflow",
    );
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});

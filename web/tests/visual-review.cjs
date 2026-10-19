// Optional real-browser visual regression. No server, deployment, or snapshot writes.
// PLAYWRIGHT_MODULE=/absolute/path/to/playwright node web/tests/visual-review.cjs
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
(async () => {
  const root = path.resolve(__dirname, '..');
  const artifacts = path.resolve(root, '../dist/ui-feedback');
  fs.mkdirSync(artifacts, { recursive: true });
  const snapshot = JSON.parse(fs.readFileSync(process.env.REVIEW_SNAPSHOT || path.join(root, 'leaderboards.json'), 'utf8'));
  const browser = await chromium.launch({headless:true});
  try {
    const page = await browser.newPage({viewport:{width:1440,height:1000}});
    const errors = [];
    page.on('pageerror', e => errors.push(e.message));
    await page.route('http://rankboard.test/**', async route => {
      const relative = new URL(route.request().url()).pathname.replace(/^\//, '') || 'index.html';
      const target = path.resolve(root, relative);
      if (!target.startsWith(root + path.sep) || !fs.existsSync(target)) return route.fulfill({status:404,body:''});
      const contentType = relative.endsWith('.json') ? 'application/json' : /\.(mjs|js)$/.test(relative) ? 'text/javascript' : relative.endsWith('.css') ? 'text/css' : 'text/html';
      await route.fulfill({contentType, body:relative === 'leaderboards.json' ? JSON.stringify(snapshot) : fs.readFileSync(target)});
    });
    await page.goto('http://rankboard.test/');
    await page.waitForSelector('.board-info');
    assert.equal(await page.locator('.board-info').getAttribute('open'), null);
    assert.equal(await page.locator('.trust-note').count(), 0);
    assert.equal(await page.locator('.board-info .time-grid').isVisible(), false);
    assert.ok((await page.locator('.board-info').boundingBox()).height <= 80, 'closed metadata should remain compact');
    await page.locator('.board-info').scrollIntoViewIfNeeded();
    await page.screenshot({path:path.join(artifacts, 'compact-desktop.png')});
    await page.setViewportSize({width:390,height:844});
    await page.locator('.board-info').scrollIntoViewIfNeeded();
    await page.screenshot({path:path.join(artifacts, 'compact-mobile.png')});
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
    await page.locator('.board-info > summary').click();
    assert.equal(await page.locator('.board-info .time-grid').isVisible(), true);
    await page.screenshot({path:path.join(artifacts, 'compact-mobile-expanded.png')});
    await page.locator('.app-nav [data-view="compare"]').click();
    await page.waitForURL('**/#/compare');
    await page.getByRole('heading', { name: '独立模型对比', exact: true }).waitFor();
    await page.locator('#compare-board').waitFor();
    assert.equal(await page.locator('.board-info .time-grid').isVisible(), false);
    await page.screenshot({path:path.join(artifacts, 'compact-mobile-compare.png')});
    await page.goto('http://rankboard.test/');
    await page.locator('[data-dimension="coding"]').click();
    for (const slug of ['aider-polyglot', 'evalplus-humaneval-plus', 'evalplus-mbpp-plus']) {
      if (!snapshot.boards.some(x => x.slug === slug)) continue;
      await page.locator(`[data-board="${slug}"]`).click();
      assert.ok(await page.locator('.data-table tbody tr').count() > 0);
      assert.equal(await page.locator('.chip.active[data-board]').evaluate(node => {
        const item = node.getBoundingClientRect(), strip = node.parentElement.getBoundingClientRect();
        return item.left >= strip.left - 1 && item.right <= strip.right + 1;
      }), true, 'selected board chip must be visible');
      await page.locator('.board-info').scrollIntoViewIfNeeded();
      await page.screenshot({path:path.join(artifacts, `${slug}-mobile.png`)});
    }
    assert.deepEqual(errors, []);
    console.log('PASS browser: compact closed disclosures, expansion, compare, 390px width, available new-source rows; screenshots in dist/ui-feedback');
  } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exitCode = 1; });

// Optional: PLAYWRIGHT_MODULE=/path/to/playwright node web/tests/news-reader-review.cjs
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
(async () => {
  const root = path.resolve(__dirname, '..');
  const browser = await chromium.launch({headless:true});
  try {
    const page = await browser.newPage({viewport:{width:390,height:844}});
    const errors = [], external = [];
    page.on('pageerror', e => errors.push(e.message));
    const snapshot = JSON.parse(fs.readFileSync(process.env.REVIEW_SNAPSHOT || path.join(root, 'leaderboards.json'), 'utf8'));
    const realArticle = snapshot.news?.articles?.find(x => typeof x.content === 'string' && x.content.trim());
    const article = realArticle || {title:'Native reader verification',url:'/articles/reader-test',published_at:'2026-10-19',content:'First paragraph.\n\n<script>window.readerInjected=true</script>\n\nThird paragraph.'};
    snapshot.news = {articles:[article,{title:'Missing body',url:'/articles/missing-body-test',content:null}]};
    await page.route('**/*', async route => {
      if (!route.request().url().startsWith('http://rankboard.test/')) { external.push(route.request().url()); return route.abort(); }
      const relative = new URL(route.request().url()).pathname.slice(1) || 'index.html';
      const target = path.resolve(root, relative);
      if (!target.startsWith(root + path.sep)) return route.abort();
      await route.fulfill({contentType:relative.endsWith('.json') ? 'application/json' : /\.(mjs|js)$/.test(relative) ? 'text/javascript' : relative.endsWith('.css') ? 'text/css' : 'text/html',body:relative === 'leaderboards.json' ? JSON.stringify(snapshot) : fs.readFileSync(target)});
    });
    await page.goto('http://rankboard.test/#/trends');
    const item = page.locator('.news-item').first();
    const url = await item.getAttribute('data-article');
    await item.click();
    await page.locator('#news-reader:not([hidden])').waitFor();
    assert.equal(await page.locator('#reader-content h1').innerText(), article.title);
    assert.equal(await page.locator('#reader-content p').count(), article.content.trim().split(/\n\s*\n/).filter(Boolean).length + 1);
    assert.equal(await page.locator('iframe').count(), 0);
    assert.equal(await page.locator('#reader-content script').count(), 0);
    assert.equal(await page.locator('[data-reader-source]').getAttribute('href'), url);
    assert.equal(await page.locator('#app').evaluate(x => x.inert), true);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
    const artifacts = path.resolve(root, '../dist/ui-feedback'); fs.mkdirSync(artifacts,{recursive:true});
    await page.screenshot({path:path.join(artifacts,'news-reader-native-mobile.png')});
    await page.setViewportSize({width:1440,height:1000});
    await page.screenshot({path:path.join(artifacts,'news-reader-native-desktop.png')});
    await page.locator('[data-close-reader].icon-button').click();
    assert.equal(await page.locator('#reader-content').innerText(), '');
    assert.equal(await page.locator('#app').evaluate(x => x.inert), false);
    assert.equal(await item.evaluate(x => x === document.activeElement), true);
    await page.locator('.news-item').nth(1).click();
    assert.match(await page.locator('.reader-empty').innerText(), /正文未获取/);
    assert.equal(await page.locator('[data-reader-source]').isVisible(), true);
    await page.keyboard.press('Escape');
    assert.equal(await page.locator('#news-reader').isVisible(), false);
    assert.deepEqual(errors, []);
    assert.deepEqual(external, [], 'native reader must not load publisher pages');
    console.log(`PASS native reader: ${realArticle ? 'actual snapshot' : 'fixture'} paragraphs, no iframe/external requests, safe markup, missing body fallback, mobile/desktop, focus restore`);
  } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exitCode = 1; });

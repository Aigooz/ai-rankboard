// Optional: PLAYWRIGHT_MODULE=/path/to/playwright node web/tests/hle-review.cjs
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
(async()=>{
 const root=path.resolve(__dirname,'..'),snapshot=JSON.parse(fs.readFileSync(path.join(root,'leaderboards.json'),'utf8'));
 assert.equal(snapshot.boards.length,26);
 assert.deepEqual(snapshot.boards.filter(b=>b.dimension==='reasoning').map(b=>b.slug),['livebench-reasoning']);
 assert.equal(snapshot.boards.some(b=>b.slug.startsWith('hle')),false);
 assert.equal(Object.keys(snapshot.entriesByBoard).some(slug=>slug.startsWith('hle')),false);
 const browser=await chromium.launch({headless:true});
 try{
  const page=await browser.newPage({viewport:{width:390,height:844}}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.route('http://rankboard.test/**',async route=>{
   const relative=new URL(route.request().url()).pathname.slice(1)||'index.html',target=path.resolve(root,relative);
   if(!target.startsWith(root+path.sep))return route.abort();
   await route.fulfill({contentType:relative.endsWith('.json')?'application/json':/\.(mjs|js)$/.test(relative)?'text/javascript':relative.endsWith('.css')?'text/css':'text/html',body:fs.readFileSync(target)});
  });
  await page.goto('http://rankboard.test/');
  await page.locator('[data-dimension="reasoning"]').waitFor();
  assert.equal(await page.locator('[data-dimension="reasoning"]').innerText(),'推理');
  await page.locator('[data-dimension="reasoning"]').click();
  assert.equal(await page.locator('.data-table tbody tr').count(),snapshot.entriesByBoard['livebench-reasoning'].length);
  assert.equal(await page.locator('[data-board^="hle"]').count(),0);
  assert.equal(await page.locator('.benchmark-reference').getAttribute('open'),null);
  const artifacts=path.resolve(root,'../dist/ui-feedback');fs.mkdirSync(artifacts,{recursive:true});
  await page.screenshot({path:path.join(artifacts,'reasoning-without-hle-scores-mobile.png')});
  await page.locator('.benchmark-reference > summary').click();
  assert.equal(await page.locator('.public-example').count(),1);
  assert.equal(await page.locator('.public-example').isVisible(),true);
  assert.match(await page.locator('.benchmark-reference').innerText(),/不是 HLE 成绩/);
  assert.doesNotMatch(await page.locator('.benchmark-reference').innerText(),/当前题集版本：LiveBench/);
  assert.match(await page.locator('.training-warning').innerText(),/NEVER/);
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
  await page.locator('.benchmark-reference').scrollIntoViewIfNeeded();
  await page.screenshot({path:path.join(artifacts,'hle-reference-only-mobile.png')});
  await page.setViewportSize({width:1440,height:1000});
  await page.screenshot({path:path.join(artifacts,'hle-reference-only-desktop.png')});
  await page.locator('.app-nav [data-view="compare"]').click();
  await page.waitForURL('**/#/compare');
  await page.locator('#compare-board').waitFor();
  assert.equal(await page.locator('#compare-board option').count(),26);
  assert.equal(await page.locator('#compare-board option[value^="hle"]').count(),0);
  assert.deepEqual(errors,[]);
  console.log('PASS browser: 26 boards, LiveBench-only reasoning, no HLE score chips/options, preserved reference, mobile/desktop');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});

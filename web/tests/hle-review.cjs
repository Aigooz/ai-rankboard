// Optional: PLAYWRIGHT_MODULE=/path/to/playwright node web/tests/hle-review.cjs
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
(async()=>{
 const root=path.resolve(__dirname,'..'),snapshot=JSON.parse(fs.readFileSync(path.join(root,'leaderboards.json'),'utf8'));
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
  assert.equal(await page.locator('[data-dimension="reasoning"]').evaluate(node=>{const r=node.getBoundingClientRect();return r.left>=0&&r.right<=innerWidth;}),true,'HLE entry should be visible without swiping');
  await page.locator('[data-dimension="reasoning"]').click();
  assert.equal(await page.locator('[data-board].active').getAttribute('data-board'),'hle-diamond-no-tools-high');
  assert.equal(await page.locator('.data-table tbody tr').count(),8);
  const artifacts=path.resolve(root,'../dist/ui-feedback');fs.mkdirSync(artifacts,{recursive:true});
  for(const slug of ['hle-official-2025-04','hle-diamond-no-tools-high']){
   await page.locator(`[data-board="${slug}"]`).click();
   assert.equal(await page.locator('.data-table tbody tr').count(),snapshot.entriesByBoard[slug].length);
   assert.equal(await page.locator('.benchmark-reference').getAttribute('open'),null);
   assert.equal(await page.locator('.public-example').isVisible(),false);
   await page.screenshot({path:path.join(artifacts,slug+'-mobile.png')});
  }
  await page.locator('.benchmark-reference > summary').click();
  assert.equal(await page.locator('.public-example').count(),1);
  assert.equal(await page.locator('.public-example').isVisible(),true);
  assert.match(await page.locator('.public-example').innerText(),/Ecology|Edward V/);
  assert.match(await page.locator('.benchmark-reference').innerText(),/题集版本日期不等于/);
  assert.match(await page.locator('.training-warning').innerText(),/NEVER/);
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
  await page.locator('.benchmark-reference').scrollIntoViewIfNeeded();
  await page.screenshot({path:path.join(artifacts,'hle-public-example-mobile.png')});
  await page.setViewportSize({width:1440,height:1000});
  await page.screenshot({path:path.join(artifacts,'hle-public-example-desktop.png')});
  assert.deepEqual(errors,[]);
  console.log('PASS HLE browser: actual 9/8 model boards, closed reference, one public example, restrictions, mobile/desktop');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});

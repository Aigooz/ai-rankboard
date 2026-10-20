// Optional: PLAYWRIGHT_MODULE=/path/to/playwright node web/tests/detail-review.cjs
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
(async()=>{
 const root=path.resolve(__dirname,'..'),snapshot=JSON.parse(fs.readFileSync(path.join(root,'leaderboards.json'),'utf8'));
 const entries=Object.values(snapshot.entriesByBoard).flat(),entrySlugs=new Set(entries.map(x=>x.slug));
 const catalog=Object.entries(snapshot.models).find(([slug,model])=>!entrySlugs.has(slug)&&model.vendor);
 const single=entries.find(x=>(x.price_in==null)!==(x.price_out==null));
 const browser=await chromium.launch({headless:true});
 try{
  const page=await browser.newPage({viewport:{width:390,height:844}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.route('http://rankboard.test/**',async route=>{
   const relative=new URL(route.request().url()).pathname.slice(1)||'index.html',target=path.resolve(root,relative);
   if(!target.startsWith(root+path.sep))return route.abort();
   await route.fulfill({contentType:relative.endsWith('.json')?'application/json':/\.(js|mjs)$/.test(relative)?'text/javascript':relative.endsWith('.css')?'text/css':'text/html',body:fs.readFileSync(target)});
  });
  await page.goto('http://rankboard.test/');await page.locator('.data-table tr[data-slug]').first().click();
  await page.locator('#model-modal:not([hidden])').waitFor();
  assert.equal(await page.locator('.detail-specs .detail-spec').count(),4);
  assert.ok(await page.locator('.detail-score').count()>0);
  assert.equal(await page.locator('.detail-radar').getAttribute('open'),null);
  assert.equal(await page.locator('#model-modal [data-favorite]').count(),1);assert.equal(await page.locator('#model-modal [data-compare]').count(),1);
  assert.equal(await page.locator('#model-modal .modal-panel').evaluate(node=>node.scrollWidth>node.clientWidth),false);
  const artifacts=path.resolve(root,'../dist/ui-feedback');fs.mkdirSync(artifacts,{recursive:true});
  await page.screenshot({path:path.join(artifacts,'model-detail-mobile.png')});
  await page.setViewportSize({width:1440,height:1000});await page.screenshot({path:path.join(artifacts,'model-detail-desktop.png')});
  const related=page.locator('[data-related-model]').first();
  if(await related.count()) {const slug=await related.getAttribute('data-related-model');await related.click();await page.waitForURL('**/#/model/'+encodeURIComponent(slug));assert.equal(await page.locator('#model-modal').isVisible(),true);}
  if(catalog){await page.goto('http://rankboard.test/#/model/'+encodeURIComponent(catalog[0]));await page.locator('#model-modal:not([hidden])').waitFor();assert.match(await page.locator('#modal-content').innerText(),/暂无榜单成绩/);}
  if(single){await page.goto('http://rankboard.test/#/model/'+encodeURIComponent(single.slug));await page.locator('#model-modal:not([hidden])').waitFor();assert.ok(await page.locator('.detail-pricing').innerText());}
  assert.deepEqual(errors,[]);
  console.log('PASS actual snapshot detail: 4 specs, board results, collapsed radar, related navigation, catalog-only model, single-sided price, mobile/desktop. Catalog:',catalog?.[0],'single:',single?.slug);
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});

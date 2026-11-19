// Optional real-browser regression. No server, deployment, or snapshot writes.
// PLAYWRIGHT_MODULE=/absolute/path/to/playwright node web/tests/board-context-review.cjs
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
(async()=>{
 const root=path.resolve(__dirname,'..'),artifacts=path.resolve(root,'../dist/ui-feedback');
 fs.mkdirSync(artifacts,{recursive:true});
 const snapshot=JSON.parse(fs.readFileSync(path.join(root,'leaderboards.json'),'utf8'));
 const browser=await chromium.launch({headless:true}); const errors=[];
 try{
  const page=await browser.newPage({viewport:{width:1440,height:1000}}); page.on('pageerror',e=>errors.push(e.message));
  await page.route('http://rankboard.test/**',async route=>{const rel=new URL(route.request().url()).pathname.replace(/^\//,'')||'index.html';const target=path.resolve(root,rel);if(!target.startsWith(root+path.sep)||!fs.existsSync(target))return route.fulfill({status:404,body:''});await route.fulfill({contentType:rel.endsWith('.json')?'application/json':/\.(mjs|js)$/.test(rel)?'text/javascript':rel.endsWith('.css')?'text/css':'text/html',body:rel==='leaderboards.json'?JSON.stringify(snapshot):fs.readFileSync(target)});});
  await page.goto('http://rankboard.test/'); await page.waitForSelector('.board-context');
  await page.locator('.board-context').scrollIntoViewIfNeeded(); await page.screenshot({path:path.join(artifacts,'board-context-desktop.png')});
  for(const width of [390,320]){
   await page.setViewportSize({width,height:844}); await page.locator('.board-context').scrollIntoViewIfNeeded(); await page.screenshot({path:path.join(artifacts,`board-context-${width}.png`)});
   assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,`${width} must not overflow`);
   assert.equal(await page.locator('.board-context h2').evaluate(n=>n.scrollWidth>n.clientWidth+1),false);
   for(const child of await page.locator('.board-context-facts dd').all()) assert.equal(await child.evaluate(n=>n.scrollWidth>n.clientWidth+1),false);
  }
  await page.setViewportSize({width:1440,height:1000}); await page.locator('[data-dimension="coding"]').click(); await page.locator('[data-board="aider-polyglot"]').click(); await page.waitForSelector('.board-context'); assert.match(await page.locator('.board-context-facts').innerText(),/%/); await page.locator('.board-context').scrollIntoViewIfNeeded(); await page.screenshot({path:path.join(artifacts,'board-context-aider-desktop.png')});
  await page.setViewportSize({width:320,height:844}); await page.locator('.board-context').scrollIntoViewIfNeeded(); await page.screenshot({path:path.join(artifacts,'board-context-aider-320.png')}); assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
  assert.deepEqual(errors,[]); console.log('PASS browser: board context desktop, 390px, 320px, Aider unit and no overflow');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});

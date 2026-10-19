// Optional: PLAYWRIGHT_MODULE=/path/to/playwright node web/tests/translation-review.cjs
const fs = require('node:fs'), path = require('node:path'), assert = require('node:assert/strict');
const {chromium} = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
(async()=>{
  const browser = await chromium.launch({headless:true});
  try {
    const root = path.resolve(__dirname,'..'), page = await browser.newPage({viewport:{width:390,height:844}});
    const snapshot = JSON.parse(fs.readFileSync(path.join(root,'leaderboards.json'),'utf8'));
    snapshot.news = {articles:[{title:'Translation test',url:'/articles/translation-test',content:'This model is useful.\n\nIt solves coding tasks.'}]};
    await page.route('http://rankboard.test/**',async route=>{
      const relative = new URL(route.request().url()).pathname.slice(1)||'index.html', target = path.resolve(root,relative);
      if (!target.startsWith(root+path.sep)) return route.abort();
      await route.fulfill({contentType:relative.endsWith('.json')?'application/json':/\.(js|mjs)$/.test(relative)?'text/javascript':relative.endsWith('.css')?'text/css':'text/html',body:relative==='leaderboards.json'?JSON.stringify(snapshot):fs.readFileSync(target)});
    });
    await page.goto('http://rankboard.test/#/trends');
    // Real browser CORS fetch, never route or mock this probe.
    const cors = await page.evaluate(async()=>{
      try {const response=await fetch('https://api.mymemory.translated.net/get?q=Hello&langpair=en%7Czh-CN');const data=await response.json();return {ok:response.ok,status:data.responseStatus,text:data.responseData?.translatedText};}catch(e){return {error:e.message};}
    });
    console.log('Real browser CORS probe:',JSON.stringify(cors));
    assert.equal(cors.ok,true); assert.equal(Number(cors.status),200); assert.ok(cors.text);
    let calls=0, fail=true;
    await page.route('https://api.mymemory.translated.net/**',async route=>{
      ++calls;
      if(fail){fail=false;return route.fulfill({status:429,headers:{'Access-Control-Allow-Origin':'*'},body:'quota'});}
      await route.fulfill({headers:{'Access-Control-Allow-Origin':'*'},contentType:'application/json',body:JSON.stringify({responseStatus:200,responseData:{translatedText:'中文译文 '+new URL(route.request().url()).searchParams.get('q')}})});
    });
    await page.locator('.news-item').click();
    await page.locator('#reader-translate').click();
    await page.getByRole('button',{name:'重试翻译',exact:true}).waitFor();
    assert.match(await page.locator('#reader-content').innerText(),/This model/);
    await page.locator('#reader-translate').click();
    await page.getByRole('button',{name:'查看原文',exact:true}).waitFor();
    assert.match(await page.locator('#reader-content h1').innerText(),/中文译文/);
    assert.match(await page.locator('#reader-translation-status').innerText(),/机器翻译，仅供参考/);
    await page.locator('#reader-translate').click();
    assert.equal(await page.locator('#reader-content h1').innerText(),'Translation test');
    await page.locator('#reader-translate').click();
    const savedCalls=calls;
    const artifacts=path.resolve(root,'../dist/ui-feedback');fs.mkdirSync(artifacts,{recursive:true});
    await page.screenshot({path:path.join(artifacts,'news-translation-mobile.png')});
    await page.locator('[data-close-reader].icon-button').click();
    await page.locator('.news-item').click();
    await page.locator('#reader-translate').click();
    await page.getByRole('button',{name:'查看原文',exact:true}).waitFor();
    assert.equal(calls,savedCalls,'reopened article uses memory cache');
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
    console.log('PASS translation UI: real CORS, failure retry, original toggle, session memory cache, 390px screenshot');
  }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});

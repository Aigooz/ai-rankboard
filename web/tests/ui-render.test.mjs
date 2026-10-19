import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import * as core from '../ranking-core.mjs';
const source = fs.readFileSync(new URL('../app.js', import.meta.url), 'utf8').replace(/^import .*?;\r?\n/gm, '').replace('\ninit();', '\n/* test initialization disabled */');
function ui(snapshot) {
  const element = { textContent:'', innerHTML:'', hidden:true, style:{}, querySelectorAll:()=>[], querySelector:()=>null };
  const context = vm.createContext({ ...core, createTranslator:()=>async()=>[], URL, console, Intl, Date, document:{getElementById:()=>element,querySelectorAll:()=>[]}, window:{}, location:{hash:'#/'}, Set, Map });
  vm.runInContext(source, context);
  context.fixture = snapshot;
  vm.runInContext('state.snapshot = normalizeSnapshot(fixture); state.loading = false;', context);
  return expression => vm.runInContext(expression, context);
}
const old = { schemaVersion:3, generatedAt:'2026-10-19T00:00:00Z', boards:[{slug:'overall', name:'综合',dimension:'overall',score_type:'aa_index',url:'https://example.com',last_success_at:'2026-10-18T00:00:00Z'},{slug:'arena-code',name:'Arena',dimension:'coding',score_type:'arena_elo'}], entriesByBoard:{overall:[{slug:'a',display_name:'A',vendor:'OpenAI',score:55,rank:1,price_in:1,price_out:2}], 'arena-code':[{slug:'a',display_name:'A',score:1400,rank:1}]},models:{} };
test('old schema3 renders leaderboard comparison selection and missing history without exceptions', () => {
  const run=ui(old);
  assert.match(run('leaderboardTemplate()'), /快照生成时间/);
  assert.equal(run('historyTemplate("overall")'), '');
  const home = run('leaderboardTemplate()');
  assert.doesNotMatch(home, /class="trust-note"/);
  assert.ok(home.indexOf('id="table-body"') < home.indexOf('class="overview-disclosure"'));
  assert.match(home, /<details class="overview-disclosure"><summary>/);
  assert.match(run('selectTemplate()'), /Arena/);
  run('state.comparison = ["a", "missing"]');
  assert.match(run('comparisonTemplate()'), /本榜未收录/);
  assert.match(run('detailTemplate(modelDetail("a"))'), /不是百分位/);
});
test('optional health anomalies and versioned history are escaped and shown independently', () => {
  const data=structuredClone(old);
  data.boards[0].health={latestAttemptAt:'2026-10-19T01:00:00Z',latestAttemptStatus:'error',error:'<script>bad</script>',anomalies:[{message:'<img src=x>'}]};
  data.boards[0].evaluation={version:'v2',upstreamUpdatedAt:null};
  data.historyByBoard={overall:[{fetchedAt:'2026-10-17T00:00:00Z',evaluationVersion:'v2',entries:[{slug:'a',rank:2,score:50}]},{fetchedAt:'2026-10-18T00:00:00Z',evaluationVersion:'v2',entries:[{slug:'a',rank:1,score:55}]}]};
  const run=ui(data);
  const info=run('boardInfo("overall")');
  assert.match(info, /&lt;script&gt;/); assert.doesNotMatch(info, /<script>/);
  assert.match(info, /v2 \/ 未提供/);
  assert.match(run('historyTemplate("overall")'), /50 → 55/);
  run('state.snapshot.historyByBoard.overall[1].evaluationVersion = "v3"');
  assert.match(run('historyTemplate("overall")'), /不推断/);
});
test('combined filters and favorites use current board before sorting', () => {
  const data = structuredClone(old);
  data.entriesByBoard.overall.push({slug:'b', display_name:'Beta', vendor:'DeepSeek',license:'MIT',score:50,rank:2}, {slug:'c',display_name:'Gamma',vendor:'Unknown',license:'MIT',score:60,rank:3});
  const run = ui(data);
  run('state.region="domestic"; state.license="MIT"; state.query="beta"; favorites.add("b"); state.favoritesOnly=true; state.sort="score";');
  assert.equal(run('filteredEntries().length'), 1);
  assert.equal(run('filteredEntries()[0].slug'), 'b');
  run('state.query="no-match"');
  assert.match(run('leaderboardTemplate()'), /没有符合条件/);
  run('state.query=""; state.region="unknown"; state.favoritesOnly=false');
  assert.equal(run('filteredEntries()[0].slug'), 'c');
});
test('Aider and EvalPlus coding boards appear without a hardcoded source allowlist', () => {
  const data = structuredClone(old);
  for (const [slug, source, scoreType] of [['aider-polyglot','Aider','aider_polyglot'], ['evalplus-humaneval-plus','EvalPlus','evalplus']]) {
    data.boards.push({slug, name:source + ' coding',dimension:'coding',score_type:scoreType,source:{id:source.toLowerCase(),name:source,url:'https://example.com/'+slug}, metric:{description:source+' success rate'}});
    data.entriesByBoard[slug] = [{slug:'model-'+slug,display_name:source+' model',score:91,rank:1}];
  }
  const run = ui(data);
  run('state.dimension="coding"; state.board="aider-polyglot"');
  const html = run('leaderboardTemplate()');
  assert.match(html, /data-board="evalplus-humaneval-plus"/);
  assert.match(html, /Aider model/);
  assert.match(run('boardInfo("evalplus-humaneval-plus")'), /EvalPlus success rate/);
  assert.match(run('comparisonTemplate()'), /Aider coding/);
});

test('incomplete price and unknown currency do not become free or CNY', () => {
  const run = ui(old);
  assert.equal(run('blendedPrice({priceIn:1})'), null);
  assert.equal(run('blendedPrice({priceIn:0,priceOut:0})'), 0);
  assert.equal(run('blendedPrice({priceIn:1,priceOut:1,currency:"EUR"})'), null);
  assert.ok(Math.abs(run('blendedPrice({priceIn:1,priceOut:1,currency:"USD"})') - 7.2) < 0.0001);
});

test('provenance defaults closed with visible failure badges and generic source names', () => {
  const data = structuredClone(old);
  data.sources = [{id:'new-source', name:'New Public Benchmark',url:'https://example.com/new'}];
  data.boards[0].source_id = 'new-source';
  data.boards[0].health = {latestAttemptStatus:'error', error:'bad'};
  const run = ui(data), info = run('boardInfo("overall")');
  assert.match(info, /^<details class="board-info"/);
  assert.doesNotMatch(info.split('<summary>')[0], /\bopen\b/);
  const summary = info.split('</summary>')[0];
  assert.match(summary, /New Public Benchmark/);
  assert.match(summary, /抓取失败/);
  assert.doesNotMatch(summary, /快照生成时间|跨榜分数/);
  assert.match(info, /https:\/\/example.com/);
});

test('industry articles render as reader buttons rather than outbound navigation', () => {
  const data = structuredClone(old);
  data.news = {articles:[{title:'Article <test>',url:'/articles/test-news',published_at:'2026-10-19'},{title:'Unsafe',url:'javascript:alert(1)'}]};
  const run = ui(data), html = run('trendsTemplate()');
  assert.match(html, /<button class="news-item"/);
  assert.match(html, /data-article="https:\/\/artificialanalysis.ai\/articles\/test-news"/);
  assert.match(html, /Article &lt;test&gt;/);
  assert.match(html, /原文地址不可用/);
  assert.doesNotMatch(html, /href="https:\/\/modelsage.cn/);
});

test('native reader escapes snapshot paragraphs and supports missing content', () => {
  const run = ui(old);
  const html = run('articleContentTemplate({title:"Unsafe <title>", published_at:"2026-10-19", content:"First paragraph.\\n\\n<script>evil()</script>\\n\\nThird paragraph."})');
  assert.match(html, /Unsafe &lt;title&gt;/);
  assert.match(html, /&lt;script&gt;evil\(\)&lt;\/script&gt;/);
  assert.match(html, /2026-10-19/);
  assert.doesNotMatch(html, /<script>|<iframe/);
  assert.equal((html.match(/<p>/g) || []).length, 3);
  assert.match(run('articleContentTemplate({content:null})'), /正文未获取/);
  assert.match(run('articleContentTemplate({content:"   "})'), /正文未获取/);
  assert.match(run('articleContentTemplate({content:{bad:true}})'), /正文未获取/);
});

test('HLE reference stays closed, limited to one public text example with no answer', () => {
  const data = structuredClone(old);
  data.boards.push({slug:'livebench-reasoning',name:'LiveBench reasoning',dimension:'reasoning',kind:'livebench',score_type:'livebench',release:'LiveBench release'});
  data.benchmarkReferences = {hle:{name:'HLE',publisher:'CAIS',description:'Original vs Diamond',trainingWarning:'NO TRAINING',canary:'test-canary',licenseNotice:'Do not redistribute',examples:[{modality:'text',category:'Ecology',question:'Public <question>',author:'Author',sourceUrl:'https://lastexam.ai',answer:'DO-NOT-SHOW'}, {modality:'text',question:'SECOND-DO-NOT-SHOW'}]}};
  const run = ui(data), html = run('benchmarkReferenceTemplate("livebench-reasoning")');
  assert.match(html, /^<details class=/); assert.doesNotMatch(html.split('<summary>')[0], /open/);
  assert.match(html, /Public &lt;question&gt;/); assert.match(html, /NO TRAINING/); assert.match(html, /test-canary/);
  assert.match(html, /题集版本日期不等于/); assert.match(html, /Do not redistribute/);
  assert.doesNotMatch(html, /DO-NOT-SHOW/);
  assert.equal(run('benchmarkReferenceTemplate("overall")'), '');
  run('state.snapshot.benchmarkReferences = undefined');
  assert.equal(run('benchmarkReferenceTemplate("livebench-reasoning")'), '');
});

test('reasoning uses snapshot order and defaults to LiveBench without HLE scoring navigation', () => {
  const data = structuredClone(old);
  data.boards.push({slug:'livebench-reasoning',dimension:'reasoning',name:'LiveBench reasoning'}, {slug:'other-reasoning',dimension:'reasoning',name:'Other'});
  const run = ui(data);
  assert.equal(run('DIMENSIONS[2].slug'), 'reasoning');
  assert.equal(run('DIMENSIONS[2].name'), '推理');
  assert.equal(run('boardsForDimension("reasoning").map(x=>x.slug).join(",")'), 'livebench-reasoning,other-reasoning');
  run('state.dimension="reasoning"; state.board="overall"; leaderboardTemplate()');
  assert.equal(run('state.board'),'livebench-reasoning');
});

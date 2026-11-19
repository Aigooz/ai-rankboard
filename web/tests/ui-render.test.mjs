import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import * as core from '../ranking-core.mjs';
import * as presentation from '../detail-presentation.mjs';
const source = fs.readFileSync(new URL('../app.js', import.meta.url), 'utf8').replace(/^import .*?;\r?\n/gm, '').replace('\ninit();', '\n/* test initialization disabled */');
function ui(snapshot) {
  const element = { textContent:'', innerHTML:'', hidden:true, style:{}, querySelectorAll:()=>[], querySelector:()=>null };
  const context = vm.createContext({ ...core, ...presentation, createTranslator:()=>async()=>[], URL, console, Intl, Date, document:{getElementById:()=>element,querySelectorAll:()=>[]}, window:{}, location:{hash:'#/'}, Set, Map });
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
  assert.match(run('detailTemplate(modelDetail("a"))'), /主要榜单成绩/);
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
test('GPT6.2 search finds each exact GPT-6.2 configuration with original rank', () => {
  const data = structuredClone(old);
  data.entriesByBoard.overall = [
    {slug:'astra-max', display_name:'GPT-6.2 Astra (max)', rank:3, score:96.1},
    {slug:'sol-max', display_name:'GPT-6.2 Sol (max)', rank:13, score:88.7},
    {slug:'other', display_name:'GPT-6.1', rank:20, score:80},
  ];
  const run = ui(data);
  run('state.query="GPT6.2"');
  assert.equal(run('filteredEntries().map(x => x.rank).join(",")'), '3,13');
  run('state.query="GPT6.2 Sol"');
  assert.equal(run('filteredEntries()[0].slug'), 'sol-max');
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

test('source filter keeps all source choices available and builds indexes for schema3 snapshots', () => {
  const data = structuredClone(old);
  data.sources = [{id:'arena',name:'Arena Source'}, {id:'other',name:'Other Source'}];
  data.boards[0].source_id = 'arena';
  data.boards[1].source_id = 'other';
  data.boards[1].source = {id:'other',name:'Other Source'};
  data.boards.push({slug:'arena-code-extra',name:'Arena Extra',dimension:'coding',source_id:'arena',score_type:'arena_elo'});
  data.entriesByBoard['arena-code-extra'] = [{slug:'b',display_name:'B',score:60,rank:1}];
  const run = ui(data);
  assert.equal(run('state.snapshot.entriesByModel.a.length'), 2);
  assert.equal(run('state.snapshot.boardBySlug.overall.slug'), 'overall');
  assert.equal(run('state.snapshot.scoreRanges.overall.min'), 55);
  run('state.dimension="coding"; state.sourceId="other"');
  const html = run('leaderboardTemplate()');
  assert.match(html, /value="arena"/);
  assert.match(html, /value="other" selected/);
  assert.equal(run('boardsForDimension("coding").map(board=>board.slug).join(",")'), 'arena-code');
});

test('board display is source-first and keeps its own metric context', () => {
  const data = structuredClone(old);
  data.boards[0].source_id = 'aider';
  data.boards[0].source = { id: 'aider', name: 'Aider' };
  data.boards[0].metric = { name: 'Aider pass@2', description: 'Polyglot 双轮通过率。', unit: '%' };
  data.boards[0].evaluation = { version: 'Aider 2026.11' };
  data.boards[0].health = { latestAttemptStatus: 'error', lastSuccessAt: '2026-11-08T00:00:00Z', stale: true, staleAfterHours: 48 };
  data.entriesByBoard.overall[0].score_ci = 1.5;
  const run = ui(data);
  const html = run('leaderboardTemplate()');
  assert.match(html, /class="board-context"/);
  assert.match(html, /当前榜单/);
  assert.match(html, /Polyglot 双轮通过率/);
  assert.match(html, /Aider 2026\.11/);
  assert.match(html, /缓存可能过期/);
  assert.match(html, /最近抓取失败/);
  assert.match(html, /status-badge-failed/);
  assert.match(html, /指标口径/);
  assert.match(html, /本榜成绩（%）/);
  assert.match(html, /1\.5 %/);
  assert.doesNotMatch(html, /综合榜平均分/);
  assert.match(html, /当前榜平均成绩/);
});

test('incomplete price and unknown currency do not become free or CNY', () => {
  const run = ui(old);
  assert.equal(run('blendedPrice({priceIn:1})'), null);
  assert.equal(run('blendedPrice({priceIn:0,priceOut:0,currency:"CNY"})'), 0);
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

test('reasoning uses neutral snapshot order without HLE scoring navigation', () => {
  const data = structuredClone(old);
  data.boards.push({slug:'livebench-reasoning',dimension:'reasoning',name:'LiveBench reasoning'}, {slug:'other-reasoning',dimension:'reasoning',name:'Other'});
  const run = ui(data);
  assert.equal(run('DIMENSIONS[2].slug'), 'reasoning');
  assert.equal(run('DIMENSIONS[2].name'), '推理');
  assert.equal(run('boardsForDimension("reasoning").map(x=>x.slug).join(",")'), 'livebench-reasoning,other-reasoning');
  run('state.dimension="reasoning"; state.board="overall"; leaderboardTemplate()');
  assert.equal(run('state.board'),'livebench-reasoning');
});

test('OpenRouter is only in trends and leaves home board, filters and radar untouched', () => {
  const data = structuredClone(old);
  data.boards.push({
    slug: 'openrouter-weekly-usage',
    name: 'OpenRouter 近期使用趋势',
    dimension: 'usage',
    score_type: 'openrouter_usage',
    source_id: 'openrouter',
    source: { id: 'openrouter', name: 'OpenRouter', url: 'https://openrouter.ai/rankings' },
    metric: {
      name: 'OpenRouter 周 token 使用量',
      description: '聚合平台周 token 使用趋势，不代表能力或市场份额。',
      unit: '万亿 tokens/周',
    },
    evaluation: { version: null, upstreamUpdatedAt: '2026-10-18' },
    health: { lastSuccessAt: '2026-10-19T00:00:00Z', latestAttemptStatus: 'ok' },
  });
  data.sources = [{ id: 'openrouter', name: 'OpenRouter', url: 'https://openrouter.ai/rankings' }];
  data.entriesByBoard['openrouter-weekly-usage'] = [
    { slug: 'alpha', display_name: 'Alpha Long Model Name', vendor: 'Author A', score: 62.1, rank: 1, weekly_change: '+17%' },
    { slug: 'beta', display_name: 'Beta', vendor: 'Author B', score: 46.6, rank: 2, weekly_change: 'new' },
  ];
  const run = ui(data);
  assert.equal(run('DIMENSIONS.some(x => x.slug === "usage")'), false);
  assert.doesNotMatch(run('leaderboardTemplate()'), /data-dimension="usage"|usage-board-panel/);
  run('state.query="home query"');
  const html = run('trendsTemplate()');
  assert.equal(run('state.board'), 'overall');
  assert.equal(run('state.query'), 'home query');
  assert.match(html, /OpenRouter 近期使用趋势/);
  assert.match(html, /万亿 tokens\/周/);
  assert.match(html, /2026-10-18（\d+ 天前）/);
  assert.match(html, /Alpha Long Model Name/);
  assert.match(html, /\+17%/);
  assert.doesNotMatch(run('usageBoardTemplate()'), /混合价|上下文|模型发布|能力雷达|selection-toolbar|data-dimension/);
  assert.doesNotMatch(run('detailTemplate(modelDetail("alpha"))'), /class="detail-radar"/);
  assert.match(run('detailTemplate(modelDetail("alpha"))'), /使用趋势|OpenRouter|万亿 tokens\/周/);
  assert.equal(
    run('selectRadarScores([{dimension:"usage",boardSlug:"openrouter-weekly-usage",score:62.1,scoreMin:3.67,scoreMax:62.1}]).length'),
    0,
  );
});

test('dimension boards use neutral deterministic order regardless of snapshot source order', () => {
  const data = structuredClone(old);
  data.boards.push(
    {slug:'zeta-first',name:'Zeta',dimension:'speed',source_id:'z-source'},
    {slug:'beta-second',name:'Alpha',dimension:'speed',source_id:'a-source'},
    {slug:'alpha-third',name:'Alpha',dimension:'speed',source_id:'z-source'},
  );
  const run = ui(data);
  assert.equal(
    run('boardsForDimension("speed").map(board=>board.slug).join(",")'),
    'alpha-third,beta-second,zeta-first',
  );
  run('state.sourceId="z-source"');
  assert.equal(
    run('boardsForDimension("speed").map(board=>board.slug).join(",")'),
    'alpha-third,zeta-first',
  );
});
test('current LiveBench 2.0 precedes old releases and its matrix does not mix 1.0', () => {
  const data = structuredClone(old);
  for (const [slug, name, dimension, historical, kind] of [
    ['livebench-overall','LiveBench 1.0 历史 综合','overall',true,'livebench'],
    ['livebench-v2-overall','LiveBench 2.0 综合','overall',false,'livebench_v2'],
    ['livebench-v2-coding','LiveBench 2.0 代码','coding',false,'livebench_v2'],
  ]) {
    data.boards.push({slug, name, dimension, historical, kind, release:'v2 release'});
    data.entriesByBoard[slug]=[{slug:'astra',display_name:'GPT-6.2 Astra [LiveBench 2.0]',rank:2,score:84.87}];
  }
  const run = ui(data);
  assert.ok(run('boardsForDimension("overall").findIndex(x=>x.slug==="livebench-v2-overall") < boardsForDimension("overall").findIndex(x=>x.slug==="livebench-overall")'));
  assert.match(run('livebenchMatrix()'), /LiveBench/);
  assert.doesNotMatch(run('livebenchMatrix()'), /data-board="livebench-overall"/);
});

test('detail supports catalog-only peers and exact pricing provenance without best-rank summary', () => {
 const data=structuredClone(old);
 data.models={catalog:{slug:'catalog',display_name:'Catalog model',vendor:'OpenAI',context_window:'128K',source_url:'https://example.com/catalog-specs'}};
 data.entriesByBoard.overall[0].currency='USD';data.entriesByBoard.overall[0].fetched_at='2026-10-19T00:00:00Z';data.entriesByBoard.overall[0].source_url='https://example.com/exact-price';
 const run=ui(data);
 assert.equal(run('findModel("catalog")'),true);
 assert.equal(run('modelDetail("catalog").scores.length'),0);
 const catalog=run('detailTemplate(modelDetail("catalog"))');assert.match(catalog,/暂无榜单成绩/);assert.match(catalog,/128K/);assert.match(catalog,/https:\/\/example.com\/catalog-specs/);
 const html=run('detailTemplate(modelDetail("a"))');
 assert.match(html,/data-related-model="catalog"/);assert.match(html,/https:\/\/example.com\/exact-price/);
 assert.match(html,/2026/);assert.match(html,/1400 Elo/);
 assert.doesNotMatch(html,/综合名次|最佳/);
 assert.ok(html.indexOf('class="detail-primary"')<html.indexOf('class="detail-pricing"'));
 assert.match(html,/<details class="detail-results"><summary>全部榜单成绩/);
 assert.doesNotMatch(html,/<details class="detail-results" open/);
 assert.doesNotMatch(html,/class="detail-radar"/);
});
test('detail invalid rank and absent metadata are not represented as zero or undefined',()=>{
 const data=structuredClone(old);data.entriesByBoard.overall[0].rank=0;data.entriesByBoard.overall[0].score=null;
 const run=ui(data),html=run('detailTemplate(modelDetail("a"))');assert.match(html,/名次未获取/);assert.match(html,/成绩未获取/);assert.match(html,/未获取/);assert.doesNotMatch(html,/#0|undefined/);
});

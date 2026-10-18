import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import * as core from '../ranking-core.mjs';
const source = fs.readFileSync(new URL('../app.js', import.meta.url), 'utf8').replace(/^import .*?;\r?\n/, '').replace('\ninit();', '\n/* test initialization disabled */');
function ui(snapshot) {
  const element = { textContent:'', innerHTML:'', hidden:true, style:{}, querySelectorAll:()=>[], querySelector:()=>null };
  const context = vm.createContext({ ...core, URL, console, Intl, Date, document:{getElementById:()=>element,querySelectorAll:()=>[]}, window:{}, location:{hash:'#/'}, Set, Map });
  vm.runInContext(source, context);
  context.fixture = snapshot;
  vm.runInContext('state.snapshot = normalizeSnapshot(fixture); state.loading = false;', context);
  return expression => vm.runInContext(expression, context);
}
const old = { schemaVersion:3, generatedAt:'2026-10-19T00:00:00Z', boards:[{slug:'overall', name:'综合',dimension:'overall',score_type:'aa_index',url:'https://example.com',last_success_at:'2026-10-18T00:00:00Z'},{slug:'arena-code',name:'Arena',dimension:'coding',score_type:'arena_elo'}], entriesByBoard:{overall:[{slug:'a',display_name:'A',vendor:'OpenAI',score:55,rank:1,price_in:1,price_out:2}], 'arena-code':[{slug:'a',display_name:'A',score:1400,rank:1}]},models:{} };
test('old schema3 renders leaderboard comparison selection and missing history without exceptions', () => {
  const run=ui(old);
  assert.match(run('leaderboardTemplate()'), /快照生成时间/);
  assert.match(run('leaderboardTemplate()'), /暂无历史记录/);
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
test('incomplete price and unknown currency do not become free or CNY', () => {
  const run = ui(old);
  assert.equal(run('blendedPrice({priceIn:1})'), null);
  assert.equal(run('blendedPrice({priceIn:0,priceOut:0})'), 0);
  assert.equal(run('blendedPrice({priceIn:1,priceOut:1,currency:"EUR"})'), null);
  assert.ok(Math.abs(run('blendedPrice({priceIn:1,priceOut:1,currency:"USD"})') - 7.2) < 0.0001);
});

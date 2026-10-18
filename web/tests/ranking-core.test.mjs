import test from 'node:test';
import assert from 'node:assert/strict';
import { loadFavorites, saveFavorites, loadComparison, saveComparison, toggleComparison, safeUrl, boardStatus, modelRegion, comparableEntries, metricExplanation, historyComparable } from '../ranking-core.mjs';
const memory = () => { const data = new Map(); return { getItem: k => data.get(k), setItem: (k,v) => data.set(k,v) }; };
test('favorites survive reload, malformed and denied storage remain safe', () => {
  const storage = memory(); assert.equal(saveFavorites(storage, new Set(['a','b'])), true);
  assert.deepEqual([...loadFavorites(storage)], ['a','b']);
  assert.equal(loadFavorites({ getItem() { throw Error('denied'); } }).size, 0);
  assert.equal(loadFavorites({ getItem: () => '{oops' }).size, 0);
  assert.equal(loadFavorites({ getItem: () => '[1,null,"a","a"]' }).size, 1);
  assert.equal(loadFavorites({ getItem: () => '{}' }).size, 0);
  assert.equal(saveFavorites(undefined, new Set(['a'])), false);
});
test('comparison deduplicates persisted input and enforces four models', () => {
  assert.equal(loadComparison({ getItem: () => '["a","a",1,"b","c","d","e"]' }).length, 4);
  const storage = memory(); saveComparison(storage, ['a']); assert.deepEqual(loadComparison(storage), ['a']);
  assert.deepEqual(toggleComparison(['a'], 'a').selected, []);
  assert.equal(toggleComparison(['a','b','c','d'], 'e').selected.length, 4);
  assert.ok(toggleComparison(['a','b','c','d'], 'e').error);
  assert.equal(saveComparison(undefined, []), false);
});
test('comparison only reads selected board; missing scores are not fabricated', () => {
  const boards = { arena: [{slug:'a', score:1400}], livebench: [{slug:'b',score:80}] };
  assert.deepEqual(comparableEntries(boards, 'arena', ['a','b']), [{slug:'a',entry:boards.arena[0]}, {slug:'b',entry:null}]);
});
test('three timestamps never substitute generation for collection or upstream version', () => {
  const now = Date.parse('2026-10-19T00:00:00Z');
  assert.equal(boardStatus({generatedAt:'2026-10-19T00:00:00Z'}, now).missing, true);
  assert.equal(boardStatus({last_success_at:'2026-10-16T00:00:00Z'}, now).stale, true);
  assert.equal(boardStatus({last_success_at:'2026-10-18T23:00:00Z'}, now).stale, false);
  assert.equal(boardStatus({health:{lastSuccessAt:'2026-10-16T00:00:00Z',stale:false}}, now).stale, true);
  assert.equal(boardStatus({last_success_at:'bad'}, now).missing, true);
});
test('unknown vendors are not classified as international', () => {
  assert.equal(modelRegion('Z.AI'), 'domestic'); assert.equal(modelRegion('阿里百炼'), 'domestic');
  assert.equal(modelRegion('Google DeepMind'), 'international'); assert.equal(modelRegion('new-vendor'), 'unknown');
});
test('untrusted source links exclude executable URL schemes', () => {
  assert.equal(safeUrl('javascript:alert(1)'), ''); assert.equal(safeUrl('data:text/html,x'), '');
  assert.equal(safeUrl('https://livebench.ai/leaderboard'), 'https://livebench.ai/leaderboard');
});
test('score explanations and history avoid false comparisons', () => {
  assert.match(metricExplanation({score_type:'arena_elo'}), /不是百分制/);
  assert.equal(metricExplanation({metric:{description:'upstream definition'}}), 'upstream definition');
  assert.equal(historyComparable({evaluationVersion:null}, {evaluationVersion:null}), false);
  assert.equal(historyComparable({evaluationVersion:'a'}, {evaluationVersion:'b'}), false);
  assert.equal(historyComparable({evaluationVersion:'a'}, {evaluationVersion:'a'}), true);
});

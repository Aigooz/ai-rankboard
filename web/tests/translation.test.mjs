import test from 'node:test';
import assert from 'node:assert/strict';
import {translationChunks, createTranslator} from '../translation.mjs';
test('translation chunks enforce utf8 byte limit without splitting unicode', () => {
  const source = 'A中😀'.repeat(500);
  const chunks = translationChunks(source);
  assert.equal(chunks.join(''), source);
  for (const chunk of chunks) assert.ok(new TextEncoder().encode(chunk).length <= 450);
});
test('translation is sequential, cached and preserves paragraph boundaries', async () => {
  let running = 0, maxRunning = 0, requests = 0;
  const translate = createTranslator(async url => {
    ++requests; maxRunning = Math.max(maxRunning, ++running);
    await Promise.resolve(); --running;
    const text = new URL(url).searchParams.get('q');
    return {ok:true,json:async()=>({responseStatus:200,responseData:{translatedText:'中'+text}})};
  });
  const result = await translate(['a','b']);
  assert.deepEqual(result, ['中a','中b']); assert.equal(maxRunning,1);
  await translate(['a','b']); assert.equal(requests,2);
});
test('partial successful chunks are retained when retrying failure', async () => {
  let fail = true, requests = 0;
  const translate = createTranslator(async () => { ++requests; if (requests === 2 && fail) throw Error('offline'); return {ok:true,json:async()=>({responseStatus:200,responseData:{translatedText:'译'}})}; });
  await assert.rejects(translate(['a','b'])); fail = false;
  assert.deepEqual(await translate(['a','b']), ['译','译']); assert.equal(requests,3);
});
test('quota and aborted requests do not become valid translations', async () => {
  const translate = createTranslator(async()=>({ok:true,json:async()=>({responseStatus:403,quotaFinished:true,responseData:{translatedText:'quota'}})}));
  await assert.rejects(translate(['a']), /额度/);
  const controller = new AbortController(); controller.abort();
  await assert.rejects(translate(['a'],{signal:controller.signal}), {name:'AbortError'});
});

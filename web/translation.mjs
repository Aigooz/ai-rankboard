// Translation is opt-in. Cache contains only the current page session's text.
export function translationChunks(text, maxBytes = 450) {
  if (maxBytes < 4) throw new Error('分块长度无效');
  const chunks = []; let chunk = '', bytes = 0;
  for (const char of String(text || '')) {
    const size = new TextEncoder().encode(char).length;
    if (bytes + size > maxBytes) { chunks.push(chunk); chunk = ''; bytes = 0; }
    chunk += char; bytes += size;
  }
  if (chunk) chunks.push(chunk);
  return chunks;
}
export function createTranslator(fetcher = fetch) {
  const cache = new Map();
  return async function translate(paragraphs, {signal, onProgress = () => {}} = {}) {
    const pieces = paragraphs.map(text => translationChunks(text));
    const total = pieces.reduce((sum, parts) => sum + parts.length, 0);
    let done = 0;
    const result = [];
    for (const parts of pieces) {
      const translated = [];
      for (const part of parts) {
        if (signal?.aborted) throw new DOMException('翻译已取消', 'AbortError');
        if (!cache.has(part)) {
          const controller = new AbortController();
          const abort = () => controller.abort();
          signal?.addEventListener('abort', abort, {once:true});
          const timer = setTimeout(abort, 20000);
          try {
            const url = new URL('https://api.mymemory.translated.net/get');
            url.searchParams.set('q', part); url.searchParams.set('langpair', 'en|zh-CN');
            const response = await fetcher(url.href, {signal:controller.signal, credentials:'omit', referrerPolicy:'no-referrer'});
            if (!response.ok) throw new Error(`翻译服务 HTTP ${response.status}`);
            const data = await response.json();
            const text = data.responseData?.translatedText;
            if (Number(data.responseStatus) !== 200 || data.quotaFinished === true || typeof text !== 'string' || !text.trim()) throw new Error('翻译服务暂不可用或额度已用完');
            if (cache.size >= 200) cache.delete(cache.keys().next().value);
            cache.set(part, text);
          } finally { clearTimeout(timer); signal?.removeEventListener('abort', abort); }
        }
        if (signal?.aborted) throw new DOMException('翻译已取消', 'AbortError');
        translated.push(cache.get(part));
        onProgress(++done, total);
      }
      result.push(translated.join(''));
    }
    return result;
  };
}

// Pure detail presentation rules; no inferred prices, vendor ownership, or cross-board scores.
export function validAmount(value) {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0;
}
export function currencyCode(value) {
  return typeof value === 'string' && value.trim() ? value.trim().toUpperCase() : null;
}
export function estimateBlendedPrice(entry = {}) {
  if (!validAmount(entry.priceIn) || !validAmount(entry.priceOut)) return null;
  const currency = currencyCode(entry.currency);
  const rate = currency === 'USD' ? 7.2 : ['CNY', 'RMB'].includes(currency) ? 1 : null;
  if (rate == null) return null;
  const price = ((entry.priceIn * .75) + (entry.priceOut * .25)) * rate;
  return Number.isFinite(price) ? price : null;
}
export function rawPriceLabel(amount, currency) {
  if (!validAmount(amount)) return '未获取';
  const code = currencyCode(currency);
  return `${amount.toLocaleString('zh-CN', { maximumFractionDigits: 6 })} ${code || '（币种未获取）'} / 1M tokens`;
}
export function parameterLabel(value) {
  return validAmount(value) && value > 0 ? `${value}B` : '未获取';
}
export function textMetadata(value) {
  return typeof value === 'string' && value.trim() ? value.trim() : '未获取';
}
export function detailDate(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}(?:T.*)?$/.test(value) || !Number.isFinite(Date.parse(value))) return '未获取';
  const date = value.slice(0,10);
  return new Date(date).toISOString().slice(0,10) === date ? date : '未获取';
}
export function mergeModelMetadata(entry = {}, stored = {}) {
  const merged = {...entry};
  for (const [key,value] of Object.entries(stored)) {
    if (value != null && value !== '') merged[key] = value;
  }
  // Pricing is one source record, never combine one board's input with another's output.
  const hasStoredPricing = stored.priceIn != null || stored.priceOut != null;
  const pricing = hasStoredPricing ? stored : entry;
  for (const key of ['priceIn','priceOut','currency']) merged[key] = pricing[key];
  return merged;
}
export function selectPriceRecord(entriesByBoard, slug) {
  const boards = Object.keys(entriesByBoard).sort((a,b) => a === 'overall' ? -1 : b === 'overall' ? 1 : a.localeCompare(b));
  for (const boardSlug of boards) {
    const entry = entriesByBoard[boardSlug].find(item => item.slug === slug && (item.priceIn != null || item.priceOut != null));
    if (entry) return { entry, boardSlug };
  }
  return null;
}
export function scoreUnit(board = {}) {
  const unit = board.metric?.unit;
  if (typeof unit === 'string' && unit.trim()) return unit.trim();
  const type = board.scoreType || board.score_type;
  if (type === 'arena_elo') return 'Elo';
  if (['livebench','swebench','swe_bench','evalplus','aider_polyglot'].includes(type) || String(board.slug || '').startsWith('swe-bench')) return '%';
  return null;
}
export function vendorKey(value) {
  const key = typeof value === 'string' ? value.trim().toLowerCase().replace(/\s+/g,' ') : '';
  return ['','unknown','unknown vendor','未知','未知厂商','未获取','n/a','na','-','—'].includes(key) ? '' : key;
}
export function sameVendorModels(model, models, limit = 8) {
  const key = vendorKey(model.vendor);
  if (!key) return [];
  const unique = new Map();
  for (const candidate of models) {
    if (!candidate?.slug || candidate.slug === model.slug || vendorKey(candidate.vendor) !== key || unique.has(candidate.slug)) continue;
    unique.set(candidate.slug, candidate);
  }
  return [...unique.values()].sort((a,b) => textMetadata(a.displayName).localeCompare(textMetadata(b.displayName), 'zh-CN')).slice(0,limit);
}

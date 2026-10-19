// Pure helpers shared by the UI and dependency-free Node tests.
export const FAVORITES_KEY = "ai-rankboard.favorites.v1";
export function loadFavorites(storage) {
  try {
    const value = JSON.parse(storage?.getItem(FAVORITES_KEY) || "[]");
    return new Set(Array.isArray(value) ? value.filter(x => typeof x === "string" && x.length <= 300).slice(0, 500) : []);
  } catch { return new Set(); }
}
export function saveFavorites(storage, favorites) {
  try { storage.setItem(FAVORITES_KEY, JSON.stringify([...favorites])); return true; }
  catch { return false; }
}
export function toggleComparison(selected, slug) {
  if (selected.includes(slug)) return { selected: selected.filter(x => x !== slug), error: "" };
  if (selected.length >= 4) return { selected, error: "最多对比 4 个模型，请先移除一个。" };
  return { selected: [...selected, slug], error: "" };
}
export function safeUrl(value) {
  try { const url = new URL(value); return ["https:", "http:"].includes(url.protocol) ? url.href : ""; }
  catch { return ""; }
}
const domestic = new Set("alibaba alibabaqwen alibabaath qwen 阿里 阿里巴巴 阿里百炼 通义千问 deepseek 深度求索 zai zhipu zhipuai 智谱 智谱ai 智谱清言 bytedance 字节跳动 火山引擎 豆包 火山引擎豆包 tencent 腾讯 腾讯混元 minimax 稀宇科技 moonshot moonshotai moonshotkimi kimi 月之暗面 stepfun 阶跃星辰 xiaomi 小米 baidu 百度 百度千帆 klingai kling 可灵 快手 kuaishou meituan 美团 antgroup 蚂蚁集团 蚂蚁 hidream 智象未来 01ai 零一万物 sensenova sensetime 商汤 modelbest 面壁智能 internlm internvl 上海人工智能实验室 shanghaiailaboratory huawei 华为 iflytek 科大讯飞".split(" "));
const international = new Set("openai anthropic google googledeepmind deepmind xai spacexai mistral mistralai meta metaai nvidia microsoft microsoftai amazon amazonwebservices aws ai2 allenai ibm cohere blackforestlabs perplexity perplexityai upstage recraft luma lumaai krea ideogram reve runway poolside inception inceptionai kandinsky pika genmo genmoai diffbot leonardoai stabilityai huggingface databricks reka rekaai".split(" "));
export function modelRegion(vendor) {
  const key = String(vendor || "").trim().toLowerCase().replace(/[\s._/\-]+/g, "");
  return domestic.has(key) ? "domestic" : international.has(key) ? "international" : "unknown";
}
export function boardStatus(board = {}, now = Date.now()) {
  const health = board.health || {};
  const success = health.lastSuccessAt || board.lastSuccessAt || board.last_success_at;
  const timestamp = success ? Date.parse(success) : NaN;
  const stale = health.stale === true || (Number.isFinite(timestamp) && now - timestamp > (health.staleAfterHours || 48) * 3600000);
  return { success, stale, missing: !Number.isFinite(timestamp), health };
}
export function comparableEntries(entriesByBoard, board, selected) {
  return selected.map(slug => ({ slug, entry: (entriesByBoard[board] || []).find(x => x.slug === slug) || null }));
}
export function metricExplanation(board = {}) {
  if (board.metric?.description) return board.metric.description;
  const type = board.scoreType || board.score_type;
  if (type === "arena_elo") return "Arena Elo / 偏好评分：由该榜盲测偏好估计，不是百分制；仅同榜、同评测条件下比较。";
  if (type === "livebench" || board.kind === "livebench") return "LiveBench：该分类任务的评测成绩；题集与 release 变化会影响分数，不同分类不能直接等同。";
  if (String(type).includes("swe") || String(board.slug).startsWith("swe")) return "SWE-bench：软件工程任务解决率，受任务子集、Agent 配置及评测条件影响。";
  return "来源定义的指标（" + (type || "未提供类型") + "）：仅用于本榜比较，非本应用计算的跨榜综合分。";
}

export function loadComparison(storage) {
  try { const value = JSON.parse(storage?.getItem("ai-rankboard.compare.v1") || "[]"); return Array.isArray(value) ? [...new Set(value.filter(x => typeof x === "string" && x.length <= 300))].slice(0,4) : []; } catch { return []; }
}
export function saveComparison(storage, selected) {
  try { storage.setItem("ai-rankboard.compare.v1", JSON.stringify(selected.slice(0,4))); return true; } catch { return false; }
}
export function historyComparable(previous, latest) {
  return !!previous?.evaluationVersion && previous.evaluationVersion === latest?.evaluationVersion;
}

// Reader is deliberately restricted to the article publisher, not arbitrary snapshot URLs.
export function articleUrl(value) {
  try {
    const url = new URL(String(value || ""), "https://artificialanalysis.ai");
    if (url.origin !== "https://artificialanalysis.ai" || !/^\/articles\/[a-zA-Z0-9][a-zA-Z0-9_-]*\/?$/.test(url.pathname)) return "";
    return url.origin + url.pathname;
  } catch { return ""; }
}

import { estimateBlendedPrice, rawPriceLabel, parameterLabel, textMetadata, detailDate, mergeModelMetadata, selectPriceRecord, scoreUnit, sameVendorModels, primaryDetailScore } from "./detail-presentation.mjs";
import { createTranslator } from "./translation.mjs";
import { articleUrl, loadComparison, saveComparison, historyComparable, loadFavorites, saveFavorites, toggleComparison, safeUrl, modelRegion, boardStatus, comparableEntries, metricExplanation } from "./ranking-core.mjs";
let storage;
try { storage = window.localStorage; } catch { /* private mode / policy */ }
const favorites = loadFavorites(storage);

const SNAPSHOT_URLS = [
  "./leaderboards.json",
  "https://api.github.com/repos/Aigooz/ai-rankboard/contents/leaderboards.json?ref=main",
  "https://raw.githubusercontent.com/Aigooz/ai-rankboard-updates/main/leaderboards.json",
  "https://raw.githubusercontent.com/Aigooz/ai-rankboard/main/leaderboards.json",
  "https://ghfast.top/https://raw.githubusercontent.com/Aigooz/ai-rankboard-updates/main/leaderboards.json",
];

const DIMENSIONS = [
  { slug: "overall", name: "综合" },
  { slug: "coding", name: "代码" },
  { slug: "reasoning", name: "推理" },
  { slug: "agent", name: "Agent" },
  { slug: "writing", name: "写作" },
  { slug: "multimodal", name: "多模态" },
  { slug: "search", name: "搜索" },
  { slug: "math", name: "数学" },
  { slug: "instruction", name: "指令遵循" },
  { slug: "analysis", name: "数据分析" },
  { slug: "speed", name: "速度" },
  { slug: "value", name: "性价比" },
];

const SCENARIOS = [
  { dimension: "coding", label: "编程" },
  { dimension: "agent", label: "智能体" },
  { dimension: "writing", label: "写作" },
  { dimension: "math", label: "数学" },
  { dimension: "multimodal", label: "视觉" },
  { dimension: "search", label: "搜索" },
];

const VENDOR_COLORS = [
  { test: (value) => value.startsWith("openai"), color: "#101010", fallback: "OA" },
  { test: (value) => value.startsWith("anthropic"), color: "#CC785C", fallback: "AN" },
  { test: (value) => value.startsWith("google"), color: "#4285F4", fallback: "GG" },
  { test: (value) => value.includes("阿里百炼") || value.startsWith("alibaba") || value.includes("qwen"), color: "#FF6A00", fallback: "AL" },
  { test: (value) => value.startsWith("deepseek"), color: "#4D6BFE", fallback: "DS" },
  { test: (value) => value === "xai" || value === "spacexai", color: "#171717", fallback: "XA" },
  { test: (value) => value.startsWith("mistral"), color: "#FF7000", fallback: "MI" },
  { test: (value) => value.includes("moonshot") || value.includes("kimi"), color: "#1B1B1F", fallback: "MK" },
  { test: (value) => value.includes("zhipu") || value.includes("z.ai"), color: "#2A6AF5", fallback: "Z" },
  { test: (value) => value === "meta" || value.startsWith("meta "), color: "#0866FF", fallback: "ME" },
  { test: (value) => value.startsWith("minimax"), color: "#F23F5D", fallback: "MM" },
  { test: (value) => value.startsWith("xiaomi") || value.includes("小米"), color: "#FF6900", fallback: "XM" },
  { test: (value) => value.includes("tencent") || value.includes("腾讯"), color: "#0052D9", fallback: "TX" },
  { test: (value) => value.startsWith("bytedance") || value.includes("火山引擎") || value.includes("豆包"), color: "#325AB4", fallback: "BD" },
  { test: (value) => value.startsWith("nvidia"), color: "#76B900", fallback: "NV" },
  { test: (value) => value.startsWith("amazon"), color: "#FF9900", fallback: "AM" },
  { test: (value) => value.startsWith("microsoft"), color: "#0078D4", fallback: "MS" },
  { test: (value) => value.startsWith("baidu") || value.includes("百度千帆"), color: "#2932E1", fallback: "BD" },
  { test: (value) => value.startsWith("perplexity"), color: "#20B8CD", fallback: "PP" },
];

const state = {
  snapshot: null,
  error: null,
  loading: true,
  dimension: "overall",
  board: "overall",
  query: "",
  sort: "rank",
  vendor: "",
  license: "",
  scatterMetric: "overall",
  region: "",
  favoritesOnly: false,
  comparison: loadComparison(storage),
  compareBoard: "overall",
  notice: "",
};

const app = document.getElementById("app");
const modal = document.getElementById("model-modal");
const modalContent = document.getElementById("modal-content");
const modalTitle = document.getElementById("modal-title");

init();

async function init() {
  let previousView = parseHash().view;
  window.addEventListener("hashchange", () => {
    const route = parseHash();
    closeReader();
    if (route.view !== previousView) window.scrollTo({ top: 0, behavior: "instant" });
    previousView = route.view;
    if (!route.slug) closeModal();
    render();
  });

  document.querySelectorAll("[data-close-modal]").forEach((node) => {
    node.addEventListener("click", () => {
      closeModal();
      location.hash = "#/";
    });
  });

  initReader();
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !modal.hidden) {
      closeModal();
      location.hash = "#/";
    }
  });

  try {
    state.snapshot = await loadSnapshot();
    state.loading = false;
    const route = parseHash();
    if (route.slug && findModel(route.slug)) openModal(route.slug);
    render();
  } catch (error) {
    state.loading = false;
    state.error = error;
    renderError(error);
  }
}

async function loadSnapshot() {
  let lastError = null;
  for (const url of SNAPSHOT_URLS) {
    try {
      const response = await fetch(url, {
        cache: "no-store",
        headers: { Accept: "application/vnd.github.raw+json" },
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const snapshot = await response.json();
      if (snapshot?.schemaVersion !== 3 || !snapshot.entriesByBoard) throw new Error("快照格式不支持");
      return normalizeSnapshot(snapshot);
    } catch (error) {
      lastError = error;
    }
  }
  throw lastError || new Error("无法加载榜单数据");
}

function normalizeSnapshot(snapshot) {
  const entriesByBoard = Object.fromEntries(
    Object.entries(snapshot.entriesByBoard || {}).map(([slug, entries]) => [
      slug,
      entries.map(normalizeEntry),
    ]),
  );
  const models = Object.fromEntries(
    Object.entries(snapshot.models || {}).map(([slug, model]) => [
      slug,
      normalizeModel(model),
    ]),
  );

  return {
    ...snapshot,
    boards: (snapshot.boards || []).map((board) => ({
      ...board,
      sourceId: board.source_id || board.sourceId,
      scoreType: board.score_type || board.scoreType,
      kind: board.kind,
      category: board.category,
      release: board.release,
      lastSuccessAt: board.last_success_at || board.lastSuccessAt,
      modelCount: board.model_count,
    })),
    entriesByBoard,
    models,
    usageRanking: snapshot.usageRanking ? {
      ...snapshot.usageRanking,
      weekLabel: snapshot.usageRanking.week_label,
      totalTokens: snapshot.usageRanking.total_tokens,
      platformWow: snapshot.usageRanking.platform_wow,
      generatedAt: snapshot.usageRanking.generated_at,
      entries: (snapshot.usageRanking.entries || []).map((entry) => ({
        ...entry,
        modelUrl: entry.model_url,
      })),
    } : null,
    news: snapshot.news ? {
      ...snapshot.news,
      articles: (snapshot.news.articles || []).map((article) => ({
        ...article,
        publishedAt: article.published_at,
      })),
    } : null,
  };
}

function normalizeEntry(entry) {
  return {
    ...entry,
    displayName: entry.display_name || entry.displayName || entry.slug,
    paramsB: entry.params_b,
    contextWindow: entry.context_window,
    releaseDate: entry.release_date,
    priceIn: entry.price_in,
    priceOut: entry.price_out,
    sourceUrl: entry.source_url,
    fetchedAt: entry.fetched_at,
  };
}

function normalizeModel(model) {
  return {
    ...model,
    displayName: model.display_name || model.displayName || model.slug,
    paramsB: model.params_b,
    contextWindow: model.context_window,
    sourceUrl: model.source_url,
    fetchedAt: model.fetched_at,
    releaseDate: model.release_date,
    releaseDateSourceUrl: model.release_date_source_url,
    priceIn: model.price_in ?? model.priceIn,
    priceOut: model.price_out ?? model.priceOut,
  };
}

function parseHash() {
  const hash = location.hash.replace(/^#\/?/, "");
  const [path, slug] = hash.split("/");
  if (path === "trends") return { view: "trends" };
  if (path === "select") return { view: "select" };
  if (path === "compare") return { view: "compare" };
  if (path === "model" && slug) return { view: "leaderboard", slug: decodeURIComponent(slug) };
  return { view: "leaderboard" };
}

function render() {
  if (state.loading || !state.snapshot) return;
  const route = parseHash();
  document.querySelectorAll(".app-nav a").forEach((node) => {
    node.classList.toggle("active", node.dataset.view === route.view);
  });
  renderDataMeta();
  if (route.slug && findModel(route.slug)) openModal(route.slug);
  if (route.view === "compare") { app.innerHTML = comparisonTemplate(); bindComparison(); return; }

  if (route.view === "trends") {
    app.innerHTML = trendsTemplate();
    bindTrends();
    return;
  }

  if (route.view === "select") {
    app.innerHTML = selectTemplate();
    bindSelect();
    return;
  }

  app.innerHTML = leaderboardTemplate();
  bindLeaderboard();
}

function renderDataMeta() {
  const generated = formatDate(state.snapshot.generatedAt);
  document.getElementById("data-meta").textContent = `快照生成：${generated} · 来源：${(state.snapshot.sources || []).map(x => x.name || x.id).filter(Boolean).join(" / ") || state.snapshot.source?.name || "见各榜单"}`;
}

function vendorIcon(vendor) {
  const raw = String(vendor || "").trim();
  const key = raw.toLowerCase();
  const spec = VENDOR_COLORS.find((item) => item.test(key)) || {};
  const fallback = spec.fallback || fallbackVendorLabel(raw);
  const color = spec.color || "#5B6472";
  return `<span class="vendor-icon" style="--vendor-color:${color}" aria-hidden="true">${escapeHtml(fallback)}</span>`;
}

function fallbackVendorLabel(vendor) {
  const letters = String(vendor || "").match(/[a-zA-Z]+/g) || [];
  const label = letters.slice(0, 2).map((part) => part[0].toUpperCase()).join("");
  if (label) return label;
  return String(vendor || "").trim().slice(0, 1) || "AI";
}

function scoreColor(score, minScore = 0, maxScore = 100) {
  if (score == null || Number.isNaN(score)) return "#5D6675";
  if (minScore == null || maxScore == null || maxScore <= minScore) return "#5D6675";
  return `rgb(${rgbForScoreRatio(scoreRatio(score, minScore, maxScore)).join(" ")})`;
}

function scoreRatio(score, minScore = 0, maxScore = 100) {
  if (score == null || Number.isNaN(score) || minScore == null || maxScore == null || maxScore <= minScore) return 0;
  return Math.min(Math.max((Number(score) - minScore) / (maxScore - minScore), 0), 1);
}

function rgbForScoreRatio(ratio) {
  const low = [239, 68, 68];
  const mid = [245, 158, 11];
  const high = [34, 197, 94];
  const mix = (from, to, amount) => from.map((value, index) => Math.round(value + (to[index] - value) * amount));
  return ratio < .5 ? mix(low, mid, ratio * 2) : mix(mid, high, (ratio - .5) * 2);
}

function rankColor(rank) {
  if (rank === 1) return "#D97706";
  if (rank === 2) return "#64748B";
  if (rank === 3) return "#B45309";
  return null;
}

function renderError(error) {
  app.innerHTML = `
    <section class="panel error-state">
      <h1>榜单加载失败</h1>
      <p>${escapeHtml(error.message || "网络请求失败")}。请检查网络后重试，或直接访问开放仓库。</p>
      <button class="retry-button" type="button" onclick="location.reload()">重新加载</button>
    </section>
  `;
}

function leaderboardTemplate() {
  const boards = boardsForDimension(state.dimension);
  if (!boards.some((board) => board.slug === state.board)) {
    state.board = boards[0]?.slug || state.board;
  }
  const entries = filteredEntries();
  const generated = formatDate(state.snapshot.generatedAt);
  const overview = leaderboardOverview();
  const livebenchRelease = livebenchReleaseLabel();

  return `
    <div class="view-header">
      <div>
        <h1>模型排行</h1>
        <p>按能力、价格和发布状态对比 ${formatNumber(entries.length)} 个模型</p>
      </div>
      <div class="freshness compact-freshness">快照生成 ${generated}</div>
    </div>
    <div class="dimension-tabs" role="tablist" aria-label="榜单维度">
      ${DIMENSIONS.map((dimension) => `
        <button class="chip ${dimension.slug === state.dimension ? "active" : ""}" type="button" data-dimension="${dimension.slug}">
          ${dimension.name}
        </button>
      `).join("")}
    </div>
    ${boards.length > 1 ? `
      <div class="dimension-tabs" aria-label="具体榜单">
        ${boards.map((board) => `
          <button class="chip ${board.slug === state.board ? "active" : ""}" type="button" data-board="${board.slug}">
            ${escapeHtml(board.name)}
          </button>
        `).join("")}
      </div>
    ` : ""}
    ${boardInfo(state.board)}
    ${benchmarkReferenceTemplate(state.board)}
    ${selectionToolbar()}
    <div class="control-panel">
      <label class="search-field">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m21 21-4.35-4.35"/></svg>
        <input id="search-input" type="search" value="${escapeAttr(state.query)}" aria-label="搜索模型、厂商或 slug" placeholder="搜索模型、厂商或 slug" autocomplete="off">
      </label>
      <select id="sort-select" aria-label="排序方式">
        <option value="rank" ${state.sort === "rank" ? "selected" : ""}>按名次</option>
        <option value="score" ${state.sort === "score" ? "selected" : ""}>按分数</option>
        <option value="price" ${state.sort === "price" ? "selected" : ""}>按价格</option>
        <option value="release" ${state.sort === "release" ? "selected" : ""}>按上新</option>
      </select>
      <select id="vendor-select" aria-label="厂商筛选">
        ${vendorOptions()}
      </select>
      <select id="region-select" aria-label="开发者归属筛选"><option value="">全部归属</option><option value="domestic" ${state.region === "domestic" ? "selected" : ""}>国内</option><option value="international" ${state.region === "international" ? "selected" : ""}>国外</option><option value="unknown" ${state.region === "unknown" ? "selected" : ""}>归属未知</option></select>
      <select id="license-select" aria-label="许可筛选">
        ${licenseOptions()}
      </select>
    </div>
    <div class="panel">
      <div class="table-scroll" tabindex="0" role="region" aria-label="模型榜单，可横向滚动">
        <table class="data-table">
          <thead>
            <tr>
              <th>名次</th><th>模型</th><th>分数</th><th>混合价 / 1M</th><th>上下文</th><th>发布</th><th>收藏 / 对比</th>
            </tr>
          </thead>
          <tbody id="table-body">
            ${tableRows(entries)}
          </tbody>
        </table>
      </div>
      ${entries.length ? "" : `<div class="empty-state">没有符合条件的模型。</div>`}
    </div>
    ${disclosure("筛选与价格说明", "归属按开发者厂商别名识别，不代表 API 部署位置；未识别厂商保留为未知。价格仅作估算，USD 暂按 7.2 换算人民币。")}
    ${historyTemplate(state.board)}
    <details class="overview-disclosure"><summary>概览图表与 LiveBench 能力矩阵</summary>
    <div class="metric-grid">
      ${overview.cards.map((item) => `
        <article class="metric-card" style="--metric-color:${item.color}">
          <span>${item.label}</span>
          <strong>${item.value}</strong>
          <small>${item.detail}</small>
        </article>
      `).join("")}
    </div>
    <div class="insight-grid">
      <section class="panel chart-panel">
        <div class="panel-head">
          <div>
            <h2>价格分布</h2>
            <p class="panel-sub">按综合榜混合价分层</p>
          </div>
        </div>
        <div class="panel-body">
          ${priceDistribution(overview.priced)}
        </div>
      </section>
      <section class="panel chart-panel">
        <div class="panel-head">
          <div>
            <h2>厂商集中度</h2>
            <p class="panel-sub">上榜模型数量前 8 名</p>
          </div>
        </div>
        <div class="panel-body">
          ${vendorDistribution(overview.vendorCounts)}
        </div>
      </section>
      <section class="panel chart-panel">
        <div class="panel-head">
          <div>
            <h2>能力分布</h2>
            <p class="panel-sub">综合榜分数按 20 分一段分层</p>
          </div>
        </div>
        <div class="panel-body">
          ${scoreDistribution(overview.scored)}
        </div>
      </section>
    </div>
    ${livebenchMatrix()}
    </details>
  `;
}

function leaderboardOverview() {
  const entries = state.snapshot.entriesByBoard.overall || [];
  const scored = entries.filter((entry) => entry.score != null);
  const priced = entries.filter((entry) => entry.score != null && blendedPrice(entry) != null);
  const prices = priced.map((entry) => blendedPrice(entry)).sort((a, b) => a - b);
  const averageScore = scored.length ? scored.reduce((total, entry) => total + entry.score, 0) / scored.length : 0;
  const medianPrice = percentile(prices, .5);
  const latestDate = state.snapshot.generatedAt ? new Date(state.snapshot.generatedAt) : new Date();
  const cutoff = new Date(latestDate);
  cutoff.setDate(cutoff.getDate() - 30);
  const newCount = entries.filter((entry) => {
    const date = new Date(entry.releaseDate || "");
    return !Number.isNaN(date.getTime()) && date >= cutoff;
  }).length;
  const vendorCounts = Object.entries(
    entries.reduce((counts, entry) => {
      const vendor = entry.vendor || "未知厂商";
      counts[vendor] = (counts[vendor] || 0) + 1;
      return counts;
    }, {}),
  ).sort((a, b) => b[1] - a[1]);

  return {
    scored,
    priced,
    vendorCounts,
    cards: [
      { label: "模型样本", value: formatNumber(entries.length), detail: `${state.snapshot.boards.length} 个独立榜单；统计样本为综合榜`, color: "#0EA5E9" },
      { label: "综合榜平均分", value: averageScore.toFixed(1), detail: `${formatNumber(scored.length)} 个已评分`, color: "#22C55E" },
      { label: "混合价中位数", value: formatPrice(medianPrice), detail: "(输入 ×3 + 输出) ÷4", color: "#F59E0B" },
      { label: "30 天上新", value: formatNumber(newCount), detail: "以快照日期为基准", color: "#EC4899" },
    ],
  };
}

const LIVEBENCH_MATRIX_COLUMNS = [
  { slug: "livebench-overall", label: "综合" },
  { slug: "livebench-reasoning", label: "推理" },
  { slug: "livebench-coding", label: "代码" },
  { slug: "livebench-agentic-coding", label: "Agent 代码" },
  { slug: "livebench-math", label: "数学" },
  { slug: "livebench-data-analysis", label: "数据分析" },
  { slug: "livebench-writing", label: "语言" },
  { slug: "livebench-instruction-following", label: "指令遵循" },
];

function isLiveBenchBoard(board) {
  return board?.kind === "livebench" || board?.scoreType === "livebench";
}

function livebenchReleaseLabel() {
  const release = state.snapshot.benchmarkMeta?.livebench?.release;
  if (release) return release;
  return state.snapshot.boards.find(isLiveBenchBoard)?.release || "";
}

function livebenchMatrix() {
  const available = LIVEBENCH_MATRIX_COLUMNS
    .map((column) => ({ ...column, board: state.snapshot.boards.find((board) => board.slug === column.slug) }))
    .filter((column) => column.board && (state.snapshot.entriesByBoard[column.slug] || []).length);
  const overall = available.find((column) => column.slug === "livebench-overall");
  if (!overall || available.length < 2) return "";

  const entries = (state.snapshot.entriesByBoard[overall.slug] || []).slice().sort((a, b) => (a.rank || 999) - (b.rank || 999)).slice(0, 10);
  const byBoard = Object.fromEntries(available.map((column) => [column.slug, new Map(
    (state.snapshot.entriesByBoard[column.slug] || []).map((entry) => [entry.slug, entry]),
  )]));
  const ranges = Object.fromEntries(available.map((column) => {
    const values = (state.snapshot.entriesByBoard[column.slug] || []).map((entry) => entry.score).filter((score) => score != null);
    return [column.slug, { min: values.length ? Math.min(...values) : 0, max: values.length ? Math.max(...values) : 100 }];
  }));
  const release = livebenchReleaseLabel();
  const updated = formatDate(state.snapshot.generatedAt);

  return `
    <section class="panel livebench-matrix-panel">
      <div class="panel-head livebench-matrix-head">
        <div>
          <div class="eyebrow">LiveBench 多维评测</div>
          <h2>能力矩阵</h2>
          <p class="panel-sub">按综合榜前 10 名横向比较各能力，颜色越亮代表该分类内相对表现越强。列间不作统一尺度比较。</p>
        </div>
        <div class="benchmark-meta">
          <strong>${escapeHtml(release || "—")}</strong>
          <span>${available.length} 个分类 · 快照生成于 ${escapeHtml(updated)}</span>
        </div>
      </div>
      <div class="matrix-scroll">
        <table class="livebench-matrix">
          <thead>
            <tr>
              <th>模型</th>
              ${available.map((column) => `<th title="${escapeAttr(column.board.name)}">${escapeHtml(column.label)}</th>`).join("")}
            </tr>
          </thead>
          <tbody>
            ${entries.map((entry) => `
              <tr tabindex="0" data-slug="${escapeAttr(entry.slug)}">
                <th scope="row">
                  <span class="matrix-rank">#${entry.rank || "—"}</span>
                  <span class="matrix-model">${escapeHtml(entry.displayName)}</span>
                  <span class="matrix-vendor">${escapeHtml(entry.vendor || "未知厂商")}</span>
                </th>
                ${available.map((column) => {
                  const cell = byBoard[column.slug].get(entry.slug);
                  const range = ranges[column.slug];
                  const ratio = cell?.score == null ? 0 : scoreRatio(cell.score, range.min, range.max);
                  const color = cell?.score == null ? "#94A3B8" : scoreColor(cell.score, range.min, range.max);
                  return `<td class="matrix-score" style="--matrix-color:${color};--matrix-ratio:${(ratio * 100).toFixed(1)}%">${cell?.score == null ? "—" : cell.score.toFixed(1)}<span></span></td>`;
                }).join("")}
              </tr>
            `).join("")}
          </tbody>
        </table>
      </div>
    </section>
  `;
}

function percentile(sortedValues, ratio) {
  if (!sortedValues.length) return null;
  const position = (sortedValues.length - 1) * ratio;
  const lower = Math.floor(position);
  const upper = Math.ceil(position);
  if (lower === upper) return sortedValues[lower];
  return sortedValues[lower] + (sortedValues[upper] - sortedValues[lower]) * (position - lower);
}

function priceDistribution(entries) {
  const bands = [
    { label: "免费", test: (price) => price === 0 },
    { label: "≤¥1", test: (price) => price > 0 && price <= 1 },
    { label: "¥1-5", test: (price) => price > 1 && price <= 5 },
    { label: "¥5-20", test: (price) => price > 5 && price <= 20 },
    { label: ">¥20", test: (price) => price > 20 },
  ];
  const priced = entries.filter((entry) => blendedPrice(entry) != null);
  const max = Math.max(...bands.map((band) => priced.filter((entry) => band.test(blendedPrice(entry))).length), 1);
  return `
    <div class="distribution-list">
      ${bands.map((band, index) => {
        const count = priced.filter((entry) => band.test(blendedPrice(entry))).length;
        return `
          <div class="distribution-row">
            <span>${band.label}</span>
            <span class="distribution-bar"><i style="width:${Math.max((count / max) * 100, count ? 2 : 0)}%;--bar-delay:${index * 65}ms"></i></span>
            <strong>${count}</strong>
          </div>
        `;
      }).join("")}
    </div>
  `;
}

function vendorDistribution(counts) {
  const items = counts.slice(0, 8);
  const max = Math.max(...items.map(([, count]) => count), 1);
  return `
    <div class="distribution-list">
      ${items.map(([vendor, count], index) => `
        <div class="distribution-row">
          <span class="distribution-name" title="${escapeAttr(vendor)}">${escapeHtml(vendor)}</span>
          <span class="distribution-bar"><i style="width:${Math.max((count / max) * 100, 4)}%;--bar-delay:${index * 55}ms"></i></span>
          <strong>${count}</strong>
        </div>
      `).join("")}
    </div>
  `;
}

function tableRows(entries) {
  const scoredCount = entries.filter((entry) => entry.score != null).length;
  return entries.map((entry, index) => {
    const price = blendedPrice(entry);
    const rank = entry.rank || index + 1;
    const rankColorValue = rankColor(rank);
    return `
      <tr tabindex="0" data-slug="${escapeAttr(entry.slug)}">
        <td class="rank-cell">
          <span class="rank-badge" ${rankColorValue ? `style="--rank-color:${rankColorValue}"` : ""}>${rank}</span>
        </td>
        <td>
          <div class="model-cell">
            ${vendorIcon(entry.vendor)}
            <span class="model-info">
              <span class="model-name">${escapeHtml(entry.displayName)}</span>
              <span class="model-vendor">${escapeHtml(entry.vendor || "未知厂商")}</span>
            </span>
          </div>
        </td>
        <td class="score-cell">${scoreCell(entry, scoredCount)}</td>
        <td class="num">${formatPrice(price)}</td>
        <td class="num">${escapeHtml(entry.contextWindow || "—")}</td>
        <td class="num">${formatDate(entry.releaseDate)}</td>
        <td>${modelActions(entry.slug)}</td>
      </tr>
    `;
  }).join("");
}

function scoreCell(entry, scoredCount) {
  if (entry.score == null) {
    return `
      <span class="score-pill" style="--score-color:#5D6675">—</span>
      <span class="score-meter" style="--score-ratio:0%"><i></i></span>
    `;
  }
  const values = (state.snapshot.entriesByBoard[state.board] || []).map(x => x.score).filter(Number.isFinite);
  const min = Math.min(...values), max = Math.max(...values);
  const ratio = scoreRatio(entry.score, min, max) * 100;
  const percentile = topPercentile(entry.rank, scoredCount);
  return `
    <span class="score-pill" style="--score-color:${scoreColor(entry.score, min, max)}">${entry.score.toFixed(1)}</span>
    <span class="score-meter" style="--score-color:${scoreColor(entry.score, min, max)};--score-ratio:${ratio.toFixed(1)}%"><i></i></span>
    ${percentile ? `<small>${percentile}</small>` : ""}
  `;
}

function scoreMeter(score, minScore = 0, maxScore = 100, className = "") {
  if (score == null) {
    return `<span class="score-meter${className ? ` ${className}` : ""}" style="--score-ratio:0%"><i></i></span>`;
  }
  const ratio = scoreRatio(score, minScore, maxScore) * 100;
  return `<span class="score-meter${className ? ` ${className}` : ""}" style="--score-color:${scoreColor(score, minScore, maxScore)};--score-ratio:${ratio.toFixed(1)}%"><i></i></span>`;
}

function topPercentile(rank, total) {
  if (!rank || !total) return "";
  if (rank > total) return "";
  const ratio = Math.min(Math.max(((rank - .5) / total) * 100, .1), 100);
  return `前 ${ratio.toFixed(1)}%`;
}

function scoreDistribution(entries) {
  const scored = entries.filter((entry) => entry.score != null);
  const bands = [
    { label: "0-20", value: 10 },
    { label: "20-40", value: 30 },
    { label: "40-60", value: 50 },
    { label: "60-80", value: 70 },
    { label: "80-100", value: 90 },
  ];
  const counts = bands.map((band) => scored.filter((entry) => entry.score >= band.value - 10 && entry.score < band.value + 10).length);
  const max = Math.max(...counts, 1);
  return `
    <div class="distribution-list">
      ${bands.map((band, index) => `
        <div class="distribution-row">
          <span>${band.label}</span>
          <span class="distribution-bar"><i style="width:${Math.max((counts[index] / max) * 100, counts[index] ? 3 : 0)}%;--bar-delay:${index * 55}ms;--bar-color:${scoreColor(band.value)}"></i></span>
          <strong>${counts[index]}</strong>
        </div>
      `).join("")}
    </div>
  `;
}

function trendsTemplate() {
  const usage = state.snapshot.usageRanking;
  const news = state.snapshot.news;
  const maxShare = Math.max(...(usage?.entries || []).map((entry) => entry.share || 0), 1);

  return `
    <div class="view-header">
      <div>
        <h1>行业趋势</h1>
      </div>
      <div class="freshness">用量记录生成时间<strong>${formatDate(usage?.generatedAt)}</strong></div>
    </div>
    <div class="trend-grid">
      <section class="panel">
        <div class="panel-head">
          <div>
            <h2>网关用量榜</h2>
            <p class="panel-sub">${escapeHtml(usage?.weekLabel || "")} · 总调用量 ${escapeHtml(usage?.totalTokens || "—")} · 环比 ${escapeHtml(usage?.platformWow || "—")}</p>
          </div>
        </div>
        <div class="panel-body">
          <div class="usage-list">
          ${(usage?.entries || []).map((entry) => {
            const slug = modelSlugForName(entry.name);
            const changeClass = (entry.wow || "").startsWith("+") ? "up" : (entry.wow || "").startsWith("-") ? "down" : "";
            return `
              <button class="usage-item" type="button" data-slug="${escapeAttr(slug || "")}" data-clickable="${slug ? "true" : "false"}">
                <span class="usage-position">${entry.position}</span>
                <span class="model-cell">${vendorIcon(entry.name)}<span class="usage-name">${escapeHtml(entry.name)}</span></span>
                <span class="usage-tokens">${escapeHtml(entry.tokens)}<span class="usage-change ${changeClass}">${escapeHtml(entry.wow || "")}</span></span>
                <span class="usage-bar" aria-hidden="true"><span style="width:${Math.max((entry.share / maxShare) * 100, 2)}%"></span></span>
              </button>
            `;
          }).join("")}
          </div>
        </div>
      </section>
      <section class="panel">
        <div class="panel-head">
          <div>
            <h2>行业资讯</h2>
            <p class="panel-sub">Artificial Analysis 原文 · 应用内阅读</p>
          </div>
        </div>
        <div class="panel-body">
          <div class="news-list">
          ${(news?.articles || []).slice(0, 10).map((article) => `
            <button class="news-item" type="button" data-article="${escapeAttr(articleUrl(article.url))}" ${articleUrl(article.url) ? "" : "disabled"}>
              <h3>${escapeHtml(article.title)}</h3>
              <span>${formatDate(article.publishedAt)}${articleUrl(article.url) ? " · 阅读原文" : " · 原文地址不可用"}</span>
            </button>
          `).join("")}
          </div>
        </div>
      </section>
    </div>
    <details class="trend-notes"><summary>数据来源与历史记录</summary><div class="disclosure-body"><p>用量仅统计 OpenRouter 聚合网关，由 ModelSage 提供，不代表全球调用量。资讯来自 Artificial Analysis。</p>${state.snapshot.boards.map(board => historyTemplate(board.slug)).filter(Boolean).join("") || "<p>暂无可用历史记录。</p>"}</div></details>
  `;
}

function selectTemplate() {
  const overallEntries = state.snapshot.entriesByBoard.overall || [];
  const priced = overallEntries.filter((entry) => entry.score != null && blendedPrice(entry) != null);
  const valuePicks = [...priced]
    .sort((a, b) => (b.score / Math.max(blendedPrice(b), .5)) - (a.score / Math.max(blendedPrice(a), .5)))
    .slice(0, 6);
  const tiers = tierPicks(priced);
  const scenarioPicks = SCENARIOS.flatMap((scenario) => boardsForDimension(scenario.dimension).map((board) => {
    const ranked = [...(state.snapshot.entriesByBoard[board.slug] || [])].filter(x => x.score != null).sort((a,b) => (a.rank || 9999) - (b.rank || 9999));
    return { ...scenario, label: `${scenario.label} · ${board.name}`, total: ranked.length, entries: ranked.slice(0,2).map(x => ({ ...x, boardName: board.name })) };
  })).filter(x => x.entries.length);
  const latest = overallEntries
    .filter((entry) => entry.releaseDate)
    .sort((a, b) => String(b.releaseDate).localeCompare(String(a.releaseDate)))
    .slice(0, 8);

  return `
    <div class="view-header">
      <div>
        <h1>智能选型</h1>
        <p>各榜内选型参考，不跨榜合并评分或选出统一冠军</p>
      </div>
      <div class="freshness">样本规模<strong>${formatNumber(overallEntries.length)} 个模型</strong></div>
    </div>
    <section class="panel chart-panel">
      <div class="panel-head">
        <div>
          <h2>能力-价格全景</h2>
          <p class="panel-sub">气泡越大响应越快 · 左上角性价比越高</p>
        </div>
      </div>
      <div class="panel-body">
        <div class="dimension-tabs scatter-tabs">
          ${["overall", "coding", "agent", "writing", "math", "speed"].map((slug) => {
            const dimension = DIMENSIONS.find((item) => item.slug === slug);
            return `<button class="chip ${slug === state.scatterMetric ? "active" : ""}" type="button" data-scatter-metric="${slug}">${dimension?.name || slug}</button>`;
          }).join("")}
        </div>
        ${scatterChart(state.scatterMetric, overallEntries)}
      </div>
    </section>
    <div class="select-grid">
      <section class="panel">
        <div class="panel-head">
          <div>
            <h2>性价比首选</h2>
            ${disclosure("估算规则", "仅综合榜内的分数/价格启发式排序，价格分母最低按 ¥0.5；非官方性价比指标，不代表实际任务效果。")}
          </div>
        </div>
        <div class="panel-body">
          <div class="selection-list">
            ${valuePicks.map((entry, index) => `
              <button class="selection-row" type="button" data-slug="${escapeAttr(entry.slug)}">
                <span class="selection-leading">${index + 1}</span>
                ${vendorIcon(entry.vendor)}
                <span class="selection-info">
                  <span class="selection-model">${escapeHtml(entry.displayName)}</span>
                  <span class="selection-meta">${escapeHtml(entry.vendor || "未知厂商")} · 能力 ${entry.score.toFixed(1)} · 综合 #${entry.rank || "—"}</span>
                  ${scoreMeter(entry.score)}
                </span>
                <span class="selection-right">
                  <span class="selection-price">${formatPrice(blendedPrice(entry))}</span>
                  <span class="selection-badge">估算比值 ${(entry.score / Math.max(blendedPrice(entry), .5)).toFixed(1)}</span>
                </span>
              </button>
            `).join("")}
          </div>
        </div>
      </section>
      <section class="panel">
        <div class="panel-head">
          <div>
            <h2>同档更优</h2>
            <p class="panel-sub">混合价按 (输入 ×3 + 输出) ÷4 计算</p>
          </div>
        </div>
        <div class="panel-body">
          <div class="tier-grid">
            ${tiers.map((tier) => `
              <div class="tier-item">
                <div class="tier-label">${tier.label}</div>
                <div class="tier-model">${tier.model ? `<button class="model-chip" type="button" data-slug="${escapeAttr(tier.model.slug)}">${escapeHtml(tier.model.displayName)}</button>` : "暂无"}</div>
                <span class="tier-detail">${tier.model ? `能力 ${tier.model.score.toFixed(1)} · ${formatPrice(blendedPrice(tier.model))}` : ""}</span>
                ${tier.model ? scoreMeter(tier.model.score) : ""}
              </div>
            `).join("")}
          </div>
        </div>
      </section>
    </div>

    <section class="panel" style="margin-top:16px">
      <div class="panel-head">
        <div>
          <h2>按需求选模型</h2>
          <p class="panel-sub">按独立榜单展示前两名；不同来源不合并评分，不代表实际任务必然更优</p>
        </div>
      </div>
      <div class="panel-body">
        <div class="recommend-grid">
          ${scenarioPicks.map((scenario) => `
            <article class="recommend-card">
              <div class="recommend-label"><span>${scenario.label}</span><span>${scenario.total} 个模型</span></div>
              ${scenario.entries.map((entry, index) => `
                <div class="recommend-model">
                  <button class="model-chip" type="button" data-slug="${escapeAttr(entry.slug)}">${escapeHtml(entry.displayName)}</button>
                  <span class="score-pill" style="--score-color:${scoreColor(entry.score)}">${entry.score.toFixed(1)}</span>
                </div>
                <span class="recommend-detail">${escapeHtml(entry.vendor || "未知厂商")} · ${formatPrice(blendedPrice(entry))} · ${escapeHtml(entry.boardName)} #${entry.rank || "—"}</span>
                ${scoreMeter(entry.score)}
              `).join("")}
            </article>
          `).join("")}
        </div>
      </div>
    </section>

    <section class="panel" style="margin-top:16px">
      <div class="panel-head">
        <div>
          <h2>最新模型</h2>
          <p class="panel-sub">按发布时间排序，适合关注新模型上线节奏</p>
        </div>
      </div>
      <div class="panel-body">
        <div class="latest-list">
          ${latest.map((entry) => `
            <button class="latest-row" type="button" data-slug="${escapeAttr(entry.slug)}">
              ${vendorIcon(entry.vendor)}
              <span class="selection-info">
                <span class="selection-model">${escapeHtml(entry.displayName)}</span>
                <span class="latest-meta">${escapeHtml(entry.vendor || "未知厂商")} · ${formatDate(entry.releaseDate)} · 综合 #${entry.rank || "—"}</span>
              </span>
              <span class="latest-score">
                <strong>${entry.score == null ? "-" : entry.score.toFixed(1)}</strong>
                ${scoreMeter(entry.score)}
              </span>
            </button>
          `).join("")}
        </div>
      </div>
    </section>
  `;
}

function bindLeaderboard() {
  const searchInput = document.getElementById("search-input");
  const sortSelect = document.getElementById("sort-select");
  const vendorSelect = document.getElementById("vendor-select");
  const licenseSelect = document.getElementById("license-select");
  // Adjust only the tab strip: scrollIntoView can also move the document.
  document.querySelectorAll(".dimension-tabs .chip.active").forEach(node => {
    const strip = node.parentElement;
    const itemRect = node.getBoundingClientRect();
    const stripRect = strip.getBoundingClientRect();
    if (itemRect.left < stripRect.left) strip.scrollLeft -= stripRect.left - itemRect.left;
    else if (itemRect.right > stripRect.right) strip.scrollLeft += itemRect.right - stripRect.right;
  });

  document.querySelectorAll("[data-dimension]").forEach((node) => {
    node.addEventListener("click", () => {
      state.dimension = node.dataset.dimension;
      const boards = boardsForDimension(state.dimension);
      state.board = boards[0]?.slug || "overall";
      render();
    });
  });

  document.querySelectorAll("[data-board]").forEach((node) => {
    node.addEventListener("click", () => {
      state.board = node.dataset.board;
      render();
    });
  });

  searchInput.addEventListener("input", () => {
    state.query = searchInput.value;
    const position = searchInput.selectionStart;
    render();
    const next = document.getElementById("search-input"); next.focus();
    try { next.setSelectionRange(position, position); } catch {}
  });
  sortSelect.addEventListener("change", () => { state.sort = sortSelect.value; render(); searchInput.focus(); });
  vendorSelect.addEventListener("change", () => { state.vendor = vendorSelect.value; render(); });
  licenseSelect.addEventListener("change", () => { state.license = licenseSelect.value; render(); });
  document.getElementById("region-select").addEventListener("change", event => { state.region = event.target.value; render(); });
  bindActions();
  bindModelRows();
}

function bindTrends() {
  document.querySelectorAll("[data-article]").forEach(node => node.addEventListener("click", () => openReader(node.dataset.article)));
  bindModelRows(true);
}

function bindSelect() {
  document.querySelectorAll("[data-slug]").forEach((node) => {
    node.addEventListener("click", () => {
      if (!node.dataset.slug) return;
      location.hash = `#/model/${encodeURIComponent(node.dataset.slug)}`;
      openModal(node.dataset.slug);
    });
  });
  document.querySelectorAll("[data-scatter-metric]").forEach((node) => {
    node.addEventListener("click", () => {
      state.scatterMetric = node.dataset.scatterMetric;
      render();
    });
  });
  document.querySelectorAll("[data-scatter-chart]").forEach((svg) => {
    svg.addEventListener("click", (event) => {
      const rect = svg.getBoundingClientRect();
      const x = (event.clientX - rect.left) / rect.width * 920;
      const y = (event.clientY - rect.top) / rect.height * 330;
      const nearest = [...svg.querySelectorAll(".scatter-hit")]
        .map((hit) => ({
          hit,
          distance: Math.hypot(hit.cx.baseVal.value - x, hit.cy.baseVal.value - y),
        }))
        .sort((a, b) => a.distance - b.distance)[0];
      const slug = nearest?.hit.dataset.slug;
      if (nearest && nearest.distance <= 28 && slug) {
        location.hash = `#/model/${encodeURIComponent(slug)}`;
        openModal(slug);
      }
    });
  });
}

function scatterChart(metric, baseEntries) {
  const boards = boardsForDimension(metric).slice(0, 1);
  const scoreBySlug = new Map();
  boards.forEach((board) => {
    (state.snapshot.entriesByBoard[board.slug] || []).forEach((entry) => {
      if (entry.score == null || Number.isNaN(entry.score)) return;
      scoreBySlug.set(entry.slug, entry.score);
    });
  });
  const points = baseEntries
    .filter((entry) => entry.priceIn != null || entry.priceOut != null)
    .map((entry) => {
      const price = blendedPrice(entry);
      const score = scoreBySlug.get(entry.slug);
      return price != null && price > 0 && score != null ? { ...entry, price, score } : null;
    })
    .filter(Boolean);
  if (points.length < 2) return `<div class="chart-empty">当前维度样本不足。</div>`;

  const speedScores = new Map();
  boardsForDimension("speed").forEach((board) => {
    (state.snapshot.entriesByBoard[board.slug] || []).forEach((entry) => {
      if (entry.score != null) speedScores.set(entry.slug, entry.score);
    });
  });
  const speedValues = points.map((point) => speedScores.get(point.slug)).filter((value) => value != null);
  const speedMin = Math.min(...(speedValues.length ? speedValues : [0]));
  const speedMax = Math.max(...(speedValues.length ? speedValues : [1]));
  const minLog = Math.log10(Math.min(...points.map((point) => point.price)));
  const maxLog = Math.log10(Math.max(...points.map((point) => point.price)));
  const plotLeft = 58;
  const plotTop = 22;
  const plotWidth = 842;
  const plotHeight = 268;
  const normalizeX = (price) => (Math.log10(price) - minLog) / Math.max(maxLog - minLog, .01);
  const scoreMin = Math.floor(Math.min(...points.map((point) => point.score)) / 10) * 10;
  const scoreMax = Math.max(Math.ceil(Math.max(...points.map((point) => point.score)) / 10) * 10, scoreMin + 10);
  const normalizeY = (score) => 1 - (score - scoreMin) / (scoreMax - scoreMin);
  const xFor = (price) => plotLeft + normalizeX(price) * plotWidth;
  const yFor = (score) => plotTop + normalizeY(score) * plotHeight;
  const gridValues = [-2, -1, 0, 1, 2, 3, 4].filter((decade) => decade >= minLog - .01 && decade <= maxLog + .01);
  const labels = { [-2]: "¥0.01", [-1]: "¥0.1", 0: "¥1", 1: "¥10", 2: "¥100", 3: "¥1000", 4: "¥1万" };
  const scoreLines = [scoreMin, (scoreMin + scoreMax) / 2, scoreMax];

  return `
    ${disclosure(`评分来源：${boards[0]?.name || "未提供"}`, "仅该榜内比较，价格为综合榜记录。气泡速度来自独立速度榜，不参与评分。")}
    <div class="scatter-wrap">
      <svg viewBox="0 0 920 330" role="img" aria-label="能力价格散点图" data-scatter-chart>
        <defs>
          <linearGradient id="scatter-bg" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stop-color="#6366F1" stop-opacity=".09"/>
            <stop offset="52%" stop-color="#0EA5E9" stop-opacity=".05"/>
            <stop offset="100%" stop-color="#F97316" stop-opacity=".08"/>
          </linearGradient>
          <linearGradient id="scatter-axis" x1="0" y1="0" x2="1" y2="0">
            <stop offset="0%" stop-color="#6366F1" stop-opacity=".65"/>
            <stop offset="100%" stop-color="#0EA5E9" stop-opacity=".65"/>
          </linearGradient>
        </defs>
        <rect x="${plotLeft}" y="${plotTop}" width="${plotWidth}" height="${plotHeight}" rx="18" fill="url(#scatter-bg)"/>
        ${scoreLines.map((score) => `
          <line x1="${plotLeft}" y1="${yFor(score)}" x2="${plotLeft + plotWidth}" y2="${yFor(score)}" stroke="currentColor" stroke-opacity=".08"/>
          <text x="${plotLeft - 10}" y="${yFor(score) + 4}" text-anchor="end" class="scatter-label">${score.toFixed(0)}</text>
        `).join("")}
        ${gridValues.map((decade) => {
          const ratio = Math.min(Math.max((decade - minLog) / Math.max(maxLog - minLog, .01), 0), 1);
          return `
            <line x1="${plotLeft + ratio * plotWidth}" y1="${plotTop}" x2="${plotLeft + ratio * plotWidth}" y2="${plotTop + plotHeight}" stroke="currentColor" stroke-opacity=".07"/>
            <text x="${plotLeft + ratio * plotWidth}" y="${plotTop + plotHeight + 20}" text-anchor="middle" class="scatter-label">${labels[decade]}</text>
          `;
        }).join("")}
        <line x1="${plotLeft}" y1="${plotTop + plotHeight}" x2="${plotLeft + plotWidth}" y2="${plotTop + plotHeight}" stroke="url(#scatter-axis)" stroke-width="1.6"/>
        ${points.map((point) => {
          const x = plotLeft + normalizeX(point.price) * plotWidth;
          const y = plotTop + normalizeY(point.score) * plotHeight;
          const speed = speedScores.get(point.slug);
          const speedRatio = speedValues.length && speed != null ? Math.min(Math.max((speed - speedMin) / Math.max(speedMax - speedMin, .01), 0), 1) : .35;
          const radius = 4 + speedRatio * 6;
          const color = scoreColor(point.score);
          const label = `${point.displayName} · ${metricLabel(metric)} ${point.score.toFixed(1)} · ${formatPrice(point.price)}`;
          return `
            <circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="${(radius * 1.8).toFixed(1)}" fill="${color}" opacity=".12"/>
            <circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="${radius.toFixed(1)}" fill="${color}" fill-opacity=".86"/>
            <circle class="scatter-hit" cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="7" fill="rgba(0,0,0,0)" pointer-events="all" data-slug="${escapeAttr(point.slug)}"><title>${escapeHtml(label)}</title></circle>
          `;
        }).join("")}
      </svg>
    </div>
  `;
}

function metricLabel(slug) {
  return DIMENSIONS.find((dimension) => dimension.slug === slug)?.name || "能力";
}

function bindModelRows(allowEmptySlug = false) {
  document.querySelectorAll("[data-slug]").forEach((node) => {
    const slug = node.dataset.slug;
    if (!slug && !allowEmptySlug) return;
    if (!slug) return;
    const open = () => {
      location.hash = `#/model/${encodeURIComponent(slug)}`;
      openModal(slug);
    };
    node.addEventListener("click", event => { if (!event.target.closest("button, a, input, select")) open(); });
    node.addEventListener("keydown", (event) => {
      if (event.target === node && (event.key === "Enter" || event.key === " ")) {
        event.preventDefault();
        open();
      }
    });
  });
}

function openModal(slug) {
  const detail = modelDetail(slug);
  if (!detail) return;
  modalTitle.textContent = detail.model.displayName;
  modalContent.innerHTML = detailTemplate(detail);
  bindActions(modalContent);
  modalContent.querySelectorAll("[data-related-model]").forEach(node => node.addEventListener("click", () => {
    location.hash = `#/model/${encodeURIComponent(node.dataset.relatedModel)}`;
    openModal(node.dataset.relatedModel);
  }));
  modalContent.scrollTop = 0;
  modal.hidden = false;
  document.body.style.overflow = "hidden";
  modal.querySelector("[data-close-modal].icon-button")?.focus();
}

function closeModal() {
  modal.hidden = true;
  modalContent.innerHTML = "";
  document.body.style.overflow = "";
}

function detailTemplate({ model, scores }) {
  const primary = primaryDetailScore(scores);
  const radarScores = selectRadarScores(scores);
  const related = sameVendorModels(model, [...Object.values(state.snapshot.models), ...Object.values(state.snapshot.entriesByBoard).flat().map(entry => ({...mergeModelMetadata(entry, state.snapshot.models[entry.slug]), slug:entry.slug}))]);
  const stat = (label, value) => `<div class="detail-spec"><dt>${label}</dt><dd>${escapeHtml(value)}</dd></div>`;
  const specs = [["上下文", textMetadata(model.contextWindow)], ["参数量", parameterLabel(model.paramsB)], ["许可", textMetadata(model.license)], ["发布日期", detailDate(model.releaseDate)]];
  const known = specs.filter(([,value]) => value !== "未获取");
  const missing = specs.filter(([,value]) => value === "未获取");
  const valueText = score => Number.isFinite(score.score) ? `${score.score}${score.unit ? ` ${score.unit}` : ""}` : "成绩未获取";
  const rankText = score => Number.isInteger(score.rank) && score.rank > 0 ? `#${score.rank}` : "名次未获取";
  return `
    <div class="detail-intro"><span>${escapeHtml(textMetadata(model.vendor))}</span><span>${scores.length} 个榜单记录</span></div>
    <section class="detail-primary" aria-label="主要榜单成绩">${primary ? `<div><span>${escapeHtml(primary.boardName)}</span><strong>${escapeHtml(valueText(primary))}</strong><small>${rankText(primary)} · 仅此榜排名</small></div><p>${sourceLink(primary.sourceUrl, "成绩来源")} · 抓取 ${escapeHtml(detailDate(primary.fetchedAt))}</p>` : `<p>暂无可展示的榜单成绩</p>`}</section>
    ${modelActions(model.slug)}
    ${known.length ? `<dl class="detail-specs" aria-label="已知模型规格">${known.map(([label,value]) => stat(label,value)).join("")}</dl>` : ""}
    <section class="detail-pricing" aria-label="模型价格"><h3>每百万 tokens 价格</h3><dl class="detail-price-grid">${stat("输入", rawPriceLabel(model.priceIn,model.currency))}${stat("输出",rawPriceLabel(model.priceOut,model.currency))}</dl></section>
    <details class="detail-extra"><summary>资料与价格出处${missing.length ? ` · ${missing.length} 项资料未获取` : ""}</summary><div class="disclosure-body">
      ${missing.length ? `<p>${missing.map(([label]) => label).join("、")}：未获取，不用抓取日期代替发布日期。</p>` : ""}
      ${model.releaseDateSourceUrl ? `<p>${sourceLink(model.releaseDateSourceUrl,"发布日期出处")}</p>` : ""}
      <p>${sourceLink(model.sourceUrl,"模型记录来源（非必然官网）")}</p><p>价格记录：${escapeHtml(model.priceBoardName)} · 抓取 ${escapeHtml(fullTime(model.priceFetchedAt))}</p><p>${sourceLink(model.priceSourceUrl,"价格来源")}。原币种展示，不混合不同来源价格；缺失不按零计算。</p>
    </div></details>
    <details class="detail-results"><summary>全部榜单成绩 · ${scores.length} 条</summary><div class="disclosure-body"><p class="detail-caution">不同榜单不是统一量尺，请逐榜查看。</p>
      ${scores.length ? `<div class="detail-score-list">${scores.map(score => `<article class="detail-score"><div class="detail-score-heading"><h3>${escapeHtml(score.boardName)}</h3><strong>${escapeHtml(valueText(score))}</strong><span>${rankText(score)}</span></div><details><summary>来源与评测说明</summary><p>${sourceLink(score.sourceUrl,"榜单来源")} · ${escapeHtml(score.board?.metric?.name || score.board?.scoreType || "指标未提供")}</p><p>成功抓取：${escapeHtml(fullTime(score.fetchedAt))} · 评测版本：${escapeHtml(score.board?.evaluation?.version || score.board?.release || "未获取")}</p></details></article>`).join("")}</div>` : `<p class="muted">暂无榜单成绩，缺失不作为零分。</p>`}
    </div></details>
    ${radarScores.length >= 3 ? `<details class="detail-radar"><summary>相对位置雷达</summary><p class="muted">各榜内 min-max 相对位置，不是统一能力分数；每维取首个有效榜，不跨榜汇总。</p>${radarChart(radarScores)}</details>` : ""}
    ${related.length ? `<details class="detail-related"><summary>同厂商其他模型 · ${related.length}</summary><div class="related-model-list">${related.map(item => `<button type="button" class="chip" data-related-model="${escapeAttr(item.slug)}">${escapeHtml(item.displayName || item.slug)}</button>`).join("")}</div></details>` : ""}
  `;
}
function selectRadarScores(scores) {
  const priority = ["overall", "coding", "writing", "multimodal", "agent", "math", "search", "speed", "value"];
  const groups = scores
    .filter((score) => Number.isFinite(score.score) && Number.isFinite(score.scoreMin) && Number.isFinite(score.scoreMax) && score.scoreMax > score.scoreMin)
    .reduce((groups, score) => {
      const dimension = score.dimension || score.boardSlug;
      if (!groups.has(dimension)) groups.set(dimension, score);
      return groups;
    }, new Map());
  return Array.from(groups.values())
    .sort((a, b) => {
      const left = priority.indexOf(a.dimension || a.boardSlug);
      const right = priority.indexOf(b.dimension || b.boardSlug);
      if (left !== right) return (left < 0 ? 999 : left) - (right < 0 ? 999 : right);
      return radarRatio(b) - radarRatio(a);
    })
    .slice(0, 6);
}

function radarRatio(score) {
  return scoreRatio(score.score, score.scoreMin, score.scoreMax);
}

function radarChart(scores) {
  const width = 620;
  const height = 430;
  const centerX = width / 2;
  const centerY = height / 2 + 4;
  const radius = 142;
  const angleStep = (Math.PI * 2) / scores.length;
  const startAngle = -Math.PI / 2;
  const pointFor = (index, ratio) => {
    const angle = startAngle + index * angleStep;
    return {
      x: centerX + Math.cos(angle) * radius * ratio,
      y: centerY + Math.sin(angle) * radius * ratio,
    };
  };
  const polygon = (ratio) => scores.map((_, index) => {
    const point = pointFor(index, ratio);
    return `${index === 0 ? "M" : "L"}${point.x.toFixed(1)} ${point.y.toFixed(1)}`;
  }).join(" ") + " Z";
  const dataPoints = scores.map((score, index) => pointFor(index, radarRatio(score)));
  const dataPath = dataPoints.map((point, index) => `${index === 0 ? "M" : "L"}${point.x.toFixed(1)} ${point.y.toFixed(1)}`).join(" ") + " Z";

  return `
    <section class="panel radar-panel">
      <div class="panel-head">
        <div>
          <h2>能力雷达</h2>
          <p class="panel-sub">各榜样本内 min-max 相对位置（非百分位）；不计算跨榜均值</p>
        </div>
        <div class="radar-summary">
          <strong>${scores.length} 维</strong>
          <span>不同轴不可当作统一尺度</span>
        </div>
      </div>
      <div class="panel-body">
        <div class="radar-wrap">
          <svg viewBox="0 0 ${width} ${height}" role="img" aria-label="模型能力雷达图">
            <defs>
              <linearGradient id="radar-fill" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0%" stop-color="#6366F1" stop-opacity=".42"/>
                <stop offset="55%" stop-color="#0EA5E9" stop-opacity=".22"/>
                <stop offset="100%" stop-color="#F97316" stop-opacity=".10"/>
              </linearGradient>
              <linearGradient id="radar-stroke" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0%" stop-color="#6366F1"/>
                <stop offset="50%" stop-color="#0EA5E9"/>
                <stop offset="100%" stop-color="#F97316"/>
              </linearGradient>
            </defs>
            ${[.25, .5, .75, 1].map((ratio) => `
              <path d="${polygon(ratio)}" fill="${ratio === 1 ? "transparent" : "currentColor"}" fill-opacity="${ratio === 1 ? 0 : .03}" stroke="currentColor" stroke-opacity="${ratio === 1 ? .14 : .08}"/>
            `).join("")}
            ${scores.map((_, index) => {
              const point = pointFor(index, 1);
              return `<line x1="${centerX}" y1="${centerY}" x2="${point.x}" y2="${point.y}" stroke="currentColor" stroke-opacity=".07"/>`;
            }).join("")}
            <path d="${dataPath}" fill="url(#radar-fill)" stroke="url(#radar-stroke)" stroke-width="2.4" stroke-linejoin="round"/>
            ${dataPoints.map((point) => `
              <circle cx="${point.x.toFixed(1)}" cy="${point.y.toFixed(1)}" r="5.4" fill="#fff"/>
              <circle cx="${point.x.toFixed(1)}" cy="${point.y.toFixed(1)}" r="3.4" fill="url(#radar-stroke)"/>
            `).join("")}
            ${scores.map((score, index) => {
              const angle = startAngle + index * angleStep;
              const anchor = {
                x: centerX + Math.cos(angle) * (radius + 42),
                y: centerY + Math.sin(angle) * (radius + 34),
              };
              const label = axisLabel(score);
              const value = `${Math.round(radarRatio(score) * 100)}% ${score.rank ? `#${score.rank}` : ""}`;
              const anchorX = Math.cos(angle) < -.25 ? "end" : Math.cos(angle) > .25 ? "start" : "middle";
              return `
                <text x="${anchor.x}" y="${anchor.y - 4}" text-anchor="${anchorX}" class="radar-label">${escapeHtml(label)}</text>
                <text x="${anchor.x}" y="${anchor.y + 18}" text-anchor="${anchorX}" class="radar-value">${escapeHtml(value)}</text>
              `;
            }).join("")}
          </svg>
        </div>
      </div>
    </section>
  `;
}

function axisLabel(score) {
  const labels = {
    overall: "综合", coding: "代码", writing: "写作", multimodal: "多模态",
    agent: "Agent", search: "搜索", speed: "速度", value: "性价比", math: "数学",
    reasoning: "推理", instruction: "指令遵循", analysis: "数据分析",
  };
  const dimension = score.dimension || score.boardSlug;
  return labels[dimension] || score.boardName?.split(" ")[0] || dimension;
}

function filteredEntries() {
  const keyword = state.query.trim().toLowerCase();
  const entries = [...(state.snapshot.entriesByBoard[state.board] || [])];
  const filtered = entries.filter((entry) => {
    const matchesKeyword = !keyword
      || entry.displayName.toLowerCase().includes(keyword)
      || (entry.vendor || "").toLowerCase().includes(keyword)
      || entry.slug.toLowerCase().includes(keyword);
    const matchesVendor = !state.vendor || entry.vendor === state.vendor;
    const matchesLicense = !state.license || (entry.license || "") === state.license;
    return matchesKeyword && matchesVendor && matchesLicense
      && (!state.region || modelRegion(entry.vendor || state.snapshot.models[entry.slug]?.vendor) === state.region)
      && (!state.favoritesOnly || favorites.has(entry.slug));
  });

  const sorters = {
    rank: (a, b) => (a.rank || 9999) - (b.rank || 9999),
    score: (a, b) => (b.score ?? -1) - (a.score ?? -1),
    price: (a, b) => (blendedPrice(a) ?? 999999) - (blendedPrice(b) ?? 999999),
    release: (a, b) => (b.releaseDate || "").localeCompare(a.releaseDate || ""),
  };
  return filtered.sort(sorters[state.sort] || sorters.rank);
}

function vendorOptions() {
  const values = [...new Set((state.snapshot.entriesByBoard[state.board] || []).map((entry) => entry.vendor).filter(Boolean))].sort();
  return `<option value="">全部厂商</option>${values.map((value) => `<option value="${escapeAttr(value)}" ${value === state.vendor ? "selected" : ""}>${escapeHtml(value)}</option>`).join("")}`;
}

function licenseOptions() {
  const values = [...new Set((state.snapshot.entriesByBoard[state.board] || []).map((entry) => entry.license).filter(Boolean))].sort();
  return `<option value="">全部许可</option>${values.map((value) => `<option value="${escapeAttr(value)}" ${value === state.license ? "selected" : ""}>${escapeHtml(value)}</option>`).join("")}`;
}

function boardsForDimension(dimension) {
  return state.snapshot.boards.filter((board) => board.dimension === dimension);
}

function findModel(slug) {
  return !!state.snapshot.models[slug] || Object.values(state.snapshot.entriesByBoard)
    .some((entries) => entries.some((entry) => entry.slug === slug));
}

function modelDetail(slug) {
  const entries = Object.values(state.snapshot.entriesByBoard).flat();
  const firstEntry = entries.find((entry) => entry.slug === slug);
  const stored = state.snapshot.models[slug];
  if (!firstEntry && !stored) return null;
  const selectedPrice = selectPriceRecord(state.snapshot.entriesByBoard, slug);
  const priceRecord = selectedPrice?.entry || (stored?.priceIn != null || stored?.priceOut != null ? stored : null);
  const priceBoard = selectedPrice?.boardSlug;
  const model = { ...mergeModelMetadata(firstEntry, stored), priceIn:priceRecord?.priceIn, priceOut:priceRecord?.priceOut, currency:priceRecord?.currency, slug,
    priceBoardName: state.snapshot.boards.find(board => board.slug === priceBoard)?.name || "模型元数据记录",
    priceFetchedAt: priceRecord?.fetchedAt,
    priceSourceUrl: priceRecord?.sourceUrl || state.snapshot.boards.find(board => board.slug === priceBoard)?.url,
  };
  const scores = Object.entries(state.snapshot.entriesByBoard).flatMap(([boardSlug, boardEntries]) => {
    const entry = boardEntries.find((item) => item.slug === slug);
    if (!entry) return [];
    const board = state.snapshot.boards.find((item) => item.slug === boardSlug);
    const boardScores = boardEntries.map((item) => item.score).filter(Number.isFinite);
    return [{
      boardSlug,
      dimension: board?.dimension || boardSlug,
      boardName: board?.name || boardSlug,
      rank: entry.rank,
      score: Number.isFinite(entry.score) ? entry.score : null,
      board,
      sourceUrl: entry.sourceUrl || board?.url || board?.source?.url,
      fetchedAt: entry.fetchedAt || board?.health?.lastSuccessAt || board?.lastSuccessAt,
      unit: scoreUnit(board),
      scoreMin: boardScores.length ? Math.min(...boardScores) : null,
      scoreMax: boardScores.length ? Math.max(...boardScores) : null,
    }];
  });
  return { model, scores };
}

function modelSlugForName(name) {
  const overall = state.snapshot.entriesByBoard.overall || [];
  const exact = overall.find((entry) => entry.displayName === name);
  if (exact) return exact.slug;
  const key = normalizeModelName(name);
  const match = overall.find((entry) => normalizeModelName(entry.displayName) === key);
  return match?.slug || null;
}

function normalizeModelName(name) {
  return textBefore(name.toLowerCase(), "(")
    .replace(/[-_]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function textBefore(text, separator) {
  const index = text.indexOf(separator);
  return index === -1 ? text : text.slice(0, index);
}

function tierPicks(entries) {
  const tierRules = [
    { label: "免费", test: (price) => price === 0 },
    { label: "入门 ≤¥1", test: (price) => price > 0 && price <= 1 },
    { label: "主力 ≤¥5", test: (price) => price > 1 && price <= 5 },
    { label: "旗舰 >¥5", test: (price) => price > 5 },
  ];
  return tierRules.map((rule) => {
    const model = entries.filter((entry) => rule.test(blendedPrice(entry))).sort((a, b) => b.score - a.score)[0];
    return { ...rule, model };
  });
}

function blendedPrice(entry) {
  return estimateBlendedPrice(entry);
}

function formatPrice(price) {
  if (price == null) return "—";
  return `¥${price.toFixed(2)}`;
}

function formatDate(value) {
  if (!value) return "—";
  return String(value).slice(0, 10);
}

function formatNumber(value) {
  return new Intl.NumberFormat("zh-CN").format(value);
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (char) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
  })[char]);
}

function escapeAttr(value) {
  return escapeHtml(value);
}

function sourceLink(url, label = "榜单来源") {
  const safe = safeUrl(url);
  return safe ? `<a href="${escapeAttr(safe)}" target="_blank" rel="noopener noreferrer">${escapeHtml(label)} ↗</a>` : `<span class="muted">来源链接未提供</span>`;
}
function fullTime(value) {
  if (!value || !Number.isFinite(Date.parse(value))) return "未提供";
  return new Date(value).toLocaleString("zh-CN", { timeZoneName: "short" });
}
function boardInfo(slug) {
  const board = state.snapshot.boards.find(x => x.slug === slug) || {};
  const { success, stale, missing, health } = boardStatus(board);
  const evaluation = board.evaluation || {};
  const version = evaluation.version || board.release;
  const noEntries = !(state.snapshot.entriesByBoard[slug] || []).length;
  const source = board.source || (state.snapshot.sources || []).find(x => x.id === board.sourceId) || {};
  const badges = [noEntries && "数据缺失", stale && "缓存过期", missing && "抓取时间未知", (health.error || health.latestAttemptStatus === "error") && "抓取失败", health.anomalies?.length && "变化提醒"].filter(Boolean);
  return `<details class="board-info" aria-label="数据来源与状态">
    <summary><span class="provenance-label">${escapeHtml(source.name || board.sourceId || board.name || slug)}</span><span class="provenance-time">抓取 ${escapeHtml(success ? formatDate(success) : "时间未知")}</span>${badges.map(x => `<span class="status-badge">${escapeHtml(x)}</span>`).join("")}<span class="disclosure-label">来源与指标说明</span></summary>
    <div class="disclosure-body"><h2>${escapeHtml(board.name || slug)} · ${sourceLink(board.url || source.url, "查看原始榜单")}</h2>
    <p class="muted">跨榜分数不可直接比较，本应用不计算跨榜综合分。</p>
    <p>${escapeHtml(metricExplanation(board))}</p>
    <dl class="time-grid"><div><dt>快照生成时间</dt><dd>${escapeHtml(fullTime(state.snapshot.generatedAt))}</dd></div><div><dt>本榜成功抓取时间</dt><dd>${escapeHtml(fullTime(success))}</dd></div><div><dt>上游评测版本 / 更新时间</dt><dd>${escapeHtml(version || "版本未提供")} / ${escapeHtml(fullTime(evaluation.upstreamUpdatedAt))}</dd></div></dl>
    <p class="${stale || missing || noEntries ? "warning" : "muted"}">${noEntries ? "本榜数据缺失。" : ""}${missing ? "成功抓取时间缺失，无法判断缓存年龄。" : stale ? `抓取缓存已超过 ${health.staleAfterHours || 48} 小时，可能过期。` : "抓取缓存未超过过期阈值。"} 抓取成功不等于上游更新；版本号不是抓取时间。</p>
    ${health.latestAttemptAt ? `<p class="muted">最近尝试：${escapeHtml(fullTime(health.latestAttemptAt))} · ${escapeHtml(health.latestAttemptStatus || "未知")}</p>` : ""}
    ${health.error ? `<p class="warning">抓取异常：${escapeHtml(health.error)}；保留已有可用数据。</p>` : ""}
    ${(health.anomalies || []).map(x => `<p class="warning">变化提示：${escapeHtml(x.message || x.code || "未知异常")}（不代表已确认的数据错误）</p>`).join("")}
  </div></details>`;
}
function modelActions(slug) {
  const favorite = favorites.has(slug), selected = state.comparison.includes(slug);
  return `<span class="model-actions"><button type="button" class="chip" data-favorite="${escapeAttr(slug)}" aria-pressed="${favorite}" aria-label="${favorite ? "取消收藏" : "收藏"} ${escapeAttr(slug)}">${favorite ? "★ 已收藏" : "☆ 收藏"}</button><button type="button" class="chip" data-compare="${escapeAttr(slug)}" aria-pressed="${selected}">${selected ? "移出对比" : "加入对比"}</button></span>`;
}
function selectionToolbar() {
  return `<div class="selection-toolbar"><label><input type="checkbox" id="favorites-only" ${state.favoritesOnly ? "checked" : ""}> 只看收藏（${favorites.size}）</label><a href="#/compare">独立对比（${state.comparison.length}/4）</a><span>收藏仅存当前浏览器，不上传、不跨设备同步。</span></div><p role="status" class="warning">${escapeHtml(state.notice)}</p>`;
}
function bindActions(root = app) {
  root.querySelectorAll("[data-favorite]").forEach(node => node.addEventListener("click", event => {
    event.stopPropagation();
    const slug = node.dataset.favorite;
    if (favorites.has(slug)) favorites.delete(slug); else favorites.add(slug);
    state.notice = saveFavorites(storage, favorites) ? "" : "浏览器不允许保存，收藏仅在本次页面会话有效。";
    render();
    if (!modal.hidden) openModal(slug);
  }));
  root.querySelectorAll("[data-compare]").forEach(node => node.addEventListener("click", event => {
    event.stopPropagation();
    const result = toggleComparison(state.comparison, node.dataset.compare);
    state.comparison = result.selected; state.notice = result.error;
    if (!saveComparison(storage, state.comparison) && !state.notice) state.notice = "浏览器不允许保存，对比选择仅在本次会话有效。";
    render();
    if (!modal.hidden) openModal(node.dataset.compare);
  }));
  root.querySelector("#favorites-only")?.addEventListener("change", event => { state.favoritesOnly = event.target.checked; render(); });
}
function comparisonTemplate() {
  const board = state.snapshot.boards.find(x => x.slug === state.compareBoard) || state.snapshot.boards[0];
  if (!board) return `<div class="empty-state">暂无可对比榜单</div>`;
  state.compareBoard = board.slug;
  const columns = comparableEntries(state.snapshot.entriesByBoard, board.slug, state.comparison);
  return `<div class="view-header"><div><h1>独立模型对比</h1><p>最多 4 个模型 · 同榜原始成绩对比</p></div><a href="#/">返回榜单添加模型</a></div>
    <p role="status" class="warning">${escapeHtml(state.notice)}</p>
    <label class="compare-selector">对比榜单 <select id="compare-board">${state.snapshot.boards.map(x => `<option value="${escapeAttr(x.slug)}" ${x.slug === board.slug ? "selected" : ""}>${escapeHtml(x.name)}</option>`).join("")}</select></label>
    ${boardInfo(board.slug)}
    ${columns.length ? `<div class="panel comparison-scroll" tabindex="0" role="region" aria-label="同榜模型对比，可横向滚动"><table class="comparison-table"><thead><tr><th>同榜指标</th>${columns.map(({slug,entry}) => `<th>${escapeHtml(entry?.displayName || state.snapshot.models[slug]?.displayName || slug)}<br><button class="chip" data-compare="${escapeAttr(slug)}">移除</button></th>`).join("")}</tr></thead><tbody>${[
      ["名次", x => x ? `#${x.rank || "—"}` : "本榜未收录"],
      ["原始成绩", x => x?.score == null ? "未提供（不补零）" : String(x.score)],
      ["开发者", x => x?.vendor || "未提供"],
      ["估算混合价 / 1M", x => x ? formatPrice(blendedPrice(x)) : "未提供"],
      ["上下文", x => x?.contextWindow || "未提供"],
      ["模型发布日期", x => x?.releaseDate || "未提供"],
    ].map(([label, value]) => `<tr><th scope="row">${label}</th>${columns.map(({entry}) => `<td>${escapeHtml(value(entry))}</td>`).join("")}</tr>`).join("")}</tbody></table></div>` : `<div class="empty-state">尚未选择模型。回到排行榜点击“加入对比”。</div>`}
    ${disclosure("价格与可比性说明", "价格按 (输入 ×3 + 输出) ÷4 估算，USD 固定按 7.2 换算；缺失项不作为免费或零分。不同模型配置仍可能影响可比性。")}`;
}
function bindComparison() {
  document.getElementById("compare-board")?.addEventListener("change", event => { state.compareBoard = event.target.value; render(); });
  bindActions();
}
function historyTemplate(slug) {
  const raw = state.snapshot.historyByBoard?.[slug];
  const history = (Array.isArray(raw) ? raw : []).filter(x => x && Number.isFinite(Date.parse(x.fetchedAt))).slice().sort((a,b) => Date.parse(a.fetchedAt) - Date.parse(b.fetchedAt));
  const previous = history.length > 1 ? history[history.length - 2] : null;
  const latest = history.at(-1);
  const canCompare = historyComparable(previous, latest);
  const changes = canCompare ? (latest.entries || []).flatMap(entry => {
    const old = (previous.entries || []).find(x => x.slug === entry.slug);
    if (!old || (old.rank === entry.rank && old.score === entry.score)) return [];
    return [{ entry, old }];
  }) : [];
  if (!history.length) return "";
  return `<details class="history-panel"><summary>历史变化 · ${history.length} 次记录${canCompare && changes.length ? ` · ${changes.length} 项变化` : ""}</summary><div class="disclosure-body"><p class="muted">仅展示真实成功抓取记录；抓取时间不是上游评测发布时间，评测版本变化可能导致成绩不可比。历史可能只保留 Top 100，缺失模型不推断为退榜。</p>
    ${history.length ? `<details><summary>${history.length} 次抓取记录（点击展开时间线）</summary><ul>${history.map(x => `<li>${escapeHtml(fullTime(x.fetchedAt))} · ${escapeHtml(x.modelCount ?? "未知")} 个模型 · 评测版本 ${escapeHtml(x.evaluationVersion || "未知")}${x.truncated ? " · 仅保留部分记录" : ""}</li>`).join("")}</ul></details>` : `<p>暂无历史记录（旧快照可能不含历史），不生成虚构趋势。</p>`}
    ${previous ? `<h3>最近两次抓取的变化</h3><p class="muted">${escapeHtml(fullTime(previous.fetchedAt))} → ${escapeHtml(fullTime(latest.fetchedAt))}</p>${!canCompare ? "<p>评测版本未知或发生变化，不推断名次/成绩升降；请查看采集记录。</p>" : changes.length ? `<ul>${changes.slice(0,20).map(({entry,old}) => `<li>${escapeHtml(entry.display_name || entry.slug)}：名次 ${escapeHtml(old.rank ?? "未提供")} → ${escapeHtml(entry.rank ?? "未提供")}；原始成绩 ${escapeHtml(old.score ?? "未提供")} → ${escapeHtml(entry.score ?? "未提供")}</li>`).join("")}</ul>` : "<p>共同保留的模型中未发现名次或成绩变化。</p>"}` : history.length ? "<p>只有一次记录，尚不能计算历史变化。</p>" : ""}
  </div></details>`;
}

function disclosure(label, text) {
  return `<details class="inline-disclosure"><summary>${escapeHtml(label)}</summary><p>${escapeHtml(text)}</p></details>`;
}

let readerTrigger = null;
let readerArticle = null;
let translationController = null;
let translatedArticle = null;
const translateArticleText = createTranslator();
function initReader() {
  const reader = document.getElementById("news-reader");
  reader.querySelector("#reader-translate").addEventListener("click", translateReader);
  reader.querySelectorAll("[data-close-reader]").forEach(node => node.addEventListener("click", closeReader));
  reader.addEventListener("keydown", event => {
    if (event.key === "Escape") { event.preventDefault(); closeReader(); }
    if (event.key === "Tab") {
      const first = reader.querySelector("[data-reader-source]"), last = reader.querySelector("#reader-content");
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    }
  });
}
function articleContentTemplate(article = {}) {
  const paragraphs = typeof article.content === "string" ? article.content.trim().slice(0, 8000).split(/\n\s*\n/).filter(Boolean) : [];
  return `<p class="reader-date">${escapeHtml(formatDate(article.publishedAt || article.published_at))} · Artificial Analysis</p><h1 class="reader-article-title">${escapeHtml(article.title || "资讯阅读")}</h1>${paragraphs.length ? paragraphs.map(text => `<p>${escapeHtml(text)}</p>`).join("") : `<p class="reader-empty">正文未获取，请点击上方“打开原文”。</p>`}`;
}
function openReader(url) {
  const safe = articleUrl(url);
  if (!safe) return;
  const article = (state.snapshot.news?.articles || []).find(item => articleUrl(item.url) === safe);
  if (!article) return;
  const reader = document.getElementById("news-reader");
  readerTrigger = document.activeElement;
  readerArticle = article; translatedArticle = null;
  const translateButton = document.getElementById("reader-translate");
  translateButton.disabled = !article.content?.trim();
  translateButton.textContent = "翻译为中文";
  document.getElementById("reader-translation-status").textContent = "按需机翻，将文章文本发送至 MyMemory（第三方）。";
  document.getElementById("reader-title").textContent = "资讯阅读";
  reader.querySelector("[data-reader-source]").href = safe;
  const content = document.getElementById("reader-content");
  content.innerHTML = articleContentTemplate(article);
  content.scrollTop = 0;
  reader.hidden = false;
  document.body.style.overflow = "hidden";
  document.querySelectorAll(".app-header, #app, .app-footer").forEach(node => { node.inert = true; });
  reader.querySelector("[data-close-reader].icon-button").focus();
}
function closeReader() {
  const reader = document.getElementById("news-reader");
  if (!reader || reader.hidden) return;
  translationController?.abort(); translationController = null;
  readerArticle = null; translatedArticle = null;
  reader.hidden = true;
  document.getElementById("reader-content").replaceChildren();
  document.querySelectorAll(".app-header, #app, .app-footer").forEach(node => { node.inert = false; });
  document.body.style.overflow = "";
  if (readerTrigger?.isConnected) readerTrigger.focus({ preventScroll: true });
  readerTrigger = null;
}

async function translateReader() {
  if (!readerArticle?.content?.trim()) return;
  const article = readerArticle;
  const button = document.getElementById("reader-translate");
  const status = document.getElementById("reader-translation-status");
  const content = document.getElementById("reader-content");
  if (translatedArticle) {
    const chinese = button.dataset.view !== "chinese";
    content.innerHTML = articleContentTemplate(chinese ? translatedArticle : article);
    button.dataset.view = chinese ? "chinese" : "original";
    button.textContent = chinese ? "查看原文" : "查看译文";
    content.scrollTop = 0;
    return;
  }
  const controller = new AbortController(); translationController = controller;
  button.disabled = true;
  status.textContent = "正在翻译… 机器翻译，仅供参考。";
  try {
    const source = [article.title || "资讯阅读", ...article.content.trim().slice(0,8000).split(/\n\s*\n/).filter(Boolean)];
    const result = await translateArticleText(source, {signal:controller.signal, onProgress:(done,total) => {
      if (translationController === controller) status.textContent = `正在翻译 ${done}/${total} · 机器翻译，仅供参考`;
    }});
    if (translationController !== controller) return;
    translatedArticle = {...article, title:result[0], content:result.slice(1).join("\n\n")};
    content.innerHTML = articleContentTemplate(translatedArticle);
    content.scrollTop = 0;
    button.dataset.view = "chinese"; button.textContent = "查看原文";
    status.textContent = "机器翻译，仅供参考 · MyMemory 提供，可切换查看原文。";
  } catch (error) {
    if (translationController !== controller) return;
    status.textContent = "翻译失败，请重试（可能网络异常或免费额度限制）。原文仍可阅读。";
    button.textContent = "重试翻译";
  } finally {
    if (translationController === controller) { button.disabled = false; translationController = null; }
  }
}

function benchmarkReferenceTemplate(slug) {
  const board = state.snapshot.boards.find(item => item.slug === slug);
  const reference = state.snapshot.benchmarkReferences?.hle;
  if (!reference || !board || board.dimension !== "reasoning") return "";
  // The reference is one publisher-public example, not permission to mirror the dataset.
  const example = (Array.isArray(reference.examples) ? reference.examples : []).find(item => item?.modality === "text" && typeof item.question === "string");
  return `<details class="benchmark-reference inline-disclosure"><summary>HLE 测评说明与公开示例（无评分榜）</summary><div class="disclosure-body">
    <h2>${escapeHtml(reference.name || "HLE")} · ${escapeHtml(reference.publisher || "")}</h2>
    <p>${escapeHtml(reference.description || "")}</p>
    <p>此处仅保留 HLE 测评参考资料，不提供 HLE 模型评分榜；上方成绩属于当前所选推理榜，不是 HLE 成绩。题集版本日期不等于每个模型成绩发布日期；原版 HLE 与 HLE-Diamond、不同工具与推理配置不能直接混比。</p>
    <p>${sourceLink(reference.sourceUrl, "官方说明")} · ${sourceLink(reference.datasetUrl, "数据集访问条件")} · ${sourceLink(reference.paperUrl, "论文")}</p>
    <p class="warning">${escapeHtml(reference.licenseNotice || "仅引用官网公开示例，不提供完整题库。")}</p>
    ${reference.trainingWarning ? `<p class="training-warning">${escapeHtml(reference.trainingWarning)}</p>` : ""}
    ${reference.canary ? `<p class="muted">Benchmark canary：<code>${escapeHtml(reference.canary)}</code></p>` : ""}
    ${example ? `<article class="public-example"><h3>官网公开示例 · ${escapeHtml(example.category || "")}</h3><p>${escapeHtml(example.question)}</p><p class="muted">作者：${escapeHtml(example.author || "未提供")} · ${sourceLink(example.sourceUrl, "题目原始出处")}</p><p>${escapeHtml(example.note || "仅官网公开单题，不代表完整题集。")}</p><p class="muted">不提供答案或答题入口，不下载或镜像受限题库。</p></article>` : `<p>暂无获准公开的示例题。</p>`}
  </div></details>`;
}

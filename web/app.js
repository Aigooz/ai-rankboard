const SNAPSHOT_URLS = [
  "https://raw.githubusercontent.com/Aigooz/ai-rankboard-updates/main/leaderboards.json",
  "https://ghfast.top/https://raw.githubusercontent.com/Aigooz/ai-rankboard-updates/main/leaderboards.json",
];

const DIMENSIONS = [
  { slug: "overall", name: "综合" },
  { slug: "coding", name: "代码" },
  { slug: "agent", name: "Agent" },
  { slug: "writing", name: "写作" },
  { slug: "multimodal", name: "多模态" },
  { slug: "search", name: "搜索" },
  { slug: "math", name: "数学" },
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
};

const app = document.getElementById("app");
const modal = document.getElementById("model-modal");
const modalContent = document.getElementById("modal-content");
const modalTitle = document.getElementById("modal-title");

init();

async function init() {
  window.addEventListener("hashchange", () => {
    const route = parseHash();
    if (!route.slug) closeModal();
    render();
  });

  document.querySelectorAll("[data-close-modal]").forEach((node) => {
    node.addEventListener("click", () => {
      closeModal();
      location.hash = "#/";
    });
  });

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
      const response = await fetch(url, { cache: "no-store" });
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
      sourceId: board.source_id,
      scoreType: board.score_type,
      lastSuccessAt: board.last_success_at,
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
    displayName: entry.display_name,
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
    displayName: model.display_name,
    paramsB: model.params_b,
    contextWindow: model.context_window,
    sourceUrl: model.source_url,
    releaseDate: model.release_date,
  };
}

function parseHash() {
  const hash = location.hash.replace(/^#\/?/, "");
  const [path, slug] = hash.split("/");
  if (path === "trends") return { view: "trends" };
  if (path === "select") return { view: "select" };
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
  document.getElementById("data-meta").textContent = `快照更新：${generated} · 来源：ModelSage / LiveBench / SWE-bench`;
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

  return `
    <div class="view-header">
      <div>
        <h1>模型排行</h1>
        <p>按能力、价格和发布状态对比 ${formatNumber(entries.length)} 个模型</p>
      </div>
      <div class="freshness">最新快照<strong>${generated}</strong></div>
    </div>
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
    <div class="control-panel">
      <label class="search-field">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m21 21-4.35-4.35"/></svg>
        <input id="search-input" type="search" value="${escapeAttr(state.query)}" placeholder="搜索模型、厂商或 slug" autocomplete="off">
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
      <select id="license-select" aria-label="许可筛选">
        ${licenseOptions()}
      </select>
    </div>
    <div class="panel">
      <div class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th>名次</th><th>模型</th><th>分数</th><th>混合价 / 1M</th><th>上下文</th><th>发布</th>
            </tr>
          </thead>
          <tbody id="table-body">
            ${tableRows(entries)}
          </tbody>
        </table>
      </div>
      ${entries.length ? "" : `<div class="empty-state">没有符合条件的模型。</div>`}
    </div>
  `;
}

function leaderboardOverview() {
  const entries = state.snapshot.entriesByBoard.overall || [];
  const scored = entries.filter((entry) => entry.score != null);
  const priced = entries.filter((entry) => entry.score != null && blendedPrice(entry) > 0);
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
      { label: "模型样本", value: formatNumber(entries.length), detail: `9 个榜单维度`, color: "#0EA5E9" },
      { label: "平均能力", value: averageScore.toFixed(1), detail: `${formatNumber(scored.length)} 个已评分`, color: "#22C55E" },
      { label: "混合价中位数", value: formatPrice(medianPrice), detail: "输入 ×3 + 输出 ÷4", color: "#F59E0B" },
      { label: "30 天上新", value: formatNumber(newCount), detail: "以快照日期为基准", color: "#EC4899" },
    ],
  };
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
  const ratio = scoreRatio(entry.score, 0, 100) * 100;
  const percentile = topPercentile(entry.rank, scoredCount);
  return `
    <span class="score-pill" style="--score-color:${scoreColor(entry.score)}">${entry.score.toFixed(1)}</span>
    <span class="score-meter" style="--score-color:${scoreColor(entry.score)};--score-ratio:${ratio.toFixed(1)}%"><i></i></span>
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
        <p>真实网关调用量与最新模型评测资讯</p>
      </div>
      <div class="freshness">更新时间<strong>${formatDate(usage?.generatedAt || state.snapshot.generatedAt)}</strong></div>
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
            <p class="panel-sub">来自 ModelSage 的评测与分析</p>
          </div>
        </div>
        <div class="panel-body">
          <div class="news-list">
          ${(news?.articles || []).slice(0, 10).map((article) => `
            <a class="news-item" href="https://modelsage.cn${escapeAttr(article.url)}" target="_blank" rel="noreferrer">
              <h3>${escapeHtml(article.title)}</h3>
              <span>${formatDate(article.publishedAt)}</span>
            </a>
          `).join("")}
          </div>
        </div>
      </section>
    </div>
  `;
}

function selectTemplate() {
  const overallEntries = state.snapshot.entriesByBoard.overall || [];
  const priced = overallEntries.filter((entry) => entry.score != null && blendedPrice(entry) > 0);
  const valuePicks = [...priced]
    .sort((a, b) => (b.score / Math.max(blendedPrice(b), .5)) - (a.score / Math.max(blendedPrice(a), .5)))
    .slice(0, 6);
  const tiers = tierPicks(priced);
  const scenarioPicks = SCENARIOS.map((scenario) => {
    const unique = new Map();
    boardsForDimension(scenario.dimension).forEach((board) => {
      (state.snapshot.entriesByBoard[board.slug] || []).forEach((entry) => {
        if (entry.score == null) return;
        const existing = unique.get(entry.slug);
        if (!existing || entry.score > existing.score) {
          unique.set(entry.slug, { ...entry, boardName: board.name });
        }
      });
    });
    const ranked = [...unique.values()].sort((a, b) => b.score - a.score);
    const best = ranked[0];
    const alternative = best ? ranked.slice(1).find((entry) => {
      const bestPrice = blendedPrice(best);
      const candidatePrice = blendedPrice(entry);
      return entry.score >= best.score * .8 && candidatePrice != null && (bestPrice == null || candidatePrice < bestPrice);
    }) : null;
    return {
      ...scenario,
      total: unique.size,
      entries: best ? [best, alternative].filter(Boolean) : [],
    };
  }).filter((scenario) => scenario.entries.length);
  const latest = overallEntries
    .filter((entry) => entry.releaseDate)
    .sort((a, b) => String(b.releaseDate).localeCompare(String(a.releaseDate)))
    .slice(0, 8);

  return `
    <div class="view-header">
      <div>
        <h1>智能选型</h1>
        <p>按场景找能力首选，按档位找更低价格</p>
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
            <p class="panel-sub">能力分越高、混合价越低，排名越靠前</p>
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
                  <span class="selection-badge">每元 ${(entry.score / Math.max(blendedPrice(entry), .5)).toFixed(1)}</span>
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
            <p class="panel-sub">混合价按输入 ×3 + 输出 ÷4 计算</p>
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
          <p class="panel-sub">每个场景优先展示能力首选；价格更低且能力接近时，补充省钱替代</p>
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
                <span class="recommend-detail">${escapeHtml(entry.vendor || "未知厂商")} · ${formatPrice(blendedPrice(entry))} · ${index === 0 ? "能力首选" : "省钱替代"}</span>
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
    document.getElementById("table-body").innerHTML = tableRows(filteredEntries());
  });
  sortSelect.addEventListener("change", () => { state.sort = sortSelect.value; render(); searchInput.focus(); });
  vendorSelect.addEventListener("change", () => { state.vendor = vendorSelect.value; render(); });
  licenseSelect.addEventListener("change", () => { state.license = licenseSelect.value; render(); });
  bindModelRows();
}

function bindTrends() {
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
  const boards = boardsForDimension(metric);
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
    node.addEventListener("click", open);
    node.addEventListener("keydown", (event) => {
      if (event.key === "Enter" || event.key === " ") {
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
  const overall = scores.find((score) => score.boardSlug === "overall");
  const bestRank = scores.length ? Math.min(...scores.map((score) => score.rank || 999)) : null;
  const overallScoredCount = overall
    ? (state.snapshot.entriesByBoard[overall.boardSlug] || []).filter((entry) => entry.score != null).length
    : 0;
  const boardScoreCounts = Object.fromEntries(
    Object.entries(state.snapshot.entriesByBoard).map(([slug, entries]) => [
      slug,
      entries.filter((entry) => entry.score != null).length,
    ]),
  );
  const radarScores = selectRadarScores(scores);
  const radar = radarScores.length >= 3 ? radarChart(radarScores) : "";
  return `
    <div class="detail-grid">
      <div class="detail-stat"><span>综合名次</span><strong>${overall ? `#${overall.rank}` : "—"}</strong><small>${topPercentile(overall?.rank, overallScoredCount) || "—"}</small></div>
      <div class="detail-stat"><span>综合分数</span><strong>${overall?.score == null ? "—" : overall.score.toFixed(1)}</strong>${scoreMeter(overall?.score)}</div>
      <div class="detail-stat"><span>混合价</span><strong>${formatPrice(blendedPrice(model))}</strong></div>
      <div class="detail-stat"><span>发布日期</span><strong>${formatDate(model.releaseDate)}</strong></div>
    </div>
    <div class="detail-chips">
      <span class="detail-chip">上榜 <strong>${scores.length}</strong></span>
      <span class="detail-chip">最佳 <strong>${bestRank && bestRank < 999 ? `#${bestRank}` : "—"}</strong></span>
      <span class="detail-chip">${escapeHtml(model.license || "许可未知")}</span>
      <span class="detail-chip">上下文 <strong>${escapeHtml(model.contextWindow || "—")}</strong></span>
      <span class="detail-chip">参数 <strong>${model.paramsB == null ? "—" : `${model.paramsB}B`}</strong></span>
    </div>
    <p class="detail-source">
      ${escapeHtml(model.vendor || "未知厂商")}
      ${model.sourceUrl ? ` · <a href="${escapeAttr(model.sourceUrl)}" target="_blank" rel="noreferrer">官方来源</a>` : ""}
    </p>
    ${radar}
    <h2 class="section-title">各榜成绩</h2>
    ${scores.length ? `
      <div class="score-list">
        ${scores.map((score) => `
          <div class="score-row" style="--score-color:${scoreColor(score.score, score.scoreMin, score.scoreMax)};--score-ratio:${scoreRatio(score.score, score.scoreMin, score.scoreMax) * 100}%;--score-ratio-number:${scoreRatio(score.score, score.scoreMin, score.scoreMax)}">
            <div class="score-row-name">${escapeHtml(score.boardName)}</div>
            <div class="score-row-rank"><strong>#${score.rank}</strong><span>${topPercentile(score.rank, boardScoreCounts[score.boardSlug])}</span></div>
            <div class="score-row-score">${score.score == null ? "—" : score.score.toFixed(1)}</div>
            <div class="score-bar"><span></span></div>
          </div>
        `).join("")}
      </div>
    ` : `<div class="muted">暂无跨榜成绩。</div>`}
  `;
}

function selectRadarScores(scores) {
  const priority = ["overall", "coding", "writing", "multimodal", "agent", "math", "search", "speed", "value"];
  const groups = scores
    .filter((score) => score.score != null)
    .reduce((groups, score) => {
      const dimension = score.dimension || score.boardSlug;
      const existing = groups.get(dimension);
      if (!existing || radarRatio(score) > radarRatio(existing)) groups.set(dimension, score);
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
  const dataPoints = scores.map((score, index) => pointFor(index, Math.max(radarRatio(score), .12)));
  const dataPath = dataPoints.map((point, index) => `${index === 0 ? "M" : "L"}${point.x.toFixed(1)} ${point.y.toFixed(1)}`).join(" ") + " Z";
  const average = Math.round(scores.reduce((total, score) => total + radarRatio(score), 0) / scores.length * 100);

  return `
    <section class="panel radar-panel">
      <div class="panel-head">
        <div>
          <h2>能力雷达</h2>
          <p class="panel-sub">榜单内归一化百分位</p>
        </div>
        <div class="radar-summary">
          <strong>${scores.length} 维</strong>
          <span>均值 ${average}%</span>
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
    return matchesKeyword && matchesVendor && matchesLicense;
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
  return Object.values(state.snapshot.entriesByBoard)
    .some((entries) => entries.some((entry) => entry.slug === slug));
}

function modelDetail(slug) {
  const entries = Object.values(state.snapshot.entriesByBoard).flat();
  const firstEntry = entries.find((entry) => entry.slug === slug);
  if (!firstEntry) return null;
  const stored = state.snapshot.models[slug] || {};
  const model = { ...firstEntry, ...stored, slug };
  const scores = Object.entries(state.snapshot.entriesByBoard).flatMap(([boardSlug, boardEntries]) => {
    const entry = boardEntries.find((item) => item.slug === slug);
    if (!entry) return [];
    const board = state.snapshot.boards.find((item) => item.slug === boardSlug);
    const boardScores = boardEntries.map((item) => item.score).filter((score) => score != null && !Number.isNaN(score));
    return [{
      boardSlug,
      dimension: board?.dimension || boardSlug,
      boardName: board?.name || boardSlug,
      rank: entry.rank,
      score: entry.score,
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
  if (entry.priceIn == null && entry.priceOut == null) return null;
  const rate = entry.currency === "USD" ? 7.2 : 1;
  const priceIn = (entry.priceIn || 0) * rate;
  const priceOut = (entry.priceOut || 0) * rate;
  return (priceIn * 3 + priceOut) / 4;
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

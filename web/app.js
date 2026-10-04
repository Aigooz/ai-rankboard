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

  return `
    <div class="view-header">
      <div>
        <h1>模型排行</h1>
        <p>按能力、价格和发布状态对比 ${formatNumber(entries.length)} 个模型</p>
      </div>
      <div class="freshness">最新快照<strong>${generated}</strong></div>
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

function tableRows(entries) {
  return entries.map((entry, index) => {
    const price = blendedPrice(entry);
    return `
      <tr tabindex="0" data-slug="${escapeAttr(entry.slug)}">
        <td class="rank-cell">#${entry.rank || index + 1}</td>
        <td>
          <span class="model-name">${escapeHtml(entry.displayName)}</span>
          <span class="model-vendor">${escapeHtml(entry.vendor || "未知厂商")}</span>
        </td>
        <td><span class="score-pill">${entry.score == null ? "—" : entry.score.toFixed(1)}</span></td>
        <td class="num">${formatPrice(price)}</td>
        <td class="num">${escapeHtml(entry.contextWindow || "—")}</td>
        <td class="num">${formatDate(entry.releaseDate)}</td>
      </tr>
    `;
  }).join("");
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
      <section class="card">
        <h2>网关用量榜</h2>
        <p class="card-sub">${escapeHtml(usage?.weekLabel || "")} · 总调用量 ${escapeHtml(usage?.totalTokens || "—")} · 环比 ${escapeHtml(usage?.platformWow || "—")}</p>
        <div>
          ${(usage?.entries || []).map((entry) => {
            const slug = modelSlugForName(entry.name);
            const changeClass = (entry.wow || "").startsWith("+") ? "up" : (entry.wow || "").startsWith("-") ? "down" : "";
            return `
              <button class="usage-item" type="button" data-slug="${escapeAttr(slug || "")}" data-clickable="${slug ? "true" : "false"}">
                <span class="usage-position">${entry.position}</span>
                <span class="usage-name">${escapeHtml(entry.name)}</span>
                <span class="usage-tokens">${escapeHtml(entry.tokens)}<span class="usage-change ${changeClass}">${escapeHtml(entry.wow || "")}</span></span>
                <span class="usage-bar" aria-hidden="true"><span style="width:${Math.max((entry.share / maxShare) * 100, 2)}%"></span></span>
              </button>
            `;
          }).join("")}
        </div>
      </section>
      <section class="card">
        <h2>行业资讯</h2>
        <p class="card-sub">来自 ModelSage 的评测与分析</p>
        <div>
          ${(news?.articles || []).slice(0, 10).map((article) => `
            <a class="news-item" href="https://modelsage.cn${escapeAttr(article.url)}" target="_blank" rel="noreferrer">
              <h3>${escapeHtml(article.title)}</h3>
              <span>${formatDate(article.publishedAt)}</span>
            </a>
          `).join("")}
        </div>
      </section>
    </div>
  `;
}

function selectTemplate() {
  const overallEntries = state.snapshot.entriesByBoard.overall || [];
  const priced = overallEntries.filter((entry) => entry.score != null && blendedPrice(entry) > 0);
  const tiers = tierPicks(priced);
  const scenarioPicks = SCENARIOS.map((scenario) => {
    const boards = boardsForDimension(scenario.dimension);
    const board = boards.find((item) => !item.slug.startsWith("arena-")) || boards[0];
    const entries = (state.snapshot.entriesByBoard[board?.slug] || [])
      .filter((entry) => entry.score != null)
      .slice(0, 2);
    return { ...scenario, entries };
  }).filter((scenario) => scenario.entries.length);

  return `
    <div class="view-header">
      <div>
        <h1>智能选型</h1>
        <p>按场景找能力首选，按档位找更低价格</p>
      </div>
      <div class="freshness">样本规模<strong>${formatNumber(overallEntries.length)} 个模型</strong></div>
    </div>
    <div class="select-grid">
      <section class="card">
        <h2>场景推荐</h2>
        <p class="card-sub">每个场景展示综合分最高的两个模型</p>
        <div class="recommend-list">
          ${scenarioPicks.map((scenario) => `
            <div class="recommend-item">
              <div class="recommend-label">${scenario.label}</div>
              <div>
                ${scenario.entries.map((entry, index) => `
                  <div class="recommend-model">
                    <button class="chip" type="button" data-slug="${escapeAttr(entry.slug)}" style="margin-right:6px">${escapeHtml(entry.displayName)}</button>
                    ${index === 0 ? `<span class="score-pill">${entry.score.toFixed(1)}</span>` : ""}
                    <span class="recommend-detail">${escapeHtml(entry.vendor || "未知厂商")} · ${formatPrice(blendedPrice(entry))}</span>
                  </div>
                `).join("")}
              </div>
            </div>
          `).join("")}
        </div>
      </section>
      <section class="card">
        <h2>价格档位</h2>
        <p class="card-sub">混合价按输入 ×3 + 输出 ÷4 计算</p>
        <div class="tier-grid">
          ${tiers.map((tier) => `
            <div class="tier-item">
              <div class="tier-label">${tier.label}</div>
              <div class="tier-model">${tier.model ? escapeHtml(tier.model.displayName) : "暂无"}</div>
              <span class="tier-detail">${tier.model ? `分数 ${tier.model.score.toFixed(1)} · ${formatPrice(blendedPrice(tier.model))}` : ""}</span>
            </div>
          `).join("")}
        </div>
      </section>
    </div>
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
  return `
    <div class="detail-grid">
      <div class="detail-stat"><span>综合名次</span><strong>${overall ? `#${overall.rank}` : "—"}</strong></div>
      <div class="detail-stat"><span>综合分数</span><strong>${overall?.score == null ? "—" : overall.score.toFixed(1)}</strong></div>
      <div class="detail-stat"><span>混合价</span><strong>${formatPrice(blendedPrice(model))}</strong></div>
      <div class="detail-stat"><span>发布日期</span><strong>${formatDate(model.releaseDate)}</strong></div>
    </div>
    <p class="detail-source">
      ${escapeHtml(model.vendor || "未知厂商")} · ${escapeHtml(model.license || "许可未知")} · 上下文 ${escapeHtml(model.contextWindow || "—")} · 参数 ${model.paramsB == null ? "—" : `${model.paramsB}B`}
      ${model.sourceUrl ? ` · <a href="${escapeAttr(model.sourceUrl)}" target="_blank" rel="noreferrer">官方来源</a>` : ""}
    </p>
    <h2 style="margin:0 0 10px;font-size:16px">各榜成绩</h2>
    ${scores.length ? scores.map((score) => `
      <div class="score-row">
        <div class="score-row-name">${escapeHtml(score.boardName)}</div>
        <div class="score-row-rank">#${score.rank}</div>
        <div class="score-row-score">${score.score == null ? "—" : score.score.toFixed(1)}</div>
      </div>
    `).join("") : `<div class="muted">暂无跨榜成绩。</div>`}
  `;
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
    return [{
      boardSlug,
      boardName: board?.name || boardSlug,
      rank: entry.rank,
      score: entry.score,
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

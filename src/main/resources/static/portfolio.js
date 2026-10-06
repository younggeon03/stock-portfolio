/*
 * 화면 그리는 코드.
 *
 * 흐름:
 *   1. /api/portfolio/unified 로 총자산·비중·보유종목을 한 번에 받아 그린다
 *   2. /api/toss/volatility 와 /api/analysis/status 로 표의 빈 칸을 나중에 채운다
 *   3. 종목을 누르면 드로어가 열리고 차트·뉴스·기업분석을 불러온다
 *
 * ★ 시세는 토스에서만 가져온다
 * 주가는 어느 증권사를 통해 보든 같은 값이다. 나무에서 산 국내 종목도 토스 시세로 조회된다.
 */

// ── 화면 요소 ───────────────────────────────────────────
const messageBox = document.getElementById("message");
const heroBox = document.getElementById("hero");
const allocBox = document.getElementById("alloc");
const brokersBox = document.getElementById("brokers");
const tableBody = document.getElementById("holdingsBody");
const tableScroll = document.getElementById("tableScroll");
const tableCount = document.getElementById("tableCount");
const scrollFade = document.getElementById("scrollFade");
const refreshBtn = document.getElementById("refreshBtn");

/** 기본으로 보여줄 줄 수. CSS 의 --rows-visible 과 같은 값이어야 한다 */
const ROWS_VISIBLE = 5;

const drawer = document.getElementById("drawer");
const drawerBackdrop = document.getElementById("drawerBackdrop");
const drawerTitle = document.getElementById("drawerTitle");
const drawerMeta = document.getElementById("drawerMeta");
const drawerPrice = document.getElementById("drawerPrice");
const drawerClose = document.getElementById("drawerClose");

const chartHost = document.getElementById("chartHost");
const chartLegend = document.getElementById("chartLegend");
const chartSub = document.getElementById("chartSub");
const newsBody = document.getElementById("newsBody");
const newsMeta = document.getElementById("newsMeta");
const briefBtn = document.getElementById("briefBtn");
const analysisMeta = document.getElementById("analysisMeta");
const analysisBody = document.getElementById("analysisBody");
const analyzeBtn = document.getElementById("analyzeBtn");
// 저장된 조사는 두고 판정·내 위치·위험만 검색 없이 다시 쓴다. 조사가 보관 일수 안일 때만 보인다
const reassessBtn = document.getElementById("reassessBtn");

/** 지금 보고 있는 범위. ALL / TOSS / NAMUH */
let currentScope = "ALL";

/** 화면에 그려진 종목 목록 */
let currentItems = [];

/** 드로어에 띄운 종목. 늦게 도착한 응답을 걸러내는 데도 쓴다 */
let currentSymbol = null;

let analysisTimer = null;
let analysisStartedAt = null;
let dragDepth = 0;

// 이동평균선 설정(MA_STYLE)·처음 보이는 봉 수(CHART_VISIBLE_BARS)·차트 색 읽기는 public/chart-view.js 에 있다


// ── 나를 구분하는 값 ────────────────────────────────────

/**
 * 이 브라우저를 구분하는 값.
 * 직접 입력한 보유종목을 사람별로 나누려고 쓴다. 회원가입 없이 각자 자기 목록을 갖는다.
 * 브라우저를 바꾸면 다른 사람으로 취급되는 게 한계다.
 */
function ownerKey() {
    let key = localStorage.getItem("portfolioOwnerKey");
    if (!key) {
        key = "u_" + Math.random().toString(36).slice(2) + Date.now().toString(36);
        localStorage.setItem("portfolioOwnerKey", key);
    }
    return key;
}

/** 서버를 부를 때 항상 이걸 쓴다. 나를 구분하는 값을 헤더에 붙여준다 */
function apiFetch(url, options = {}) {
    const headers = Object.assign({}, options.headers || {}, { "X-Owner-Key": ownerKey() });
    // POST·PUT·DELETE 는 CSRF 토큰을 같이 보낸다. 서버가 XSRF-TOKEN 쿠키로 내려준 값이다
    const method = (options.method || "GET").toUpperCase();
    if (method !== "GET" && method !== "HEAD") {
        const token = readCookie("XSRF-TOKEN");
        if (token) headers["X-XSRF-TOKEN"] = token;
    }
    return fetch(url, Object.assign({}, options, { headers: headers })).then(res => {
        // 로그인이 풀렸다 (서버를 다시 띄웠거나 12시간이 지남). 로그인 화면으로 보낸다
        if (res.status === 401) {
            location.href = "/public/login.html";
        }
        return res;
    });
}

function readCookie(name) {
    const hit = document.cookie.split("; ").find(c => c.startsWith(name + "="));
    return hit ? decodeURIComponent(hit.substring(name.length + 1)) : null;
}


// ── 숫자 다듬기 ─────────────────────────────────────────

function withComma(value, decimals = 0) {
    if (value === null || value === undefined) return "-";
    return Number(value).toLocaleString("ko-KR", {
        minimumFractionDigits: decimals, maximumFractionDigits: decimals
    });
}

/** 소수점 매수(0.034888주)면 소수점을 살리고, 1주 단위면 정수로 */
function formatQuantity(value) {
    const n = Number(value);
    if (n === 0) return "0";
    if (Number.isInteger(n)) return withComma(n);
    return n.toFixed(6).replace(/0+$/, "").replace(/\.$/, "");
}

function formatPrice(value, currency) {
    if (value === null || value === undefined) return "-";
    if (currency === "USD") return "$" + withComma(value, 2);
    return withComma(value);
}

function formatRate(rate) {
    if (rate === null || rate === undefined) return "-";
    const n = Number(rate);
    return (n > 0 ? "+" : "") + n.toFixed(2) + "%";
}

function colorClass(value) {
    if (value === null || value === undefined) return "muted";
    return Number(value) >= 0 ? "up" : "down";
}

/** 이 종목을 어느 증권사에 들고 있는지. lots 에서 이름만 뽑아 중복 없이 잇는다 */
function brokerLabel(item) {
    if (!item.lots || item.lots.length === 0) return "-";
    const names = [];
    item.lots.forEach(lot => {
        if (lot.brokerName && !names.includes(lot.brokerName)) names.push(lot.brokerName);
    });
    return names.join(" + ");
}


// ── 알림 ────────────────────────────────────────────────

function showLoading() { messageBox.innerHTML = '<div class="msg loading">불러오는 중...</div>'; }
function showError(text) { setMessage("error", text); }
function showWarn(text) { setMessage("warn", text); }
function showOk(text) { setMessage("ok", text); }
function clearMessage() { messageBox.innerHTML = ""; }

function setMessage(kind, text) {
    messageBox.innerHTML = '<div class="msg ' + kind + '"></div>';
    messageBox.querySelector(".msg").textContent = text;
}


// ── 표 그리기 ───────────────────────────────────────────

async function loadAll() {
    showLoading();
    heroBox.innerHTML = "";
    allocBox.innerHTML = "";
    brokersBox.innerHTML = "";
    tableBody.innerHTML = "";
    closeDrawer();

    try {
        const response = await apiFetch("/api/portfolio/unified?scope=" + encodeURIComponent(currentScope));
        if (!response.ok) {
            const err = await response.json().catch(() => ({}));
            showError(err.error || ("서버 오류 (HTTP " + response.status + ")"));
            return;
        }

        // 여기 나오는 이름들은 UnifiedPortfolioView.java 의 필드 그대로다
        const data = await response.json();
        currentItems = data.items || [];
        clearMessage();

        renderHero(data);
        renderAlloc(currentItems);
        renderBrokers(data.brokers);
        renderTable(currentItems);

        const symbols = currentItems.map(i => i.symbol);
        loadVolatility(symbols);
        loadAnalysisStatus(symbols);

    } catch (e) {
        showError("서버에 연결하지 못했습니다. 앱이 실행 중인지 확인하세요.\n" + e.message);
    }
}

/** 총자산. 화면에서 가장 큰 숫자 하나에 나머지를 붙인다 */
function renderHero(data) {
    const cls = colorClass(data.totalProfitLossKrw);
    heroBox.innerHTML = `
        <div class="hero-total">${withComma(data.totalValueKrw)}<span class="won">원</span></div>
        <div class="hero-sub">
            <span>매입 ${withComma(data.totalPurchaseKrw)}</span>
            <span class="sep">|</span>
            <span class="${cls}">손익 ${withComma(data.totalProfitLossKrw)}</span>
            <span class="${cls}">${formatRate(data.totalProfitRatePercent)}</span>
        </div>`;
}

/**
 * 비중 스트립.
 *
 * ★ 이 화면의 주인공이다.
 * 이 앱을 만든 계기가 "한 종목이 자산의 81% 였다" 를 발견한 것이었다.
 * 표 안의 작은 막대로는 그게 눈에 안 들어와서 전체 폭으로 올렸다.
 * 색이 아니라 명도로 구분한다. 빨강·파랑은 등락 전용이라 여기 쓰면 뜻이 섞인다.
 */
function renderAlloc(items) {
    if (!items || items.length === 0) { allocBox.innerHTML = ""; return; }

    // 덩어리가 충분히 넓을 때만 안에 이름을 적는다. 좁은 곳에 글자를 넣으면 잘려서 지저분하다
    const segments = items.map(item => {
        const pct = Number(item.weightPercent) || 0;
        const label = pct >= 12
            ? `<span class="seg-label">${escapeHtml(item.name)} ${pct.toFixed(1)}%</span>`
            : "";
        return `<div class="alloc-seg" data-symbol="${escapeHtml(item.symbol)}"
                     style="width:${pct}%" title="${escapeHtml(item.name)} ${pct.toFixed(2)}%">${label}</div>`;
    }).join("");

    // 막대 안에 이미 이름을 적은 종목은 범례에서 뺀다. 같은 글자가 두 번 보이면 지저분하다
    const labelled = items.filter(i => (Number(i.weightPercent) || 0) >= 12).length;
    const top = items.slice(labelled, 5);
    const restPct = items.slice(5).reduce((sum, i) => sum + (Number(i.weightPercent) || 0), 0);

    let legend = top.map(item =>
        `<span><b>${escapeHtml(item.name)}</b> <i>${Number(item.weightPercent).toFixed(1)}%</i></span>`
    ).join("");
    if (restPct > 0) {
        legend += `<span><b>그 외 ${items.length - 5}종목</b> <i>${restPct.toFixed(1)}%</i></span>`;
    }

    allocBox.innerHTML = `<div class="alloc-bar">${segments}</div>
                          <div class="alloc-legend">${legend}</div>`;

    allocBox.querySelectorAll(".alloc-seg").forEach(seg => {
        const symbol = seg.dataset.symbol;
        seg.addEventListener("click", () => openDrawer(symbol));
        // 스트립과 표를 잇는다. 한쪽을 가리키면 다른 쪽이 켜진다
        seg.addEventListener("mouseenter", () => linkHighlight(symbol, true));
        seg.addEventListener("mouseleave", () => linkHighlight(symbol, false));
    });
}

/**
 * 비중 스트립의 덩어리와 표의 줄을 서로 비춘다.
 *
 * ★ 장식이 아니다.
 * 스트립에서 제일 큰 덩어리가 어느 종목인지 바로 안 보이는 게 문제였다.
 * 한쪽에 마우스를 올리면 다른 쪽이 같이 켜져서 "이 덩어리가 저 줄" 이 눈에 들어온다.
 */
function linkHighlight(symbol, on) {
    const row = tableBody.querySelector(`tr[data-symbol="${CSS.escape(symbol)}"]`);
    if (row) row.classList.toggle("lit", on);
    const seg = allocBox.querySelector(`.alloc-seg[data-symbol="${CSS.escape(symbol)}"]`);
    if (seg) seg.classList.toggle("lit", on);
}

function renderBrokers(brokers) {
    if (!brokers || brokers.length === 0) { brokersBox.innerHTML = ""; return; }

    brokersBox.innerHTML = brokers.map(b => {
        if (b.error) {
            return `<div class="broker-chip err"><span class="bname">${escapeHtml(b.brokerName)}</span>
                    ${escapeHtml((b.error || "").split("\n")[0])}</div>`;
        }
        return `<div class="broker-chip"><span class="bname">${escapeHtml(b.brokerName)}</span>
                ${withComma(b.valueKrw)}원 · ${Number(b.weightPercent).toFixed(1)}% · ${b.itemCount}종목</div>`;
    }).join("");
}

function renderTable(items) {
    if (!items || items.length === 0) {
        tableBody.innerHTML = '<tr><td colspan="9" class="empty">보유 중인 종목이 없습니다.</td></tr>';
        return;
    }

    // data-label 은 모바일에서 값 왼쪽에 붙는 열 이름이 된다 (CSS 가 읽는다)
    tableBody.innerHTML = items.map(item => {
        const brokerText = brokerLabel(item);
        const brokerClass = brokerText.includes("+") ? "split" : "single";
        return `
        <tr data-symbol="${escapeHtml(item.symbol)}" tabindex="0"
            aria-label="${escapeHtml(item.name)} 상세 보기">
            <td data-label="종목">
                <span class="name">${escapeHtml(item.name)}</span>
                <span class="code">${escapeHtml(item.symbol)}${item.marketCountry ? " · " + item.marketCountry : ""}</span>
            </td>
            <td data-label="보유처"><span class="${brokerClass}">${escapeHtml(brokerText)}</span></td>
            <td data-label="수량">${formatQuantity(item.quantity)}</td>
            <td data-label="현재가">${formatPrice(item.lastPrice, item.currency)}</td>
            <td data-label="평가금액">${withComma(item.marketValueKrw)}</td>
            <td data-label="수익률" class="${colorClass(item.profitRatePercent)}">${formatRate(item.profitRatePercent)}</td>
            <td data-label="변동성" class="muted" data-vol="${escapeHtml(item.symbol)}">···</td>
            <td data-label="비중" class="bar-cell">
                <div class="bar-bg"><div class="bar-fill" style="width:${Math.min(Number(item.weightPercent), 100)}%"></div></div>
                <div class="bar-text">${Number(item.weightPercent).toFixed(2)}%</div>
            </td>
            <td data-label="기업분석" class="muted" data-ana="${escapeHtml(item.symbol)}">···</td>
        </tr>`;
    }).join("");

    tableBody.querySelectorAll("tr[data-symbol]").forEach(row => {
        const symbol = row.dataset.symbol;
        row.addEventListener("click", () => openDrawer(symbol));
        row.addEventListener("mouseenter", () => linkHighlight(symbol, true));
        row.addEventListener("mouseleave", () => linkHighlight(symbol, false));

        // 마우스 없이도 종목을 열 수 있어야 한다.
        // 줄에 초점이 가면 표의 스크롤 영역도 키보드로 다룰 수 있게 된다.
        row.addEventListener("keydown", event => {
            if (event.key !== "Enter" && event.key !== " ") return;
            event.preventDefault();   // Space 로 페이지가 내려가지 않게
            openDrawer(symbol);
        });
        row.addEventListener("focus", () => linkHighlight(symbol, true));
        row.addEventListener("blur", () => linkHighlight(symbol, false));
    });

    updateTableCount(items.length);
    updateScrollFade();
}

/**
 * 표에 몇 개가 있고 몇 개가 접혀 있는지 알려준다.
 * 다섯 줄만 보이므로, 알려주지 않으면 나머지가 있는 줄 모른다.
 */
function updateTableCount(total) {
    if (!tableCount) return;
    // 좁은 화면은 카드로 바뀌면서 전부 펼쳐진다. 거기서 "아래로 3개 더" 는 거짓말이다
    const hidden = isCardLayout() ? 0 : total - ROWS_VISIBLE;
    tableCount.textContent = hidden > 0
        ? `${total}종목 · 아래로 ${hidden}개 더`
        : `${total}종목`;
}

/** 표가 카드로 바뀌는 폭인가. CSS 의 700px 기준과 같아야 한다 */
function isCardLayout() {
    return window.matchMedia("(max-width: 700px)").matches;
}

/** 끝까지 내리면 아래쪽 그림자를 끈다. 더 있는 줄 알고 계속 내리는 걸 막는다 */
function updateScrollFade() {
    if (!tableScroll || !scrollFade) return;
    const atBottom = tableScroll.scrollTop + tableScroll.clientHeight >= tableScroll.scrollHeight - 2;
    const noScroll = tableScroll.scrollHeight <= tableScroll.clientHeight + 2;
    scrollFade.classList.toggle("off", atBottom || noScroll);
}


// ── 표의 빈 칸 채우기 ───────────────────────────────────

async function loadVolatility(symbols) {
    if (!symbols || symbols.length === 0) return;
    try {
        const res = await apiFetch("/api/toss/volatility?symbols=" + encodeURIComponent(symbols.join(",")));
        if (!res.ok) { markCells(symbols, "data-vol", "-", "조회 실패"); return; }

        (await res.json()).forEach(v => {
            const cell = tableBody.querySelector(`td[data-vol="${v.symbol}"]`);
            if (!cell) return;
            if (v.error) {
                cell.textContent = "-";
                cell.title = v.error;
                return;
            }
            cell.textContent = Number(v.annualizedVolatilityPercent).toFixed(1) + "%";
            cell.classList.remove("muted");
            cell.title = `하루 평균 ${Number(v.dailyVolatilityPercent).toFixed(2)}% 등락 · 최근 ${v.dataPoints}거래일`;
        });
    } catch (e) {
        markCells(symbols, "data-vol", "-", e.message);
    }
}

/** DB 만 읽으므로 비용이 들지 않는다 */
async function loadAnalysisStatus(symbols) {
    if (!symbols || symbols.length === 0) return;
    try {
        const res = await apiFetch("/api/analysis/status?symbols=" + encodeURIComponent(symbols.join(",")));
        if (!res.ok) return;

        (await res.json()).forEach(a => {
            const cell = tableBody.querySelector(`td[data-ana="${a.symbol}"]`);
            if (!cell) return;
            if (a.status === "OK") {
                cell.textContent = a.ageDays === 0 ? "오늘" : a.ageDays + "일 전";
                if (!a.stale) cell.classList.remove("muted");
            } else if (a.status === "RUNNING") {
                cell.textContent = "분석 중";
                cell.classList.remove("muted");
            } else if (a.status === "ERROR") {
                cell.textContent = a.analyzedAt ? "이전 분석" : "실패";
                cell.title = a.lastError || "";
            } else {
                cell.textContent = "없음";
            }
        });
    } catch (e) {
        // 표는 이미 보이므로 조용히 넘어간다
    }
}

function markCells(symbols, attr, text, title) {
    symbols.forEach(s => {
        const cell = tableBody.querySelector(`td[${attr}="${s}"]`);
        if (cell) { cell.textContent = text; cell.title = title || ""; }
    });
}


// ── 드로어 ──────────────────────────────────────────────

function openDrawer(symbol) {
    const item = currentItems.find(i => i.symbol === symbol);
    if (!item) return;

    currentSymbol = symbol;
    stopPolling();

    drawerTitle.textContent = item.name;
    drawerMeta.textContent = `${item.symbol} · ${item.marketCountry || ""} · ${brokerLabel(item)}`;
    drawerPrice.innerHTML = `${formatPrice(item.lastPrice, item.currency)}
        <span class="${colorClass(item.profitRatePercent)}" style="font-size:13px; margin-left:6px;">
        ${formatRate(item.profitRatePercent)}</span>`;

    // 선택한 줄 표시
    tableBody.querySelectorAll("tr").forEach(r => r.classList.toggle("sel", r.dataset.symbol === symbol));
    allocBox.querySelectorAll(".alloc-seg").forEach(s => s.classList.toggle("sel", s.dataset.symbol === symbol));

    drawer.classList.add("on");
    // ★ aria-hidden 이 아니라 inert 를 쓴다.
    // aria-hidden 은 "읽어주지 마라" 일 뿐이라 닫힌 드로어에도 Tab 이 그대로 들어간다.
    // 실제로 페이지에서 Tab 을 누르면 첫 초점이 안 보이는 드로어 안으로 떨어졌다.
    drawer.removeAttribute("inert");
    drawerBackdrop.classList.add("on");

    // 데스크톱에서 본문을 왼쪽으로 밀어 표가 드로어에 가리지 않게 한다 (CSS 가 처리)
    document.body.classList.add("drawer-open");
    document.body.style.overflow = "hidden";   // 뒤 화면이 같이 밀리지 않게
    updateWrapNarrow();

    switchTab("analysis");   // 종목을 누르는 이유는 "지금 어떤가" 를 보려는 것이다
    loadChart(symbol);
    loadNews(symbol);
    loadAnalysis(symbol);
}

function closeDrawer() {
    drawer.classList.remove("on");
    drawer.setAttribute("inert", "");
    drawerBackdrop.classList.remove("on");
    document.body.classList.remove("drawer-open");
    document.body.classList.remove("wrap-narrow");
    document.body.style.overflow = "";
    tableBody.querySelectorAll("tr").forEach(r => r.classList.remove("sel"));
    allocBox.querySelectorAll(".alloc-seg").forEach(s => s.classList.remove("sel"));
    stopPolling();
    disposeChart();
    currentSymbol = null;
}

// ── 드로어 크기 조절 ────────────────────────────────────

/*
 * 사용자가 모서리를 끌어 드로어 크기를 바꾼다.
 * 데스크톱은 왼쪽 모서리를 좌우로(너비), 모바일은 위쪽 모서리를 위아래로(높이).
 *
 * ★ 왜 CSS 변수에 넣는가
 * 드로어 너비와 본문이 밀리는 거리가 항상 같아야 한다. 두 군데를 따로 고치면
 * 끄는 도중에 어긋나 본문이 드로어 밑으로 들어간다. 변수 하나를 둘이 같이 본다.
 */

const drawerResize = document.getElementById("drawerResize");

/** 데스크톱 너비 한계. 너무 좁으면 차트가 안 보이고, 너무 넓으면 표가 사라진다 */
const DRAWER_MIN_W = 360;
const DRAWER_MAX_RATIO = 0.85;

/** 모바일 높이 한계 */
const DRAWER_MIN_H_RATIO = 0.35;
const DRAWER_MAX_H_RATIO = 0.95;

function isMobileLayout() {
    return window.matchMedia("(max-width: 900px)").matches;
}

function applyDrawerWidth(px) {
    const max = window.innerWidth * DRAWER_MAX_RATIO;
    const clamped = Math.round(Math.min(Math.max(px, DRAWER_MIN_W), max));
    document.documentElement.style.setProperty("--drawer-w", clamped + "px");
    announceDrawerSize(clamped / window.innerWidth);
    updateWrapNarrow();
    return clamped;
}

/** 손잡이가 role="separator" 라 지금 크기를 백분율로 알려줘야 한다 */
function announceDrawerSize(ratio) {
    if (drawerResize) {
        drawerResize.setAttribute("aria-valuenow", String(Math.round(ratio * 100)));
    }
}

function applyDrawerHeight(px) {
    const min = window.innerHeight * DRAWER_MIN_H_RATIO;
    const max = window.innerHeight * DRAWER_MAX_H_RATIO;
    const clamped = Math.round(Math.min(Math.max(px, min), max));
    document.documentElement.style.setProperty("--drawer-h", clamped + "px");
    announceDrawerSize(clamped / window.innerHeight);
    return clamped;
}

/**
 * 표에 자리가 부족하면 열 네 개를 숨긴다 (CSS 가 처리).
 * 드로어를 좁게 끌어두면 자리가 남으므로, 열려 있다는 이유만으로 숨기지 않는다.
 */
function updateWrapNarrow() {
    const open = document.body.classList.contains("drawer-open");
    const drawerPx = drawer.getBoundingClientRect().width;
    const left = window.innerWidth - drawerPx;
    document.body.classList.toggle("wrap-narrow", open && !isMobileLayout() && left < 900);
}

/** 저장해둔 크기를 꺼내 적용한다. 없으면 CSS 기본값(화면 절반)을 그대로 쓴다 */
function restoreDrawerSize() {
    try {
        const w = Number(localStorage.getItem("drawerWidth"));
        if (w > 0) applyDrawerWidth(w);
        const h = Number(localStorage.getItem("drawerHeight"));
        if (h > 0) applyDrawerHeight(h);
    } catch (e) { /* 저장이 막혀 있어도 기본값으로 동작한다 */ }
}

function saveDrawerSize(key, value) {
    try { localStorage.setItem(key, String(value)); } catch (e) { /* 무시 */ }
}

/** 기본값으로 되돌린다. CSS 에 적힌 50vw / 88vh 로 돌아간다 */
function resetDrawerSize() {
    document.documentElement.style.removeProperty("--drawer-w");
    document.documentElement.style.removeProperty("--drawer-h");
    try {
        localStorage.removeItem("drawerWidth");
        localStorage.removeItem("drawerHeight");
    } catch (e) { /* 무시 */ }
    updateWrapNarrow();
    requestAnimationFrame(fitChart);
}

if (drawerResize) {
    let dragging = false;

    drawerResize.addEventListener("pointerdown", event => {
        dragging = true;
        drawerResize.setPointerCapture(event.pointerId);
        document.body.classList.add("resizing");
        event.preventDefault();
    });

    drawerResize.addEventListener("pointermove", event => {
        if (!dragging) return;
        // 화면 오른쪽(아래쪽) 끝에서 손가락까지의 거리가 곧 드로어 크기다
        if (isMobileLayout()) {
            applyDrawerHeight(window.innerHeight - event.clientY);
        } else {
            applyDrawerWidth(window.innerWidth - event.clientX);
        }
        fitChartSoon();
    });

    const endDrag = event => {
        if (!dragging) return;
        dragging = false;
        document.body.classList.remove("resizing");
        try { drawerResize.releasePointerCapture(event.pointerId); } catch (e) { /* 무시 */ }

        const style = getComputedStyle(document.documentElement);
        if (isMobileLayout()) {
            saveDrawerSize("drawerHeight", parseInt(style.getPropertyValue("--drawer-h"), 10));
        } else {
            saveDrawerSize("drawerWidth", parseInt(style.getPropertyValue("--drawer-w"), 10));
        }
        requestAnimationFrame(fitChart);
    };

    drawerResize.addEventListener("pointerup", endDrag);
    drawerResize.addEventListener("pointercancel", endDrag);
    drawerResize.addEventListener("dblclick", resetDrawerSize);

    // 키보드로도 조절할 수 있게 한다. 마우스를 못 쓰는 경우가 있다
    drawerResize.addEventListener("keydown", event => {
        const step = event.shiftKey ? 80 : 24;
        const box = drawer.getBoundingClientRect();
        if (isMobileLayout()) {
            if (event.key === "ArrowUp") applyDrawerHeight(box.height + step);
            else if (event.key === "ArrowDown") applyDrawerHeight(box.height - step);
            else return;
            saveDrawerSize("drawerHeight", Math.round(drawer.getBoundingClientRect().height));
        } else {
            if (event.key === "ArrowLeft") applyDrawerWidth(box.width + step);
            else if (event.key === "ArrowRight") applyDrawerWidth(box.width - step);
            else if (event.key === "Home") { resetDrawerSize(); return; }
            else return;
            saveDrawerSize("drawerWidth", Math.round(drawer.getBoundingClientRect().width));
        }
        event.preventDefault();
        requestAnimationFrame(fitChart);
    });
}

// 창 크기가 바뀌면 한계값이 달라진다. 넘치지 않게 다시 재운다
window.addEventListener("resize", () => {
    const box = drawer.getBoundingClientRect();
    if (!isMobileLayout() && box.width > 0) applyDrawerWidth(box.width);
    updateWrapNarrow();
    // 표가 카드로 바뀌면 안내 문구와 스크롤 표시도 달라진다
    updateTableCount(currentItems.length);
    updateScrollFade();
});

restoreDrawerSize();


/** 탭은 이미 받아온 내용을 보여주기만 한다. 다시 부르지 않는다 */
function switchTab(name) {
    document.querySelectorAll(".drawer-tabs button").forEach(b => b.classList.toggle("on", b.dataset.tab === name));
    document.querySelectorAll(".tab-pane").forEach(p => p.classList.toggle("on", p.dataset.pane === name));

    // 차트는 숨어 있는 동안 크기를 못 재므로, 보일 때 다시 맞춰준다
    if (name === "chart") requestAnimationFrame(fitChart);
}


// ── 캔들차트 ────────────────────────────────────────────
// 그리는 코드는 public/chart-view.js. 공개 기업분석 화면과 같이 쓴다. 여기서는 데이터만 받아 넘긴다

const stockChart = createStockChart({ host: chartHost, legend: chartLegend, sub: chartSub });

/** 이전 차트를 정리한다. 안 하면 종목을 옮길 때마다 캔버스가 쌓인다 */
function disposeChart() {
    stockChart.dispose();
}

/** 드로어 크기를 끄는 동안 차트 폭을 따라가게 */
function fitChartSoon() {
    stockChart.fitSoon();
}

async function loadChart(symbol) {
    disposeChart();

    if (!window.LightweightCharts) {
        chartHost.innerHTML = '<div class="state">차트 라이브러리를 불러오지 못했습니다.<br>'
            + '인터넷 연결을 확인하고 새로고침해 주세요.</div>';
        return;
    }

    chartHost.innerHTML = '<div class="state">불러오는 중...</div>';

    try {
        const res = await apiFetch("/api/toss/chart?symbol=" + encodeURIComponent(symbol));
        if (symbol !== currentSymbol) return;    // 그 사이 다른 종목을 눌렀으면 버린다

        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            chartHost.innerHTML = '<div class="state err"></div>';
            chartHost.querySelector(".state").textContent = err.error || "차트를 불러오지 못했습니다.";
            return;
        }

        const data = await res.json();
        if (!data.points || data.points.length < 2) {
            chartHost.innerHTML = '<div class="state">차트를 그릴 데이터가 부족합니다.</div>';
            return;
        }
        stockChart.draw(data);

    } catch (e) {
        chartHost.innerHTML = '<div class="state err"></div>';
        chartHost.querySelector(".state").textContent = "차트를 불러오지 못했습니다: " + e.message;
    }
}


// ── 뉴스 ────────────────────────────────────────────────

async function loadNews(symbol) {
    const item = currentItems.find(i => i.symbol === symbol);
    const query = item && item.name ? item.name : symbol;

    newsBody.innerHTML = '<div class="news-empty">불러오는 중...</div>';

    try {
        // symbol 은 요약을 하루 한 번만 만들게 하는 캐시 키로 쓰인다
        const res = await apiFetch("/api/news?query=" + encodeURIComponent(query)
            + "&symbol=" + encodeURIComponent(symbol));
        if (symbol !== currentSymbol) return;

        if (!res.ok) { newsBody.innerHTML = '<div class="news-empty">뉴스를 불러오지 못했습니다.</div>'; return; }

        const data = await res.json();
        const list = data.items || [];
        if (list.length === 0) {
            newsBody.innerHTML = '<div class="news-empty">관련 뉴스를 찾지 못했습니다.</div>';
            return;
        }

        /*
         * 인사이트가 먼저, 목록이 뒤.
         *
         * ★ 목록은 0원이라 자동으로 불러오고, 인사이트는 돈이 나가므로 버튼을 눌러야 만든다.
         * 여기서는 오늘 이미 만들어둔 게 있으면 그걸 보여줄 뿐이다.
         */
        newsCount = list.length;
        updateBriefButton(!!data.brief);
        newsBody.innerHTML = renderNewsBrief(data.brief) + list.map(n => {
            const url = safeUrl(n.link);
            const title = escapeHtml(n.title);
            return `<div class="news-item">`
                + (url ? `<a href="${escapeHtml(url)}" target="_blank" rel="noopener noreferrer">${title}</a>` : title)
                + `<div class="date">${escapeHtml(n.pubDate || "")}</div></div>`;
        }).join("");

    } catch (e) {
        newsBody.innerHTML = '<div class="news-empty">뉴스를 불러오지 못했습니다.</div>';
        updateBriefButton(false, true);
    }
}

/** 지금 화면에 그려진 기사 수. 기사가 없으면 인사이트를 만들 게 없다 */
let newsCount = 0;

function updateBriefButton(hasBrief, failed) {
    if (!briefBtn) return;
    briefBtn.disabled = failed || newsCount === 0;
    briefBtn.textContent = hasBrief ? "다시 정리" : "인사이트 보기";
    if (newsMeta) {
        newsMeta.textContent = failed ? ""
            : (hasBrief ? "오늘 정리한 내용입니다" : "헤드라인 " + newsCount + "건");
    }
}

/**
 * ★ 여기서 돈이 나간다.
 * 같은 날 두 번 눌러도 서버가 만들어둔 걸 돌려주므로 한 번만 과금된다.
 */
async function loadNewsBrief(symbol) {
    const item = currentItems.find(i => i.symbol === symbol);
    const query = item && item.name ? item.name : symbol;

    briefBtn.disabled = true;
    briefBtn.textContent = "정리하는 중...";
    if (newsMeta) newsMeta.textContent = "헤드라인을 읽고 있습니다";

    try {
        const res = await apiFetch("/api/news/brief?query=" + encodeURIComponent(query)
            + "&symbol=" + encodeURIComponent(symbol), { method: "POST" });
        if (symbol !== currentSymbol) return;

        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            if (newsMeta) newsMeta.textContent = err.error || "인사이트를 만들지 못했습니다.";
            updateBriefButton(false);
            return;
        }

        const data = await res.json();
        if (!data.brief) {
            // 크레딧이 없거나 헤드라인에 잡을 줄기가 없을 때. 목록은 그대로 둔다
            if (newsMeta) newsMeta.textContent = "인사이트를 만들지 못했습니다. 크레딧 잔액을 확인하세요.";
            updateBriefButton(false);
            return;
        }

        const existing = newsBody.querySelector(".news-brief");
        if (existing) existing.remove();
        newsBody.insertAdjacentHTML("afterbegin", renderNewsBrief(data.brief));
        updateBriefButton(true);

    } catch (e) {
        if (newsMeta) newsMeta.textContent = "인사이트를 만들지 못했습니다: " + e.message;
        updateBriefButton(false);
    }
}


/**
 * 뉴스 요약.
 *
 * 헤드라인 제목만 보고 만든 것이라 본문을 읽은 게 아니다. 화면에도 그렇게 적는다.
 * 요약이 없으면 빈 문자열을 돌려주고 목록만 남는다.
 */
function renderNewsBrief(brief) {
    if (!brief || !brief.headline) return "";

    return `<div class="news-brief">
        <div class="nb-label">지금 무슨 일이 있나</div>
        <div class="nb-headline">${escapeHtml(brief.headline)}</div>
        ${brief.detail ? `<div class="nb-detail">${prose(brief.detail)}</div>` : ""}
        ${brief.impact ? `<div class="nb-impact"><span>주가 영향</span>${emphasizeNumbers(escapeHtml(brief.impact))}</div>` : ""}
        <div class="nb-note">아래 헤드라인 제목만 보고 정리한 것입니다. 기사 본문은 직접 확인하세요.</div>
    </div>`;
}


// ── 기업분석 ────────────────────────────────────────────

function stopPolling() {
    if (analysisTimer) { clearInterval(analysisTimer); analysisTimer = null; }
}

/** 저장된 분석만 읽는다. 클로드를 부르지 않으므로 비용이 없다 */
async function loadAnalysis(symbol) {
    analysisMeta.textContent = "";
    analysisBody.innerHTML = '<div class="state">불러오는 중...</div>';
    analyzeBtn.style.display = "none";
    reassessBtn.hidden = true;

    try {
        const res = await apiFetch("/api/analysis/" + encodeURIComponent(symbol));
        if (symbol !== currentSymbol) return;

        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            analysisBody.innerHTML = '<div class="state err"></div>';
            analysisBody.querySelector(".state").textContent = err.error || "분석을 불러오지 못했습니다.";
            return;
        }

        const data = await res.json();
        renderAnalysis(data);
        if (data.status === "RUNNING") startPolling(symbol);

    } catch (e) {
        analysisBody.innerHTML = '<div class="state err"></div>';
        analysisBody.querySelector(".state").textContent = "분석을 불러오지 못했습니다: " + e.message;
    }
}

/** 돌고 있는 작업이 전체 분석인지 판단만 새로인지. RUNNING 응답은 둘을 구분하지 않아 화면이 기억한다 */
let runningKind = "full";

/** 진행 중 문구. 판단만 새로는 검색이 없어 훨씬 빨리 끝난다 */
function runningText(elapsed) {
    const time = elapsed ? ` (${elapsed}초)` : "";
    return runningKind === "reassess"
        ? `저장된 조사로 판단만 새로 쓰고 있습니다${time}<br>웹 검색이 없어 보통 1분 안에 끝납니다.`
        : `클로드가 웹 검색으로 조사하고 있습니다${time}<br>보통 2~5분 걸립니다. 다른 종목을 봐도 분석은 계속됩니다.`;
}

/**
 * ★ 여기서만 돈이 나간다. 그래서 버튼을 눌러야 실행된다
 *
 * @param mode "new" 첫 분석, "refresh" 전체 다시 분석, "reassess" 판단만 새로
 */
async function startAnalysis(symbol, mode) {
    analyzeBtn.disabled = true;
    reassessBtn.disabled = true;
    (mode === "reassess" ? reassessBtn : analyzeBtn).textContent = "시작하는 중...";
    analysisStartedAt = Date.now();
    runningKind = mode === "reassess" ? "reassess" : "full";

    try {
        const base = "/api/analysis/" + encodeURIComponent(symbol);
        const url = mode === "reassess" ? base + "/reassess" : base + (mode === "refresh" ? "?refresh=true" : "");
        const res = await apiFetch(url, { method: "POST" });

        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            analysisBody.innerHTML = '<div class="state err"></div>';
            analysisBody.querySelector(".state").textContent = err.error || "분석을 시작하지 못했습니다.";
            return;
        }

        const data = await res.json();
        renderAnalysis(data);
        if (data.status === "RUNNING") startPolling(symbol);

    } catch (e) {
        analysisBody.innerHTML = '<div class="state err"></div>';
        analysisBody.querySelector(".state").textContent = "분석을 시작하지 못했습니다: " + e.message;
    } finally {
        analyzeBtn.disabled = false;
        reassessBtn.disabled = false;
        reassessBtn.textContent = "판단만 새로";
        // 성공했으면 renderAnalysis 가 이미 알맞은 이름을 달았다. 실패했을 때만 되돌린다
        if (analyzeBtn.textContent === "시작하는 중...") analyzeBtn.textContent = "다시 시도";
    }
}

/** 몇 초 간격으로 상태를 확인한다. 끝나면 스스로 멈춘다 */
function startPolling(symbol) {
    stopPolling();
    if (!analysisStartedAt) analysisStartedAt = Date.now();

    analysisTimer = setInterval(async () => {
        if (symbol !== currentSymbol) { stopPolling(); return; }

        const elapsed = Math.round((Date.now() - analysisStartedAt) / 1000);
        if (elapsed > 600) {
            stopPolling();
            analysisBody.innerHTML = '<div class="state">10분이 넘도록 끝나지 않았습니다. 새로고침 후 확인해 주세요.</div>';
            return;
        }

        try {
            const res = await apiFetch("/api/analysis/" + encodeURIComponent(symbol));
            if (!res.ok || symbol !== currentSymbol) return;

            const data = await res.json();
            if (data.status === "RUNNING") {
                analysisBody.innerHTML = `<div class="state"><div class="spinner"></div>${runningText(elapsed)}</div>`;
            } else {
                stopPolling();
                renderAnalysis(data);
                loadAnalysisStatus(currentItems.map(i => i.symbol));
            }
        } catch (e) { /* 일시적 실패는 다음 차례에 다시 확인한다 */ }
    }, 3000);
}

function renderAnalysis(data) {
    analyzeBtn.style.display = "inline-block";
    reassessBtn.hidden = true;

    if (data.status === "NONE") {
        analysisMeta.textContent = "아직 분석하지 않았습니다";
        analyzeBtn.textContent = "기업분석 실행";
        analyzeBtn.onclick = () => startAnalysis(data.symbol, "new");
        analysisBody.innerHTML = '<div class="state">버튼을 누르면 클로드가 웹 검색으로 재무·지표·컨센서스·사업구조를 조사합니다.<br>'
            + '2~5분 걸리고 비용이 들기 때문에 자동으로 실행하지 않습니다.</div>';
        return;
    }

    if (data.status === "RUNNING") {
        analysisMeta.textContent = "분석 중";
        analyzeBtn.style.display = "none";
        analysisBody.innerHTML = `<div class="state"><div class="spinner"></div>${runningText(0)}</div>`;
        return;
    }

    // 조사가 보관 일수 안이면 싼 "판단만 새로" 를 앞에 둔다. 낡았으면 전체 다시 분석만 (서버도 400 으로 막는다)
    const canReassess = !!data.analysis && !data.stale;
    reassessBtn.hidden = !canReassess;
    reassessBtn.title = "저장된 조사는 그대로, 판정·내 위치·위험만 지금 가격으로 다시 씁니다. 웹 검색 없음, 약 100~300원";
    reassessBtn.onclick = () => startAnalysis(data.symbol, "reassess");
    analyzeBtn.textContent = canReassess ? "전체 다시 분석" : "다시 분석";
    analyzeBtn.title = "웹 검색부터 전부 다시 합니다. 1회 800~1,600원";
    analyzeBtn.onclick = () => startAnalysis(data.symbol, "refresh");

    // 본문(배너·판정·섹션·리스크·안내문)은 기업분석 화면과 같이 쓴다 (public/analysis-view.js)
    const content = analysisContentHtml(data);
    if (content.meta !== null) analysisMeta.textContent = content.meta;
    analysisBody.innerHTML = content.html;
    bindPeriodToggles(analysisBody);
}

// ── 스크린샷으로 종목 추가 ──────────────────────────────

const shotBtn = document.getElementById("shotBtn");
const shotInput = document.getElementById("shotInput");
const dropZone = document.getElementById("dropZone");
const shotModal = document.getElementById("shotModal");
const shotBody = document.getElementById("shotBody");
const shotSub = document.getElementById("shotSub");
const shotFoot = document.getElementById("shotFoot");

function closeShotModal() {
    shotModal.classList.remove("on");
    shotBody.innerHTML = "";
    shotFoot.style.display = "none";
    shotInput.value = "";       // 같은 파일을 다시 골라도 이벤트가 나도록 비운다
}

/** ★ 이 호출은 비용이 든다 (한 장에 수십 원 수준) */
async function uploadScreenshot(file) {
    if (!file) return;
    if (!file.type.startsWith("image/")) { alert("이미지 파일만 올릴 수 있습니다."); return; }

    shotModal.classList.add("on");
    shotSub.textContent = file.name;
    shotFoot.style.display = "none";
    shotBody.innerHTML = `<div class="state"><div class="spinner"></div>
        인공지능이 스크린샷을 분석 중입니다...<br>
        종목명과 수량, 평단가를 읽고 있습니다. 10~30초쯤 걸립니다.</div>`;

    try {
        const form = new FormData();
        form.append("file", file);
        const res = await apiFetch("/api/import/screenshot", { method: "POST", body: form });

        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            shotBody.innerHTML = '<div class="state err"></div>';
            shotBody.querySelector(".state").textContent = err.error || ("분석에 실패했습니다 (HTTP " + res.status + ")");
            return;
        }
        renderReview(await res.json());

    } catch (e) {
        shotBody.innerHTML = '<div class="state err"></div>';
        shotBody.querySelector(".state").textContent = "분석에 실패했습니다: " + e.message;
    }
}

function renderReview(preview) {
    const rows = preview.holdings || [];
    if (rows.length === 0) {
        shotBody.innerHTML = '<div class="review-warn">'
            + escapeHtml(preview.note || "스크린샷에서 종목을 찾지 못했습니다. 보유종목 목록이 보이는 화면을 캡처해 주세요.") + "</div>";
        return;
    }

    let html = "";
    if (preview.symbolMasterEmpty) {
        html += '<div class="review-warn">종목 목록을 아직 받아오지 않아 코드를 자동으로 찾지 못했습니다. 아래에서 직접 입력해 주세요.</div>';
    }
    if (preview.needsReviewCount > 0) {
        html += `<div class="review-warn">노란색으로 표시된 ${preview.needsReviewCount}개 줄은 확인이 필요합니다.</div>`;
    }
    if (preview.note) html += '<div class="review-note">' + escapeHtml(preview.note) + "</div>";

    html += `<table class="review"><thead><tr>
        <th style="width:24%">종목명</th><th style="width:16%">종목코드</th>
        <th style="width:16%">수량</th><th style="width:17%">평단가</th>
        <th style="width:14%">통화</th><th style="width:8%"></th>
        </tr></thead><tbody id="reviewBody"></tbody></table>`;

    shotBody.innerHTML = html;
    const body = document.getElementById("reviewBody");
    rows.forEach(r => body.appendChild(buildReviewRow(r)));

    shotFoot.style.display = "flex";
    shotSub.textContent = `${rows.length}개 종목을 읽었습니다. 확인 후 저장하세요.`;
}

function buildReviewRow(row) {
    const tr = document.createElement("tr");
    if (row && row.needsReview) tr.classList.add("warn");

    const name = row ? (row.name || row.rawName || "") : "";
    const symbol = row ? (row.symbol || "") : "";
    const qty = row && row.quantity != null ? row.quantity : "";
    const price = row && row.averagePurchasePrice != null ? row.averagePurchasePrice : "";
    const cur = row ? (row.currency || "KRW") : "KRW";

    tr.innerHTML = `
        <td><input class="r-name" value="${escapeHtml(name)}" placeholder="종목명">
            ${row && row.issues && row.issues.length > 0
                ? '<div class="row-issues">' + escapeHtml(row.issues.join(" · ")) + "</div>" : ""}</td>
        <td><input class="r-symbol${!symbol ? " bad" : ""}" value="${escapeHtml(symbol)}" placeholder="005930"></td>
        <td class="num"><input class="r-qty${qty === "" ? " bad" : ""}" value="${escapeHtml(String(qty))}" placeholder="0"></td>
        <td class="num"><input class="r-price${price === "" ? " bad" : ""}" value="${escapeHtml(String(price))}" placeholder="0"></td>
        <td><select class="r-cur">
            <option value="KRW"${cur === "KRW" ? " selected" : ""}>원</option>
            <option value="USD"${cur === "USD" ? " selected" : ""}>달러</option>
        </select></td>
        <td><button class="del-row">삭제</button></td>`;

    tr.querySelector(".del-row").addEventListener("click", () => tr.remove());

    // 종목명을 고치고 칸을 벗어나면 코드를 자동으로 찾아준다
    tr.querySelector(".r-name").addEventListener("blur", async event => {
        const symbolInput = tr.querySelector(".r-symbol");
        if (symbolInput.value.trim() !== "") return;
        const q = event.target.value.trim();
        if (!q) return;
        try {
            const res = await apiFetch("/api/symbols/search?q=" + encodeURIComponent(q));
            if (!res.ok) return;
            const match = await res.json();
            if (match && match.symbol) {
                symbolInput.value = match.symbol;
                symbolInput.classList.remove("bad");
                if (match.currency) tr.querySelector(".r-cur").value = match.currency;
            }
        } catch (e) { /* 자동 찾기는 실패해도 직접 입력하면 된다 */ }
    });

    return tr;
}

function collectReviewRows() {
    const rows = [], problems = [];
    document.querySelectorAll("#reviewBody tr").forEach((tr, index) => {
        const name = tr.querySelector(".r-name").value.trim();
        const symbol = tr.querySelector(".r-symbol").value.trim().toUpperCase();
        const qty = tr.querySelector(".r-qty").value.trim().replace(/,/g, "");
        const price = tr.querySelector(".r-price").value.trim().replace(/,/g, "");
        const cur = tr.querySelector(".r-cur").value;

        if (!name && !symbol && !qty && !price) return;   // 빈 줄은 건너뛴다

        const line = index + 1;
        if (!symbol) problems.push(`${line}번째 줄: 종목코드가 비어 있습니다`);
        if (!name) problems.push(`${line}번째 줄: 종목명이 비어 있습니다`);
        if (!qty || Number(qty) <= 0) problems.push(`${line}번째 줄: 수량을 확인해주세요`);
        if (!price || Number(price) <= 0) problems.push(`${line}번째 줄: 평단가를 확인해주세요`);

        rows.push({
            symbol: symbol, name: name,
            marketCountry: cur === "USD" ? "US" : "KR",    // 통화로 시장을 정한다
            currency: cur, quantity: qty, averagePurchasePrice: price
        });
    });
    return { rows: rows, problems: problems };
}

async function saveReviewRows() {
    const collected = collectReviewRows();
    if (collected.rows.length === 0) { alert("저장할 종목이 없습니다."); return; }
    if (collected.problems.length > 0) {
        alert("아래 항목을 먼저 고쳐주세요.\n\n" + collected.problems.join("\n"));
        return;
    }

    const saveBtn = document.getElementById("shotSave");
    saveBtn.disabled = true;
    saveBtn.textContent = "저장 중...";

    try {
        // POST 는 기존 목록에 더한다. 국내 화면과 해외 화면을 따로 올릴 수 있다
        const res = await apiFetch("/api/manual-holdings", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ holdings: collected.rows })
        });
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            alert(err.error || "저장에 실패했습니다.");
            return;
        }
        closeShotModal();
        showOk(collected.rows.length + "개 종목을 저장했습니다.");
        await loadAll();

    } catch (e) {
        alert("저장에 실패했습니다: " + e.message);
    } finally {
        saveBtn.disabled = false;
        saveBtn.textContent = "저장";
    }
}


// ── 이벤트 연결 ─────────────────────────────────────────

document.querySelectorAll(".scope").forEach(btn => {
    btn.addEventListener("click", () => {
        document.querySelectorAll(".scope").forEach(b => b.classList.remove("primary"));
        btn.classList.add("primary");
        currentScope = btn.dataset.scope;
        loadAll();
    });
});

document.querySelectorAll(".drawer-tabs button").forEach(btn => {
    btn.addEventListener("click", () => switchTab(btn.dataset.tab));
});

refreshBtn.addEventListener("click", loadAll);
if (briefBtn) briefBtn.addEventListener("click", () => { if (currentSymbol) loadNewsBrief(currentSymbol); });
if (tableScroll) tableScroll.addEventListener("scroll", updateScrollFade, { passive: true });
drawerClose.addEventListener("click", closeDrawer);
drawerBackdrop.addEventListener("click", closeDrawer);

document.addEventListener("keydown", e => {
    if (e.key !== "Escape") return;
    if (shotModal.classList.contains("on")) closeShotModal();
    else if (drawer.classList.contains("on")) closeDrawer();
});

shotBtn.addEventListener("click", () => shotInput.click());
shotInput.addEventListener("change", e => uploadScreenshot(e.target.files[0]));
document.getElementById("shotClose").addEventListener("click", closeShotModal);
document.getElementById("shotCancel").addEventListener("click", closeShotModal);
document.getElementById("shotSave").addEventListener("click", saveReviewRows);
document.getElementById("addRowBtn").addEventListener("click", () => {
    const body = document.getElementById("reviewBody");
    if (body) body.appendChild(buildReviewRow(null));
});
shotModal.addEventListener("click", e => { if (e.target === shotModal) closeShotModal(); });

// 화면 아무 곳에나 이미지를 끌어다 놓을 수 있게 한다.
// 자식 요소를 지날 때마다 enter/leave 가 나므로 깊이를 세어야 깜빡이지 않는다.
window.addEventListener("dragenter", e => { e.preventDefault(); dragDepth++; dropZone.classList.add("on"); });
window.addEventListener("dragover", e => e.preventDefault());
window.addEventListener("dragleave", e => {
    e.preventDefault();
    if (--dragDepth <= 0) { dragDepth = 0; dropZone.classList.remove("on"); }
});
window.addEventListener("drop", e => {
    e.preventDefault();
    dragDepth = 0;
    dropZone.classList.remove("on");
    const files = e.dataTransfer ? e.dataTransfer.files : null;
    if (files && files.length > 0) uploadScreenshot(files[0]);
});

// 캡처 직후 Ctrl+V 가 가장 빠르다
window.addEventListener("paste", e => {
    const items = e.clipboardData ? e.clipboardData.items : null;
    if (!items) return;
    for (const item of items) {
        if (item.type && item.type.startsWith("image/")) { uploadScreenshot(item.getAsFile()); break; }
    }
});

// 로그인을 켠 서버에서만 로그아웃 버튼을 보인다. 내 PC 개발 모드(비밀번호 없음)에서는 누를 의미가 없다
const logoutBtn = document.getElementById("logoutBtn");
apiFetch("/api/me").then(r => r.ok ? r.json() : null).then(me => {
    if (me && me.loginRequired) logoutBtn.hidden = false;
}).catch(() => {});
logoutBtn.addEventListener("click", () => {
    // 로그아웃도 POST 라 CSRF 토큰이 필요하다. 끝나면 공개 첫 화면으로
    apiFetch("/logout", { method: "POST" }).finally(() => { location.href = "/"; });
});

loadAll();

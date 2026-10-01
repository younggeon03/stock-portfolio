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

/** 지금 보고 있는 범위. ALL / TOSS / NAMUH */
let currentScope = "ALL";

/** 화면에 그려진 종목 목록 */
let currentItems = [];

/** 드로어에 띄운 종목. 늦게 도착한 응답을 걸러내는 데도 쓴다 */
let currentSymbol = null;

let analysisTimer = null;
let analysisStartedAt = null;
let dragDepth = 0;

/** 이동평균선 설정. 국내 증권사 기본값이다 */
const MA_STYLE = {
    5:   { color: "#f08c00", dark: "#ffb340", label: "5일" },
    20:  { color: "#2f9e44", dark: "#5fd67e", label: "20일" },
    60:  { color: "#7048e8", dark: "#a98cff", label: "60일" },
    120: { color: "#868e96", dark: "#aeb6c0", label: "120일" }
};

/**
 * 차트에 몇 봉을 처음부터 보여줄지.
 *
 * ★ 이평선이 잘려 보이던 이유가 여기 있었다.
 * 토스에서 200봉을 받는데, 120일선은 121번째 봉부터 값이 생긴다.
 * 200봉을 전부 펼쳐 놓으면 120일선이 화면 한가운데에서 뚝 시작해 "잘린 선" 으로 보인다.
 *
 * 그래서 계산은 200봉 전부로 하고 처음 보이는 구간만 마지막 80봉으로 잡는다.
 * 80 = 200 - 120 이라, 보이는 구간에서는 네 선이 모두 왼쪽 끝까지 이어진다.
 * 앞쪽 데이터는 그대로 들어 있으니 왼쪽으로 끌면 계속 나온다.
 */
const CHART_VISIBLE_BARS = 80;

/** 차트가 화면 모드(밝은/어두운)를 따라가게 한다 */
function isDarkMode() {
    return window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches;
}

/** CSS 변수 하나를 읽는다. 색을 두 군데에 적어두면 반드시 어긋난다 */
function cssVar(name, fallback) {
    const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
    return v || fallback;
}


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
    return fetch(url, Object.assign({}, options, { headers: headers }));
}


// ── 안전장치 ────────────────────────────────────────────

/**
 * 화면에 글자를 넣기 전에 HTML 특수문자를 막는다.
 * 분석 본문은 클로드가 웹에서 읽어온 내용이 섞인 글이다.
 * 그대로 넣으면 스크립트가 실행될 수 있으므로 항상 이 함수를 거친다.
 */
function escapeHtml(text) {
    if (text === null || text === undefined) return "";
    return String(text)
        .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}

/**
 * 분석 문장을 읽을 수 있는 모양으로 만든다.
 *
 * ★ 왜 필요한가
 * 글자를 키우고 대비를 올렸는데도 "눈에 안 들어온다" 는 말이 나왔다.
 * 원인은 크기가 아니라 덩어리였다. 한 문단이 전부 같은 크기·같은 색이면
 * 어디부터 읽어야 할지 눈이 못 잡고, 통째로 건너뛰게 된다.
 *
 * 두 가지만 한다.
 *
 * 1. 첫 문장을 진하게 (`.lead`)
 *    각 문단의 첫 문장이 대개 결론이다. 그것만 읽고 넘어가도 뜻이 통해야 한다.
 *
 * 2. 단위가 붙은 숫자를 진하게 (`.num`)
 *    이 화면에서 실제로 찾는 건 숫자다. "-19.47%" 나 "81.47%" 가 회색 문장 속에 묻히면
 *    문단을 처음부터 끝까지 읽어야 찾을 수 있다.
 *    단위 없는 숫자는 건드리지 않는다. 연도나 순번까지 진해지면 다시 노이즈가 된다.
 *
 * ★ 순서 주의: 반드시 escapeHtml 을 먼저 하고 그 결과에 태그를 넣는다.
 */
/*
 * 단위 붙은 숫자.
 *
 * ★ 뒤에 오는 글자로 거르면 안 된다.
 * 한국어는 단위 뒤에 조사가 붙는다 — "122.25 달러로", "-3,962,139원이다".
 * 처음엔 뒤에 한글이 오면 제외했는데, 그러면 정작 흔한 경우가 전부 빠졌다.
 *
 * 긴 단위를 먼저 적는다. "10억원" 에서 "억" 만 먼저 잡히면 "원" 이 따로 남는다.
 */
const NUMBER_WITH_UNIT = /(-?\d[\d,]*(?:\.\d+)?)(\s?)(억원|조원|%|원|달러|배|억|조|포인트|bp)/g;

function emphasizeNumbers(escaped) {
    return escaped.replace(NUMBER_WITH_UNIT, '<b class="num">$1$2$3</b>');
}

/** 문단: 첫 문장은 굵게, 숫자는 강조 */
function prose(text) {
    if (!text) return "";
    const escaped = escapeHtml(text);

    // 첫 문장의 끝. 한국어는 "~다." "~요." 로 끝나고 뒤에 공백이나 줄바꿈이 온다.
    const end = escaped.search(/[.!?](\s|$)/);
    if (end < 0 || end > 120) {
        // 문장 구분이 없거나 첫 문장이 지나치게 길면 나누지 않는다.
        // 억지로 자르면 리드가 또 하나의 덩어리가 된다.
        return emphasizeNumbers(escaped);
    }

    const lead = escaped.slice(0, end + 1);
    const restText = escaped.slice(end + 1).trim();
    return '<span class="lead">' + emphasizeNumbers(lead) + "</span>"
        + (restText ? " " + emphasizeNumbers(restText) : "");
}

/** http/https 로 시작하는 주소만 링크로 만든다 */
function safeUrl(url) {
    if (typeof url !== "string") return null;
    return /^https?:\/\//i.test(url.trim()) ? url.trim() : null;
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

let chart = null;
let candleSeries = null;
let maSeries = {};
let chartResizeObserver = null;
/** 마지막으로 그린 차트 데이터. 화면 모드가 바뀌면 이걸로 다시 그린다 */
let lastChartData = null;

/** 이전 차트를 정리한다. 안 하면 종목을 옮길 때마다 캔버스가 쌓인다 */
function disposeChart() {
    if (chartResizeObserver) { chartResizeObserver.disconnect(); chartResizeObserver = null; }
    if (chart) { chart.remove(); chart = null; }
    candleSeries = null;
    maSeries = {};
    lastChartData = null;
    chartHost.innerHTML = "";
    chartLegend.innerHTML = "";
    chartSub.textContent = "";
}

/*
 * 화면 모드가 바뀌면 차트를 다시 그린다.
 *
 * 차트는 캔버스라 CSS 변수를 스스로 따라가지 못한다.
 * 이걸 안 걸어두면 밝은 화면에서 보다가 어두워졌을 때 차트만 하얗게 남는다.
 */
if (window.matchMedia) {
    window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", () => {
        if (lastChartData) drawChart(lastChartData);
    });
}

/**
 * 차트 크기를 화면에 맞춘다.
 *
 * ★ 폭이 0인 채로 그려지는 경우가 있다.
 * 기본 탭이 기업분석이라, 종목을 누르면 차트는 숨겨진 탭 안에서 만들어진다.
 * 그때 폭이 0이면 봉 간격 계산이 깨져서, 나중에 차트 탭을 열었을 때
 * 200봉이 오른쪽 구석에 뭉쳐 있고 왼쪽은 텅 빈 모양이 된다.
 *
 * 그래서 "처음으로 진짜 폭이 생긴 순간" 에 한 번 보이는 구간을 다시 잡아준다.
 * 그 뒤로는 크기만 맞춘다. 매번 다시 잡으면 드로어를 끌 때마다
 * 사용자가 왼쪽으로 끌어놓은 위치가 튕겨나간다.
 */
let chartSized = false;

function fitChart() {
    if (!chart || chartHost.clientWidth <= 0) return;

    chart.applyOptions({ width: chartHost.clientWidth, height: chartHost.clientHeight });

    if (!chartSized) {
        chartSized = true;
        applyChartWindow();
    }
}

/** 마지막 CHART_VISIBLE_BARS 봉만 보이게 맞춘다 */
function applyChartWindow() {
    if (!chart || !lastChartData) return;
    const total = lastChartData.points.length;
    const from = Math.max(0, total - CHART_VISIBLE_BARS);
    chart.timeScale().setVisibleLogicalRange({ from: from - 0.5, to: total - 0.5 });
}

/*
 * 끄는 동안에는 크기 변경이 초당 수십 번 들어온다.
 * 매번 다시 그리면 손가락을 못 따라오므로 한 프레임에 한 번으로 묶는다.
 */
let fitPending = false;
function fitChartSoon() {
    if (fitPending) return;
    fitPending = true;
    requestAnimationFrame(() => { fitPending = false; fitChart(); });
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
        drawChart(data);

    } catch (e) {
        chartHost.innerHTML = '<div class="state err"></div>';
        chartHost.querySelector(".state").textContent = "차트를 불러오지 못했습니다: " + e.message;
    }
}

/**
 * 캔들차트를 그린다.
 *
 * ★ 색 규칙
 * 오른 날(양봉)은 빨강, 내린 날(음봉)은 파랑. 국내 증권사 관습이다.
 * 미국과 반대라 헷갈릴 수 있지만, 이 앱은 한국에서 쓰는 앱이다.
 *
 * ★ 라이브러리 버전 주의
 * v5 에서 addCandlestickSeries() 가 없어지고 addSeries(타입, 옵션) 으로 통일됐다.
 * v4 문법을 쓰면 바로 터진다.
 */
function drawChart(data) {
    const LC = window.LightweightCharts;
    // 모드가 바뀌어 다시 그릴 때는 이전 차트를 먼저 치운다. 안 치우면 캔버스가 쌓인다
    if (chartResizeObserver) { chartResizeObserver.disconnect(); chartResizeObserver = null; }
    if (chart) { chart.remove(); chart = null; }
    maSeries = {};
    lastChartData = data;
    chartHost.innerHTML = "";

    const isUsd = data.currency === "USD";

    // 색은 CSS 변수에서 읽는다. 여기에 직접 적으면 어두운 화면에서 차트만 하얗게 남는다
    const dark = isDarkMode();
    const bg = cssVar("--bg", "#ffffff");
    const grid = cssVar("--rule", "#f2f4f6");
    const border = cssVar("--rule-2", "#e5e8eb");
    const axisInk = cssVar("--ink-3", "#8b95a1");
    const rise = cssVar("--rise", "#e03131");
    const fall = cssVar("--fall", "#1b64da");

    chart = LC.createChart(chartHost, {
        width: chartHost.clientWidth,
        height: chartHost.clientHeight,
        layout: { background: { color: bg }, textColor: axisInk, fontSize: 11,
                  fontFamily: getComputedStyle(document.body).fontFamily },
        grid: { vertLines: { color: grid }, horzLines: { color: grid } },
        // 기본 여백이 넉넉해서 축이 0 까지 내려간다. SOXL 처럼 저점이 높은 종목은
        // 화면 아래 절반이 빈 채로 남는다. 캔들이 차지하는 면적을 늘린다.
        rightPriceScale: { borderColor: border, scaleMargins: { top: 0.08, bottom: 0.08 } },
        timeScale: { borderColor: border, timeVisible: false },
        crosshair: { mode: LC.CrosshairMode ? LC.CrosshairMode.Normal : 0 },
        localization: {
            priceFormatter: p => isUsd ? "$" + p.toFixed(2) : Math.round(p).toLocaleString("ko-KR")
        }
    });

    candleSeries = chart.addSeries(LC.CandlestickSeries, {
        upColor: rise, downColor: fall,
        borderUpColor: rise, borderDownColor: fall,
        wickUpColor: rise, wickDownColor: fall,
        priceFormat: { type: "price", precision: isUsd ? 2 : 0, minMove: isUsd ? 0.01 : 1 }
    });

    candleSeries.setData(data.points.map(p => ({
        time: p.date,
        open: Number(p.open), high: Number(p.high),
        low: Number(p.low), close: Number(p.close)
    })));

    // 이동평균선
    (data.movingAverages || []).forEach(ma => {
        const style = MA_STYLE[ma.period];
        if (!style || !ma.values || ma.values.length === 0) return;

        const line = chart.addSeries(LC.LineSeries, {
            // 어두운 화면에서는 같은 색이 바탕에 묻힌다. 밝기를 올린 짝을 따로 둔다
            color: dark ? style.dark : style.color, lineWidth: 2,
            priceLineVisible: false, lastValueVisible: false, crosshairMarkerVisible: false
        });
        line.setData(ma.values.map(v => ({ time: v.date, value: Number(v.value) })));
        maSeries[ma.period] = line;
    });

    renderChartLegend(data, dark);

    /*
     * 보이는 구간을 마지막 80봉으로 맞춘다.
     *
     * fitContent() 로 200봉을 전부 펼치면 120일선이 화면 한가운데서 시작해 잘린 선으로 보인다.
     * 데이터는 200봉 다 들어 있으므로 왼쪽으로 끌면 계속 나온다.
     *
     * 지금은 숨겨진 탭이라 폭이 0일 수 있다. 그러면 이 계산이 헛돌기 때문에
     * fitChart() 가 진짜 폭이 생겼을 때 한 번 더 잡아준다.
     */
    chartSized = chartHost.clientWidth > 0;
    applyChartWindow();

    // 드로어가 열리거나 화면이 돌아가면 폭이 바뀐다
    chartResizeObserver = new ResizeObserver(() => fitChart());
    chartResizeObserver.observe(chartHost);

    /*
     * 요약 한 줄.
     *
     * ★ 화면에 보이는 구간만 말한다.
     * 200봉 전체로 계산하면 "+182%" 라고 써 놓고 화면에는 내리는 구간만 보이는 일이 생긴다.
     * 숫자와 그림이 어긋나면 둘 다 안 믿게 된다.
     */
    const shown = data.points.slice(-CHART_VISIBLE_BARS);
    const closes = shown.map(p => Number(p.close));
    const first = closes[0], last = closes[closes.length - 1];
    const change = ((last - first) / first) * 100;
    chartSub.innerHTML = `보이는 ${closes.length}거래일 `
        + `<span class="${change >= 0 ? "up" : "down"}">${formatRate(change)}</span> · `
        + `최고 ${formatPrice(Math.max(...closes), data.currency)} · `
        + `최저 ${formatPrice(Math.min(...closes), data.currency)}`
        + `<span class="chart-hint">왼쪽으로 끌면 ${data.points.length}거래일까지 나옵니다</span>`;
}

/** 이평선 범례. 누르면 해당 선을 켜고 끈다 (좁은 화면에서 선 4개는 뻑뻑하다) */
function renderChartLegend(data, dark) {
    const periods = (data.movingAverages || [])
        .filter(ma => ma.values && ma.values.length > 0)
        .map(ma => ma.period);

    if (periods.length === 0) { chartLegend.innerHTML = ""; return; }

    chartLegend.innerHTML = periods.map(p => {
        const color = dark ? MA_STYLE[p].dark : MA_STYLE[p].color;
        return `<button data-ma="${p}"><span class="dot" style="background:${color}"></span>${MA_STYLE[p].label}</button>`;
    }).join("");

    chartLegend.querySelectorAll("button").forEach(btn => {
        btn.addEventListener("click", () => {
            const period = btn.dataset.ma;
            const line = maSeries[period];
            if (!line) return;
            const nowOff = btn.classList.toggle("off");
            line.applyOptions({ visible: !nowOff });
        });
    });
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

/** ★ 여기서만 돈이 나간다. 그래서 버튼을 눌러야 실행된다 */
async function startAnalysis(symbol, refresh) {
    analyzeBtn.disabled = true;
    analyzeBtn.textContent = "시작하는 중...";
    analysisStartedAt = Date.now();

    try {
        const url = "/api/analysis/" + encodeURIComponent(symbol) + (refresh ? "?refresh=true" : "");
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
        analyzeBtn.textContent = "기업분석 실행";
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
                analysisBody.innerHTML = `<div class="state"><div class="spinner"></div>
                    클로드가 웹 검색으로 조사하고 있습니다 (${elapsed}초)<br>
                    보통 2~5분 걸립니다. 다른 종목을 봐도 분석은 계속됩니다.</div>`;
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

    if (data.status === "NONE") {
        analysisMeta.textContent = "아직 분석하지 않았습니다";
        analyzeBtn.textContent = "기업분석 실행";
        analyzeBtn.onclick = () => startAnalysis(data.symbol, false);
        analysisBody.innerHTML = '<div class="state">버튼을 누르면 클로드가 웹 검색으로 재무·지표·컨센서스·사업구조를 조사합니다.<br>'
            + '2~5분 걸리고 비용이 들기 때문에 자동으로 실행하지 않습니다.</div>';
        return;
    }

    if (data.status === "RUNNING") {
        analysisMeta.textContent = "분석 중";
        analyzeBtn.style.display = "none";
        analysisBody.innerHTML = '<div class="state"><div class="spinner"></div>클로드가 웹 검색으로 조사하고 있습니다.<br>보통 2~5분 걸립니다.</div>';
        return;
    }

    analyzeBtn.textContent = "다시 분석";
    analyzeBtn.onclick = () => startAnalysis(data.symbol, true);

    let html = "";

    if (data.status === "ERROR") {
        if (!data.analysis) {
            analysisMeta.textContent = "분석 실패";
            analysisBody.innerHTML = '<div class="state err"></div>';
            analysisBody.querySelector(".state").textContent = data.lastError || "알 수 없는 오류";
            return;
        }
        html += '<div class="fail-banner">' + escapeHtml("최근 재분석이 실패했습니다: " + (data.lastError || "")) + "</div>";
    }
    if (data.stale) {
        html += `<div class="stale-banner">${data.ageDays}일 전 분석입니다. 그 사이 실적 발표가 있었다면 다시 분석하세요.</div>`;
    }

    const a = data.analysis;
    if (!a) { analysisBody.innerHTML = html + '<div class="state">표시할 분석이 없습니다.</div>'; return; }

    const meta = [];
    if (data.analyzedAt) meta.push(data.analyzedAt.replace("T", " ").substring(0, 16));
    if (data.model) meta.push(data.model);
    if (a.instrumentType) meta.push(a.instrumentType === "ETF" ? "ETF·펀드형" : "개별 기업");
    if (data.webSearchCount) meta.push("웹검색 " + data.webSearchCount + "회");
    analysisMeta.textContent = meta.join(" · ");

    /*
     * 읽는 순서.
     *
     * 판정 → 한 줄 요약 → 내 자리(평단가 비교) → 감당 중인 위험 → 회사 조사
     *
     * ★ 재무제표부터 읽게 하면 안 된다.
     * 이 화면을 여는 이유는 "내가 지금 어떤 상태인가" 라서, 결론과 내 자리가 먼저 와야 한다.
     * 회사 자체에 대한 조사는 그 근거라서 뒤에 둔다.
     */
    const sections = a.sections || [];
    const position = sections.find(s => s.key === "POSITION_REVIEW");
    const rest = sections.filter(s => s.key !== "POSITION_REVIEW");

    html += renderVerdict(a.verdict);
    if (a.oneLineSummary) html += '<div class="summary-line">' + emphasizeNumbers(escapeHtml(a.oneLineSummary)) + "</div>";
    if (position) html += renderSection(position);
    html += renderRisks(a.risks);
    rest.forEach(s => { html += renderSection(s); });
    html += '<div class="disclaimer">' + escapeHtml(data.disclaimer || "") + "</div>";

    analysisBody.innerHTML = html;
    bindPeriodToggles();
}

/**
 * 지표 표의 분기/연도 전환.
 *
 * 표를 다시 만들지 않고 보이기만 바꾼다. 다시 만들면 스크롤 위치가 튄다.
 */
function bindPeriodToggles() {
    analysisBody.querySelectorAll(".period-toggle button").forEach(btn => {
        btn.addEventListener("click", () => {
            const box = btn.closest(".metric-box");
            const want = btn.dataset.period;

            box.querySelectorAll(".period-toggle button")
               .forEach(b => b.classList.toggle("on", b === btn));
            box.querySelectorAll("table.metrics")
               .forEach(t => t.hidden = t.dataset.period !== want);
        });
    });
}

/**
 * 판정.
 *
 * ★ 이 화면에서 제일 먼저 읽히는 자리다.
 * 종목에 대한 일반 의견이 아니라 "지금 내 보유 상태에서" 어떠냐는 답이다.
 * 같은 종목이라도 비중과 평단가가 다르면 다른 답이 나온다. 그게 이 앱만 할 수 있는 일이다.
 *
 * 색은 여기서도 등락 규칙을 따른다. 더 담아도 되면 빨강, 줄일 상태면 파랑.
 * 한국 증시에서 빨강은 오름이라 "늘리는 쪽" 과 뜻이 맞는다.
 */
const STANCE = {
    ADD:     { label: "더 담아도",   tone: "up",    mark: "▲" },
    HOLD:    { label: "그대로",      tone: "flat",  mark: "―" },
    TRIM:    { label: "줄이는 쪽",   tone: "down",  mark: "▼" },
    EXIT:    { label: "정리 검토",   tone: "down",  mark: "▼" },
    UNCLEAR: { label: "판단 보류",   tone: "muted", mark: "?" }
};

/** basis 코드를 사람 말로. 무엇에 기대 판정했는지 보여준다 */
const BASIS_LABEL = {
    NEWS: "최신 뉴스",
    VALUATION: "밸류에이션",
    MACRO: "거시 경제",
    INDUSTRY: "산업 분석",
    PRICE: "현재 가격",
    CONCENTRATION: "내 비중"
};

function renderVerdict(verdict) {
    if (!verdict || !verdict.headline) return "";

    const s = STANCE[verdict.stance] || STANCE.UNCLEAR;

    const chips = (verdict.basis || [])
        .map(b => BASIS_LABEL[b])
        .filter(Boolean)
        .map(label => `<span class="basis">${escapeHtml(label)}</span>`)
        .join("");

    return `<div class="verdict ${s.tone}">
        <div class="v-stance"><span class="v-mark">${s.mark}</span>${escapeHtml(s.label)}</div>
        <div class="v-headline">${escapeHtml(verdict.headline)}</div>
        <div class="v-reason">${prose(verdict.reason || "")}</div>
        ${chips ? `<div class="v-basis">${chips}</div>` : ""}
    </div>`;
}

function renderSection(section) {
    const na = !section.applicable;
    const position = section.key === "POSITION_REVIEW" ? " position" : "";

    let html = `<div class="sec${na ? " na" : ""}${position}"><h3>${escapeHtml(section.title)}`;
    if (na && section.notApplicableReason) {
        html += '<span class="na-badge">' + escapeHtml(section.notApplicableReason) + "</span>";
    }
    html += "</h3>";

    if (section.body) html += '<div class="body">' + prose(section.body) + "</div>";
    if (section.bullets && section.bullets.length > 0) {
        // 목록은 이미 짧게 끊겨 있으니 리드를 따로 두지 않는다. 숫자만 강조한다.
        html += "<ul>" + section.bullets
            .map(b => "<li>" + emphasizeNumbers(escapeHtml(b)) + "</li>").join("") + "</ul>";
    }
    html += renderMetrics(section.metrics) + renderSources(section.sources) + "</div>";
    return html;
}

/**
 * 지표 표.
 *
 * ★ 기간이 열이 된다.
 * 값 하나만 보여주면 그 숫자가 좋아지는 중인지 나빠지는 중인지 알 수 없다.
 * 기간을 가로로 늘어놓으면 추이가 눈에 들어오고, 기준 시점을 따로 적을 필요도 없어진다.
 * (예전에는 "기준" 열이 따로 있었다. 열 머리글이 곧 기준이라 지웠다)
 *
 * 분기와 연도를 둘 다 가진 지표가 있으면 위에 전환 버튼이 붙는다.
 */
function renderMetrics(metrics) {
    if (!metrics || metrics.length === 0) return "";

    const quarter = pivotMetrics(metrics, "QUARTER");
    const annual = pivotMetrics(metrics, "ANNUAL");
    const point = pivotMetrics(metrics, "POINT");

    const tabs = [];
    if (quarter) tabs.push({ key: "QUARTER", label: "분기", table: quarter });
    if (annual) tabs.push({ key: "ANNUAL", label: "연도", table: annual });
    if (tabs.length === 0 && !point) return "";

    let html = '<div class="metric-box">';

    if (tabs.length > 1) {
        html += '<div class="period-toggle">' + tabs.map((t, i) =>
            `<button data-period="${t.key}"${i === 0 ? ' class="on"' : ""}>${t.label}</button>`
        ).join("") + "</div>";
    }

    tabs.forEach((t, i) => {
        html += t.table.replace("<table class=\"metrics\"",
            `<table class="metrics" data-period="${t.key}"${i === 0 ? "" : " hidden"}`);
    });

    // 기간 개념이 없는 값(총보수, 순자산총액 등)은 전환과 무관하게 항상 보인다
    if (point) html += point;

    return html + "</div>";
}

/** 같은 기간 유형의 값만 모아 피벗한다. 행은 지표, 열은 기간 */
function pivotMetrics(metrics, periodType) {
    const rows = metrics
        .map(m => ({
            label: m.label,
            note: m.note,
            points: (m.points || []).filter(p => p.periodType === periodType)
        }))
        .filter(r => r.points.length > 0);

    if (rows.length === 0) return null;

    /*
     * 시점값은 피벗하지 않는다.
     * 총보수와 순자산총액처럼 기간 개념이 없는 값들은 각자 기준일이 달라서
     * 열로 늘어놓으면 빈 칸투성이 표가 된다. 기준일은 값 옆에 작게 붙인다.
     */
    if (periodType === "POINT") {
        let html = '<table class="metrics"><tr><th>항목</th>'
            + '<th class="num">값</th><th>해석</th></tr>';
        rows.forEach(r => {
            const hit = r.points[0];
            const unknown = !hit.value || hit.value === "모름";
            const fn = hit.sourceIndex >= 0 ? `<span class="fn">${hit.sourceIndex + 1}</span>` : "";
            // 기준일은 값 왼쪽에 둔다. 값이 오른쪽 정렬이라 이래야 숫자가 열 끝에 줄을 선다.
            // 값 아래에 두면 짧은 값 한 줄 때문에 줄 높이가 두 배가 된다.
            html += "<tr><td>" + escapeHtml(r.label) + "</td>"
                + `<td class="val${unknown ? " unknown" : ""}">`
                + (hit.period ? `<span class="as-of">${escapeHtml(hit.period)}</span>` : "")
                + `${escapeHtml(hit.value)}${fn}`
                + "</td><td>" + escapeHtml(r.note) + "</td></tr>";
        });
        return html + "</table>";
    }

    // 등장하는 기간을 모은다. 모델이 최신순으로 주므로 처음 나온 순서를 그대로 쓴다
    const periods = [];
    rows.forEach(r => r.points.forEach(p => {
        if (!periods.includes(p.period)) periods.push(p.period);
    }));
    const shown = periods.slice(0, 4);   // 너무 많으면 표가 옆으로 넘친다

    let html = '<table class="metrics"><tr><th>항목</th>'
        + shown.map(p => `<th class="num">${escapeHtml(p)}</th>`).join("")
        + "<th>해석</th></tr>";

    rows.forEach(r => {
        html += "<tr><td>" + escapeHtml(r.label) + "</td>";
        shown.forEach(period => {
            const hit = r.points.find(p => p.period === period);
            if (!hit) {
                html += '<td class="val unknown">—</td>';   // 그 기간 값이 없는 지표
                return;
            }
            const unknown = !hit.value || hit.value === "모름";
            const fn = hit.sourceIndex >= 0 ? `<span class="fn">${hit.sourceIndex + 1}</span>` : "";
            html += `<td class="val${unknown ? " unknown" : ""}">${escapeHtml(hit.value)}${fn}</td>`;
        });
        html += "<td>" + escapeHtml(r.note) + "</td></tr>";
    });

    return html + "</table>";
}

function renderSources(sources) {
    if (!sources || sources.length === 0) return "";
    let html = '<div class="srcs">';
    sources.forEach((s, i) => {
        const url = safeUrl(s.url);
        const label = escapeHtml(`${i + 1}. ${s.publisher || ""} ${s.title || ""}`)
            + (s.publishedAt && s.publishedAt !== "모름" ? " (" + escapeHtml(s.publishedAt) + ")" : "");
        html += "<div>" + (url ? `<a href="${escapeHtml(url)}" target="_blank" rel="noopener noreferrer">${label}</a>` : label) + "</div>";
    });
    return html + "</div>";
}

function renderRisks(risks) {
    if (!risks || risks.length === 0) return "";
    let html = '<div class="risk-box"><h3>감당하고 있는 리스크</h3>';
    risks.forEach(r => {
        const sev = ["HIGH", "MEDIUM", "LOW"].includes(r.severity) ? r.severity : "MEDIUM";
        html += `<div class="risk ${sev}"><div class="rtitle">${escapeHtml(r.title)}<span class="rsev">${sev}</span></div>`
            + '<div class="rdetail">' + prose(r.detail) + "</div></div>";
    });
    return html + "</div>";
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

loadAll();

/*
 * 공개 화면 공용 도우미. 공개 화면은 GET 만 부르므로 CSRF 토큰이 필요 없다.
 * 바깥에서 온 글자(회사 이름 등)는 항상 esc() 를 거쳐 넣는다.
 */

function esc(text) {
    if (text === null || text === undefined) return "";
    return String(text)
        .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}

async function getJson(url) {
    const res = await fetch(url, { headers: { "Accept": "application/json" } });
    if (res.status === 404) return null;
    if (!res.ok) throw new Error("서버 응답 " + res.status);
    return res.json();
}

/**
 * 달러 금액을 한국어로. 13F 금액은 크다(버크셔 약 3천억 달러).
 * "$299.3B" 보다 "2,993억 달러" 가 한국 사람에게 바로 읽힌다.
 */
function usd(value) {
    if (value === null || value === undefined) return "-";
    const eok = value / 1e8;
    if (eok >= 1) return Math.round(eok).toLocaleString("ko-KR") + "억 달러";
    const man = value / 1e4;
    return Math.round(man).toLocaleString("ko-KR") + "만 달러";
}

function pct(value, digits = 2) {
    if (value === null || value === undefined) return "-";
    return Number(value).toFixed(digits) + "%";
}

function signedPct(value) {
    if (value === null || value === undefined) return "";
    const n = Number(value);
    return (n > 0 ? "+" : "") + n.toFixed(1) + "%";
}

function count(n) {
    return Number(n || 0).toLocaleString("ko-KR");
}

/** 2026-06-30 → "2026년 2분기" */
function quarter(dateText) {
    if (!dateText) return "-";
    const [y, m] = dateText.split("-").map(Number);
    return y + "년 " + Math.ceil(m / 3) + "분기";
}

/** 종목 표시: 티커가 있으면 굵게, 회사 이름은 아래 작게 */
function security(ticker, name) {
    const t = ticker ? `<span class="ticker">${esc(ticker)}</span>` : `<span class="muted">티커 없음</span>`;
    return `${t}<span class="sub">${esc(name)}</span>`;
}

/**
 * 변화 종류. 증가는 빨강, 감소는 파랑 — 이 앱에서 두 색은 항상 "늘었다/줄었다" 만 뜻한다.
 * 분할은 색 없이. 매수·매도가 아니다.
 */
const KIND = {
    NEW: { label: "새로 삼", cls: "rise" },
    ADDED: { label: "늘림", cls: "rise" },
    REDUCED: { label: "줄임", cls: "fall" },
    SOLD_OUT: { label: "다 팖", cls: "fall" },
    SPLIT: { label: "분할 추정", cls: "muted" },
    UNCHANGED: { label: "그대로", cls: "muted" }
};

function kindTag(kind) {
    const k = KIND[kind] || { label: kind, cls: "muted" };
    return `<span class="kind ${k.cls}">${k.label}</span>`;
}

function weightBar(weight) {
    const w = Math.max(0, Math.min(100, Number(weight || 0)));
    return `<span class="bar" aria-hidden="true"><i style="width:${w}%"></i></span>`;
}

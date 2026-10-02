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

/**
 * 종목 표시: 티커가 있으면 굵게, 회사 이름은 아래 작게.
 * 티커는 버튼이다. 누르면 stock.js 의 기업분석 창이 열린다. 버튼이라 키보드(Tab·Enter)로도 열린다
 */
function security(ticker, name) {
    const t = ticker ? stockLink(ticker) : `<span class="muted">티커 없음</span>`;
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

/*
 * 비중 트리맵. 칸 크기가 비중, 칸의 명도가 순위다(색은 등락 전용). 첫 화면과 기관 상세가 같이 쓴다.
 * rows 는 /holdings 의 holdings 배열. 위 여섯 종목과 "그 외" 한 칸으로 그린다.
 * 칸 위치는 % 로 넣는다. 화면 폭이 바뀌어도 다시 그릴 필요가 없다(좁은 화면 대응은 CSS 로만. CLAUDE.md).
 */
function drawTreemap(box, rows, name) {
    const stocks = rows.filter(r => !r.putCall && Number(r.weightPercent) > 0);
    const top = stocks.slice(0, 6);
    if (top.length === 0) return false;
    const shown = top.reduce((s, r) => s + Number(r.weightPercent), 0);
    const items = top.map((r, i) => ({ label: r.ticker || r.issuerName, pct: Number(r.weightPercent), cls: "t" + (i + 1) }));
    const restCount = rows.length - top.length;
    if (restCount > 0 && 100 - shown > 0.5) items.push({ label: `그 외 ${restCount}`, pct: 100 - shown, cls: "t6", rest: true });

    // 실제 그림 비율(약 1.8:1)로 계산해야 칸이 정사각형에 가깝게 나온다
    const W = 180, H = 100;
    // 비중은 pct 로 둔다. squarify 가 칸 너비를 w 로 쓰므로 같은 이름이면 덮인다
    const total = items.reduce((s, it) => s + it.pct, 0);
    const cells = squarify(items.map(it => ({ ...it, area: it.pct / total * W * H })), 0, 0, W, H);

    box.setAttribute("role", "img");
    box.setAttribute("aria-label", `${name} 비중: ` + items.map(it => `${it.label} ${it.pct.toFixed(1)}%`).join(", "));
    box.innerHTML = cells.map(c => {
        const wPct = c.w / W * 100, hPct = c.h / H * 100;
        // 좁은 칸에 글자를 다 넣으면 겹친다. 크기에 따라 줄인다
        const roomy = wPct > 16 && hPct > 34;
        const fits = wPct > 9 && hPct > 18;
        const text = !fits ? "" : roomy
            ? `<b>${esc(c.label)}</b><span>${c.pct.toFixed(1)}%</span>`
            : `<span>${esc(c.label)}${c.rest ? "" : " " + c.pct.toFixed(1)}</span>`;
        // 인라인 style 속성은 CSP 의 style-src 'unsafe-inline' 으로 허용돼 있다(스크립트가 아니다)
        return `<div class="cell ${c.cls}" aria-hidden="true" style="left:${(c.x / W * 100).toFixed(3)}%;top:${(c.y / H * 100).toFixed(3)}%;`
            + `width:${wPct.toFixed(3)}%;height:${hPct.toFixed(3)}%">${text}</div>`;
    }).join("");
    return true;
}

/*
 * 스퀘어리파이 트리맵(Bruls 외, 2000). 큰 값부터 짧은 변을 따라 한 줄씩 채우고,
 * 다음 칸을 넣었을 때 줄 안 칸들의 가로세로비가 나빠지면 줄을 닫는다.
 * 한 방향으로만 자르면 작은 칸이 실처럼 가늘어져 글자를 못 넣는다.
 */
function squarify(items, x, y, w, h) {
    const out = [];
    let row = [];
    const rest = items.slice();
    const worst = (r, side) => {
        const s = r.reduce((a, b) => a + b.area, 0);
        const max = Math.max(...r.map(i => i.area)), min = Math.min(...r.map(i => i.area));
        return Math.max(side * side * max / (s * s), (s * s) / (side * side * min));
    };
    const place = () => {
        const s = row.reduce((a, b) => a + b.area, 0);
        if (w >= h) {
            const cw = s / h;
            let cy = y;
            row.forEach(it => { const ch = it.area / cw; out.push({ ...it, x, y: cy, w: cw, h: ch }); cy += ch; });
            x += cw; w -= cw;
        } else {
            const rh = s / w;
            let cx = x;
            row.forEach(it => { const cw = it.area / rh; out.push({ ...it, x: cx, y, w: cw, h: rh }); cx += cw; });
            y += rh; h -= rh;
        }
        row = [];
    };
    while (rest.length) {
        const side = Math.min(w, h);
        if (row.length === 0 || worst(row.concat(rest[0]), side) <= worst(row, side)) row.push(rest.shift());
        else place();
    }
    if (row.length) place();
    return out;
}

/** 기업분석 창을 여는 티커 버튼 */
function stockLink(ticker, cls = "ticker") {
    return `<button type="button" class="${cls} stock-link" data-stock="${esc(ticker)}">${esc(ticker)}</button>`;
}

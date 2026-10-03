/*
 * AI 기업분석을 그리는 코드. 나의 포트폴리오(portfolio.js)와 공개 기업분석 화면(stock.js)이 같이 쓴다.
 * 두 화면에 따로 두면 판정 표시나 지표 표가 반드시 어긋난다. 그래서 여기 한 곳에 둔다.
 * 함수는 화면 조각(HTML 문자열)만 만들고, 어디에 넣을지·버튼은 각 화면이 정한다.
 * 스타일은 analysis.css.
 */


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

/**
 * 분석 응답 하나를 화면 조각으로. 나의 포트폴리오 드로어와 기업분석 화면이 같이 쓴다.
 * 버튼(분석 실행·다시 분석)은 화면마다 사정이 달라 여기서 만들지 않는다.
 *
 * @returns {{html: string, meta: string|null}} meta 는 머리줄(시각·모델·웹검색 수). 바꿀 게 없으면 null
 */
function analysisContentHtml(data) {
    let html = "";
    if (data.status === "ERROR") {
        if (!data.analysis) {
            return { html: '<div class="state err">' + escapeHtml(data.lastError || "알 수 없는 오류") + "</div>", meta: "분석 실패" };
        }
        html += '<div class="fail-banner">' + escapeHtml("최근 재분석이 실패했습니다: " + (data.lastError || "")) + "</div>";
    }
    if (data.stale) {
        html += `<div class="stale-banner">${data.ageDays}일 전 분석입니다. 그 사이 실적 발표가 있었다면 다시 분석하세요.</div>`;
    }

    const a = data.analysis;
    if (!a) return { html: html + '<div class="state">표시할 분석이 없습니다.</div>', meta: null };

    const meta = [];
    if (data.analyzedAt) meta.push(data.analyzedAt.replace("T", " ").substring(0, 16));
    if (data.model) meta.push(data.model);
    if (a.instrumentType) meta.push(a.instrumentType === "ETF" ? "ETF·펀드형" : "개별 기업");
    if (data.webSearchCount) meta.push("웹검색 " + data.webSearchCount + "회");
    // 안 가진 종목은 평단가 없이 현재가만 본 분석이다. 판정의 뜻이 "새로 담을 만한가" 로 바뀌므로 밝혀 둔다
    if (data.includesPosition === false) meta.push("보유하지 않은 종목 · 현재가 기준");

    /*
     * 읽는 순서.
     *
     * 판정 → 한 줄 요약 → 내 자리(평단가 비교) → 감당 중인 위험 → 회사 조사
     *
     * ★ 재무제표부터 읽게 하면 안 된다.
     * 이 화면을 여는 이유는 "내가 지금 어떤 상태인가" 라서, 결론과 내 자리가 먼저 와야 한다.
     * 회사 자체에 대한 조사는 그 근거라서 뒤에 둔다.
     * 안 가진 종목은 "내 자리" 칸이 비어 있어(해당 없음) 아예 그리지 않는다.
     */
    const sections = a.sections || [];
    const position = sections.find(s => s.key === "POSITION_REVIEW");
    const rest = sections.filter(s => s.key !== "POSITION_REVIEW");

    html += renderVerdict(a.verdict);
    if (a.oneLineSummary) html += '<div class="summary-line">' + emphasizeNumbers(escapeHtml(a.oneLineSummary)) + "</div>";
    if (position && data.includesPosition !== false) html += renderSection(position);
    html += renderRisks(a.risks);
    rest.forEach(s => { html += renderSection(s); });
    html += '<div class="disclaimer">' + escapeHtml(data.disclaimer || "") + "</div>";
    return { html, meta: meta.join(" · ") };
}

/**
 * 지표 표의 분기/연도 전환.
 *
 * 표를 다시 만들지 않고 보이기만 바꾼다. 다시 만들면 스크롤 위치가 튄다.
 */
function bindPeriodToggles(root) {
    root.querySelectorAll(".period-toggle button").forEach(btn => {
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

    // 좁은 화면에서 표가 옆으로 넘치면 이 상자가 가로로 스크롤된다. 키보드로도 스크롤하려면 초점을 받아야 한다
    // (role=region 은 달지 않는다. 한 화면에 표가 여럿이라 같은 이름의 랜드마크가 겹친다)
    let html = '<div class="metric-box" tabindex="0">';

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

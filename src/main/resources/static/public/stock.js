/*
 * 공개 종목 창(기업분석). 공개 화면 어디서든 data-stock="MSFT" 가 달린 것을 누르면 오른쪽에서 열린다.
 * 내용은 /api/public/stocks/{티커} 하나로 받는다: 공시 재무, 앱이 규칙으로 만든 "읽을 점", 기관 10곳의 움직임.
 * 맨 위 "AI 기업분석" 칸은 나의 포트폴리오의 기업분석과 같은 것이다(그리는 코드도 analysis-view.js 로 같다).
 *   - 로그인한 나: 전부 보이고 실행·다시 분석 버튼이 있다. 가진 종목은 평단가 기준, 안 가진 종목은 현재가만으로 분석된다
 *   - 방문자: 현재가만으로 분석한 것만 보인다. 평단가가 들어간 분석은 서버가 "없음" 으로 답한다
 * 그 아래 칸들(읽을 점·공시 재무·기관)은 0원 사실이다. site.js 의 esc·usd·pct·quarter·kindTag 를 쓴다.
 *
 * 주소에 #stock=MSFT 를 남긴다. 링크를 보내면 받은 사람도 같은 창이 열린 채로 본다.
 */
(function () {
    // aside 에 role=dialog 를 달면 접근성 규칙 위반이다(landmark 와 dialog 가 겹친다). div 로 만든다
    const panel = document.createElement("div");
    panel.className = "stock-drawer";
    panel.setAttribute("role", "dialog");
    panel.setAttribute("aria-modal", "true");
    panel.setAttribute("aria-labelledby", "stockTitle");
    panel.inert = true;   // 닫힌 창은 Tab 으로도 못 들어가게 (aria-hidden 은 Tab 을 못 막는다)
    panel.innerHTML = `
        <div class="stock-head">
            <div>
                <span class="kicker">기업분석 · 공시와 13F 로 만든 사실</span>
                <h2 id="stockTitle">종목</h2>
                <p class="stock-name" id="stockName"></p>
            </div>
            <button type="button" class="stock-close" aria-label="닫기">×</button>
        </div>
        <div class="stock-body" id="stockBody"></div>`;
    const backdrop = document.createElement("div");
    backdrop.className = "stock-backdrop";
    document.body.append(backdrop, panel);

    const body = panel.querySelector("#stockBody");
    let opener = null;
    let current = null;
    // 아래 함수들보다 먼저 선언해야 한다. 기업분석 페이지는 이 파일이 읽히자마자 그리기를 시작하는데,
    // const/let 을 뒤에 두면 "초기화 전 접근" 으로 멈춘다(실제로 겪음)
    /** 칸(창 본문·페이지)마다 차트 하나. 다시 그리기 전에 치워야 캔버스가 쌓이지 않는다 */
    const charts = new Map();
    /** 차트 라이브러리를 받는 중이거나 받은 약속. 한 번만 받는다 */
    let chartLib = null;

    document.addEventListener("click", e => {
        const t = e.target.closest("[data-stock]");
        if (!t) return;
        e.preventDefault();
        open(t.dataset.stock, t);
    });
    panel.querySelector(".stock-close").addEventListener("click", close);
    backdrop.addEventListener("click", close);
    document.addEventListener("keydown", e => {
        if (e.key === "Escape" && document.body.classList.contains("stock-open")) close();
    });

    /*
     * 기업분석 페이지(company.html)에서는 창 대신 페이지 본문에 그린다. 주소는 ?t=MSFT.
     * 같은 render 를 쓰므로 창과 페이지의 내용이 어긋나지 않는다.
     */
    const page = document.getElementById("stockPage");
    if (page) {
        const t = (new URLSearchParams(location.search).get("t") || "").trim().toUpperCase();
        if (/^[A-Z][A-Z0-9.-]{0,9}$/.test(t)) showOnPage(t);
    }

    async function showOnPage(ticker) {
        document.getElementById("stockQuery").value = ticker;
        document.title = ticker + " 기업분석 | 포트폴리오 분석기";
        disposeChart(page);
        page.innerHTML = '<p class="loading">불러오는 중… 처음 여는 종목은 공시를 읽느라 몇 초 걸립니다.</p>';
        try {
            const d = await getJson("/api/public/stocks/" + encodeURIComponent(ticker));
            page.innerHTML = `<div class="stock-page-head"><h2>${esc(d.ticker)}</h2><p class="stock-name">${esc(d.name || "")}</p></div>`
                + build(d);
            loadChart(page, d.ticker);
            loadAi(page, d.ticker);
        } catch (e) {
            page.innerHTML = '<p class="error">불러오지 못했습니다. 티커가 맞는지 확인하고 잠시 뒤 다시 열어 주세요.</p>';
        }
    }

    // 티커로 찾기 칸. 표에 없는 종목(예: MSFT)도 바로 연다
    document.addEventListener("submit", e => {
        const form = e.target.closest("[data-stock-search]");
        if (!form) return;
        e.preventDefault();
        const t = form.querySelector("input").value.trim().toUpperCase();
        if (/^[A-Z][A-Z0-9.-]{0,9}$/.test(t) && page) {
            history.pushState(null, "", "?t=" + encodeURIComponent(t));
            showOnPage(t);
        } else if (/^[A-Z][A-Z0-9.-]{0,9}$/.test(t)) open(t, form.querySelector("input"));
        else form.querySelector("input").setCustomValidity("미국 티커를 넣어 주세요 (예: MSFT)"), form.reportValidity();
    });
    document.addEventListener("input", e => {
        if (e.target.closest("[data-stock-search]")) e.target.setCustomValidity("");
    });

    // 주소의 #stock=MSFT 로 열기. 받은 링크로 들어왔을 때와, 주소만 바꿨을 때 둘 다
    window.addEventListener("popstate", () => {
        if (!page) return;
        const t = (new URLSearchParams(location.search).get("t") || "").toUpperCase();
        if (t) showOnPage(t);
    });

    function openFromHash() {
        const m = /^#stock=([A-Za-z0-9.-]{1,10})$/.exec(location.hash);
        if (m && m[1].toUpperCase() !== current) open(m[1].toUpperCase(), null);
    }
    window.addEventListener("hashchange", openFromHash);
    openFromHash();

    async function open(ticker, from) {
        opener = from;
        current = ticker;
        panel.querySelector("#stockTitle").textContent = ticker;
        panel.querySelector("#stockName").textContent = "";
        disposeChart(body);
        body.innerHTML = '<p class="loading">불러오는 중… 처음 여는 종목은 공시를 읽느라 몇 초 걸립니다.</p>';
        panel.inert = false;
        document.body.classList.add("stock-open");
        history.replaceState(null, "", "#stock=" + encodeURIComponent(ticker));
        panel.querySelector(".stock-close").focus();
        try {
            const data = await getJson("/api/public/stocks/" + encodeURIComponent(ticker));
            if (current !== ticker) return;   // 그 사이 다른 종목을 눌렀다
            render(data);
        } catch (e) {
            if (current !== ticker) return;
            body.innerHTML = '<p class="error">불러오지 못했습니다. 잠시 뒤 다시 열어 주세요.</p>';
        }
    }

    function close() {
        document.body.classList.remove("stock-open");
        panel.inert = true;
        current = null;
        disposeChart(body);   // 닫힌 창 안에 차트(캔버스·크기 감시)가 남지 않게
        history.replaceState(null, "", location.pathname + location.search);
        if (opener && document.contains(opener)) opener.focus();
    }

    function render(d) {
        panel.querySelector("#stockName").textContent = d.name || "";
        body.innerHTML = build(d)
            + `<p class="stock-foot"><a href="/public/company.html?t=${encodeURIComponent(d.ticker)}">기업분석 페이지로 크게 보기</a></p>`;
        loadChart(body, d.ticker);
        loadAi(body, d.ticker);
    }

    // ── 차트 칸 (그리는 코드는 chart-view.js. 나의 포트폴리오와 같다) ──

    function disposeChart(root) {
        const c = charts.get(root);
        if (c) {
            c.dispose();
            charts.delete(root);
        }
    }

    /**
     * 차트 라이브러리는 차트를 처음 그릴 때만 받는다(약 160KB).
     * 공개 화면 대부분은 차트를 안 열어서, 모든 페이지에 미리 실으면 첫 화면만 느려진다
     */
    function ensureChartLib() {
        if (window.LightweightCharts) return Promise.resolve(true);
        if (!chartLib) {
            chartLib = new Promise(resolve => {
                const s = document.createElement("script");
                s.src = "https://cdn.jsdelivr.net/npm/lightweight-charts@5.2.1/dist/lightweight-charts.standalone.production.js";
                s.onload = () => resolve(true);
                s.onerror = () => { chartLib = null; resolve(false); };
                document.head.append(s);
            });
        }
        return chartLib;
    }

    async function loadChart(root, ticker) {
        const slot = root.querySelector(".chart-slot");
        if (!slot) return;
        const host = slot.querySelector(".stock-chart-host");
        const say = text => { host.innerHTML = `<p class="muted chart-msg">${esc(text)}</p>`; };
        if (!await ensureChartLib()) {
            say("차트 라이브러리를 불러오지 못했습니다. 인터넷 연결을 확인하고 새로고침해 주세요.");
            return;
        }
        let data;
        try {
            const res = await fetch("/api/public/chart/" + encodeURIComponent(ticker), { headers: { "Accept": "application/json" } });
            data = await res.json().catch(() => null);
            if (!res.ok) {
                say((data && data.error) || "차트를 불러오지 못했습니다.");
                return;
            }
        } catch (e) {
            say("차트를 불러오지 못했습니다.");
            return;
        }
        if (!document.contains(host)) return;   // 그 사이 다른 종목을 열었거나 창을 닫았다
        if (!data || !data.points || data.points.length < 2) {
            say("차트를 그릴 데이터가 부족합니다.");
            return;
        }
        disposeChart(root);
        host.innerHTML = "";
        const c = createStockChart({ host, legend: slot.querySelector(".chart-legend"), sub: slot.querySelector(".chart-sub") });
        charts.set(root, c);
        c.draw(data);
    }

    /** 창과 페이지가 같이 쓰는 본문. 차트·AI 칸은 비워 두고 loadChart·loadAi 가 따로 채운다 */
    function build(d) {
        let html = `<section class="analysis-view chart-slot"><h3>차트 <span class="muted">일봉 · 이동평균선</span></h3>
            <div class="chart-legend"></div>
            <div class="stock-chart-host"><p class="loading">차트 불러오는 중…</p></div>
            <div class="chart-sub"></div></section>`;
        html += `<section class="analysis-view ai-slot"><h3>AI 기업분석</h3>
            <div class="ai-body"><p class="loading">불러오는 중…</p></div></section>
            <h3 class="facts-title">공시와 13F 로 본 사실</h3>`;
        if (d.summary) html += `<p class="stock-summary">${esc(d.summary)}</p>`;

        if (d.notes && d.notes.length) {
            html += `<section><h3>읽을 점</h3><ul class="stock-notes">${d.notes.map(n => `
                <li class="${n.kind === "EARNINGS_QUALITY" || n.kind === "PRICE" ? "check" : ""}">
                    <b>${esc(n.title)}</b><span>${esc(n.text)}</span></li>`).join("")}</ul></section>`;
        }

        if (d.financials && d.financials.annual && d.financials.annual.length) {
            html += financialsTable(d.financials);
        } else {
            html += '<section><h3>공시 재무</h3><p class="muted">공시 재무를 찾지 못했습니다. ETF·펀드이거나 SEC 에 재무 태그가 없는 회사입니다.</p></section>';
        }

        html += institutionsTable(d.institutions);
        html += `<p class="stock-foot">사실을 정리했을 뿐 매수·매도를 권하지 않습니다.
            재무는 SEC 10-K 원본, 기관은 13F(분기말 기준, 최대 45일 늦음)입니다.
            ${(d.financials && d.financials.sources || []).map(s =>
                `<a href="${esc(s.url)}" target="_blank" rel="noopener noreferrer">${esc(s.title)}</a>`).join(" · ")}</p>`;
        return html;
    }

    /** 연도가 열, 지표가 행. 증감은 바로 앞 해 대비 */
    function financialsTable(f) {
        const years = f.annual.slice(-3);
        const head = years.map(p => `<th scope="col" class="r">${esc((p.label || "").split(" ")[0])}</th>`).join("");
        const row = (label, pick, fmt, growth) => `<tr><th scope="row">${label}</th>${years.map((p, i) => {
            const v = pick(p);
            const prev = i > 0 ? pick(years[i - 1]) : null;
            const g = growth && prev > 0 && v != null ? (v - prev) / prev * 100 : null;
            return `<td class="r num">${v == null ? "-" : fmt(v)}${g == null ? "" :
                `<span class="sub ${g >= 0 ? "rise" : "fall"}">${g >= 0 ? "+" : ""}${g.toFixed(1)}%</span>`}</td>`;
        }).join("")}</tr>`;
        const money = v => (v / 1e8).toLocaleString("ko-KR", { maximumFractionDigits: 0 }) + "억";
        const ratio = v => Number(v).toFixed(1) + "%";
        return `<section><h3>공시 재무 <span class="muted">${esc(f.currency || "")} · ${esc(f.statementKind || "")}</span></h3>
            <table class="stock-fin"><thead><tr><th scope="col"><span class="skip">지표</span></th>${head}</tr></thead><tbody>
            ${row("매출", p => p.revenue, money, true)}
            ${row("영업이익", p => p.operatingIncome, money, true)}
            ${row("영업이익률", p => p.operatingMargin, ratio, false)}
            ${row("순이익", p => p.netIncome, money, true)}
            ${row("EPS", p => p.eps, v => Number(v).toFixed(2), true)}
            ${row("ROE", p => p.roe, ratio, false)}
            ${row("부채비율", p => p.debtRatio, ratio, false)}
            </tbody></table></section>`;
    }

    function institutionsTable(m) {
        if (!m || !m.institutions || m.institutions.length === 0) return "";
        const held = m.institutions.filter(r => r.shares > 0);
        const rows = m.institutions.map(r => {
            const change = !r.kind ? '<span class="muted">비교 불가</span>'
                : r.kind === "UNCHANGED" ? '<span class="muted">그대로</span>'
                : kindTag(r.kind) + (r.sharesChangePercent != null && r.kind !== "NEW" && r.kind !== "SOLD_OUT"
                    ? ` <span class="num ${KIND[r.kind] ? KIND[r.kind].cls : ""}">${signedPct(r.sharesChangePercent)}</span>` : "");
            return `<tr><td><a href="/public/institution.html?cik=${r.cik}">${esc(r.nameKo)}</a>
                    <span class="sub">${quarter(r.period)}</span></td>
                <td class="r num">${r.weightPercent == null ? '<span class="muted">안 가짐</span>' : pct(r.weightPercent)}</td>
                <td class="r">${r.shares > 0 || r.kind === "SOLD_OUT" ? change : ""}</td></tr>`;
        }).join("");
        return `<section><h3>기관은 어떻게 움직였나</h3>
            <p class="muted">${m.institutions.length}곳 중 ${held.length}곳이 들고 있습니다. 비중은 각 기관 13F 합계 대비, 변화는 주식 수 기준 바로 앞 분기 대비입니다.</p>
            <table><thead><tr><th scope="col">기관</th><th scope="col" class="r">비중</th><th scope="col" class="r">변화</th></tr></thead>
            <tbody>${rows}</tbody></table></section>`;
    }

    // ── AI 기업분석 칸 ──

    /** 나(운영자)인가. /api/me 가 200 이면. 한 번만 묻는다. 응답 본문을 다 읽어야 연결이 안 남는다 */
    let ownerPromise = null;
    function isOwner() {
        if (!ownerPromise) {
            ownerPromise = fetch("/api/me", { headers: { "Accept": "application/json" } })
                .then(res => res.text().then(() => res.ok))
                .catch(() => false);
        }
        return ownerPromise;
    }

    async function loadAi(root, ticker) {
        const box = root.querySelector(".ai-body");
        if (!box) return;
        const owner = await isOwner();
        let data;
        try {
            // 나에게는 평단가가 들어간 분석까지 전부, 방문자에게는 공개해도 되는 것만
            data = await getJson((owner ? "/api/analysis/" : "/api/public/analysis/") + encodeURIComponent(ticker));
        } catch (e) {
            box.innerHTML = '<p class="muted">AI 기업분석을 불러오지 못했습니다.</p>';
            return;
        }
        if (document.contains(box)) paintAi(box, ticker, data, owner);
    }

    function paintAi(box, ticker, data, owner) {
        const run = (label, refresh) => !owner ? "" :
            `<p class="ai-actions"><button type="button" class="primary ai-run" data-refresh="${refresh}">${label}</button>
             <span class="muted">1회 800~1,600원 · 2~5분</span></p>`;
        if (!data || data.status === "NONE") {
            box.innerHTML = (owner
                ? '<p class="muted">아직 분석하지 않았습니다. 가진 종목이면 평단가 기준으로, 안 가진 종목이면 평단가 없이 현재가만으로 분석합니다.</p>'
                : '<p class="muted">아직 AI 기업분석이 없습니다. 아래는 공시와 13F 로 본 사실입니다.</p>') + run("AI 기업분석 실행", false);
        } else if (data.status === "RUNNING") {
            box.innerHTML = '<p class="loading">클로드가 웹 검색으로 조사하고 있습니다. 보통 2~5분 걸립니다. 닫아도 계속됩니다.</p>';
            // 끝날 때까지 8초마다 다시 묻는다. 창을 닫거나 다른 종목을 열면(칸이 사라지면) 멈춘다
            setTimeout(() => { if (document.contains(box)) loadAi(box.parentElement, ticker); }, 8000);
        } else {
            const c = analysisContentHtml(data);
            box.innerHTML = (c.meta ? `<p class="ai-meta">${esc(c.meta)}</p>` : "") + run("다시 분석", true) + c.html;
            bindPeriodToggles(box);
        }
        const btn = box.querySelector(".ai-run");
        if (btn) btn.addEventListener("click", () => runAi(box, ticker, btn.dataset.refresh === "true"));
    }

    /** 돈이 드는 호출. 나에게만 버튼이 보이고, 서버도 로그인 없이는 막는다(401) */
    async function runAi(box, ticker, refresh) {
        box.innerHTML = '<p class="loading">분석을 시작합니다…</p>';
        const headers = { "Accept": "application/json" };
        const token = readCookie("XSRF-TOKEN");   // POST 는 CSRF 토큰이 있어야 한다
        if (token) headers["X-XSRF-TOKEN"] = token;
        try {
            // 나의 포트폴리오가 쓰는 식별값. 직접 입력한 종목까지 "가진 종목" 으로 보려고
            const key = localStorage.getItem("portfolioOwnerKey");
            if (key) headers["X-Owner-Key"] = key;
        } catch (e) { /* 저장소를 못 쓰는 브라우저. 증권사 보유만으로 판단한다 */ }
        try {
            const res = await fetch("/api/analysis/" + encodeURIComponent(ticker) + (refresh ? "?refresh=true" : ""),
                { method: "POST", headers });
            const data = await res.json().catch(() => null);
            if (!res.ok) {
                box.innerHTML = `<p class="error">${esc((data && (data.error || data.message)) || "분석을 시작하지 못했습니다 (" + res.status + ")")}</p>`;
                return;
            }
            paintAi(box, ticker, data, true);
        } catch (e) {
            box.innerHTML = '<p class="error">분석을 시작하지 못했습니다.</p>';
        }
    }

    function readCookie(name) {
        const hit = document.cookie.split("; ").find(c => c.startsWith(name + "="));
        return hit ? decodeURIComponent(hit.substring(name.length + 1)) : null;
    }
})();

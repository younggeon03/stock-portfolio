// index.html 의 화면 코드. 보안 정책(CSP)이 인라인 스크립트를 막아서 파일로 뺐다.
// 첫 화면은 "브리핑": 한 줄 요약 → 대표 기관 그림 → 세 칸 요약 → 기관 목록 → 같이 산/판 종목 표.
// 요약 문장도 전부 자료로 만든다. HTML 에 숫자를 박아두면 분기가 바뀌는 날 틀린다.
(async function () {
    // 첫 화면에 그림으로 세울 기관. 한국 사람에게 가장 이름이 알려진 곳이다. 목록에 없으면 첫 기관으로 대신한다
    const FEATURED_CIK = 1067983;

    let list = [];
    const instBox = document.getElementById("institutions");
    try {
        list = await getJson("/api/institutions");
        instBox.className = "";
        instBox.innerHTML = list.length === 0 ? '<p class="empty">아직 받은 자료가 없습니다.</p>' : `
            <ul class="inst-grid" aria-labelledby="institutions-title">${list.map(i => `
                <li><a href="/public/institution.html?cik=${i.cik}">
                    <b>${esc(i.nameKo)}</b>
                    <span class="sub">${esc(i.manager || i.name)}</span>
                    <span class="stat">${i.holdingCount != null ? count(i.holdingCount) + "종목 · " + usd(i.totalValueUsd) : "아직 없음"}</span>
                    <span class="sub">${i.latestPeriod ? quarter(i.latestPeriod) + (i.filedDate ? " · " + esc(i.filedDate.slice(5)) + " 공개" : "") : ""}</span>
                </a></li>`).join("")}
            </ul>`;
    } catch (e) {
        instBox.className = "error";
        instBox.textContent = "기관 목록을 불러오지 못했습니다. 잠시 뒤 다시 열어 주세요.";
    }

    const featured = list.find(i => i.cik === FEATURED_CIK && i.latestPeriod) || list.find(i => i.latestPeriod);
    // 세 덩어리는 서로 기다릴 이유가 없다. 하나가 실패해도 나머지는 그린다
    const [holdings, changes, consensus] = await Promise.all([
        // 그림은 위 6종목만 쓴다. 큰 기관이면 수천 줄이라 20줄만 받는다(총 줄 수는 totalRows 로 온다)
        featured ? getJson(`/api/institutions/${featured.cik}/holdings?limit=20`).catch(() => null) : null,
        featured ? getJson(`/api/institutions/${featured.cik}/changes`).catch(() => null) : null,
        getJson("/api/institutions/consensus?limit=5").catch(e => e)
    ]);

    renderTreemap(holdings);
    const topChange = renderChangeBars(changes);
    const topBought = renderConsensus(consensus);
    renderHeadline(featured, topChange, topBought, consensus);

    // ── 한 줄 요약 ──
    function renderHeadline(inst, change, bought, c) {
        const period = c && c.period ? c.period : inst && inst.latestPeriod;
        if (period) document.getElementById("edition").textContent = quarter(period) + " 브리핑";
        if (!inst || !change || !bought) return;  // 자료가 모자라면 고정 제목을 그대로 둔다
        const did = change.kind === "NEW" ? "새로 샀고" : `${Math.round(change.sharesChangePercent)}% 늘렸고`;
        const h1 = document.getElementById("headline");
        h1.innerHTML = `${esc(inst.nameKo)}${topic(inst.nameKo)} ${esc(change.ticker)} 주식을 ${did},<br>`
            + `${bought.buyers.length}곳이 같이 ${esc(bought.ticker || bought.name)} 주식을 늘렸다`;
    }

    // ── 대표 기관 비중 트리맵 (그리기는 site.js) ──
    function renderTreemap(h) {
        if (!h || !h.holdings) return;
        if (!drawTreemap(document.getElementById("treemap"), h.holdings, h.institution.nameKo, h.totalRows)) return;
        document.getElementById("featuredCaption").innerHTML =
            `<a href="/public/institution.html?cik=${h.institution.cik}">${esc(h.institution.nameKo)}</a> · 비중 · ${quarter(h.period)}`;
        document.getElementById("featured").hidden = false;
    }

    // ── 대표 기관의 큰 변화 막대 ──
    function renderChangeBars(c) {
        if (!c || !c.changes) return null;
        // 같은 회사의 다른 주식(GOOGL·GOOG)은 한 번만. 앞에 온 것이 금액이 큰 쪽이다
        const seen = new Set();
        const rows = c.changes.filter(r => {
            if (!r.ticker || !["ADDED", "REDUCED"].includes(r.kind) || seen.has(r.name)) return false;
            seen.add(r.name);
            return true;
        }).slice(0, 4);
        const firstUp = c.changes.find(r => r.ticker && (r.kind === "ADDED" || r.kind === "NEW"));
        if (rows.length === 0) return firstUp || null;

        // 막대 길이는 보이는 네 줄 중 가장 큰 변화 기준. 수백 % 짜리 하나가 나머지를 지우지 않게 100% 에서 자른다
        const max = Math.min(100, Math.max(...rows.map(r => Math.abs(Number(r.sharesChangePercent)))));
        document.getElementById("changebars").innerHTML = rows.map(r => {
            const v = Number(r.sharesChangePercent);
            const cls = v >= 0 ? "rise" : "fall";
            const width = Math.max(2, Math.min(100, Math.abs(v) / max * 100));
            return `<li class="${cls}">${stockLink(r.ticker)}
                <span class="track" aria-hidden="true"><i style="width:${width.toFixed(1)}%"></i></span>
                <span class="v ${cls}">${v >= 0 ? "+" : "−"}${Math.abs(v).toFixed(0)}%</span></li>`;
        }).join("");
        document.getElementById("featuredChangesTitle").innerHTML =
            `<a href="/public/institution.html?cik=${c.institution.cik}#changes-title">${esc(c.institution.nameKo)}</a> · 크게 바뀐 종목`;
        document.getElementById("featuredChangesFoot").textContent = `주식 수 기준 · ${quarter(c.previousPeriod)} 대비`;
        document.getElementById("featuredChanges").hidden = false;
        return firstUp || null;
    }

    // ── 같이 산 종목: 요약 칸 + 아래 표 ──
    function renderConsensus(c) {
        const bought = document.getElementById("bought");
        const sold = document.getElementById("sold");
        const head = document.getElementById("consensusHead");
        const text = document.getElementById("consensusText");
        if (c instanceof Error) {
            bought.className = sold.className = "error";
            bought.textContent = sold.textContent = "불러오지 못했습니다.";
            head.textContent = "불러오지 못했습니다";
            return null;
        }
        if (!c || !c.period) {
            bought.className = sold.className = "empty";
            bought.textContent = sold.textContent = "비교할 분기가 아직 없습니다.";
            head.textContent = "비교할 분기가 아직 없습니다";
            return null;
        }
        document.getElementById("consensusNote").textContent =
            `${quarter(c.period)}, 바로 앞 분기와 비교할 수 있는 기관 ${c.compared}곳 기준입니다. 두 곳 이상 겹친 종목 중 상위 5개입니다. 종목을 누르면 기업분석이 열립니다.`;
        bought.className = sold.className = "";
        bought.innerHTML = consensusTable(c.bought, "buyers", "늘린 기관", "같이 늘린 종목");
        sold.innerHTML = consensusTable(c.sold, "sellers", "줄인 기관", "같이 줄인 종목");

        const b = (c.bought || []).slice(0, 3);
        const s = (c.sold || []).slice(0, 3);
        if (b.length === 0) {
            head.textContent = "같이 늘린 종목이 없습니다";
            return null;
        }
        // 요약 칸의 티커도 누르면 기업분석 창이 열린다
        head.innerHTML = b.map(r => r.ticker ? stockLink(r.ticker, "head-link") : esc(r.name)).join(" · ");
        text.textContent = `${c.compared}곳 중 최대 ${b[0].buyers.length}곳이 같은 석 달에 늘렸습니다.`
            + (s.length ? ` 같이 줄인 쪽은 ${s.map(r => r.ticker || r.name).join("·")}(최대 ${s[0].sellers.length}곳)입니다.` : "");
        return b[0];
    }

    function consensusTable(rows, key, label, caption) {
        if (!rows || rows.length === 0) return '<p class="empty">해당하는 종목이 없습니다.</p>';
        const other = key === "buyers" ? "sellers" : "buyers";
        const otherLabel = key === "buyers" ? "줄인 곳" : "늘린 곳";
        return `<table><caption class="skip">${caption}</caption>
            <thead><tr><th scope="col">종목</th><th scope="col" class="r">${label}</th>
                <th scope="col" class="hide-sm">어디서</th><th scope="col" class="r hide-sm">${otherLabel}</th></tr></thead>
            <tbody>${rows.map(r => `
                <tr><td>${security(r.ticker, r.name)}</td>
                    <td class="r num">${r[key].length}곳</td>
                    <td class="hide-sm">${r[key].map(esc).join(", ")}</td>
                    <td class="r hide-sm muted">${r[other].length ? r[other].length + "곳" : "-"}</td></tr>`).join("")}
            </tbody></table>`;
    }

    // 한국어 조사 은/는. 받침이 있으면 "은". 한글이 아니면(영문 이름) "는"
    function topic(word) {
        const code = word.charCodeAt(word.length - 1) - 0xAC00;
        return code >= 0 && code <= 11171 && code % 28 !== 0 ? "은" : "는";
    }
})();

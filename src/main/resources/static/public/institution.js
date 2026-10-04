// public/institution.html 의 화면 코드. 보안 정책(CSP)이 인라인 스크립트를 막아서 파일로 뺐다.
(async function () {
    const params = new URLSearchParams(location.search);
    const cik = params.get("cik");
    /*
     * 처음에는 위 100줄만 받는다. 피델리티는 5천 줄(약 0.9MB)이라 다 받으면 느리고 표도 무거워진다(부하 테스트에서 찾음).
     * 표 아래 "전체 N줄 보기" 를 누르면 그때 전부 받는다
     */
    // 아래 load() 가 바로 불리므로 그보다 먼저 선언한다(뒤에 두면 "초기화 전 접근" 으로 멈춘다)
    const FIRST = 100;
    const title = document.getElementById("title");
    if (!cik || !/^\d+$/.test(cik)) {
        title.textContent = "기관을 찾을 수 없습니다";
        return;
    }
    document.getElementById("secLink").href =
        "https://www.sec.gov/cgi-bin/browse-edgar?action=getcompany&type=13F-HR&CIK=" + encodeURIComponent(cik);

    const select = document.getElementById("period");
    select.addEventListener("change", () => {
        params.set("period", select.value);
        history.replaceState(null, "", "?" + params.toString());
        load(select.value);
    });
    await load(params.get("period"));


    function url(kind, period, limit) {
        const p = new URLSearchParams();
        if (period) p.set("period", period);
        if (limit) p.set("limit", String(limit));
        return `/api/institutions/${cik}/${kind}?${p}`;
    }

    async function load(period) {
        let h;
        try {
            h = await getJson(url("holdings", period, FIRST));
        } catch (e) {
            title.textContent = "불러오지 못했습니다";
            return;
        }
        if (!h) {
            title.textContent = "아직 받은 자료가 없습니다";
            document.getElementById("changes").textContent = "";
            document.getElementById("holdings").textContent = "";
            return;
        }
        const inst = h.institution;
        title.textContent = inst.nameKo;
        document.title = inst.nameKo + " 포트폴리오 — " + quarter(h.period) + " | 기관 포트폴리오";
        document.getElementById("who").textContent = [inst.manager, inst.name].filter(Boolean).join(" · ");
        const note = document.getElementById("note");
        note.hidden = !inst.note;
        note.textContent = inst.note || "";

        if (select.options.length === 0) {
            select.innerHTML = inst.periods.map(p => `<option value="${esc(p)}">${quarter(p)} (${esc(p)})</option>`).join("");
        }
        select.value = h.period;
        document.getElementById("filed").textContent = h.filedDate + " 공개";

        const stocks = h.holdings.filter(r => !r.putCall);
        const top10 = stocks.slice(0, 10).reduce((s, r) => s + Number(r.weightPercent), 0);
        document.getElementById("facts").innerHTML = `
            <li><b>${usd(h.totalValueUsd)}</b>13F 합계</li>
            <li><b>${count(h.totalRows)}</b>보유 줄</li>
            <li><b>${top10.toFixed(1)}%</b>상위 10종목 비중</li>`;

        // 분기를 바꾸면 그림도 그 분기로. 옵션만 있는 분기처럼 그릴 게 없으면 감춘다
        document.getElementById("figure").hidden = !drawTreemap(document.getElementById("treemap"), h.holdings, inst.nameKo, h.totalRows);
        document.getElementById("figureCaption").textContent = "비중 · " + quarter(h.period);

        renderHoldings(h.holdings, h.totalRows, h.period);
        renderChanges(await getJson(url("changes", period, FIRST)).catch(() => null), period);
    }

    /** 표 아래 "전체 N줄 보기". 누르면 전부 받아 표를 다시 그린다 */
    function moreButton(box, shown, total, label, onMore) {
        if (!total || shown >= total) return;
        const p = document.createElement("p");
        p.className = "more-rows";
        p.innerHTML = `<button type="button">${label} ${count(total)}개 모두 보기</button> <span class="muted">지금 위 ${count(shown)}개</span>`;
        p.querySelector("button").addEventListener("click", async e => {
            e.target.disabled = true;
            e.target.textContent = "불러오는 중…";
            await onMore();
        });
        box.append(p);
    }

    function renderHoldings(rows, total, period) {
        const box = document.getElementById("holdings");
        box.className = "";
        box.innerHTML = `<table aria-labelledby="holdings-title">
            <thead><tr><th scope="col">종목</th><th scope="col" class="r">비중</th>
                <th scope="col" class="r hide-sm">금액</th><th scope="col" class="r hide-sm">주식 수</th></tr></thead>
            <tbody>${rows.map(r => `
                <tr><td>${security(r.ticker, r.issuerName)}${r.putCall ? ` <span class="kind muted">${esc(r.putCall)}</span>` : ""}</td>
                    <td class="r num">${pct(r.weightPercent)}${weightBar(r.weightPercent)}</td>
                    <td class="r hide-sm">${usd(r.valueUsd)}</td>
                    <td class="r hide-sm num">${count(r.shares)}</td></tr>`).join("")}
            </tbody></table>`;
        moreButton(box, rows.length, total, "보유", async () => {
            const all = await getJson(url("holdings", period, 0)).catch(() => null);
            if (all) renderHoldings(all.holdings, all.totalRows, period);
        });
    }

    function renderChanges(c, period) {
        const box = document.getElementById("changes");
        box.className = "";
        if (!c) {
            box.innerHTML = '<p class="empty">비교할 앞 분기가 없습니다.</p>';
            return;
        }
        const n = c.counts || {};
        document.getElementById("changesNote").textContent =
            `${quarter(c.period)}을 ${quarter(c.previousPeriod)}과 비교했습니다. 주식 수 기준이고 옵션은 뺐습니다. ` +
            `새로 삼 ${n.NEW || 0} · 늘림 ${n.ADDED || 0} · 줄임 ${n.REDUCED || 0} · 다 팖 ${n.SOLD_OUT || 0}` +
            (n.SPLIT ? ` · 분할 추정 ${n.SPLIT}(매수·매도로 안 셈)` : "");
        if (c.changes.length === 0) {
            box.innerHTML = '<p class="empty">바뀐 종목이 없습니다.</p>';
            return;
        }
        box.innerHTML = `<table aria-labelledby="changes-title">
            <thead><tr><th scope="col">변화</th><th scope="col">종목</th><th scope="col" class="r">주식 수</th>
                <th scope="col" class="r hide-sm">비중 전 → 후</th></tr></thead>
            <tbody>${c.changes.map(r => `
                <tr><td>${kindTag(r.kind)}</td>
                    <td>${security(r.ticker, r.name)}</td>
                    <td class="r num ${KIND[r.kind] ? KIND[r.kind].cls : ""}">${r.kind === "NEW" ? "새로" : r.kind === "SOLD_OUT" ? "전부" : signedPct(r.sharesChangePercent)}</td>
                    <td class="r hide-sm num">${pct(r.weightBefore)} → ${pct(r.weightNow)}</td></tr>`).join("")}
            </tbody></table>`;
        moreButton(box, c.changes.length, c.totalChanges, "바뀐 종목", async () => {
            const all = await getJson(url("changes", period, 0)).catch(() => null);
            if (all) renderChanges(all, period);
        });
    }
})();

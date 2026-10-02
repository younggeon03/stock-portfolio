// public/institution.html 의 화면 코드. 보안 정책(CSP)이 인라인 스크립트를 막아서 파일로 뺐다.
(async function () {
    const params = new URLSearchParams(location.search);
    const cik = params.get("cik");
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

    async function load(period) {
        const q = period ? "?period=" + encodeURIComponent(period) : "";
        let h;
        try {
            h = await getJson(`/api/institutions/${cik}/holdings${q}`);
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
            <li><b>${count(h.holdings.length)}</b>보유 줄</li>
            <li><b>${top10.toFixed(1)}%</b>상위 10종목 비중</li>`;

        renderHoldings(h.holdings);
        renderChanges(await getJson(`/api/institutions/${cik}/changes${q}`).catch(() => null));
    }

    function renderHoldings(rows) {
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
    }

    function renderChanges(c) {
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
    }
})();

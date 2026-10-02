// index.html 의 화면 코드. 보안 정책(CSP)이 인라인 스크립트를 막아서 파일로 뺐다.
(async function () {
    const instBox = document.getElementById("institutions");
    try {
        const list = await getJson("/api/institutions");
        const rows = list.map(i => `
            <tr>
                <td><a href="/public/institution.html?cik=${i.cik}">${esc(i.nameKo)}</a>
                    <span class="sub">${esc(i.manager || i.name)}</span></td>
                <td>${i.latestPeriod ? quarter(i.latestPeriod) : '<span class="muted">아직 없음</span>'}
                    <span class="sub">${i.filedDate ? esc(i.filedDate) + " 공개" : ""}</span></td>
                <td class="r">${i.holdingCount != null ? count(i.holdingCount) + "종목" : "-"}</td>
                <td class="r hide-sm">${usd(i.totalValueUsd)}</td>
            </tr>`).join("");
        instBox.className = "";
        instBox.innerHTML = list.length === 0 ? '<p class="empty">아직 받은 자료가 없습니다.</p>' : `
            <table aria-labelledby="institutions-title">
                <thead><tr><th scope="col">기관</th><th scope="col">최신 분기</th>
                    <th scope="col" class="r">종목 수</th><th scope="col" class="r hide-sm">합계</th></tr></thead>
                <tbody>${rows}</tbody>
            </table>`;
    } catch (e) {
        instBox.className = "error";
        instBox.textContent = "기관 목록을 불러오지 못했습니다. 잠시 뒤 다시 열어 주세요.";
    }

    const bought = document.getElementById("bought");
    const sold = document.getElementById("sold");
    try {
        const c = await getJson("/api/institutions/consensus?limit=20");
        if (!c || !c.period) {
            bought.className = sold.className = "empty";
            bought.textContent = sold.textContent = "비교할 분기가 아직 없습니다.";
            return;
        }
        document.getElementById("consensusNote").textContent =
            `${quarter(c.period)}, 바로 앞 분기와 비교할 수 있는 기관 ${c.compared}곳 기준입니다. 두 곳 이상인 종목만 보입니다.`;
        bought.className = sold.className = "";
        bought.innerHTML = consensusTable(c.bought, "buyers", "늘린 기관", "같이 늘린 종목");
        sold.innerHTML = consensusTable(c.sold, "sellers", "줄인 기관", "같이 줄인 종목");
    } catch (e) {
        bought.className = sold.className = "error";
        bought.textContent = sold.textContent = "불러오지 못했습니다.";
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
})();

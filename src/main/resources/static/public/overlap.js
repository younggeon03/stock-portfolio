// public/overlap.html 의 화면 코드. 보안 정책(CSP)이 인라인 스크립트를 막아서 파일로 뺐다.
(function () {
    const input = document.getElementById("input");
    const form = document.getElementById("form");
    const result = document.getElementById("result");
    const detail = document.getElementById("detail");
    const STORE = "overlap-input";

    // 주소에 입력이 있으면(공유받은 링크) 그걸, 없으면 이 브라우저에 남긴 마지막 입력을
    const fromUrl = new URLSearchParams(location.search).get("h");
    if (fromUrl) {
        input.value = fromUrl.split(",").map(p => p.replace(":", " ")).join("\n");
    } else {
        try { input.value = localStorage.getItem(STORE) || ""; } catch (e) { /* 저장소가 막혀도 동작 */ }
    }

    // 로그인한 나(또는 내 PC 개발 모드)에게만 "내 계좌로 채우기" 를 보인다
    // 응답 본문까지 읽는다. 상태만 보고 버리면 브라우저가 연결을 붙잡아 두는 경우가 있다
    fetch("/api/me").then(r => r.ok ? r.json() : null).then(me => {
        if (me) document.getElementById("mine").hidden = false;
    }).catch(() => {});
    document.getElementById("mine").addEventListener("click", async () => {
        const res = await fetch("/api/portfolio/unified?scope=ALL");
        if (!res.ok) return;
        const data = await res.json();
        const us = data.items.filter(i => i.currency === "USD");
        input.value = us.map(i => i.symbol + " " + Number(i.marketValueKrw)).join("\n");
        document.getElementById("formNote").textContent =
            `미국 주식 ${us.length}종목을 평가금액으로 채웠습니다. 한국 주식 ${data.items.length - us.length}종목은 뺐습니다.`;
    });

    form.addEventListener("submit", e => { e.preventDefault(); run(); });
    if (fromUrl) run();

    function parse() {
        return input.value.split(/[\n,]+/).map(l => l.trim()).filter(Boolean).map(l => {
            const [t, w] = l.split(/[\s:]+/);
            return t + (w ? ":" + w.replace("%", "") : "");
        });
    }

    async function run() {
        const parts = parse();
        detail.innerHTML = "";
        if (parts.length === 0) {
            result.innerHTML = '<p class="error">종목을 한 줄에 하나씩 넣어 주세요. 예: AAPL 30</p>';
            return;
        }
        const h = parts.join(",");
        try { localStorage.setItem(STORE, input.value); } catch (e) { }
        history.replaceState(null, "", "?h=" + encodeURIComponent(h));
        result.innerHTML = '<p class="loading">계산 중…</p>';

        const res = await fetch("/api/institutions/overlap?h=" + encodeURIComponent(h));
        const body = await res.json().catch(() => null);
        if (!res.ok) {
            result.innerHTML = `<p class="error">${esc(body && body.error ? body.error : "계산하지 못했습니다")}</p>`;
            return;
        }
        result.innerHTML = `<h2 id="rank-title">겹침이 큰 기관부터</h2>
            <table aria-labelledby="rank-title">
            <thead><tr><th scope="col">기관</th><th scope="col" class="r">겹침</th>
                <th scope="col" class="r hide-sm">같이 가진 종목</th><th scope="col" class="hide-sm">크게 겹친 종목</th></tr></thead>
            <tbody>${body.map(r => `
                <tr><td><button type="button" class="linkish" data-cik="${r.cik}">${esc(r.nameKo)}</button>
                        <span class="sub">${esc(r.manager || "")} ${quarter(r.period)}</span></td>
                    <td class="r num">${pct(r.overlapPercent, 1)}${weightBar(r.overlapPercent)}</td>
                    <td class="r hide-sm">${r.sharedCount}종목</td>
                    <td class="hide-sm">${r.topShared.map(esc).join(", ") || '<span class="muted">-</span>'}</td></tr>`).join("")}
            </tbody></table>
            <p class="section-note">기관 이름을 누르면 같이 가진 종목과 그 기관에만 있는 종목이 나옵니다.</p>`;
        result.querySelectorAll("button[data-cik]").forEach(b =>
            b.addEventListener("click", () => showDetail(b.dataset.cik, h)));
    }

    async function showDetail(cik, h) {
        detail.innerHTML = '<p class="loading">불러오는 중…</p>';
        const d = await getJson(`/api/institutions/${cik}/overlap?h=${encodeURIComponent(h)}`).catch(() => null);
        if (!d) { detail.innerHTML = '<p class="error">불러오지 못했습니다.</p>'; return; }
        const r = d.result;
        detail.innerHTML = `
            <h2 id="detail-title">${esc(d.institution.nameKo)}와 겹침 ${pct(r.overlapPercent, 1)}</h2>
            ${r.shared.length ? `<table aria-labelledby="detail-title">
                <thead><tr><th scope="col">같이 가진 종목</th><th scope="col" class="r">내 비중</th>
                    <th scope="col" class="r">기관 비중</th><th scope="col" class="r hide-sm">겹친 몫</th></tr></thead>
                <tbody>${r.shared.map(s => `<tr><td>${security(s.ticker, s.name)}</td>
                    <td class="r num">${pct(s.mine, 1)}</td><td class="r num">${pct(s.theirs, 1)}</td>
                    <td class="r hide-sm num">${pct(s.overlap, 1)}</td></tr>`).join("")}</tbody></table>`
                : '<p class="empty">같이 가진 종목이 없습니다.</p>'}
            <h2>${esc(d.institution.nameKo)} 상위 종목 중 내게 없는 것</h2>
            <table><caption class="skip">기관에만 있는 상위 종목</caption>
                <thead><tr><th scope="col">종목</th><th scope="col" class="r">기관 비중</th></tr></thead>
                <tbody>${r.theirTopMissing.map(t => `<tr><td>${security(t.ticker, t.name)}</td>
                    <td class="r num">${pct(t.weightPercent, 1)}</td></tr>`).join("")}</tbody></table>
            ${r.onlyMine.length ? `<p class="section-note">나만 가진 종목: ${r.onlyMine.map(m => esc(m.ticker)).join(", ")}</p>` : ""}
            <p><a href="/public/institution.html?cik=${d.institution.cik}">${esc(d.institution.nameKo)} 보유 전체 보기</a></p>`;
        detail.querySelector("h2").scrollIntoView({ behavior: "smooth", block: "start" });
    }
})();

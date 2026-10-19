// public/dividends.html 의 화면 코드. 보안 정책(CSP)이 인라인 스크립트를 막아서 파일로 뺐다.
(function () {
    const input = document.getElementById("input");
    const result = document.getElementById("result");
    const STORE = "dividend-input";
    let qtyByName = new Map();   // 마지막으로 조회한 이름 → 보유 주식 수. 주소·서버로는 안 나간다

    /*
     * ★ 주소에는 종목만 싣고 보유 주식 수는 싣지 않는다.
     * 공개 화면이라 주소가 서버 접속 로그·방문 기록·공유한 링크에 그대로 남는다. 수량은 이 브라우저(localStorage)에만 둔다.
     * 주소로 열었을 때는 저장해 둔 입력에서 같은 종목의 수량을 찾아 붙인다. 예전 링크(종목:수량)도 그대로 열린다.
     */
    let saved = "";
    try { saved = localStorage.getItem(STORE) || ""; } catch (e) { }
    const savedQty = new Map(parseLines(saved).filter(l => l.qty).map(l => [l.name, l.qty]));

    const fromUrl = new URLSearchParams(location.search).get("q");
    if (fromUrl) {
        input.value = fromUrl.split(",").map(p => {
            const [name, qty] = p.split(":");
            const q = qty || savedQty.get(name.trim());
            return q ? name.trim() + " " + q : name.trim();
        }).join("\n");
    } else {
        input.value = saved;
    }

    // 로그인한 나(또는 내 PC 개발 모드)에게만: 국내 보유 종목과 수량으로 채운다
    fetch("/api/me").then(r => r.ok ? r.json() : null).then(me => {
        if (me) document.getElementById("mine").hidden = false;
    }).catch(() => {});
    document.getElementById("mine").addEventListener("click", async () => {
        const res = await fetch("/api/portfolio/unified?scope=ALL");
        if (!res.ok) return;
        const data = await res.json();
        const kr = data.items.filter(i => i.marketCountry === "KR" && /^\d{6}$/.test(i.symbol));
        input.value = kr.map(i => i.symbol + " " + Math.floor(Number(i.quantity))).join("\n");
        document.getElementById("formNote").textContent = `국내 보유 ${kr.length}종목을 수량과 함께 채웠습니다.`;
    });

    document.getElementById("form").addEventListener("submit", e => { e.preventDefault(); run(); });
    if (fromUrl) run();

    function won(v) {
        return v === null || v === undefined ? "-" : Math.round(Number(v)).toLocaleString("ko-KR") + "원";
    }

    /** 한 줄 = 종목코드 또는 회사 이름, 뒤에 보유 주식 수(선택). 줄바꿈과 쉼표 둘 다 받는다 */
    function parseLines(text) {
        return text.split(/[\n,]+/).map(l => l.trim()).filter(Boolean).map(l => {
            const m = l.match(/^(.*?)[\s:]+([\d,]+)$/);
            return m ? { name: m[1].trim(), qty: m[2].replace(/,/g, "") } : { name: l, qty: null };
        });
    }

    async function run() {
        const lines = parseLines(input.value);
        if (lines.length === 0) { result.innerHTML = '<p class="error">종목을 넣어 주세요. 예: 005930 10</p>'; return; }
        // 서버 요청 주소도 접속 로그에 남으니 종목만 보낸다. 예상 금액(주당 × 수량)은 여기서 곱한다
        const query = lines.map(l => l.name).join(",");
        qtyByName = new Map(lines.filter(l => l.qty).map(l => [l.name, Number(l.qty)]));
        try { localStorage.setItem(STORE, input.value); } catch (e) { }
        history.replaceState(null, "", "?q=" + encodeURIComponent(query));
        result.innerHTML = '<p class="loading">공시를 찾는 중… (처음 보는 종목은 몇 초 걸립니다)</p>';

        const res = await fetch("/api/public/dividends?q=" + encodeURIComponent(query));
        const body = await res.json().catch(() => null);
        if (!res.ok) {
            result.innerHTML = `<p class="error">${esc(body && body.error ? body.error : "불러오지 못했습니다")}</p>`;
            return;
        }
        render(body);
    }

    function render(cal) {
        // 응답의 stocks[].query 는 보낸 이름 그대로다. 그걸로 종목코드 → 수량을 잇고, 다가오는 배당에 예상 금액을 붙인다
        const qtyByCode = new Map(cal.stocks.filter(s => s.stockCode && qtyByName.has(s.query))
            .map(s => [s.stockCode, qtyByName.get(s.query)]));
        cal.upcoming.forEach(p => {
            const qty = qtyByCode.get(p.stockCode);
            p.expectedAmount = qty && p.perShareCommon !== null ? Number(p.perShareCommon) * qty : null;
        });
        const parts = [];
        const total = cal.upcoming.reduce((s, p) => s + (p.expectedAmount ? Number(p.expectedAmount) : 0), 0);
        parts.push(`<h2 id="up-title">다가오는 배당</h2>`);
        if (cal.upcoming.length === 0) {
            parts.push('<p class="empty">지금 공시된 다가오는 배당이 없습니다.</p>');
        } else {
            if (total > 0) parts.push(`<ul class="facts"><li><b>${won(total)}</b>세전 예상 합계</li></ul>`);
            parts.push(`<table aria-labelledby="up-title"><thead><tr><th scope="col">지급일</th><th scope="col">종목</th>
                <th scope="col" class="r">주당</th><th scope="col" class="r hide-sm">기준일</th><th scope="col" class="r">예상(세전)</th></tr></thead>
                <tbody>${cal.upcoming.map(p => `<tr>
                    <td>${p.payDate ? esc(p.payDate) : '<span class="muted">미정</span>'}</td>
                    <td>${esc(p.corpName)}<span class="sub">${esc(p.kind || "")}</span></td>
                    <td class="r num">${won(p.perShareCommon)}</td>
                    <td class="r hide-sm num">${esc(p.recordDate)}</td>
                    <td class="r num">${p.expectedAmount ? won(p.expectedAmount) : '<span class="muted">-</span>'}</td></tr>`).join("")}
                </tbody></table>`);
        }
        for (const s of cal.stocks) {
            parts.push(`<h2>${esc(s.corpName || s.query)}${s.stockCode ? ` <span class="muted">${esc(s.stockCode)}</span>` : ""}</h2>`);
            if (s.error) { parts.push(`<p class="error">${esc(s.error)}</p>`); continue; }
            if (s.payments.length === 0) { parts.push('<p class="empty">최근 1년 남짓 배당결정 공시가 없습니다.</p>'); continue; }
            parts.push(`<p class="section-note">최근 1년 기준일 주당 배당 합계 ${won(s.trailingPerShare)}</p>
                <table><caption class="skip">${esc(s.corpName)} 배당 기록</caption>
                <thead><tr><th scope="col">기준일</th><th scope="col">구분</th><th scope="col" class="r">보통주 주당</th>
                    <th scope="col" class="r hide-sm">시가배당률</th><th scope="col" class="r">지급일</th><th scope="col" class="r hide-sm">원문</th></tr></thead>
                <tbody>${s.payments.map(p => `<tr>
                    <td class="num">${esc(p.recordDate)}</td><td>${esc(p.kind || "-")}</td>
                    <td class="r num">${won(p.perShareCommon)}</td>
                    <td class="r hide-sm num">${p.yieldCommon !== null ? esc(p.yieldCommon) + "%" : "-"}</td>
                    <td class="r num">${p.payDate ? esc(p.payDate) : '<span class="muted">미정</span>'}</td>
                    <td class="r hide-sm"><a href="${esc(p.sourceUrl)}" rel="noopener">DART</a></td></tr>`).join("")}
                </tbody></table>`);
        }
        result.innerHTML = parts.join("");
    }
})();

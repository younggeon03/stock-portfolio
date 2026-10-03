/*
 * 캔들차트(일봉 + 이동평균선). 나의 포트폴리오 드로어와 공개 기업분석 화면이 같이 쓴다.
 * 두 화면에 따로 두면 색·보이는 구간·범례가 반드시 어긋난다. 그래서 그리는 코드는 여기 한 곳에 둔다.
 * 데이터를 어디서 받을지(로그인 주소 / 공개 주소)는 각 화면이 정한다.
 *
 * lightweight-charts v5 가 먼저 로드돼 있어야 한다(window.LightweightCharts).
 * 쓰는 법:
 *   const c = createStockChart({ host, legend, sub });
 *   c.draw(data);   // data = { currency, points:[{date,open,high,low,close}], movingAverages:[{period, values:[{date,value}]}] }
 *   c.dispose();
 */

/** 이동평균선 설정. 국내 증권사 기본값이다 */
const MA_STYLE = {
    5:   { color: "#f08c00", label: "5일" },
    20:  { color: "#2f9e44", label: "20일" },
    60:  { color: "#7048e8", label: "60일" },
    120: { color: "#868e96", label: "120일" }
};

/**
 * 차트에 몇 봉을 처음부터 보여줄지.
 *
 * ★ 이평선이 잘려 보이던 이유가 여기 있었다.
 * 토스에서 200봉을 받는데, 120일선은 121번째 봉부터 값이 생긴다.
 * 200봉을 전부 펼쳐 놓으면 120일선이 화면 한가운데에서 뚝 시작해 "잘린 선" 으로 보인다.
 *
 * 그래서 계산은 200봉 전부로 하고 처음 보이는 구간만 마지막 80봉으로 잡는다.
 * 80 = 200 - 120 이라, 보이는 구간에서는 네 선이 모두 왼쪽 끝까지 이어진다.
 * 앞쪽 데이터는 그대로 들어 있으니 왼쪽으로 끌면 계속 나온다.
 */
const CHART_VISIBLE_BARS = 80;

function createStockChart(els) {
    const { host, legend, sub } = els;
    let chart = null;
    let maSeries = {};
    let resizeObserver = null;
    /** 마지막으로 그린 데이터. 보이는 구간을 다시 잡을 때 쓴다 */
    let last = null;
    let sized = false;
    let fitPending = false;

    /** CSS 변수 하나를 읽는다. 차트는 캔버스라 CSS 변수를 스스로 못 따라간다 */
    function cssVar(name, fallback) {
        const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
        return v || fallback;
    }

    function price(value, currency) {
        const n = Number(value);
        return currency === "USD"
            ? "$" + n.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })
            : Math.round(n).toLocaleString("ko-KR");
    }

    /** 이전 차트를 정리한다. 안 하면 종목을 옮길 때마다 캔버스가 쌓인다 */
    function dispose() {
        if (resizeObserver) { resizeObserver.disconnect(); resizeObserver = null; }
        if (chart) { chart.remove(); chart = null; }
        maSeries = {};
        last = null;
        host.innerHTML = "";
        if (legend) legend.innerHTML = "";
        if (sub) sub.textContent = "";
    }

    /**
     * 차트 크기를 화면에 맞춘다.
     *
     * ★ 폭이 0인 채로 그려지는 경우가 있다.
     * 숨겨진 탭(드로어의 차트 탭) 안에서 만들어지면 폭이 0이라 봉 간격 계산이 깨져서,
     * 나중에 탭을 열었을 때 200봉이 오른쪽 구석에 뭉쳐 있게 된다.
     * 그래서 "처음으로 진짜 폭이 생긴 순간" 에 한 번 보이는 구간을 다시 잡아준다.
     * 그 뒤로는 크기만 맞춘다. 매번 다시 잡으면 사용자가 왼쪽으로 끌어놓은 위치가 튕겨나간다.
     */
    function fit() {
        if (!chart || host.clientWidth <= 0) return;
        chart.applyOptions({ width: host.clientWidth, height: host.clientHeight });
        if (!sized) {
            sized = true;
            applyWindow();
        }
    }

    /** 끄는 동안 크기 변경이 초당 수십 번 들어온다. 한 프레임에 한 번으로 묶는다 */
    function fitSoon() {
        if (fitPending) return;
        fitPending = true;
        requestAnimationFrame(() => { fitPending = false; fit(); });
    }

    /** 마지막 CHART_VISIBLE_BARS 봉만 보이게 맞춘다 */
    function applyWindow() {
        if (!chart || !last) return;
        const total = last.points.length;
        const from = Math.max(0, total - CHART_VISIBLE_BARS);
        chart.timeScale().setVisibleLogicalRange({ from: from - 0.5, to: total - 0.5 });
    }

    /**
     * 캔들차트를 그린다.
     *
     * ★ 색 규칙
     * 오른 날(양봉)은 빨강, 내린 날(음봉)은 파랑. 국내 증권사 관습이다.
     *
     * ★ 라이브러리 버전 주의
     * v5 에서 addCandlestickSeries() 가 없어지고 addSeries(타입, 옵션) 으로 통일됐다.
     */
    function draw(data) {
        const LC = window.LightweightCharts;
        if (resizeObserver) { resizeObserver.disconnect(); resizeObserver = null; }
        if (chart) { chart.remove(); chart = null; }
        maSeries = {};
        last = data;
        host.innerHTML = "";

        const isUsd = data.currency === "USD";
        const bg = cssVar("--bg", "#ffffff");
        const grid = cssVar("--rule", "#f2f4f6");
        const border = cssVar("--rule-2", "#e5e8eb");
        const axisInk = cssVar("--ink-3", "#8b95a1");
        const rise = cssVar("--rise", "#e03131");
        const fall = cssVar("--fall", "#1b64da");

        chart = LC.createChart(host, {
            width: host.clientWidth,
            height: host.clientHeight,
            layout: { background: { color: bg }, textColor: axisInk, fontSize: 11,
                      fontFamily: getComputedStyle(document.body).fontFamily },
            grid: { vertLines: { color: grid }, horzLines: { color: grid } },
            // 기본 여백이 넉넉해서 축이 0 까지 내려간다. 저점이 높은 종목은 화면 아래 절반이 빈다
            rightPriceScale: { borderColor: border, scaleMargins: { top: 0.08, bottom: 0.08 } },
            timeScale: { borderColor: border, timeVisible: false },
            crosshair: { mode: LC.CrosshairMode ? LC.CrosshairMode.Normal : 0 },
            localization: {
                priceFormatter: p => isUsd ? "$" + p.toFixed(2) : Math.round(p).toLocaleString("ko-KR")
            }
        });

        const candles = chart.addSeries(LC.CandlestickSeries, {
            upColor: rise, downColor: fall,
            borderUpColor: rise, borderDownColor: fall,
            wickUpColor: rise, wickDownColor: fall,
            priceFormat: { type: "price", precision: isUsd ? 2 : 0, minMove: isUsd ? 0.01 : 1 }
        });
        candles.setData(data.points.map(p => ({
            time: p.date,
            open: Number(p.open), high: Number(p.high),
            low: Number(p.low), close: Number(p.close)
        })));

        (data.movingAverages || []).forEach(ma => {
            const style = MA_STYLE[ma.period];
            if (!style || !ma.values || ma.values.length === 0) return;
            const line = chart.addSeries(LC.LineSeries, {
                color: style.color, lineWidth: 2,
                priceLineVisible: false, lastValueVisible: false, crosshairMarkerVisible: false
            });
            line.setData(ma.values.map(v => ({ time: v.date, value: Number(v.value) })));
            maSeries[ma.period] = line;
        });

        renderLegend(data);

        // 보이는 구간을 마지막 80봉으로. 숨겨진 탭이라 폭이 0이면 fit() 이 진짜 폭이 생겼을 때 다시 잡는다
        sized = host.clientWidth > 0;
        applyWindow();

        resizeObserver = new ResizeObserver(() => fit());
        resizeObserver.observe(host);

        /*
         * 요약 한 줄. ★ 화면에 보이는 구간만 말한다.
         * 200봉 전체로 계산하면 "+182%" 라고 써 놓고 화면에는 내리는 구간만 보이는 일이 생긴다.
         */
        if (sub) {
            const shown = data.points.slice(-CHART_VISIBLE_BARS);
            const closes = shown.map(p => Number(p.close));
            const first = closes[0], lastClose = closes[closes.length - 1];
            const change = ((lastClose - first) / first) * 100;
            // up/down 은 나의 포트폴리오, rise/fall 은 공개 화면의 등락 색 이름이다
            const cls = change >= 0 ? "up rise" : "down fall";
            sub.innerHTML = `보이는 ${closes.length}거래일 `
                + `<span class="${cls}">${change > 0 ? "+" : ""}${change.toFixed(2)}%</span> · `
                + `최고 ${price(Math.max(...closes), data.currency)} · `
                + `최저 ${price(Math.min(...closes), data.currency)}`
                + `<span class="chart-hint">왼쪽으로 끌면 ${data.points.length}거래일까지 나옵니다</span>`;
        }
    }

    /** 이평선 범례. 누르면 해당 선을 켜고 끈다 (좁은 화면에서 선 4개는 뻑뻑하다) */
    function renderLegend(data) {
        if (!legend) return;
        const periods = (data.movingAverages || [])
            .filter(ma => ma.values && ma.values.length > 0 && MA_STYLE[ma.period])
            .map(ma => ma.period);
        if (periods.length === 0) { legend.innerHTML = ""; return; }

        legend.innerHTML = periods.map(p =>
            `<button type="button" data-ma="${p}" aria-pressed="true"><span class="dot" style="background:${MA_STYLE[p].color}"></span>${MA_STYLE[p].label}</button>`
        ).join("");
        legend.querySelectorAll("button").forEach(btn => {
            btn.addEventListener("click", () => {
                const line = maSeries[btn.dataset.ma];
                if (!line) return;
                const nowOff = btn.classList.toggle("off");
                btn.setAttribute("aria-pressed", String(!nowOff));
                line.applyOptions({ visible: !nowOff });
            });
        });
    }

    return { draw, dispose, fit, fitSoon };
}

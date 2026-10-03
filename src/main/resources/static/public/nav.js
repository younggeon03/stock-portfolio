/*
 * 왼쪽 사이드바 메뉴. 모든 화면(공개 화면과 내 포트폴리오)이 이 파일 하나를 쓴다.
 *
 * ★ 새 기능을 붙일 때는 아래 MENU 에 한 줄만 넣는다
 *   { label, href, desc }                 하나짜리 메뉴
 *   { label, href, desc, children: [...] } 아래에 세부 메뉴
 *   owner: true                           나(운영자)에게만 보임. /api/me 가 200 일 때만 (로그인했거나 내 PC 개발 모드)
 *
 * 화면마다 메뉴를 따로 적으면 반드시 어긋난다. 그래서 HTML 에는 메뉴를 쓰지 않고 여기서 그린다.
 * 인라인 스크립트는 보안 정책(CSP)이 막으므로 이 파일을 <script src> 로 부른다.
 */
(function () {
    const MENU = [
        {
            label: "나의 포트폴리오", href: "/portfolio.html", owner: true,
            desc: "토스·나무 합산, 비중, 기업분석"
        },
        {
            label: "기관 포트폴리오", href: "/",
            desc: "피델리티·버크셔 등 큰 기관 10곳의 13F",
            children: [
                { label: "기관 목록", href: "/" },
                { label: "같이 산 종목", href: "/#consensus" },
                { label: "내 포트폴리오와 비교", href: "/public/overlap.html" }
            ]
        },
        {
            label: "기업분석", href: "/public/company.html",
            desc: "미국 종목의 공시 재무·읽을 점·기관 움직임"
        },
        {
            label: "배당 캘린더", href: "/public/dividends.html",
            desc: "DART 배당결정 공시로 본 일정"
        }
    ];

    const here = location.pathname + location.hash;
    const isCurrent = href => {
        if (href.includes("#")) return here === href;
        return location.pathname === href || (href === "/" && location.pathname === "/index.html");
    };

    function itemHtml(item) {
        const current = isCurrent(item.href) && !(item.children || []).some(c => c.href.includes("#") && isCurrent(c.href));
        const children = (item.children || []).map(c =>
            `<li><a href="${c.href}" class="nav-sub"${isCurrent(c.href) ? ' aria-current="page"' : ""}>${c.label}</a></li>`).join("");
        return `<li class="nav-item"${item.owner ? ' data-owner hidden' : ""}>
            <a href="${item.href}" class="nav-main"${current && !children ? ' aria-current="page"' : ""}>
                <span class="nav-label">${item.label}</span>
                ${item.desc ? `<span class="nav-desc">${item.desc}</span>` : ""}
            </a>
            ${children ? `<ul class="nav-children">${children}</ul>` : ""}
        </li>`;
    }

    function build() {
        const panel = document.createElement("nav");
        panel.id = "siteNav";
        panel.className = "site-nav";
        panel.setAttribute("aria-label", "사이트 메뉴");
        panel.setAttribute("inert", "");
        panel.innerHTML = `
            <div class="site-nav-head">
                <a href="/" class="site-nav-brand">포트폴리오 분석기</a>
                <button type="button" class="site-nav-close" aria-label="메뉴 닫기">&times;</button>
            </div>
            <ul class="site-nav-list">${MENU.map(itemHtml).join("")}</ul>
            <p class="site-nav-foot">공개 자료(SEC 13F·DART)는 누구나, 나의 포트폴리오는 운영자만 봅니다.</p>`;
        const backdrop = document.createElement("div");
        backdrop.className = "site-nav-backdrop";
        document.body.append(backdrop, panel);

        const toggle = document.createElement("button");
        toggle.type = "button";
        toggle.className = "nav-toggle";
        toggle.setAttribute("aria-label", "메뉴 열기");
        toggle.setAttribute("aria-controls", "siteNav");
        toggle.setAttribute("aria-expanded", "false");
        toggle.innerHTML = '<span aria-hidden="true"></span><span aria-hidden="true"></span><span aria-hidden="true"></span>';
        // 화면마다 머리말 모양이 달라서, 표시된 자리([data-nav-slot])가 있으면 거기, 없으면 머리말 맨 앞에 넣는다
        const slot = document.querySelector("[data-nav-slot]") || document.querySelector("header");
        if (slot) slot.prepend(toggle); else document.body.prepend(toggle);

        let lastFocus = null;
        function open() {
            lastFocus = document.activeElement;
            document.documentElement.classList.add("nav-open");
            panel.removeAttribute("inert");
            toggle.setAttribute("aria-expanded", "true");
            const first = panel.querySelector('[aria-current="page"]') || panel.querySelector("a");
            if (first) first.focus();
        }
        function close() {
            document.documentElement.classList.remove("nav-open");
            panel.setAttribute("inert", "");
            toggle.setAttribute("aria-expanded", "false");
            if (lastFocus && lastFocus.focus) lastFocus.focus();
        }
        toggle.addEventListener("click", () =>
            document.documentElement.classList.contains("nav-open") ? close() : open());
        panel.querySelector(".site-nav-close").addEventListener("click", close);
        backdrop.addEventListener("click", close);
        document.addEventListener("keydown", e => {
            if (e.key === "Escape" && document.documentElement.classList.contains("nav-open")) close();
        });
        // 같은 화면 안의 #앵커로 가면 메뉴를 닫아야 내용이 보인다
        panel.querySelectorAll('a[href*="#"]').forEach(a => a.addEventListener("click", close));

        // 운영자 메뉴는 나일 때만. 응답 본문까지 읽어야 브라우저가 연결을 놓는다
        fetch("/api/me", { headers: { "Accept": "application/json" } })
            .then(r => r.ok ? r.json() : null)
            .then(me => { if (me) panel.querySelectorAll("[data-owner]").forEach(el => { el.hidden = false; }); })
            .catch(() => {});
    }

    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", build);
    else build();
})();

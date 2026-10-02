// 로그인 화면. 폼을 그대로 보내지 않고 fetch 로 보낸다.
// CSRF 토큰을 헤더(X-XSRF-TOKEN)로 붙여야 하는데, 정적 화면이라 서버가 폼에 토큰을 심어 줄 수 없어서다.
(function () {
    const error = document.getElementById("error");
    if (new URLSearchParams(location.search).has("error")) {
        error.hidden = false;
        error.textContent = "아이디나 비밀번호가 맞지 않습니다.";
    }

    function cookie(name) {
        const hit = document.cookie.split("; ").find(c => c.startsWith(name + "="));
        return hit ? decodeURIComponent(hit.substring(name.length + 1)) : null;
    }

    document.getElementById("form").addEventListener("submit", async e => {
        e.preventDefault();
        error.hidden = true;
        // 토큰 쿠키가 아직 없으면 한 번 받아 온다 (서버가 모든 응답에 심는다)
        if (!cookie("XSRF-TOKEN")) await fetch("/api/institutions", { headers: { "Accept": "application/json" } });
        const body = new URLSearchParams({
            username: document.getElementById("username").value,
            password: document.getElementById("password").value
        });
        const res = await fetch("/login", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded", "X-XSRF-TOKEN": cookie("XSRF-TOKEN") || "" },
            body: body
        });
        // 성공하면 서버가 원래 가려던 곳(없으면 내 포트폴리오)으로 넘겨준다. 실패하면 ?error 로 돌아온다
        if (res.url.includes("error") || !res.ok) {
            error.hidden = false;
            error.textContent = "아이디나 비밀번호가 맞지 않습니다.";
            return;
        }
        location.href = res.url.endsWith("/public/login.html") ? "/portfolio.html" : res.url;
    });
})();

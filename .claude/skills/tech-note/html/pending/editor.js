// 샘플: 오른쪽 칸과 본문 코드를 에디터 모양(줄 번호·파일 탭·괄호 색)으로 바꾼다. 스크롤은 페이지 하나
(function () {
  const OPEN = "([{", CLOSE = ")]}";
  // 강조된 HTML 을 줄로 나눈다. 여러 줄에 걸친 <span>(주석 등)은 줄 끝에서 닫고 다음 줄에서 다시 연다
  function splitLines(html) {
    const out = []; let cur = ""; const stack = [];
    const re = /(<span[^>]*>)|(<\/span>)|(\n)|([^<\n]+)/g; let m;
    while ((m = re.exec(html))) {
      if (m[1]) { stack.push(m[1]); cur += m[1]; }
      else if (m[2]) { stack.pop(); cur += m[2]; }
      else if (m[3]) { cur += "</span>".repeat(stack.length); out.push(cur); cur = stack.join(""); }
      else cur += m[0];
    }
    out.push(cur);
    return out;
  }
  function rainbow(root) {
    let depth = 0;
    const w = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    const nodes = [];
    while (w.nextNode()) nodes.push(w.currentNode);
    for (const n of nodes) {
      if (n.parentElement.closest(".n, .hljs-string, .hljs-comment, .hljs-regexp, .hljs-quote, .hljs-doctag")) continue;
      if (!/[()[\]{}]/.test(n.data)) continue;
      const frag = document.createDocumentFragment(); let buf = "";
      for (const ch of n.data) {
        const o = OPEN.includes(ch), c = CLOSE.includes(ch);
        if (!o && !c) { buf += ch; continue; }
        if (buf) { frag.append(buf); buf = ""; }
        if (c) depth = Math.max(0, depth - 1);
        const s = document.createElement("span"); s.className = "br" + (depth % 3); s.textContent = ch; frag.append(s);
        if (o) depth++;
      }
      if (buf) frag.append(buf);
      n.replaceWith(frag);
    }
  }
  const escHtml = s => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  function editor(text, lang, opts) {
    const ed = document.createElement("div"); ed.className = "ed";
    const bar = document.createElement("div"); bar.className = "ed-bar";
    const name = document.createElement(opts.href ? "a" : "span");
    name.textContent = opts.title || "";
    if (opts.href) { name.href = opts.href; name.target = "_blank"; name.rel = "noopener"; }
    const tag = document.createElement("span"); tag.className = "ed-lang"; tag.textContent = opts.kind || lang || "";
    bar.append(name, tag);
    let html = escHtml(text);
    if (lang && window.hljs && hljs.getLanguage(lang)) {
      try { html = hljs.highlight(text, { language: lang, ignoreIllegals: true }).value; } catch (e) { /* 강조 없이 */ }
    }
    const lines = document.createElement("div"); lines.className = "ed-lines";
    const start = opts.start || 1;
    lines.innerHTML = splitLines(html).map((l, i) => '<div class="ln"><span class="n">' + (start + i) + '</span><span class="c">' + l + '</span></div>').join("");
    rainbow(lines);
    ed.append(bar, lines);
    return ed;
  }
  document.querySelectorAll(".sec").forEach(sec => {
    const h3 = sec.querySelector(".sec-body h3");
    let key = h3 && (h3.textContent.match(/^(\d+\.\d+)\s/) || [])[1];
    if (!key) { let p = sec.previousElementSibling; while (p && p.tagName !== "H2") p = p.previousElementSibling; key = p && (p.textContent.match(/^(\d+)\./) || [])[1]; }
    const items = (SIDES[key] || []).filter(it => !it.diagram);
    const cards = [...sec.querySelectorAll(".sec-side .ref")].filter(c => c.querySelector("pre"));
    cards.forEach((card, i) => {
      const it = items[i]; if (!it) return;
      card.querySelector(".ref-h").remove();
      card.querySelector("pre").replaceWith(editor(it.code, it.lang, { title: it.title, href: it.href, start: it.start, kind: it.href ? "실제 코드" : "예시" }));
    });
  });
  document.querySelectorAll(".doc pre > code[class*='language-']").forEach(code => {
    const lang = (code.className.match(/language-(\w+)/) || [])[1];
    if (!lang || lang === "text" || code.closest(".ed, .fig")) return;
    code.parentElement.replaceWith(editor(code.textContent.replace(/\n$/, ""), lang, { title: "예시 코드", kind: lang }));
  });
})();

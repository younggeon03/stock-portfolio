// docs/기술노트.md 를 읽기 좋은 HTML 한 장으로 만든다(목차 고정, 용어 찾기, 용어 풍선).
// 원본은 마크다운 하나뿐이다. 이 HTML 은 결과물이라 저장소에 넣지 않고 target/ 에 만든다(git 이 무시함).
// 사용: node .claude/skills/tech-note/scripts/build-html.js [출력 경로]   (저장소 루트에서)
const fs = require("fs"), path = require("path"), { execSync } = require("child_process");
const root = process.cwd();
const out = process.argv[2] || path.join(root, "target", "tech-note", "기술노트.html");

const md = fs.readFileSync(path.join(root, "docs", "기술노트.md"), "utf8").replace(/\r\n/g, "\n");
const tpl = fs.readFileSync(path.join(__dirname, "..", "html", "template.html"), "utf8");

let commit = "";
try { commit = execSync("git rev-parse --short HEAD", { cwd: root }).toString().trim(); } catch (e) { /* git 없으면 생략 */ }
const built = new Date().toLocaleString("sv-SE", { timeZone: "Asia/Seoul" }).slice(0, 16);

// </script> 가 본문에 있으면 스크립트가 거기서 끝나 버린다. 문자열 안에서 끊어 둔다
const safe = s => JSON.stringify(s).replace(/<\/(script)/gi, "<\\/$1").replace(/<!--/g, "<\\!--");

// 그림: html/diagrams/이름.svg → { 이름: svg 글 }. 마크다운에 <!-- diagram: 이름 --> 표시가 있는데 파일이 없으면 멈춘다
const diagDir = path.join(__dirname, "..", "html", "diagrams");
const diagrams = {};
if (fs.existsSync(diagDir)) {
    for (const f of fs.readdirSync(diagDir).filter(f => f.endsWith(".svg"))) {
        diagrams[path.basename(f, ".svg")] = fs.readFileSync(path.join(diagDir, f), "utf8").trim();
    }
}
// 명세(html/diagrams.js)로 그린 그림. 같은 이름이면 손으로 그린 .svg 가 이긴다
for (const [id, svg] of Object.entries(require(path.join(__dirname, "..", "html", "diagrams.js"))())) {
    if (!diagrams[id]) diagrams[id] = svg;
}
const wanted = [...md.matchAll(/<!--\s*diagram:\s*([\w-]+)/g)].map(m => m[1]);
const missing = wanted.filter(id => !diagrams[id]);
if (missing.length) {
    console.error(`그림이 없습니다: ${missing.join(", ")} (html/diagrams.js 에 명세를 쓰거나 html/diagrams/이름.svg 를 두세요)`);
    process.exit(1);
}
// 글자 상자 그림(```text)은 바로 앞에 그림 표시가 있어야 한다. HTML 판에서 글로 된 그림이 남지 않게
const lines = md.split("\n");
const bare = [];
lines.forEach((l, i) => {
    if (!/^```text/.test(l)) return;
    let j = i - 1;
    while (j >= 0 && lines[j].trim() === "") j--;
    if (j < 0 || !/<!--\s*diagram:/.test(lines[j])) bare.push(i + 1);
});
if (bare.length) {
    console.error(`그림 표시가 없는 글자 그림: docs/기술노트.md ${bare.map(n => n + "행").join(", ")}. 바로 위에 <!-- diagram: 이름 | 설명 --> 를 넣으세요`);
    process.exit(1);
}

// 오른쪽 칸: html/sides.js 의 명세대로 저장소 파일에서 실제 코드를 잘라 온다
const REPO_BLOB = "https://github.com/younggeon03/stock-portfolio/blob/main/";
const sideSpec = require(path.join(__dirname, "..", "html", "sides.js"));
const sides = {};
const sideErrors = [];
for (const [key, items] of Object.entries(sideSpec)) {
    sides[key] = items.map(it => {
        if (it.diagram) {
            if (!diagrams[it.diagram]) sideErrors.push(`${key}: 없는 그림 ${it.diagram}`);
            return { diagram: it.diagram, note: it.note };
        }
        if (it.code) return { title: it.title || "", code: it.code, lang: it.lang, note: it.note };
        const file = path.join(root, it.file);
        if (!fs.existsSync(file)) { sideErrors.push(`${key}: 없는 파일 ${it.file}`); return null; }
        const all = fs.readFileSync(file, "utf8").replace(/\r\n/g, "\n").split("\n");
        const start = all.findIndex(l => it.exact ? l.trim() === it.from : l.includes(it.from));
        if (start < 0) { sideErrors.push(`${key}: ${it.file} 에서 "${it.from}" 를 못 찾음`); return null; }
        let stop = start + (it.lines || 1);
        if (it.to) {
            const k = all.findIndex((l, i) => i > start && l.includes(it.to));
            if (k < 0) { sideErrors.push(`${key}: ${it.file} 에서 끝 표시 "${it.to}" 를 못 찾음`); return null; }
            stop = k + 1;
        }
        const part = all.slice(start, stop);
        // 공통 들여쓰기를 걷어 좁은 칸에서도 읽히게 한다
        const indent = Math.min(...part.filter(l => l.trim()).map(l => l.match(/^ */)[0].length));
        const code = part.map(l => l.slice(indent)).join("\n").replace(/\s+$/, "");
        const end = start + part.length;
        return {
            title: `${it.file.replace("src/main/java/com/mystock/portfolio/", "…/").replace("src/test/java/com/mystock/portfolio/", "test/…/").replace("src/main/resources/", "resources/")} · ${end === start + 1 ? `${end}행` : `${start + 1}–${end}행`}`,
            href: `${REPO_BLOB}${it.file.split("/").map(encodeURIComponent).join("/")}#L${start + 1}-L${end}`,
            code, lang: it.lang, note: it.note, start: start + 1,
        };
    }).filter(Boolean);
}
if (sideErrors.length) {
    console.error("오른쪽 칸 코드를 못 잘라 왔습니다 (html/sides.js 를 고치세요):\n  " + sideErrors.join("\n  "));
    process.exit(1);
}
if (process.argv.includes("--sides")) {
    // 검토용: 단락마다 잘라 온 코드의 첫 줄과 끝 줄
    for (const [k, items] of Object.entries(sides)) for (const s of items) {
        if (s.diagram) { console.log(`${k}  [그림 ${s.diagram}]`); continue; }
        const ls = s.code.split("\n");
        console.log(`${k}  ${s.title}\n      ┌ ${ls[0]}\n      └ ${ls[ls.length - 1]}`);
    }
    process.exit(0);
}

if (!tpl.includes("/*__NOTE_JSON__*/\"\"") || !tpl.includes("/*__META_JSON__*/{}") || !tpl.includes("/*__DIAGRAMS_JSON__*/{}") || !tpl.includes("/*__SIDES_JSON__*/{}")) {
    console.error("템플릿에 자리표시가 없습니다: html/template.html");
    process.exit(1);
}
const html = tpl
    .replace("/*__NOTE_JSON__*/\"\"", () => safe(md))
    .replace("/*__META_JSON__*/{}", () => safe({ commit, built }))
    .replace("/*__DIAGRAMS_JSON__*/{}", () => safe(diagrams))
    .replace("/*__SIDES_JSON__*/{}", () => safe(sides));

fs.mkdirSync(path.dirname(out), { recursive: true });
fs.writeFileSync(out, html);
console.log(`만듦: ${path.relative(root, out)} (${Math.round(html.length / 1024)}KB, 커밋 ${commit || "없음"})`);

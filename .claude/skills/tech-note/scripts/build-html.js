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

if (!tpl.includes("/*__NOTE_JSON__*/\"\"") || !tpl.includes("/*__META_JSON__*/{}")) {
    console.error("템플릿에 자리표시가 없습니다: html/template.html");
    process.exit(1);
}
const html = tpl
    .replace("/*__NOTE_JSON__*/\"\"", () => safe(md))
    .replace("/*__META_JSON__*/{}", () => safe({ commit, built }));

fs.mkdirSync(path.dirname(out), { recursive: true });
fs.writeFileSync(out, html);
console.log(`만듦: ${path.relative(root, out)} (${Math.round(html.length / 1024)}KB, 커밋 ${commit || "없음"})`);

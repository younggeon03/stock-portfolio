// 4장 샘플을 만든다: 빌드된 기술노트에 editor.css·editor.js 를 덧씌우고 본문을 4장으로 자른다
// 사용: node target/tech-note/variants/make-sample.js [시작 단락]  (저장소 루트에서)
const fs = require("fs");
const V = "target/tech-note/variants/";
const h = fs.readFileSync("target/tech-note/기술노트.html", "utf8");
const from = process.argv[2] || "## 4. ";
const out = process.argv[3] || V + "코드샘플.html";
let o = h.replace("marked.parse(NOTE_MD,", `marked.parse(NOTE_MD.slice(NOTE_MD.indexOf(${JSON.stringify(from)}), NOTE_MD.indexOf("## 5. ")),`)
    .replace("<title>기술노트</title>", "<title>기술노트 코드 표시 샘플</title>");
o = o.replace("</style>", fs.readFileSync(V + "editor.css", "utf8") + "\n</style>");
const i = o.lastIndexOf("</script>");
o = o.slice(0, i + 9) + "\n<script>\n" + fs.readFileSync(V + "editor.js", "utf8") + "\n</script>" + o.slice(i + 9);
fs.writeFileSync(out, o);
console.log("만듦", out);

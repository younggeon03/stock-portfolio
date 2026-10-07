// 문서 8개의 상대 링크와 #앵커가 실제로 있는지 검사한다. GitHub 의 앵커 규칙(소문자, 기호 삭제, 공백 → -)을 흉내 낸다.
// 사용: node .claude/skills/tech-note/scripts/linkcheck.js   (저장소 루트에서)
const fs = require("fs"), path = require("path");
const root = process.argv[2] || process.cwd();
const files = ["README.md", "CLAUDE.md", "docs/아키텍처.md", "docs/운영.md", "docs/로드맵.md",
    "docs/결정기록.md", "docs/개발일지.md", "docs/기술노트.md"];
const slug = s => s.trim().toLowerCase().replace(/[`*]/g, "").replace(/\[([^\]]*)\]\([^)]*\)/g, "$1")
    .replace(/[^\p{L}\p{N}\s_-]/gu, "").replace(/\s/g, "-");
const anchors = {};

function anchorsOf(p) {
    if (anchors[p]) return anchors[p];
    const set = new Set(), seen = {};
    let inCode = false;
    for (const l of fs.readFileSync(p, "utf8").split(/\r?\n/)) {
        if (/^```/.test(l)) inCode = !inCode;
        const m = !inCode && l.match(/^#{1,6}\s+(.*)$/);
        if (m) {
            let a = slug(m[1]);
            if (seen[a] !== undefined) { seen[a]++; a = a + "-" + seen[a]; } else seen[a] = 0;
            set.add(a);
        }
    }
    return anchors[p] = set;
}

let bad = 0, total = 0;
for (const f of files) {
    const p = path.join(root, f);
    if (!fs.existsSync(p)) { bad++; console.log(`없는 문서: ${f}`); continue; }
    let inCode = false;
    fs.readFileSync(p, "utf8").split(/\r?\n/).forEach((l, i) => {
        if (/^```/.test(l)) inCode = !inCode;
        if (inCode) return;
        for (const m of l.matchAll(/\]\(([^)\s]+)\)/g)) {
            const t = m[1];
            if (/^(https?:|mailto:)/.test(t)) continue;
            total++;
            const [file, anc] = t.split("#");
            const target = file ? path.resolve(path.dirname(p), decodeURI(file)) : p;
            if (!fs.existsSync(target)) { bad++; console.log(`${f}:${i + 1} 없는 파일 ${t}`); continue; }
            if (anc && target.endsWith(".md") && !anchorsOf(target).has(decodeURI(anc))) {
                bad++; console.log(`${f}:${i + 1} 없는 앵커 ${t}`);
            }
        }
    });
}
console.log(`링크 ${total}개, 깨진 것 ${bad}개`);
process.exit(bad ? 1 : 0);

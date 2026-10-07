// 기술노트·아키텍처에 적힌 숫자가 코드와 맞는지 볼 "현재 사실" 을 뽑는다. 문서를 고치기 전에 이것부터 돌린다.
// 사용: node .claude/skills/tech-note/scripts/facts.js   (저장소 루트에서)
const fs = require("fs"), path = require("path");
const root = process.argv[2] || process.cwd();
const read = p => fs.readFileSync(path.join(root, p), "utf8");
const walk = dir => fs.readdirSync(path.join(root, dir), { withFileTypes: true }).flatMap(e =>
    e.isDirectory() ? walk(path.join(dir, e.name)) : [path.join(dir, e.name)]);

// 마이그레이션: 버전 목록과 만들어진 테이블
const migDir = "src/main/resources/db/migration";
const migs = fs.readdirSync(path.join(root, migDir)).filter(f => /^V\d+__.*\.sql$/.test(f))
    .sort((a, b) => parseInt(a.slice(1)) - parseInt(b.slice(1)));
const tables = new Set();
for (const f of migs) {
    for (const m of read(path.join(migDir, f)).matchAll(/CREATE TABLE\s+`?(\w+)`?/gi)) tables.add(m[1]);
    for (const m of read(path.join(migDir, f)).matchAll(/DROP TABLE\s+(?:IF EXISTS\s+)?`?(\w+)`?/gi)) tables.delete(m[1]);
}
console.log(`마이그레이션 ${migs.length}개 (마지막 ${migs[migs.length - 1]})`);
console.log(`테이블 ${tables.size}개: ${[...tables].sort().join(", ")}`);

// 엔티티·컨트롤러·테스트
const java = walk("src/main/java").filter(f => f.endsWith(".java"));
const entities = java.filter(f => /@Entity\b/.test(read(f))).map(f => path.basename(f, ".java"));
const controllers = java.filter(f => /@RestController\b/.test(read(f))).map(f => path.basename(f, ".java"));
const tests = walk("src/test/java").filter(f => f.endsWith(".java"))
    .reduce((n, f) => n + (read(f).match(/@Test\b/g) || []).length, 0);
console.log(`엔티티 ${entities.length}개: ${entities.sort().join(", ")}`);
console.log(`컨트롤러 ${controllers.length}개`);
console.log(`테스트 메서드 약 ${tests}개 (@Test 개수. 정확한 수는 ./mvnw.cmd test 결과)`);

// CI 잡 이름, compose 서비스
for (const wf of fs.readdirSync(path.join(root, ".github/workflows"))) {
    const names = [...read(path.join(".github/workflows", wf)).matchAll(/^\s{4}name:\s*(.+)$/gm)].map(m => m[1].trim());
    console.log(`워크플로 ${wf}: ${names.join(" / ")}`);
}
for (const c of ["docker-compose.yml", "deploy/compose.yml"]) {
    if (!fs.existsSync(path.join(root, c))) continue;
    // services: 블록 안의 두 칸 들여쓴 키만. 아래 volumes: 의 이름이 섞이지 않게 다음 최상위 키에서 자른다
    const block = (read(c).split(/^services:\s*$/m)[1] || "").split(/^\S/m)[0];
    const services = [...block.matchAll(/^  ([\w-]+):\s*$/gm)].map(m => m[1]);
    console.log(`${c} 서비스: ${services.join(", ")}`);
}

// 프롬프트 파일, 정적 화면
console.log(`프롬프트: ${fs.readdirSync(path.join(root, "src/main/resources/prompts")).join(", ")}`);
const pages = walk("src/main/resources/static").filter(f => f.endsWith(".html")).map(f => f.replace(/\\/g, "/").split("static/")[1]);
console.log(`화면 HTML: ${pages.join(", ")}`);

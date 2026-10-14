# 이 저장소의 기술노트 규칙

tech-note 스킬(개인 스킬 `~/.claude/skills/tech-note`)의 공통 규칙 위에 이 저장소에만 해당하는 것을 적는다. 공통 규칙과 다르면 이 파일이 이긴다.
노트 원본은 `docs/기술노트.md`, 결과 HTML 은 `target/tech-note/기술노트.html`(git 이 무시함)이다.

## 독자

공통 기본값 그대로다. 자바 문법·예외·컬렉션은 알지만 프록시·멱등·서킷 브레이커·readiness·대사 같은 실무 용어는 모르는 주니어(사용자가 정한 수준, 2026-10-09).
"기초부터" 의 본보기는 2.1 DI, 2.7 프록시, 5.2 CSRF 다. 2026-10-09 에 0~15장 전체를 해라체로 다시 썼다.

## 바뀐 곳 → 고칠 장

| 바뀐 곳 | 고칠 장 |
|---|---|
| `db/migration/V*.sql`, `domain/*.java` | 1.5 데이터, **3장 전체**(ERD 3.1, 요약표 3.2, 마이그레이션 표 3.8) |
| `external/**`, RestClient·재시도·커넥션 풀 설정 | 1.6 바깥 시스템, 4장 |
| `SecurityConfig`, 요청 제한, CSP | 1.8 보안 경계, 5장 |
| `static/**` | 1.4 화면 지도, 6장 |
| `src/test/**` | 7장 (테스트 수) |
| `Dockerfile`, `docker-compose.yml`, `pom.xml` | 8장 |
| `.github/workflows/**` | 1.10, 9장 |
| `deploy/**` | 1.2 실행 단위, 10장, 11장 |
| 비동기·배치·캐시·`@Scheduled` | 1.2(배치 시각), 1.7, 2.4 |
| 새 필터·인터셉터·`@Transactional` 저장 빈 | 2.7 프록시 표 |
| 시스템 부품이 늘거나 빠짐(새 컨테이너·바깥 API·저장소) | 1.1 그림(`diagrams/overview.svg`), `diagrams.js` 의 `D.map`, 해당 장의 "지도에서 보면" 문단 |

## 장 첫머리 지도의 부분 이름

`D.map` 노드의 `part`: browser(화면), caddy(앞단 프록시), web(컨트롤러·필터), service(서비스·배치), domain(엔티티·JPA), external(바깥 API 클라이언트), extapi(바깥 API), mysql, monitoring(Prometheus·알림), code(브랜치·PR), ci, image(ghcr 이미지), deploy(deploy.sh).
0장(용어집)·13장(공부 순서)·14장(면접)은 지도를 두지 않는다. 시스템에 새 부품이 생기면(예: Redis) 노드와 `part` 를 더한다.

## 노트와 같이 고칠 다른 문서·숫자

- `CLAUDE.md` 의 테스트 수, `docs/운영.md` 의 테스트 수, `README.md` 의 기술 표
- `docs/아키텍처.md` 6장 데이터 모델의 테이블 수·표
- **마크다운은 정해진 8개만 둔다**(`CLAUDE.md` 의 문서 구조 표). 새 문서 파일을 만들지 않는다. 링크 검사 목록(config.json 의 `linkcheck`)도 이 8개다

## 이 저장소에서 지킬 것

- **공개 저장소다.** 실제 잔고·수량·평단가·계좌·IP 를 쓰지 않는다. 예시는 데모 값. `.env` 를 가리키는 오른쪽 칸을 만들지 않는다
- ERD 에는 FK 제약이 없다(같은 값으로 찾아볼 뿐). 새 FK 가 생기면 3.4 를 고친다
- HTML 판의 색·간격은 저장소 `static/public/tokens.css` 의 "브리핑" 토큰을 옮긴 것이다. 빨강·파랑은 등락 전용이라 본문·그림에 쓰지 않는다(결정기록 008). 코드 블록만 예외(사용자 요청)
- 작업은 브랜치 → PR → CI → 스쿼시 병합. 병합 뒤 같은 Artifact("기술노트")에 다시 게시한다

## 사실 뽑기

`node .tech-note/facts.js` — 마이그레이션 수·마지막 버전, 테이블·엔티티 목록, 테스트 수(어림), CI 잡 이름, compose 서비스, 프롬프트 파일, 화면 HTML.
정확한 테스트 수는 `./mvnw.cmd test` 결과로 확인한다.

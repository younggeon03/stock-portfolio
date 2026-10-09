// 기술노트 HTML 판의 그림 명세. build-html.js 가 이것을 SVG 로 만들어 넣는다.
// 마크다운의 <!-- diagram: 이름 | 설명 --> 표시가 이 이름을 가리킨다. 손으로 그린 SVG 가 필요하면 diagrams/이름.svg 로 둔다.
//
// 좌표계: 폭 720 이 본문 폭(약 650px)에 맞는다. 노드 높이는 48~56, 칸 사이는 최소 40.
// 노드 k: ''(안쪽) | 'main'(이 그림의 주인공, 굵은 테두리) | 'ext'(바깥) | 'db'
// 노드 part: 지도(map)에서 장마다 진하게 칠할 부분의 이름
// 선 d: SVG path. 라벨 좌표(lx, ly)는 직접 준다. 선과 라벨이 겹치지 않게 그려 놓고 크롬으로 한 번 본다.

const N = (x, y, w, h, t, s, k = "", extra = {}) => ({ x, y, w, h, t, s, k, ...extra });
const T = (x, y, w, t, rows, k = "") => ({ x, y, w, h: 38 + rows.length * 18, t, rows, k });
const E = (d, l, lx, ly, o = {}) => ({ d, l, lx, ly, ...o });

const D = {};

// ── 장 첫머리 지도. map@부분,부분 으로 부르면 그 부분만 진하게 ──
D.map = {
    w: 720, h: 372,
    zones: [
        { x: 312, y: 8, w: 290, h: 226, l: "Spring Boot 앱 (jar 하나)", lx: 324, ly: 26 },
        { x: 8, y: 288, w: 704, h: 76, l: "코드에서 서버까지", lx: 20, ly: 306, k: 2 },
    ],
    nodes: [
        N(20, 40, 120, 48, "브라우저", "화면 · 6장", "", { part: "browser" }),
        N(168, 40, 120, 48, "Caddy", "문 · 10장", "", { part: "caddy" }),
        N(328, 40, 258, 40, "web · 필터", "", "", { part: "web" }),
        N(328, 96, 258, 40, "service · 배치", "", "", { part: "service" }),
        N(466, 152, 120, 40, "external", "", "", { part: "external" }),
        N(328, 152, 120, 40, "domain · JPA", "", "", { part: "domain" }),
        N(620, 152, 92, 40, "바깥 API", "", "ext", { part: "extapi" }),
        N(466, 240, 120, 40, "MySQL", "", "db", { part: "mysql" }),
        N(150, 170, 140, 48, "Prometheus", "지표 · 알림", "", { part: "monitoring" }),
        N(24, 316, 120, 40, "브랜치 · PR", "", "", { part: "code" }),
        N(170, 316, 130, 40, "CI · 테스트", "", "", { part: "ci" }),
        N(326, 316, 130, 40, "이미지 · ghcr", "", "", { part: "image" }),
        N(482, 316, 130, 40, "deploy.sh", "", "", { part: "deploy" }),
    ],
    edges: [
        E("M140,64 H166", "", 0, 0),
        E("M288,60 H326", "", 0, 0),
        E("M457,80 V94", "", 0, 0),
        E("M388,136 V150", "", 0, 0),
        E("M526,136 V150", "", 0, 0),
        E("M466,172 H450", "", 0, 0),
        E("M586,172 H618", "", 0, 0),
        E("M388,192 V260 H464", "", 0, 0),
        E("M220,170 V112 H326", "지표", 236, 106, { dash: true }),
        E("M144,336 H168", "", 0, 0),
        E("M300,336 H324", "", 0, 0),
        E("M456,336 H480", "", 0, 0),
        E("M598,316 V238", "새 이미지로 교체", 606, 300, { dash: true }),
    ],
};

// ── 1.1 은 diagrams/overview.svg (손으로 그림) ──

// ── 1.3 패키지 지도 ──
D.packages = {
    w: 720, h: 360,
    nodes: [
        N(260, 16, 200, 52, "web", "컨트롤러 · 예외 처리"),
        N(260, 108, 200, 52, "service", "업무 규칙", "main"),
        N(30, 208, 190, 52, "brokerage", "증권사 보유 · 주문 없음"),
        N(265, 208, 190, 52, "external", "바깥 API 클라이언트"),
        N(500, 208, 190, 52, "domain", "엔티티 · 리포지토리", "db"),
        N(30, 296, 660, 44, "config — 보안 · 호출 한도 · 관측 · 스케줄", "", "ext"),
    ],
    edges: [
        E("M360,68 V106", "넘긴다", 370, 92),
        E("M310,160 V184 H125 V206", "", 0, 0),
        E("M360,160 V206", "", 0, 0),
        E("M410,160 V184 H595 V206", "", 0, 0),
        E("M220,234 H263", "씀", 241, 226, { a: "middle" }),
        E("M455,234 H498", "씀", 476, 226, { a: "middle" }),
    ],
    texts: [{ x: 360, y: 288, t: "모든 층에 가로로 걸친다", c: "d-n", a: "middle" }],
};

// ── 1.5 13F 사슬 ──
D.chain13f = {
    w: 720, h: 330,
    nodes: [
        N(250, 10, 220, 52, "institution", "기관"),
        N(250, 96, 220, 52, "filing_13f", "13F 제출 한 건 · 분기"),
        N(250, 182, 220, 52, "holding_13f", "종목 한 줄 · CUSIP · 주식 수", "main"),
        N(250, 268, 220, 52, "cusip_ticker", "CUSIP → 티커 (못 찾은 것도)", "db"),
    ],
    edges: [
        E("M360,62 V94", "1 : N  분기마다 13F를 낸다", 372, 82),
        E("M360,148 V180", "1 : N  보유 줄", 372, 168),
        E("M360,234 V266", "N : 1  CUSIP으로 찾는다", 372, 254, { dash: true }),
    ],
};

// ── 1.8 보안 경계 ──
D.zones = {
    w: 720, h: 330,
    zones: [
        { x: 10, y: 96, w: 220, h: 220, l: "① 공개 · 로그인 없음", lx: 22, ly: 116 },
        { x: 250, y: 96, w: 220, h: 220, l: "② 로그인한 운영자만", lx: 262, ly: 116 },
        { x: 490, y: 96, w: 220, h: 220, l: "③ 서버 안 127.0.0.1만", lx: 502, ly: 116, k: 2 },
    ],
    nodes: [
        N(260, 10, 200, 44, "인터넷 · 브라우저", "", "ext"),
        N(26, 132, 188, 40, "/ · /public/**", ""),
        N(26, 184, 188, 40, "/api/public/**", ""),
        N(26, 236, 188, 40, "GET /api/institutions/**", ""),
        N(266, 132, 188, 40, "/portfolio.html", "", "main"),
        N(266, 184, 188, 56, "나머지 /api/**", "돈이 드는 호출 포함", "main"),
        N(506, 132, 188, 40, "관리 포트 8081", ""),
        N(506, 184, 188, 40, "Prometheus 9090", ""),
        N(506, 236, 188, 40, "Grafana 3000", ""),
    ],
    edges: [
        E("M310,54 V74 H120 V94", "누구나", 126, 70),
        E("M360,54 V94", "로그인 + CSRF", 352, 88, { a: "end" }),
        E("M410,54 V74 H600 V94", "SSH 터널로만", 606, 70, { dash: true }),
    ],
};

// ── 1.9 ① 공개 기업분석 요청 ──
D["req-public"] = {
    w: 720, h: 420,
    nodes: [
        N(30, 10, 200, 48, "브라우저", "stock.js", "ext"),
        N(30, 96, 200, 48, "Caddy", "IP와 요청 번호를 붙임"),
        N(30, 182, 200, 48, "RequestIdFilter", "번호를 MDC에"),
        N(30, 268, 200, 48, "보안 규칙 · 호출 한도", "공개 주소 · 분당 120번"),
        N(30, 354, 200, 52, "StockBriefService", "6시간 캐시 먼저", "main"),
        N(520, 210, 180, 48, "SEC · 토스", "10-K 재무 · 현재가", "ext"),
        N(520, 283, 180, 48, "MySQL", "기관 10곳의 13F", "db"),
        N(520, 356, 180, 48, "StockNotes", "규칙으로 문장"),
    ],
    edges: [
        E("M130,58 V94", "GET /api/public/stocks/MSFT", 142, 82),
        E("M130,144 V180", "X-Forwarded-For · X-Request-Id", 142, 168),
        E("M130,230 V266", "", 0, 0),
        E("M130,316 V352", "통과", 142, 340),
        E("M230,380 H470 V234 H518", "캐시에 없을 때만", 250, 372),
        E("M470,307 H518", "", 0, 0),
        E("M470,380 H518", "", 0, 0),
    ],
};

// ── 1.9 ② AI 기업분석 ──
D["req-analysis"] = {
    w: 720, h: 320,
    nodes: [
        N(30, 20, 180, 52, "브라우저", "나의 포트폴리오", "ext"),
        N(290, 20, 230, 52, "CompanyAnalysisService", "사실 모으기 → 바로 RUNNING"),
        N(290, 130, 230, 52, "작업 스레드 (하나)", "2~5분", "main"),
        N(612, 130, 100, 52, "Claude API", "웹 검색", "ext"),
        N(290, 248, 230, 52, "company_analysis", "모양 검사 후 저장", "db"),
    ],
    edges: [
        E("M210,46 H288", "POST", 249, 38, { a: "middle" }),
        E("M405,72 V128", "작업을 넘김", 415, 104),
        E("M520,156 H610", "지시문 + 사실", 564, 148, { a: "middle" }),
        E("M405,182 V246", "결과 저장", 415, 218),
        E("M120,72 V274 H288", "3초마다 GET: RUNNING … OK", 130, 266, { dash: true }),
    ],
};

// ── 1.9 ③ 13F 배치 ──
D["batch-13f"] = {
    w: 720, h: 400,
    nodes: [
        N(30, 20, 220, 56, "매일 07:00", "ThirteenFSyncService", "main"),
        N(480, 20, 220, 56, "SEC EDGAR", "제출 목록 · 보유표 XML", "ext"),
        N(30, 130, 220, 56, "filing_13f · holding_13f", "받은 번호는 건너뜀", "db"),
        N(480, 130, 220, 56, "OpenFIGI", "모르는 CUSIP만 묻는다", "ext"),
        N(30, 250, 220, 48, "새 제출 이벤트", ""),
        N(290, 250, 220, 48, "배치 끝 이벤트", ""),
        N(30, 336, 220, 48, "FilingAlertService", "텔레그램 알림"),
        N(290, 336, 220, 48, "캐시 비우기 → 예열", "순서 0 → 순서 100"),
    ],
    edges: [
        E("M250,48 H478", "새 제출만 받는다", 364, 40, { a: "middle" }),
        E("M140,76 V128", "XML을 스트리밍으로 읽어 저장", 150, 108),
        E("M250,158 H478", "CUSIP → 티커", 364, 150, { a: "middle" }),
        E("M140,186 V248", "", 0, 0),
        E("M200,186 V216 H400 V248", "", 0, 0),
        E("M140,298 V334", "", 0, 0),
        E("M400,298 V334", "", 0, 0),
    ],
};

// ── 1.9 ④ · 2.4 장 마감 스냅샷과 대사 ──
D.snapshot = {
    w: 720, h: 480,
    nodes: [
        N(200, 10, 280, 52, "16:10 · 18:10 · 20:10", "PortfolioSnapshotService"),
        N(200, 96, 280, 48, "오늘 것이 이미 있나?", ""),
        N(200, 178, 280, 52, "증권사마다 statement()", "보유 + 증권사가 밝힌 합계"),
        N(200, 264, 280, 52, "Reconciler", "앱 합계 vs 증권사 합계 · 0.1%"),
        N(200, 350, 280, 52, "한 트랜잭션", "합계 upsert · 그날 줄 지우고 새로", "main"),
        N(200, 430, 280, 44, "지표 → Prometheus → 텔레그램", ""),
        N(590, 96, 120, 48, "건너뜀", "재시도 회차", "ext"),
        N(590, 178, 120, 52, "저장 안 함", "빈 날 > 틀린 날", "ext"),
    ],
    edges: [
        E("M340,62 V94", "", 0, 0),
        E("M340,144 V176", "없음", 350, 164),
        E("M340,230 V262", "", 0, 0),
        E("M340,316 V348", "", 0, 0),
        E("M340,402 V428", "어긋나면 알림", 350, 420),
        E("M480,120 H588", "있음", 534, 112, { a: "middle" }),
        E("M480,204 H588", "하나라도 실패", 534, 196, { a: "middle" }),
    ],
    texts: [{ x: 20, y: 380, t: "날짜가 키라서", c: "d-n" }, { x: 20, y: 398, t: "몇 번 돌려도 한 벌", c: "d-n" }],
};

// ── 1.10 코드에서 운영까지 ──
D.pipeline = {
    w: 720, h: 310,
    nodes: [
        N(10, 20, 120, 48, "브랜치 · 커밋", ""),
        N(160, 20, 100, 48, "PR", ""),
        N(290, 16, 170, 56, "CI", "테스트 · 이미지 · 설정 검사", "main"),
        N(520, 20, 190, 48, "main에 스쿼시 병합", ""),
        N(520, 132, 190, 56, "이미지 → ghcr.io", "sha-xxxxxxx", "db"),
        N(270, 132, 200, 56, "배포 워크플로", "SSH → deploy.sh"),
        N(30, 132, 190, 56, "헬스체크", "readiness가 UP인가?", "main"),
        N(10, 246, 160, 48, "새 버전으로 운영", ""),
        N(210, 246, 230, 48, "바로 앞 버전으로 되돌림", "", "ext"),
    ],
    edges: [
        E("M130,44 H158", "", 0, 0),
        E("M260,44 H288", "", 0, 0),
        E("M460,44 H518", "통과해야", 489, 36, { a: "middle" }),
        E("M615,68 V130", "", 0, 0),
        E("M520,160 H472", "", 0, 0),
        E("M270,160 H222", "", 0, 0),
        E("M90,188 V244", "예", 100, 220),
        E("M170,188 V214 H325 V244", "아니오", 260, 208, { a: "middle" }),
    ],
};

// ── 2.7 프록시 ──
D.proxy = {
    w: 720, h: 250,
    nodes: [
        N(20, 52, 140, 52, "부르는 쪽", "service.save()", "ext"),
        N(230, 16, 260, 124, "프록시", "", "main", { lines: ["스프링이 만든 대리 객체", "① 트랜잭션 열기", "② 진짜 객체에게 넘김", "③ 성공이면 커밋, 예외면 롤백"] }),
        N(570, 52, 140, 52, "진짜 객체", "save()"),
        N(20, 184, 140, 52, "같은 클래스 안", "this.save()", "ext"),
    ],
    edges: [
        E("M160,78 H228", "부른다", 194, 70, { a: "middle" }),
        E("M490,78 H568", "②", 529, 70, { a: "middle" }),
        E("M160,210 H640 V106", "프록시를 안 거침 → 트랜잭션 없음", 400, 202, { a: "middle", dash: true }),
    ],
};

// ── 3.1 ERD ① 13F ──
D["erd-13f"] = {
    w: 720, h: 508,
    nodes: [
        T(30, 10, 300, "institution · 기관", ["PK cik", "name · name_ko", "manager · note", "sort_order", "active  (꺼도 행은 남김)"]),
        T(30, 186, 300, "filing_13f · 분기 제출 한 건", ["PK accession_no", "UK (cik, report_period)", "filed_date", "total_value_usd", "holding_count", "fetched_at"]),
        T(30, 370, 300, "holding_13f · 보유 종목 한 줄", ["PK id", "UK (accession_no, cusip, put_call)", "IX cusip", "issuer_name", "shares · value_usd"], "main"),
        T(410, 370, 290, "cusip_ticker · CUSIP → 티커", ["PK cusip", "ticker  (NULL 가능)", "figi_name", "security_type", "resolved_at"], "db"),
    ],
    edges: [
        E("M180,138 V184", "1 : N  cik로 이어짐", 192, 166),
        E("M180,332 V368", "1 : N  accession_no로 이어짐", 192, 354),
        E("M330,430 H408", "N : 1", 369, 422, { a: "middle", dash: true }),
    ],
    texts: [{ x: 410, y: 350, t: "티커를 못 찾은 것도 남긴다", c: "d-n" }],
};

// ── 3.1 ERD ② 종목 중심 ──
D["erd-symbol"] = {
    w: 720, h: 436,
    nodes: [
        T(250, 10, 220, "symbol_master · 토스 종목", ["PK id", "IX symbol · IX search_name"]),
        T(10, 140, 220, "company_analysis", ["PK id", "UK symbol", "status", "analysis_json", "analyzed_at · researched_at", "includes_position", "updated_at · last_error", "model · 토큰 수"], "main"),
        T(250, 140, 220, "stored_financials", ["PK symbol", "market_country", "financials_json", "has_history", "fetched_at"]),
        T(490, 140, 220, "manual_holding", ["PK id", "UK (owner_key, symbol)", "quantity", "average_purchase_price", "currency · market_country"]),
        T(490, 316, 220, "oauth_token", ["PK id", "UK (provider, owner_key)", "access_token  (암호문)", "issued_at · expires_at"], "db"),
    ],
    edges: [
        E("M360,84 V104 H120 V138", "", 0, 0, { dash: true }),
        E("M360,84 V138", "symbol로 찾아볼 뿐", 370, 98, { dash: true }),
        E("M360,104 H600 V138", "", 0, 0, { dash: true, noArrowStart: true }),
        E("M600,268 V314", "owner_key", 610, 296, { dash: true }),
    ],
};

// ── 3.1 ERD ③ 공시처 번호표와 배당 ──
D["erd-dart"] = {
    w: 720, h: 320,
    nodes: [
        T(20, 10, 270, "dart_corp_code · 국내 → DART", ["PK stock_code  (6자리)", "corp_code  (8자리)", "corp_name"]),
        T(430, 10, 270, "sec_cik · 미국 티커 → SEC", ["PK ticker", "cik", "title"]),
        T(20, 176, 270, "dividend_event · 배당 공시 한 건", ["PK rcept_no", "IX (stock_code, record_date)", "per_share_common", "record_date · pay_date (NULL 가능)", "correction"], "main"),
        T(340, 176, 240, "dividend_fetch · 마지막 조회", ["PK stock_code", "fetched_at"]),
    ],
    edges: [
        E("M155,102 V174", "stock_code", 165, 148, { dash: true }),
        E("M155,124 H460 V174", "", 0, 0, { dash: true }),
    ],
    texts: [{ x: 340, y: 270, t: "\"오늘 이미 DART를 불렀나\" 를 본다", c: "d-n" }, { x: 340, y: 288, t: "하루 한 번 제한용", c: "d-n" }],
};

// ── 3.1 ERD ④ 스냅샷과 대사 ──
D["erd-snapshot"] = {
    w: 720, h: 362,
    nodes: [
        T(210, 10, 300, "portfolio_snapshot · 하루 한 줄", ["PK snapshot_date", "taken_at", "total_value_krw · total_purchase_krw", "profit_loss_krw · profit_rate_percent", "item_count", "mismatch_count"], "main"),
        T(10, 206, 330, "portfolio_snapshot_item · 그날 종목", ["PK id", "UK (snapshot_date, symbol)", "name · currency", "quantity · last_price", "market_value_krw · purchase_krw", "weight_percent"]),
        T(380, 206, 330, "reconciliation_result · 대사", ["PK id", "UK (snapshot_date, broker, scope)", "currency", "broker_total · app_sum", "difference", "matched"]),
    ],
    edges: [
        E("M360,156 V180 H175 V204", "1 : N  snapshot_date", 372, 172),
        E("M360,180 H545 V204", "", 0, 0),
    ],
};

// ── 3.2 원본이 어디에 있나 ──
D["data-origin"] = {
    w: 720, h: 320,
    zones: [
        { x: 10, y: 10, w: 340, h: 196, l: "원본이 바깥에 있다 · 다시 받을 수 있음", lx: 22, ly: 30 },
        { x: 370, y: 10, w: 340, h: 196, l: "원본이 여기뿐이다", lx: 382, ly: 30, k: 2 },
    ],
    nodes: [
        N(10, 250, 340, 56, "캐시처럼 다룬다", "실패하면 저장본 · 지워도 다시 채운다"),
        N(370, 250, 340, 56, "백업이 지키는 대상", "11.4 백업과 복구", "main"),
    ],
    texts: [
        { x: 26, y: 64, t: "filing_13f · holding_13f · cusip_ticker", c: "d-r" },
        { x: 26, y: 96, t: "stored_financials · dividend_event", c: "d-r" },
        { x: 26, y: 128, t: "symbol_master · dart_corp_code · sec_cik", c: "d-r" },
        { x: 386, y: 64, t: "manual_holding — 내가 입력한 것", c: "d-r" },
        { x: 386, y: 96, t: "company_analysis — 돈 주고 만든 것", c: "d-r" },
        { x: 386, y: 128, t: "portfolio_snapshot · _item ·", c: "d-r" },
        { x: 386, y: 146, t: "reconciliation_result — 지난 날은 못 만듦", c: "d-r" },
        { x: 386, y: 178, t: "oauth_token — 잃으면 다시 발급", c: "d-r" },
    ],
    edges: [
        E("M180,206 V248", "", 0, 0),
        E("M540,206 V248", "", 0, 0),
    ],
};

// ── 3.6 분석 상태 ──
D["analysis-states"] = {
    w: 720, h: 270,
    nodes: [
        N(20, 76, 120, 52, "NONE", "행 없음", "ext"),
        N(240, 76, 160, 52, "RUNNING", "15분 넘으면 죽은 작업"),
        N(520, 76, 160, 52, "OK", "analysis_json 저장", "main"),
        N(240, 200, 160, 52, "ERROR", "이전 분석은 남는다", "ext"),
    ],
    edges: [
        E("M140,102 H238", "실행", 189, 94, { a: "middle" }),
        E("M400,102 H518", "클로드 응답", 459, 94, { a: "middle" }),
        E("M600,76 V40 H320 V74", "판단만 새로 · 다시 분석", 460, 32, { a: "middle" }),
        E("M290,128 V198", "실패", 280, 168, { a: "end" }),
        E("M350,198 V130", "다시 실행", 360, 168),
    ],
};

// ── 4.1 토큰 두 단계 보관 ──
D.token = {
    w: 720, h: 190,
    nodes: [
        N(10, 40, 110, 52, "토큰이 필요", "", "ext"),
        N(200, 40, 120, 52, "메모리", "가장 빠름"),
        N(400, 40, 140, 52, "MySQL", "암호문 → 복호화", "db"),
        N(620, 40, 100, 52, "증권사", "새로 발급", "ext"),
    ],
    edges: [
        E("M120,66 H198", "먼저", 160, 58, { a: "middle" }),
        E("M320,66 H398", "없으면", 360, 58, { a: "middle" }),
        E("M540,66 H618", "없으면", 580, 58, { a: "middle" }),
        E("M670,92 V140 H260 V94", "받은 토큰을 양쪽에 저장 (DB에는 암호화)", 465, 160, { a: "middle", dash: true }),
        E("M470,140 V94", "", 0, 0, { dash: true }),
    ],
};

// ── 4.5 서킷 브레이커 ──
D.breaker = {
    w: 720, h: 300,
    nodes: [
        N(30, 80, 180, 56, "닫힘", "정상 · 부른다", "main"),
        N(510, 80, 180, 56, "열림", "부르지 않고 바로 실패", "ext"),
        N(270, 220, 180, 56, "반열림", "몇 번만 시험"),
    ],
    edges: [
        E("M210,108 H508", "최근 20번 중 절반 실패", 359, 100, { a: "middle" }),
        E("M640,136 V248 H452", "30초 뒤", 650, 196),
        E("M268,248 H120 V138", "시험 성공", 194, 240, { a: "middle" }),
        E("M360,220 V170 H560 V138", "시험 실패", 460, 162, { a: "middle", dash: true }),
    ],
    texts: [{ x: 360, y: 40, t: "호스트마다 하나 · 4xx는 실패로 세지 않는다", c: "d-n", a: "middle" }],
};

// ── 5.2 CSRF ──
D.csrf = {
    w: 720, h: 260,
    nodes: [
        N(10, 30, 160, 56, "수상한 사이트", "숨은 폼", "ext"),
        N(280, 30, 160, 56, "내 브라우저", "우리 앱 로그인 쿠키"),
        N(550, 30, 160, 56, "우리 앱", "쿠키 + 헤더를 검사", "main"),
        N(280, 170, 160, 56, "우리 화면 JS", "쿠키 값을 헤더로 복사"),
    ],
    edges: [
        E("M170,58 H278", "POST를 시킨다", 224, 50, { a: "middle" }),
        E("M440,58 H548", "쿠키 자동 첨부", 494, 50, { a: "middle" }),
        E("M440,198 H630 V88", "X-XSRF-TOKEN 헤더 → 통과", 535, 216, { a: "middle" }),
    ],
    texts: [
        { x: 10, y: 120, t: "남의 쿠키를 읽지 못해", c: "d-n" },
        { x: 10, y: 138, t: "헤더를 만들 수 없다 → 403", c: "d-n" },
    ],
};

// ── 10.2 리버스 프록시 ──
D.rproxy = {
    w: 720, h: 170,
    nodes: [
        N(10, 56, 130, 56, "바깥", "인터넷", "ext"),
        N(220, 10, 270, 150, "Caddy", "", "main", { lines: ["암호 풀기 (TLS 종료)", "인증서 자동 발급 · 갱신", "/actuator 같은 주소 차단", "사용자 IP를 X-Forwarded-For로", "앱 재시작 동안 요청 붙잡기"] }),
        N(570, 56, 140, 56, "앱", "바깥 포트 없음"),
    ],
    edges: [
        E("M140,84 H218", "HTTPS 443", 179, 76, { a: "middle" }),
        E("M490,84 H568", "HTTP 8080", 529, 76, { a: "middle" }),
    ],
};

// ── 11.3 요청 번호 ──
D.reqid = {
    w: 720, h: 270,
    nodes: [
        N(10, 40, 100, 48, "브라우저", "", "ext"),
        N(150, 40, 130, 48, "Caddy", "요청마다 UUID"),
        N(380, 40, 140, 48, "RequestIdFilter", "번호를 MDC에", "main"),
        N(570, 4, 140, 44, "모든 로그 줄", "requestId 필드"),
        N(570, 60, 140, 44, "응답 헤더", "X-Request-Id"),
        N(570, 116, 140, 44, "access 한 줄", "GET … 200 87ms"),
        N(380, 190, 140, 52, "작업 스레드", "AI 분석"),
        N(130, 190, 180, 52, "배치", "요청이 없다", "ext"),
    ],
    edges: [
        E("M110,64 H148", "", 0, 0),
        E("M280,64 H378", "X-Request-Id", 329, 56, { a: "middle" }),
        E("M520,64 H548 V26 H568", "", 0, 0),
        E("M548,64 V82 H568", "", 0, 0),
        E("M548,82 V138 H568", "", 0, 0),
        E("M450,88 V188", "LogContext.carry로 복사", 440, 150, { a: "end" }),
    ],
    texts: [{ x: 220, y: 262, t: "LogContext.job → 새 번호", c: "d-n", a: "middle" }],
};

// ── 15 다섯 가지 ──
D.five = {
    w: 720, h: 90,
    nodes: [
        N(4, 16, 128, 60, "15.1 요청 추적", "요청 ID · JSON 로그"),
        N(150, 16, 128, 60, "15.2 첫 방문자", "캐시 예열"),
        N(296, 16, 128, 60, "15.3 막는 검사", "커버리지 최소선"),
        N(442, 16, 128, 60, "15.4 숫자 맞추기", "스냅샷 · 대사", "main"),
        N(588, 16, 128, 60, "15.5 화면", "평가금액 추이"),
    ],
    edges: [
        E("M132,46 H148", "", 0, 0),
        E("M278,46 H294", "", 0, 0),
        E("M424,46 H440", "", 0, 0),
        E("M570,46 H586", "", 0, 0),
    ],
};

// ── 그리기 ──
const esc = s => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");

function render(id, d) {
    const mk = `arr-${id}`;
    const out = [`<svg viewBox="0 0 ${d.w} ${d.h}" xmlns="http://www.w3.org/2000/svg" role="img">`,
        `<defs><marker id="${mk}" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="currentColor"/></marker></defs>`];
    for (const z of d.zones || []) {
        out.push(`<rect class="${z.k === 2 ? "d-zone2" : "d-zone"}" x="${z.x}" y="${z.y}" width="${z.w}" height="${z.h}" rx="12"/>`);
        out.push(`<text class="d-zl" x="${z.lx}" y="${z.ly}">${esc(z.l)}</text>`);
    }
    for (const n of d.nodes) {
        const cls = ["d-node", n.k, n.part ? "p-" + n.part : "", n.rows ? "tbl" : ""].filter(Boolean).join(" ");
        out.push(`<g class="${cls}"${n.part ? ` data-part="${n.part}"` : ""}>`);
        out.push(`<rect x="${n.x}" y="${n.y}" width="${n.w}" height="${n.h}" rx="8"/>`);
        const cx = n.x + n.w / 2;
        if (n.rows) {
            out.push(`<text class="d-t" x="${n.x + 12}" y="${n.y + 20}">${esc(n.t)}</text>`);
            out.push(`<line class="d-sep" x1="${n.x}" y1="${n.y + 30}" x2="${n.x + n.w}" y2="${n.y + 30}"/>`);
            n.rows.forEach((r, i) => out.push(`<text class="d-r" x="${n.x + 12}" y="${n.y + 30 + 18 * i + 15}">${esc(r)}</text>`));
        } else if (n.lines) {
            out.push(`<text class="d-t" x="${cx}" y="${n.y + 24}" text-anchor="middle">${esc(n.t)}</text>`);
            n.lines.forEach((l, i) => out.push(`<text class="d-s" x="${cx}" y="${n.y + 46 + 20 * i}" text-anchor="middle">${esc(l)}</text>`));
        } else if (n.s) {
            out.push(`<text class="d-t" x="${cx}" y="${n.y + n.h / 2 - 3}" text-anchor="middle">${esc(n.t)}</text>`);
            out.push(`<text class="d-s" x="${cx}" y="${n.y + n.h / 2 + 14}" text-anchor="middle">${esc(n.s)}</text>`);
        } else {
            out.push(`<text class="d-t" x="${cx}" y="${n.y + n.h / 2 + 5}" text-anchor="middle">${esc(n.t)}</text>`);
        }
        out.push(`</g>`);
    }
    for (const e of d.edges || []) {
        const cls = ["d-e", e.dash ? "dash" : "", e.strong ? "strong" : ""].filter(Boolean).join(" ");
        const arrow = e.noArrowStart ? "" : ` marker-end="url(#${mk})"`;
        out.push(`<path class="${cls}" d="${e.d}"${e.noArrowStart ? "" : arrow}/>`);
        if (e.l) out.push(`<text class="d-l" x="${e.lx}" y="${e.ly}"${e.a ? ` text-anchor="${e.a}"` : ""}>${esc(e.l)}</text>`);
    }
    for (const t of d.texts || []) {
        out.push(`<text class="${t.c || "d-n"}" x="${t.x}" y="${t.y}"${t.a ? ` text-anchor="${t.a}"` : ""}>${esc(t.t)}</text>`);
    }
    out.push(`</svg>`);
    return out.join("\n");
}

module.exports = function renderAll() {
    const r = {};
    for (const [id, d] of Object.entries(D)) r[id] = render(id, d);
    return r;
};

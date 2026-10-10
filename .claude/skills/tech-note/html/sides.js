// 기술노트 HTML 판의 오른쪽 칸. 단락(### 4.1 …)마다 그 내용을 돕는 실제 코드·설정·그림을 붙인다.
// 키는 단락 번호("4.1"). 단락이 없는 장은 장 번호("7").
//
// 코드는 빌드할 때 저장소 파일에서 직접 잘라 온다. 그래서 코드가 바뀌면 다시 빌드할 때 따라온다.
//   { file: 저장소 기준 경로, from: "이 글자가 처음 나오는 줄부터", lines: 몇 줄, lang: 강조 언어, note: 왜 보는지 한 문장 }
//   to: "글자" 를 주면 lines 대신 그 글자가 나오는 줄까지 자른다
//   exact: true 면 줄 전체(앞뒤 공백 제외)가 from 과 같을 때만 고른다(앞쪽 다른 줄에 같은 글자가 있을 때)
//   from 을 못 찾으면 빌드가 멈춘다(코드가 바뀌어 위치가 사라졌다는 뜻이다. 새 위치를 찾아 고친다)
// 그림: { diagram: "diagrams.js 의 이름", note }  본문에 같은 그림이 이미 있으면 넣지 않는다(겹친다)
// 공개 저장소라 비밀값이 든 파일(.env 등)은 가리키지 않는다.

const J = "src/main/java/com/mystock/portfolio/";
const R = "src/main/resources/";

module.exports = {
    "1.1": [
        { file: "deploy/compose.yml", from: "  app:", lines: 18, lang: "yaml", note: "앱의 관리 포트도 127.0.0.1에만 묶는다. 바깥에 열린 포트는 Caddy의 80·443뿐이다." },
    ],
    "1.2": [
        { file: "deploy/compose.yml", from: "# 포트는 반드시 127.0.0.1", lines: 3, lang: "yaml", note: "\"(선택)\" 컨테이너가 왜 127.0.0.1에만 열리는지 compose 파일에 주석으로 남겨 두었다." },
    ],
    "1.3": [
        { file: J + "brokerage/BrokerageClient.java", from: "public interface BrokerageClient", lines: 26, lang: "java", note: "brokerage 층의 전부다. 주문 메서드가 없다는 것이 이 인터페이스의 핵심이다." },
    ],
    "1.4": [
        { file: R + "static/public/nav.js", from: "const MENU = [", to: "];", lang: "javascript", note: "모든 화면의 사이드바가 이 배열 하나에서 나온다. 새 화면은 여기에 한 줄을 더한다." },
    ],
    "1.5": [
        { file: R + "db/migration/V2__institution_13f.sql", from: "CREATE TABLE filing_13f", lines: 11, lang: "sql", note: "13F 제출 테이블의 실제 정의. 유니크 키가 \"한 기관·한 분기에 한 건\"을 지킨다." },
    ],
    "1.6": [
        { file: R + "application.yml", from: "toss:", lines: 8, lang: "yaml", note: "바깥 시스템마다 주소와 키 자리가 설정에 따로 있다. 값은 .env에서 들어온다." },
    ],
    "1.7": [
        { file: J + "service/institution/InstitutionCacheWarmer.java", from: "@EventListener(ApplicationReadyEvent.class)", lines: 15, lang: "java", note: "앱이 뜬 직후와 배치가 끝난 직후에 캐시를 미리 채운다. 비우기보다 늦게 돌도록 @Order(100)을 단다." },
    ],
    "1.8": [
        { file: J + "config/SecurityConfig.java", from: "http.authorizeHttpRequests(auth -> auth", exact: true, lines: 10, lang: "java", note: "어느 주소가 공개인지가 이 몇 줄에 다 있다. SecurityConfigTest가 이 규칙을 고정한다." },
    ],
    "1.9": [
        { file: J + "config/RequestIdFilter.java", from: "protected void doFilterInternal", lines: 20, lang: "java", note: "모든 요청이 가장 먼저 지나는 필터. 요청 번호를 MDC에 넣고 응답 헤더로도 돌려준다." },
    ],
    "1.10": [
        { file: ".github/workflows/deploy.yml", from: "on:", lines: 11, lang: "yaml", note: "배포 워크플로는 CI가 main에서 성공했을 때만 이어서 돈다." },
    ],
    "2.1": [
        { file: J + "service/UnifiedPortfolioService.java", from: "public UnifiedPortfolioService(List<BrokerageClient>", lines: 4, lang: "java", note: "생성자로 증권사 목록을 통째로 받는다. 증권사를 더해도 이 코드는 그대로다." },
        { file: "src/test/java/com/mystock/portfolio/service/filing/FilingPrefetchServiceTest.java", from: "private FilingPrefetchService service(", lines: 3, lang: "java", note: "테스트에서는 스프링 없이 new로 가짜를 꽂는다." },
    ],
    "2.2": [
        { file: J + "external/anthropic/AnthropicProperties.java", from: "public record AnthropicProperties(", to: ") {", lang: "java", note: "관련 설정을 record 하나로 묶어 받는다. 어떤 설정이 있는지 한눈에 보인다." },
    ],
    "2.3": [
        { file: J + "service/analysis/CompanyAnalysisService.java", from: "if (cached.isPresent() && CompanyAnalysis.STATUS_RUNNING", lines: 18, lang: "java", note: "이미 도는 분석은 다시 시작하지 않고, 15분 넘게 RUNNING이면 죽은 작업으로 본다. 실제 호출은 worker.submit으로 넘긴다." },
    ],
    "2.4": [
        { file: J + "service/snapshot/PortfolioSnapshotService.java", from: "@Scheduled(cron", lines: 14, lang: "java", note: "16:10·18:10·20:10에 돈다. 재시도 회차는 오늘 것이 있으면 바로 건너뛴다." },
        { file: J + "service/filing/FilingPrefetchService.java", from: "TossStockInfo info;", lines: 15, lang: "java", note: "공시 재무 배치: 토스가 막히면 멈추고, 주식수가 없으면 덮어쓰지 않고 건너뛴다." },
    ],
    "2.5": [
        { file: J + "web/GlobalExceptionHandler.java", from: "@ExceptionHandler(AppException.class)", lines: 12, lang: "java", note: "예외가 응답으로 바뀌는 곳. 모두 { \"error\": 문장 } 한 모양이다." },
    ],
    "2.6": [
        { file: J + "web/FinancialsController.java", from: "@Tag(name", lines: 4, lang: "java", note: "@Tag 한 줄이 Swagger 화면의 묶음 이름과 설명이 된다." },
    ],
    "2.7": [
        { file: J + "service/analysis/CompanyAnalysisStore.java", from: "/**", lines: 30, lang: "java", note: "저장만 맡는 별도 빈. 주석에 왜 따로 두었는지(같은 클래스 안 호출 함정)가 적혀 있다." },
    ],
    "3.1": [
        { file: R + "db/migration/V10__portfolio_snapshot.sql", from: "CREATE TABLE portfolio_snapshot_item", lines: 14, lang: "sql", note: "ERD ④의 자식 테이블 정의. 그림의 UK가 SQL에서는 UNIQUE KEY 한 줄이다." },
    ],
    "3.2": [
        { file: J + "external/filing/FilingService.java", from: "Optional<CompanyFinancials> fetched = fetch(", lines: 11, lang: "java", note: "\"원본이 바깥에 있는\" 테이블을 캐시처럼 다루는 모습: 새로 받으면 저장하고, 못 받으면 저장본을 쓴다." },
    ],
    "3.3": [
        { file: R + "db/migration/V2__institution_13f.sql", from: "CREATE TABLE holding_13f", lines: 15, lang: "sql", note: "대리 키 id를 PK로 두고, 업무 규칙은 세 열을 묶은 UNIQUE KEY로 지킨다." },
    ],
    "3.4": [
        { file: J + "external/thirteenf/ThirteenFSyncService.java", from: "holdings.deleteByAccessionNo(old.getAccessionNo());", lines: 2, lang: "java", note: "FK 대신 코드가 순서를 지킨다. 자식(보유 줄)을 먼저 지우고 부모(제출)를 지운다." },
    ],
    "3.5": [
        { file: J + "domain/CompanyAnalysis.java", from: "★ LONGTEXT 를 직접 지정한 이유", lines: 8, lang: "java", note: "분석 본문은 JSON 그대로 LONGTEXT 열 하나에 넣는다." },
        { file: J + "domain/CompanyAnalysis.java", from: "@Column(name = \"includes_position\"", lines: 2, lang: "java", note: "공개 여부를 가르는 값은 JSON 밖의 열로 꺼냈다. 기본값은 안전한 쪽(비공개)이다." },
    ],
    "3.6": [
        { file: J + "service/analysis/CompanyAnalysisService.java", from: "private static final Duration STALE_RUNNING", lines: 1, lang: "java", note: "RUNNING이 이 시간을 넘으면 서버가 죽었던 것으로 본다." },
    ],
    "3.7": [
        { file: J + "domain/Holding13FRepository.java", from: "public interface Holding13FRepository", lines: 8, lang: "java", note: "메서드 이름만으로 쿼리가 만들어진다. 연관관계 매핑 없이 키 값으로 조회한다." },
    ],
    "3.8": [
        { file: R + "db/migration/V9__analysis_researched_at.sql", from: "ALTER TABLE", lines: 3, lang: "sql", note: "열을 더하고(expand) 기존 줄의 값을 같은 파일에서 채운다. 이미 적용한 파일은 고치지 않는다." },
    ],
    "3.9": [
        { file: "deploy/backup.sh", from: "docker compose exec -T mysql sh -c \\", lines: 10, lang: "bash", note: "잠그지 않고 덤프를 뜨고(--single-transaction), 끝줄을 확인해 중간에 끊긴 파일을 성공으로 치지 않는다." },
    ],
    "4.1": [
        { file: J + "external/toss/TossAuthService.java", from: "public String getAccessToken()", lines: 28, lang: "java", note: "메모리 → DB → 새로 발급 순서. 새로 받은 토큰은 양쪽에 저장한다." },
    ],
    "4.2": [
        { file: J + "external/thirteenf/OpenFigiClient.java", from: "if (e.getStatusCode().isSameCodeAs(HttpStatusCode.valueOf(429)))", lines: 8, lang: "java", note: "OpenFIGI가 429(너무 자주)를 주면 실패로 보지 않고 \"한도에 걸렸다\"로 따로 다룬다." },
    ],
    "4.3": [
        { file: J + "config/HttpClientResilienceConfig.java", from: "static final Duration CONNECT_TIMEOUT", lines: 2, lang: "java", note: "모든 RestClient에 걸리는 타임아웃. 스프링 부트 3.3 기본값에는 이것이 없다." },
        { file: J + "config/HttpClientResilienceConfig.java", from: ".setConnectTimeout(timeout(CONNECT_TIMEOUT))", lines: 2, lang: "java", note: "두 타임아웃은 커넥션 풀의 연결 설정에 들어가, 풀을 같이 쓰는 모든 RestClient에 걸린다(4.9)." },
    ],
    "4.4": [
        { file: J + "config/ResilientHttpInterceptor.java", from: "boolean retryable = HttpMethod.GET.equals", lines: 36, lang: "java", note: "GET만, 네트워크 오류와 5xx만 다시 보낸다. 다시 보내기 전에 앞 응답을 닫는다." },
    ],
    "4.5": [
        { file: J + "config/HttpClientResilienceConfig.java", from: "CircuitBreakerConfig config = CircuitBreakerConfig.custom()", lines: 10, lang: "java", note: "최근 20번 중 절반 실패면 30초 동안 열린다." },
    ],
    "4.6": [
        { file: J + "service/stock/StockBriefService.java", from: "try {", lines: 18, lang: "java", note: "토스가 실패해도 예외를 삼키고 그 칸만 비운다. 재무와 기관 칸은 그대로 나간다." },
    ],
    "4.7": [
        { file: J + "external/dart/DartApiClient.java", from: "static String decode(byte[] bytes)", lines: 12, lang: "java", note: "문서가 밝힌 인코딩을 믿지 않는다. UTF-8로 엄격하게 읽어 보고, 안 맞을 때만 EUC-KR." },
    ],
    "4.8": [
        { file: J + "external/anthropic/ClaudeAnalysisClient.java", from: "int cacheWriteTokens = 0;", lines: 13, lang: "java", note: "프롬프트 캐싱이 일하는지 토큰 수로 확인한다. 캐시읽기가 0이면 캐싱이 죽은 것이다." },
    ],
    "4.9": [
        { file: J + "config/HttpClientResilienceConfig.java", from: "PoolingHttpClientConnectionManager pool = PoolingHttpClientConnectionManagerBuilder.create()", to: "return pool;", lang: "java", note: "모든 RestClient가 같이 쓰는 풀 하나. 크기·대기·수명·TCP_NODELAY를 여기서 정하고, 사용량을 지표로 내보낸다." },
        { file: R + "application.yml", from: "    hikari:", to: "tcpKeepAlive: true", lang: "yaml", note: "DB에도 대기 3초·소켓 응답 60초의 상한을 둔다. 전에는 DB가 멈추면 끝없이 기다렸다." },
        { file: R + "application.yml", from: "  tomcat:", to: "max-keep-alive-requests: 1000", lang: "yaml", note: "keep-alive 60초는 앞단 Caddy의 30초보다 길어야 한다. 반대면 간헐적인 502가 난다." },
    ],
    "5.1": [
        { file: R + "application.yml", from: "server:", lines: 14, lang: "yaml", note: "세션 12시간, 쿠키 SameSite=Strict. 운영에서는 Secure도 켠다." },
    ],
    "5.2": [
        { file: R + "static/portfolio.js", from: "function apiFetch(url, options = {})", lines: 16, lang: "javascript", note: "쓰기 요청마다 XSRF-TOKEN 쿠키 값을 헤더에 실어 보낸다. 다른 사이트는 이 값을 읽을 수 없다." },
    ],
    "5.3": [
        { file: J + "config/SecurityConfig.java", from: "http.csrf(csrf -> csrf", lines: 14, lang: "java", note: "CSRF 토큰 저장소와 CSP 헤더를 설정하는 곳." },
    ],
    "5.4": [
        { file: J + "external/auth/TokenCipher.java", from: "public String encrypt(String plain, String provider, String ownerKey)", lines: 18, lang: "java", note: "매번 새 IV, 증권사·계정을 묶은 AAD, 앞에 형식 버전. 다른 줄로 옮겨 붙이면 풀리지 않는다." },
    ],
    "6.1": [
        { file: R + "static/public/stock.js", from: "const t = e.target.closest(\"[data-stock]\");", lines: 5, lang: "javascript", note: "이벤트 위임: document에 핸들러 하나만 달고, 눌린 곳에서 가장 가까운 data-stock을 찾는다." },
    ],
    "6.2": [
        { file: R + "static/public/tokens.css", from: "--bg: #f6f5f1;", lines: 18, lang: "css", note: "색은 전부 이 변수를 거친다. 빨강·파랑은 등락 전용이다." },
    ],
    "6.3": [
        { file: R + "static/portfolio.js", from: "// ★ aria-hidden 이 아니라 inert 를 쓴다.", lines: 22, lang: "javascript", note: "닫힌 드로어는 inert로 막아 Tab이 들어가지 않게 한다." },
    ],
    "7": [
        { file: "src/test/java/com/mystock/portfolio/service/analysis/CompanyAnalysisPublicTest.java", from: "@Test", lines: 22, lang: "java", note: "보안 규칙을 테스트로 고정한 예. 이름이 한글 문장이라 깨지면 무엇이 깨졌는지 바로 안다." },
    ],
    "8.1": [
        { file: "pom.xml", from: "API 문서 자동 생성 (springdoc-openapi).", lines: 17, lang: "xml", note: "의존성마다 왜 이 버전인지 주석을 단다. springdoc는 스프링 부트 3.3과 맞는 마지막 버전에 묶어 두었다." },
    ],
    "8.2": [
        { file: "Dockerfile", from: "FROM eclipse-temurin:21-jdk AS build", lines: 28, lang: "dockerfile", note: "빌드 단계와 실행 단계가 나뉜다. 실행 이미지에는 JRE와 jar만 들어가고, 일반 사용자로 돈다." },
    ],
    "8.3": [
        { file: "docker-compose.yml", from: "depends_on:", lines: 4, lang: "yaml", note: "DB가 접속을 받을 준비가 된 뒤에 앱을 띄운다." },
    ],
    "9.1": [
        { code: "git switch -c feat/무언가\ngit commit -m \"feat: 무엇을 왜\"\ngit push -u origin HEAD\ngh pr create --fill\ngh pr checks --watch\ngh pr merge --squash", lang: "bash", title: "이 저장소에서 변경을 합치는 순서", note: "main에 직접 밀 수 없다. CI 셋이 모두 초록이어야 스쿼시 병합된다." },
    ],
    "9.2": [
        { file: ".github/workflows/ci.yml", from: "- name: 커버리지 최소선", lines: 5, lang: "yaml", note: "검사가 정말 돌았는지 문구로 확인한다. 실행 데이터가 없으면 jacoco:check는 건너뛰고도 통과하기 때문이다." },
    ],
    "9.3": [
        { file: ".github/workflows/deploy.yml", from: "#   DEPLOY_SSH_KEY", lines: 2, lang: "yaml", note: "서버 접속 키와 호스트 키는 GitHub Secrets에만 있다. 호스트 키를 고정해 중간자 공격을 막는다." },
    ],
    "10.1": [
        { file: "deploy/server-setup.sh", from: "# 오라클 우분투 이미지는 iptables", lines: 12, lang: "bash", note: "클라우드 콘솔에서 포트를 열어도 OS 방화벽이 또 막는다. 80·443을 iptables에 연다." },
    ],
    "10.2": [
        { file: "deploy/Caddyfile", from: "{$DOMAIN}", lines: 21, lang: "plaintext", note: "Caddy 설정의 거의 전부다. 도메인만 적으면 인증서를 알아서 받는다." },
    ],
    "10.3": [
        { file: "deploy/deploy.sh", from: "local deadline=$((SECONDS + TIMEOUT)) dead=0", lines: 16, lang: "bash", note: "벽시계(SECONDS)로 기다리고, 컨테이너가 계속 죽으면 일찍 실패로 판정한다." },
    ],
    "11.1": [
        { file: R + "application.yml", from: "management:", lines: 17, lang: "yaml", note: "관리 주소를 8081로 떼고, readiness에 DB 검사를 넣는다." },
    ],
    "11.2": [
        { file: "deploy/monitoring/alerts.yml", from: "- alert: CircuitBreakerOpen", lines: 8, lang: "yaml", note: "알림 규칙 하나. for로 지속 시간을 두어 한 번 튄 값으로는 울리지 않는다." },
    ],
    "11.3": [
        { file: J + "common/LogContext.java", from: "public static Runnable carry(Runnable task)", lines: 12, lang: "java", note: "다른 스레드로 일을 넘길 때 요청 번호를 복사해 가고, 끝나면 치운다." },
    ],
    "11.4": [
        { file: "deploy/backup.sh", from: "if ! gunzip -c", lines: 5, lang: "bash", note: "덤프 끝줄에 Dump completed가 없으면 실패로 본다. 끊긴 백업은 백업이 아니다." },
    ],
    "11.5": [
        { file: "docs/운영.md", from: "### 장애 기록 양식", lines: 20, lang: "markdown", note: "장애를 겪을 때마다 이 양식으로 남긴다." },
    ],
    "12": [
        { file: "CLAUDE.md", from: "| 파일 | 넣을 것 |", lines: 10, lang: "markdown", note: "문서를 수명으로 나눈 표. 새 내용은 \"이게 언제 바뀌나\"로 넣을 곳을 고른다." },
    ],
    "15.1": [
        { file: J + "config/RequestIdFilter.java", from: "private static final Pattern SAFE", lines: 1, lang: "java", note: "바깥에서 온 번호는 이 모양일 때만 받는다. 줄바꿈으로 가짜 로그 줄을 끼우는 공격을 막는다." },
    ],
    "15.2": [
        { file: J + "service/institution/InstitutionCacheWarmer.java", from: "@EventListener(ThirteenFDataChangedEvent.class)", lines: 6, lang: "java", note: "비우기(순서 0)보다 늦게 채우도록 순서를 100으로 둔다." },
    ],
    "15.3": [
        { file: "pom.xml", from: "<rules>", to: "</rules>", lang: "xml", note: "커버리지 최소선. 계산 코드 패키지에만 높은 선을 건다." },
    ],
    "15.4": [
        { file: J + "service/snapshot/PortfolioSnapshotStore.java", from: "@Transactional", lines: 20, lang: "java", note: "한 트랜잭션에서 합계를 고쳐 쓰고 그날 줄을 지우고 새로 넣는다. 몇 번 돌려도 한 벌이다." },
    ],
    "15.5": [
        { file: R + "static/portfolio.js", from: "function renderHistory(points)", lines: 24, lang: "javascript", note: "선에는 색을 쓰지 않는다. 캔버스는 CSS 변수를 못 읽어 그릴 때 읽어 넘긴다." },
    ],
};

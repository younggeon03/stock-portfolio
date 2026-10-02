package com.mystock.portfolio.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * 공개 영역과 내 영역을 가른다.
 *
 * ★ 왜 필요한가
 * 서버에 올리면 주소만 알면 누구나 들어온다. 지금 첫 화면은 내 잔고이고, 기업분석 버튼은 한 번에 800원이 넘는다.
 * 기관 포트폴리오처럼 누구에게 보여줘도 되는 공개 자료만 열고, 나머지는 로그인 뒤로 둔다.
 *
 * ★ 공개 (로그인 없이)
 *   - 공개 화면과 그 파일 (/, /index.html, /public/**)
 *   - 기관 13F 조회 GET /api/institutions/** (단 /sync 는 아님), 공개 API /api/public/**, 피드 /feeds/**
 *   - 헬스체크·지표 /actuator/** (관리 포트 8081 에만 있고 바깥에 열지 않는다)
 * ★ 내 영역 (로그인)
 *   - 그 밖의 전부. 잔고·증권사·분석·스크린샷·뉴스·Swagger·13F 수동 받기
 *
 * ★ 계정은 하나다
 * 이 앱은 여러 사람이 각자 계좌를 연결하는 서비스가 아니다(결정기록 004). 그래서 회원 테이블 없이
 * 환경변수의 아이디·비밀번호 하나만 둔다. 비밀번호는 기동할 때 bcrypt 로 바꿔 메모리에만 둔다.
 *
 * ★ 비밀번호가 비면 전부 연다
 * 내 PC 에서 개발할 때 매번 로그인하게 하면 불편하다. 대신 기동 로그에 경고를 남긴다. 서버에서는 반드시 넣는다.
 *
 * ★ CSRF
 * 로그인 쿠키가 있는 상태에서 다른 사이트가 내 브라우저로 "분석 실행" 을 보내면 돈이 나간다.
 * 쿠키를 SameSite=Strict 로 두고(application.yml), 그 위에 CSRF 토큰을 둔다.
 * 토큰은 XSRF-TOKEN 쿠키로 내려가고 화면 JS 가 POST 마다 X-XSRF-TOKEN 헤더로 돌려보낸다.
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final String username;
    private final String password;

    public SecurityConfig(@Value("${security.owner.username:owner}") String username,
                          @Value("${security.owner.password:}") String password) {
        this.username = username;
        this.password = password;
    }

    /** 로그인을 요구하는지. 비밀번호가 있을 때만 */
    public boolean loginRequired() {
        return StringUtils.hasText(password);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);

        if (!loginRequired()) {
            log.warn("OWNER_PASSWORD 가 없어 로그인 없이 전부 열어 둡니다. 서버에서는 반드시 넣으세요 (docs/운영.md)");
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }

        http.authorizeHttpRequests(auth -> auth
                        // 13F 수동 받기와 진행 상황은 내 것. 아래의 GET 공개보다 먼저 와야 한다
                        .requestMatchers("/api/institutions/sync").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/institutions", "/api/institutions/**").permitAll()
                        .requestMatchers("/api/public/**", "/feeds/**").permitAll()
                        .requestMatchers("/", "/index.html", "/public/**", "/robots.txt", "/favicon.ico",
                                "/error", "/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info",
                                "/actuator/prometheus").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form.defaultSuccessUrl("/portfolio.html", false))
                .logout(logout -> logout.logoutSuccessUrl("/"))
                // API 는 로그인 화면으로 넘기지 않고 401 을 준다. fetch 는 넘겨받은 HTML 을 JSON 으로 못 읽는다
                .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), new AntPathRequestMatcher("/api/**")));
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        // 비밀번호가 없으면 쓰이지 않지만, 빈이 없으면 스프링이 임의 비밀번호를 만들어 로그에 찍는다
        String raw = loginRequired() ? password : java.util.UUID.randomUUID().toString();
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password(encoder.encode(raw))
                .roles("OWNER")
                .build());
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 화면 JS 가 헤더로 보낸 토큰은 그대로 비교하고, 로그인 폼처럼 숨은 칸으로 보낸 토큰은 XOR 로 푼다.
     * 스프링 시큐리티 6.3 문서의 "SPA" 설정 그대로다 (6.4 부터는 csrf.spa() 한 줄).
     */
    static final class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {
        private final CsrfTokenRequestHandler delegate = new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
            delegate.handle(request, response, csrfToken);
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
            if (StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))) {
                return super.resolveCsrfTokenValue(request, csrfToken);
            }
            return delegate.resolveCsrfTokenValue(request, csrfToken);
        }
    }

    /** 토큰은 처음 쓰일 때 만들어진다. 화면이 첫 POST 전에 쿠키를 갖고 있도록 매 요청 꺼내 둔다 */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}

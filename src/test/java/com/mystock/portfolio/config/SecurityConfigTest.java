package com.mystock.portfolio.config;

import com.mystock.portfolio.external.thirteenf.ThirteenFSyncService;
import com.mystock.portfolio.service.UnifiedPortfolioService;
import com.mystock.portfolio.service.institution.InstitutionPortfolioService;
import com.mystock.portfolio.web.InstitutionController;
import com.mystock.portfolio.web.UnifiedPortfolioController;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 누가 무엇을 볼 수 있는지. 서버에 올리면 이 규칙 하나가 내 계좌를 지킨다.
 * 컨트롤러 뒤의 서비스는 가짜다. DB·외부 API 를 부르지 않는다.
 */
class SecurityConfigTest {

    @Nested
    @WebMvcTest(controllers = {InstitutionController.class, UnifiedPortfolioController.class})
    @Import(SecurityConfig.class)
    @TestPropertySource(properties = "security.owner.password=test-only-password")
    class 비밀번호가_있으면 {

        @Autowired
        MockMvc mvc;

        @MockBean
        InstitutionPortfolioService institutionService;
        @MockBean
        ThirteenFSyncService syncService;
        @MockBean
        UnifiedPortfolioService portfolioService;

        @Test
        void 기관_포트폴리오는_로그인_없이_본다() throws Exception {
            when(institutionService.list()).thenReturn(List.of());

            mvc.perform(get("/api/institutions")).andExpect(status().isOk());
        }

        @Test
        void 내_잔고_API_는_로그인_없이_401_이다() throws Exception {
            // 로그인 화면으로 넘기면 fetch 가 HTML 을 JSON 으로 읽다 깨진다. 401 이어야 화면이 로그인으로 보낸다
            mvc.perform(get("/api/portfolio/unified")).andExpect(status().isUnauthorized());
        }

        @Test
        void 내_화면은_브라우저면_로그인_화면으로_넘긴다() throws Exception {
            // 브라우저는 Accept: text/html 을 보낸다. 그때만 로그인 화면으로 넘기고, 그 밖의 클라이언트는 401
            mvc.perform(get("/portfolio.html").accept(org.springframework.http.MediaType.TEXT_HTML))
                    .andExpect(status().is3xxRedirection());
        }

        @Test
        void 기관_수동_받기와_진행_상황은_내_것이다() throws Exception {
            mvc.perform(post("/api/institutions/sync").with(csrf())).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/institutions/sync")).andExpect(status().isUnauthorized());
        }

        @Test
        void 로그인해도_CSRF_토큰_없는_POST_는_막는다() throws Exception {
            // 다른 사이트가 내 브라우저로 버튼을 누르게 하는 공격. 토큰이 없으면 403
            mvc.perform(post("/api/institutions/sync").with(user("owner"))).andExpect(status().isForbidden());
        }

        @Test
        void 로그인하고_CSRF_토큰이_있으면_된다() throws Exception {
            when(syncService.startAsync()).thenReturn(true);

            mvc.perform(post("/api/institutions/sync").with(user("owner")).with(csrf()))
                    .andExpect(status().isAccepted());
        }
    }

    @Nested
    @WebMvcTest(controllers = UnifiedPortfolioController.class)
    @Import(SecurityConfig.class)
    @TestPropertySource(properties = "security.owner.password=")
    class 비밀번호가_없으면 {

        @Autowired
        MockMvc mvc;

        @MockBean
        UnifiedPortfolioService portfolioService;

        @Test
        void 내_PC_개발용으로_전부_연다() throws Exception {
            mvc.perform(get("/api/portfolio/unified")).andExpect(status().isOk());
        }
    }
}

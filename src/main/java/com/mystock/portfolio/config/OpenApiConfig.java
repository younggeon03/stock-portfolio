package com.mystock.portfolio.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API 문서(Swagger UI)의 첫 화면에 들어갈 설명.
 *
 * ★ 왜 이 클래스가 필요한가
 * 주소 목록·파라미터·응답 형태는 springdoc 이 컨트롤러를 읽어 알아서 만든다.
 * 하지만 코드만 봐서는 절대 알 수 없는 것들이 있다.
 *   - 어떤 주소가 돈을 쓰는지
 *   - X-Owner-Key 헤더가 무슨 뜻인지
 *   - 주문 API 가 "빠진" 게 아니라 "일부러 안 만든" 것인지
 * 그 셋을 여기 적어서 문서를 여는 사람이 가장 먼저 보게 한다.
 *
 * 화면: http://localhost:8080/swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI portfolioOpenApi() {
        return new OpenAPI().info(new Info()
                .title("포트폴리오 분석 API")
                .version("v1")
                .description("""
                        토스증권과 나무증권 보유종목을 합쳐서 보는 개인용 API 입니다.

                        ## 주문 기능은 없습니다

                        빠진 게 아니라 일부러 만들지 않았습니다.
                        `BrokerageClient` 인터페이스에 주문 메서드를 정의하지 않았기 때문에
                        컨트롤러에서 부를 대상 자체가 없습니다.

                        ## 돈이 나가는 주소는 두 개뿐입니다

                        - `POST /api/analysis/{symbol}` — 1회 $0.5~2, 2~5분 걸립니다
                        - `POST /api/import/screenshot` — 1장에 수십 원

                        나머지는 전부 0원입니다. 화면을 고치는 동안에는
                        `POST /api/import/screenshot/sample` 을 쓰세요.
                        클로드를 부르지 않고 같은 모양의 가짜 결과를 돌려줍니다.

                        ## X-Owner-Key 헤더

                        직접 입력·스크린샷 관련 주소는 이 헤더로 사람을 구분합니다.
                        회원가입 대신 브라우저가 처음 들어올 때 만든 임의의 값입니다.
                        빠뜨리면 남의 목록을 보거나 덮어쓸 수 있으니 항상 붙여 보내세요.

                        ## 실패했을 때

                        `{ "error": "사람이 읽고 바로 조치할 수 있는 한국어 설명" }` 형태로 옵니다.
                        토스 403 은 지금 이 컴퓨터의 공인 IP 까지 알려줍니다.
                        """));
    }
}

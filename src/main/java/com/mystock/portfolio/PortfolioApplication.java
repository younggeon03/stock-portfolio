package com.mystock.portfolio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// @ConfigurationPropertiesScan: TossApiProperties 처럼 @ConfigurationProperties 가 붙은 클래스를
// 자동으로 찾아 빈으로 등록해준다. 이게 없으면 설정값이 주입되지 않는다.
@SpringBootApplication
@ConfigurationPropertiesScan
public class PortfolioApplication {

    public static void main(String[] args) {
        // Spring 컨텍스트가 뜨기 전에 .env 파일을 읽어 System Property 로 먼저 등록한다.
        EnvFileLoader.loadIntoSystemProperties(".env");
        SpringApplication.run(PortfolioApplication.class, args);
    }
}

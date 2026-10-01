package com.mystock.portfolio.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 정해진 시각에 도는 배치(@Scheduled)를 켠다.
 *
 * 지금은 13F 받기 하나다. 배치는 앱이 떠 있을 때만 돈다. 내 PC 에서는 꺼져 있는 날이 많아
 * 하루 이틀 늦게 받을 수 있고, 그래도 문제가 없게 "받은 접수번호는 건너뛰기" 로 만들어 두었다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}

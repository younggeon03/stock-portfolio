package com.mystock.portfolio.external.thirteenf;

import java.time.LocalDate;

/**
 * 13F 를 새로 받아 저장을 마쳤다는 알림.
 *
 * 받는 쪽(알림 발송)과 받기(배치)를 떼어 놓으려고 이벤트로 낸다.
 * 텔레그램이 죽어도 13F 받기는 계속돼야 하고, 알림 채널을 늘려도 배치 코드는 그대로여야 한다.
 */
public record NewFilingEvent(long cik, String institutionName, LocalDate reportPeriod, LocalDate filedDate,
                             int holdingCount) {
}

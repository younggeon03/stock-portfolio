package com.mystock.portfolio.external.toss;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * 지금 내 공인 IP 가 뭔지 알아내는 도구.
 *
 * ★ 왜 필요한가
 * 토스는 "허용 IP 관리" 에 등록된 IP 에서 온 요청만 받아준다. 등록 안 된 곳에서 오면 403 이다.
 * 그런데 학교/회사/집 인터넷은 IP 가 수시로 바뀐다. 바뀌면 어제까지 되던 게 갑자기 403 이 된다.
 *
 * 이때 "IP 를 등록하세요" 라고만 하면 사용자가 또 IP 를 찾아봐야 한다.
 * 그래서 403 이 나는 순간 현재 IP 를 직접 조회해서 "이 값을 등록하세요" 라고 알려준다.
 *
 * 실패해도 문제없도록 Optional 로 돌려준다. (IP 조회가 안 된다고 앱이 죽으면 안 되니까)
 */
@Component
public class PublicIpLookup {

    private static final Logger log = LoggerFactory.getLogger(PublicIpLookup.class);

    /** IP 만 한 줄로 돌려주는 공개 서비스 */
    private static final String IP_LOOKUP_URL = "https://api.ipify.org";

    private final RestClient publicDataRestClient;

    // 이 프로젝트에는 RestClient 빈이 두 개(토스용, 공개데이터용) 있어서
    // @Qualifier 로 "공개 데이터용" 을 콕 집어 주입받는다.
    public PublicIpLookup(@Qualifier("publicDataRestClient") RestClient publicDataRestClient) {
        this.publicDataRestClient = publicDataRestClient;
    }

    /** 현재 공인 IP. 조회 실패하면 빈 Optional. */
    public Optional<String> currentIp() {
        try {
            String ip = publicDataRestClient.get()
                    .uri(IP_LOOKUP_URL)
                    .retrieve()
                    .body(String.class);

            if (ip == null || ip.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(ip.trim());

        } catch (Exception e) {
            // 여기서 실패해도 원래 에러 메시지는 그대로 나가야 하므로 조용히 넘어간다
            log.warn("공인 IP 조회에 실패했습니다: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 403 안내문에 덧붙일 문장을 만든다.
     * IP 조회에 성공하면 "현재 IP 는 ... 입니다" 를, 실패하면 직접 확인하라는 안내를 돌려준다.
     */
    public String registerGuide() {
        return currentIp()
                .map(ip -> "지금 이 컴퓨터의 공인 IP 는 [" + ip + "] 입니다. 이 값을 등록하세요. "
                        + "(인터넷 회선에 따라 IP 가 주기적으로 바뀝니다. 바뀌면 다시 등록해야 합니다)")
                .orElse("https://ifconfig.me 에서 현재 IP 를 확인한 뒤 등록하세요.");
    }
}

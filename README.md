# 두 증권사를 합쳐서 보는 포트폴리오 분석기

[![CI](https://github.com/younggeon03/stock-portfolio/actions/workflows/ci.yml/badge.svg)](https://github.com/younggeon03/stock-portfolio/actions/workflows/ci.yml)

토스증권과 나무증권 계좌를 하나로 합쳐 **실제 비중**을 계산하고, 공시 재무와 함께 종목을 정리하는 서버입니다. 조회 전용이며 주문 기능은 없습니다.

> 같은 SOXL을 두 곳에 나눠 들고 있었는데, 토스 앱에서는 비중 2.98%였고 합치면 81.47%였습니다. 한쪽만 보면 정반대로 판단하게 됩니다.

![드로어와 캔들차트](docs/images/드로어-차트.png)

## 주요 기능

- 두 증권사 보유종목 합산과 실제 비중, 해외주식 원화 환산
- 공시 재무(DART·SEC EDGAR)로 PER·PBR을 앱이 직접 계산, 일봉 캔들차트
- 기관 투자자 포트폴리오(SEC 13F)·배당 캘린더 공개 화면
- 내 보유 상태를 반영한 기업분석(Claude + 웹 검색, 버튼을 눌러야만 실행)

## 운영 중 해결한 문제

| 문제 | 해결 | 결과 |
|---|---|---|
| 공개 조회가 요청마다 계산을 다시 해 동시 접속에 느려짐 | 결과를 캐시하고 배치가 데이터를 바꿀 때만 비움 | 동시 30명 p95 8.8초 → 0.099초 (k6) |
| 환율 조회가 실패하면 기본값 1이 들어가 해외주식이 1,380배 작게 표시 | 기본값 대신 예외를 던지도록 바꿈 | 틀린 숫자가 조용히 화면에 나가지 않음 |
| 업그레이드할 때마다 13초간 502 | 앞단 Caddy가 재시작 동안 요청을 잡아 둠 | 업그레이드 중 실패 응답 0 |

## 기술

| | |
|---|---|
| 백엔드 | Java 21 · Spring Boot 3.3 · JPA · MySQL · Flyway |
| 외부 연동 | 토스증권·나무증권 Open API · DART · SEC EDGAR · Anthropic |
| 품질·배포 | JUnit 5 테스트 239개 · GitHub Actions · Docker · GHCR |
| 운영 | Prometheus · Grafana · Alertmanager → 텔레그램 |

## 실행

```bash
cp .env.example .env         # 키를 채웁니다
docker compose up -d --build # 앱 + MySQL → http://localhost:8080
```

## 문서

- [아키텍처](docs/아키텍처.md) — 구조, 화면, 기능별 상태
- [결정기록](docs/결정기록.md) — 되돌리기 어려운 판단과 이유
- [운영](docs/운영.md) — 실행·배포·장애 대응

개인 프로젝트입니다. 투자 판단의 근거로 쓰라고 만든 것이 아닙니다.

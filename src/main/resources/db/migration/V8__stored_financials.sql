-- 공시 재무(DART·SEC)를 주가 없이 저장한다. 예전에는 메모리 캐시(6시간)뿐이라 재시작하면 다시 받았다.
-- PER·PBR 은 주가로 매일 바뀌어 저장하지 않고 읽을 때 계산한다. 결산일 종가로 잰 과거 배수(history)는 함께 저장한다.
CREATE TABLE stored_financials (
  symbol varchar(20) NOT NULL,
  market_country varchar(2) NOT NULL,
  financials_json longtext NOT NULL,
  has_history bit(1) NOT NULL,
  fetched_at datetime(6) NOT NULL,
  PRIMARY KEY (symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

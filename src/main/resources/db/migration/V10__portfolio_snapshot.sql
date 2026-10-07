-- 장 마감 뒤 하루 한 번 남기는 보유 현황 스냅샷과, 증권사 합계와의 대사 결과.
-- 매일 수익률 이력을 쌓고, 앱이 더한 합계가 증권사 합계와 어긋나는 날을 기록한다(PortfolioSnapshotService).
--
-- ★ 날짜가 키다. 같은 날 다시 돌리면(재시도·수동 실행) 그날 줄을 지우고 새로 쓴다 — 두 번 돌아도 한 벌만 남는다.
-- 과거 날짜는 다시 만들 수 없다. 증권사는 "지금" 잔고만 준다. 그래서 하루에 세 번(16·18·20시) 시도한다.

CREATE TABLE portfolio_snapshot (
  snapshot_date date NOT NULL,
  taken_at datetime(6) NOT NULL,
  total_value_krw decimal(20,2) NOT NULL,
  total_purchase_krw decimal(20,2) NOT NULL,
  profit_loss_krw decimal(20,2) NOT NULL,
  profit_rate_percent decimal(9,2) NOT NULL,
  item_count int NOT NULL,
  -- 대사에서 어긋난 줄 수. 0 이면 증권사 합계와 다 맞았다
  mismatch_count int NOT NULL,
  PRIMARY KEY (snapshot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE portfolio_snapshot_item (
  id bigint NOT NULL AUTO_INCREMENT,
  snapshot_date date NOT NULL,
  symbol varchar(20) NOT NULL,
  name varchar(150) NOT NULL,
  currency varchar(3) NOT NULL,
  quantity decimal(20,6) NOT NULL,
  last_price decimal(20,4) NOT NULL,
  market_value_krw decimal(20,2) NOT NULL,
  purchase_krw decimal(20,2) NOT NULL,
  weight_percent decimal(7,2) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_snapshot_item_date_symbol (snapshot_date, symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE reconciliation_result (
  id bigint NOT NULL AUTO_INCREMENT,
  snapshot_date date NOT NULL,
  broker varchar(20) NOT NULL,
  -- 증권사가 합계를 나눠 준 단위. 예: KRW, USD, 국내, 해외(원화)
  scope varchar(20) NOT NULL,
  currency varchar(3) NOT NULL,
  broker_total decimal(20,4) NOT NULL,
  app_sum decimal(20,4) NOT NULL,
  difference decimal(20,4) NOT NULL,
  matched bit(1) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_reconciliation_date_scope (snapshot_date, broker, scope)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 배당 캘린더. DART "현금ㆍ현물배당결정" 공시에서 꺼낸 값을 남긴다.
-- 공시는 한 번 나오면 안 바뀐다(정정은 새 접수번호로 나온다). 그래서 접수번호를 키로 두고 다시 받지 않는다.

CREATE TABLE dividend_event (
  rcept_no varchar(14) NOT NULL,
  stock_code varchar(6) NOT NULL,
  corp_name varchar(150) NOT NULL,
  kind varchar(20) DEFAULT NULL,
  cash_type varchar(20) DEFAULT NULL,
  per_share_common decimal(15,2) NOT NULL,
  per_share_preferred decimal(15,2) DEFAULT NULL,
  yield_common decimal(7,2) DEFAULT NULL,
  record_date date NOT NULL,
  pay_date date DEFAULT NULL,
  board_date date DEFAULT NULL,
  announced_date date NOT NULL,
  correction bit(1) NOT NULL,
  fetched_at datetime(6) NOT NULL,
  PRIMARY KEY (rcept_no),
  KEY idx_dividend_event_stock (stock_code, record_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 종목마다 DART 공시 목록을 마지막으로 본 때. 하루에 한 번만 다시 본다 (DART 하루 2만 건 한도)
CREATE TABLE dividend_fetch (
  stock_code varchar(6) NOT NULL,
  fetched_at datetime(6) NOT NULL,
  PRIMARY KEY (stock_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

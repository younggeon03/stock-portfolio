-- 첫 스키마. 2026-10-01 까지 Hibernate(ddl-auto: update)가 만들어 온 테이블을 그대로 옮겼다.
--
-- 이미 테이블이 있는 DB(내 PC)는 이 파일을 실행하지 않고 "V1 까지 적용됨" 으로만 표시한다
-- (spring.flyway.baseline-on-migrate). 빈 DB(도커, 서버)에서만 실제로 실행된다.
-- 그래서 이 파일은 지금 운영 중인 DB 와 한 글자도 다르면 안 된다. 바꿀 게 생기면 V2 를 새로 만든다.
--
-- 제약조건 이름 중 UK7u6q... 는 Hibernate 가 지은 이름이다. 기존 DB 와 똑같이 두어야
-- 나중에 V2 에서 이름으로 지울 때 두 환경에서 같은 SQL 이 통한다.

CREATE TABLE company_analysis (
  id bigint NOT NULL AUTO_INCREMENT,
  analysis_json longtext,
  analyzed_at datetime(6) DEFAULT NULL,
  input_tokens int DEFAULT NULL,
  last_error varchar(500) DEFAULT NULL,
  model varchar(50) DEFAULT NULL,
  name varchar(100) DEFAULT NULL,
  output_tokens int DEFAULT NULL,
  status varchar(10) NOT NULL,
  symbol varchar(20) NOT NULL,
  updated_at datetime(6) NOT NULL,
  web_search_count int DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY UK7u6q6deemks6f2yl8nu2bx5yg (symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE dart_corp_code (
  stock_code varchar(6) NOT NULL,
  corp_code varchar(8) NOT NULL,
  corp_name varchar(150) NOT NULL,
  updated_at datetime(6) NOT NULL,
  PRIMARY KEY (stock_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE manual_holding (
  id bigint NOT NULL AUTO_INCREMENT,
  average_purchase_price decimal(20,4) NOT NULL,
  currency varchar(3) NOT NULL,
  market_country varchar(2) NOT NULL,
  name varchar(100) NOT NULL,
  owner_key varchar(64) NOT NULL,
  quantity decimal(20,6) NOT NULL,
  symbol varchar(20) NOT NULL,
  updated_at datetime(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_manual_holding_owner_symbol (owner_key, symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE oauth_token (
  id bigint NOT NULL AUTO_INCREMENT,
  access_token text NOT NULL,
  expires_at datetime(6) NOT NULL,
  issued_at datetime(6) NOT NULL,
  owner_key varchar(64) NOT NULL,
  provider varchar(20) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_oauth_token_provider_owner (provider, owner_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sec_cik (
  ticker varchar(20) NOT NULL,
  cik bigint NOT NULL,
  title varchar(200) NOT NULL,
  updated_at datetime(6) NOT NULL,
  PRIMARY KEY (ticker)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE symbol_master (
  id bigint NOT NULL AUTO_INCREMENT,
  currency varchar(3) NOT NULL,
  market varchar(20) NOT NULL,
  market_country varchar(2) NOT NULL,
  name varchar(150) NOT NULL,
  search_name varchar(150) NOT NULL,
  security_type varchar(30) DEFAULT NULL,
  symbol varchar(20) NOT NULL,
  updated_at datetime(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_symbol_master_symbol (symbol),
  KEY idx_symbol_master_search (search_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

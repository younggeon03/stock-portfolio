-- 기관 투자자 포트폴리오 (SEC 13F).
--
-- 미국 주식을 1억 달러 넘게 굴리는 기관은 분기가 끝나고 45일 안에 보유 종목을 SEC 에 낸다(13F-HR).
-- 공개 자료라 누구에게 보여줘도 된다. 매일 아침 새로 낸 게 있는지 보고 최근 8분기만 둔다.

-- 따라가는 기관. 목록을 바꾸려면 새 마이그레이션으로 넣거나 active 를 끈다
CREATE TABLE institution (
  cik bigint NOT NULL,
  name varchar(150) NOT NULL,
  name_ko varchar(100) NOT NULL,
  manager varchar(100) DEFAULT NULL,
  note varchar(200) DEFAULT NULL,
  sort_order int NOT NULL,
  active bit(1) NOT NULL,
  PRIMARY KEY (cik)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 분기마다 낸 13F 한 건. 같은 분기에 원본은 하나다. 정정본(13F-HR/A)은 아직 받지 않는다
CREATE TABLE filing_13f (
  accession_no varchar(25) NOT NULL,
  cik bigint NOT NULL,
  report_period date NOT NULL,
  filed_date date NOT NULL,
  total_value_usd bigint NOT NULL,
  holding_count int NOT NULL,
  fetched_at datetime(6) NOT NULL,
  PRIMARY KEY (accession_no),
  UNIQUE KEY uk_filing_13f_cik_period (cik, report_period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 13F 의 보유 한 줄. 원본은 같은 종목이 운용역마다 여러 줄로 나뉘어 있어서 CUSIP·풋콜 단위로 합쳐 넣는다.
-- put_call 은 주식이면 빈 문자열이다 (NULL 이면 유니크 키가 중복을 못 막는다)
CREATE TABLE holding_13f (
  id bigint NOT NULL AUTO_INCREMENT,
  accession_no varchar(25) NOT NULL,
  cusip varchar(9) NOT NULL,
  issuer_name varchar(200) NOT NULL,
  title_of_class varchar(100) DEFAULT NULL,
  put_call varchar(4) NOT NULL,
  share_type varchar(3) DEFAULT NULL,
  shares bigint NOT NULL,
  value_usd bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_holding_13f_filing_cusip (accession_no, cusip, put_call),
  KEY idx_holding_13f_cusip (cusip)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- CUSIP → 티커. 13F 는 티커를 안 주고 CUSIP 만 준다. OpenFIGI 로 한 번 바꾸면 여기 남겨 다시 묻지 않는다.
-- ticker 가 NULL 이면 찾아봤지만 없었다는 뜻이다 (채권·워런트 등)
CREATE TABLE cusip_ticker (
  cusip varchar(9) NOT NULL,
  ticker varchar(20) DEFAULT NULL,
  figi_name varchar(200) DEFAULT NULL,
  security_type varchar(50) DEFAULT NULL,
  resolved_at datetime(6) NOT NULL,
  PRIMARY KEY (cusip)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- CIK 는 2026-10-01 에 EDGAR 에서 확인했다
INSERT INTO institution (cik, name, name_ko, manager, note, sort_order, active) VALUES
  (1608046, 'National Pension Service', '국민연금공단', NULL, '미국 주식만. 국내 주식은 13F 에 없다', 10, b'1'),
  (1067983, 'Berkshire Hathaway Inc', '버크셔 해서웨이', '워런 버핏', NULL, 20, b'1'),
  (1336528, 'Pershing Square Capital Management', '퍼싱 스퀘어', '빌 애크먼', NULL, 30, b'1'),
  (1649339, 'Scion Asset Management', '사이언 애셋', '마이클 버리', '2025년 3분기를 끝으로 13F 제출을 멈췄다', 40, b'1'),
  (1350694, 'Bridgewater Associates', '브리지워터', '레이 달리오', NULL, 50, b'1'),
  (1697748, 'ARK Investment Management', 'ARK 인베스트', '캐시 우드', NULL, 60, b'1'),
  (1536411, 'Duquesne Family Office', '듀케인 패밀리 오피스', '스탠리 드러켄밀러', NULL, 70, b'1'),
  (1656456, 'Appaloosa LP', '아팔루사', '데이비드 테퍼', NULL, 80, b'1'),
  (1065521, 'SoftBank Group Corp', '소프트뱅크 그룹', '손정의', NULL, 90, b'1'),
  (1374170, 'Norges Bank', '노르웨이 중앙은행 (국부펀드)', NULL, '보유 종목이 수천 개라 처음 티커를 찾는 데 오래 걸린다', 100, b'1');

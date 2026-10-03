-- 따라가는 기관을 "인덱스 제외 큰 10곳" 으로 바꾼다 (2026-10-03, 결정기록 013).
--
-- 기준: SEC 2026년 2분기 13F 데이터셋의 보고액(TABLEVALUETOTAL) 상위에서
--   ① 인덱스 운용사(블랙록·뱅가드·스테이트스트리트·지오드·노던트러스트·슈왑·인베스코·디멘셔널 등)
--   ② 은행·증권·자산관리 플랫폼(모건스탠리·JP모건·골드만·UBS 등. 고객 자산이 대부분이다)
--   ③ 마켓메이커·헤지된 멀티전략/퀀트(서스퀘하나·제인스트리트·시타델·밀레니엄·AQR·DE쇼 등. 보유가 헤지라 견해가 아니다)
--   를 빼고, 같은 그룹은 가장 큰 부문 하나만 남긴 위에서 10곳.
-- 괄호 안은 2026년 2분기 13F 보고액(달러). CIK 는 같은 데이터셋에서 확인했다.
--
-- 빠지는 기관은 지우지 않고 active 만 끈다. 받아 둔 13F 도 그대로 남아 되돌릴 수 있다.

UPDATE institution SET active = b'0'
 WHERE cik IN (1608046, 1336528, 1649339, 1350694, 1697748, 1536411, 1656456, 1065521);

INSERT INTO institution (cik, name, name_ko, manager, note, sort_order, active) VALUES
  (315066,  'FMR LLC', '피델리티 (FMR)', NULL, '피델리티 펀드 전체. 인덱스 펀드도 일부 섞여 있다', 10, b'1'),           -- 2.30조
  (1422849, 'Capital World Investors', '캐피털 그룹 (월드 인베스터스)', NULL,
            '아메리칸 펀드를 운용하는 캐피털 그룹의 세 부문 중 가장 큰 곳', 30, b'1'),                                   -- 8,460억
  (902219,  'Wellington Management Group LLP', '웰링턴 매니지먼트', NULL, NULL, 40, b'1'),                             -- 5,800억
  (38777,   'Franklin Resources Inc', '프랭클린 템플턴', NULL, NULL, 50, b'1'),                                       -- 4,620억
  (850529,  'Fisher Asset Management, LLC', '피셔 인베스트먼트', '켄 피셔', NULL, 60, b'1'),                           -- 3,360억
  (912938,  'Massachusetts Financial Services Co', 'MFS 인베스트먼트', NULL, NULL, 70, b'1'),                         -- 3,150억
  (1109448, 'AllianceBernstein L.P.', '얼라이언스번스틴', NULL, NULL, 80, b'1'),                                      -- 3,020억
  (748054,  'American Century Companies Inc', '아메리칸 센추리', NULL, NULL, 100, b'1');                              -- 2,260억

-- 남는 두 곳은 보고액 순서 자리로 옮긴다
UPDATE institution SET sort_order = 20 WHERE cik = 1374170;   -- 노르웨이 중앙은행 1.00조
UPDATE institution SET sort_order = 90 WHERE cik = 1067983;   -- 버크셔 해서웨이 2,990억

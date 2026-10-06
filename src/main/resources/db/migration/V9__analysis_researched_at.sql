-- 기업분석의 "조사한 시각" 을 판정 시각과 따로 남긴다.
-- "판단만 새로" 는 웹 조사(섹션)를 그대로 두고 판정·내 위치·위험만 다시 쓴다. 그때 analyzed_at 만 바뀌고
-- researched_at 은 마지막 전체 분석 시각으로 남는다. 화면은 둘이 다르면 날짜를 둘 다 보인다.
-- 지금까지의 분석은 전부 전체 분석이라 같은 값으로 채운다.
ALTER TABLE company_analysis
  ADD COLUMN researched_at datetime(6) DEFAULT NULL;
UPDATE company_analysis SET researched_at = analyzed_at;

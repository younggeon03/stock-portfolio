-- 기업분석에 내 평단가·수량·손익이 들어갔는지 표시한다.
-- 안 가진 종목을 현재가만으로 분석한 것(0)만 공개 기업분석 화면에 나간다.
-- 지금까지 저장된 분석은 전부 가진 종목을 평단가와 함께 분석한 것이라 1(비공개)로 둔다.
ALTER TABLE company_analysis
  ADD COLUMN includes_position BIT(1) NOT NULL DEFAULT b'1';

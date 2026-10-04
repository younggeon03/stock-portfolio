// 공개 화면이 읽는 주소에 부하를 준다. 바깥 API 를 부르지 않고 DB 만 읽는 주소들이다(13F 조회).
// 돌리는 법·결과는 docs/운영.md "부하 테스트" 절.
//
// ★ 앱의 호출 한도(IP 당 분당 120번)를 올린 별도 인스턴스에 대고 돌린다. 한도를 그대로 두면 k6 는 한 IP 라
//   몇 초 만에 429 만 받는다. 운영 중인 앱에 대고 돌리지 않는다.
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE || 'http://host.docker.internal:18080';

export const options = {
  scenarios: {
    // 방문자가 서서히 늘었다 줄어드는 모양. 동시 사용자 수(VU)를 올려 가며 어디서 꺾이는지 본다
    ramp: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 10 },
        { duration: '40s', target: 30 },
        { duration: '20s', target: 0 },
      ],
    },
  },
  // 목표: 실패 1% 미만, 95% 가 500ms 안. 넘으면 k6 가 실패(종료 코드 99)로 끝난다
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{page:home}': ['p(95)<500'],
    'http_req_duration{page:institution}': ['p(95)<500'],
  },
};

// 첫 화면이 부르는 셋과 기관 상세. 비율은 실제 방문 모양을 짐작해 첫 화면을 더 자주
const CIKS = ['1067983', '315066', '1374170', '902219'];

export default function () {
  const r1 = http.get(`${BASE}/api/institutions`, { tags: { page: 'home' } });
  const r2 = http.get(`${BASE}/api/institutions/consensus?limit=5`, { tags: { page: 'home' } });
  check(r1, { '기관 목록 200': (r) => r.status === 200 });
  check(r2, { '같이 산 종목 200': (r) => r.status === 200 });

  const cik = CIKS[Math.floor(Math.random() * CIKS.length)];
  const r3 = http.get(`${BASE}/api/institutions/${cik}/holdings?limit=100`, { tags: { page: 'institution' } });
  const r4 = http.get(`${BASE}/api/institutions/${cik}/changes?limit=100`, { tags: { page: 'institution' } });
  check(r3, { '보유 200': (r) => r.status === 200 });
  check(r4, { '변화 200': (r) => r.status === 200 });

  sleep(1);   // 사람이 화면을 읽는 시간
}

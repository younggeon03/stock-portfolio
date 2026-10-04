// 한계 찾기. 사람의 읽는 시간(sleep) 없이 동시 사용자를 올려 초당 몇 건까지 버티는지 본다.
// public-read.js 와 같은 주소를 같은 비율로 부른다. 결과는 docs/운영.md "부하 테스트"
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE || 'http://host.docker.internal:18080';

export const options = {
  scenarios: {
    stress: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '15s', target: 50 },
        { duration: '30s', target: 100 },
        { duration: '15s', target: 0 },
      ],
    },
  },
  thresholds: { http_req_failed: ['rate<0.01'] },
};

const CIKS = ['1067983', '315066', '1374170', '902219'];

export default function () {
  const cik = CIKS[Math.floor(Math.random() * CIKS.length)];
  const res = http.batch([
    ['GET', `${BASE}/api/institutions`],
    ['GET', `${BASE}/api/institutions/consensus?limit=5`],
    ['GET', `${BASE}/api/institutions/${cik}/holdings?limit=100`],
    ['GET', `${BASE}/api/institutions/${cik}/changes?limit=100`],
  ]);
  res.forEach(r => check(r, { '200': (x) => x.status === 200 }));
}

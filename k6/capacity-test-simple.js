/**
 * 간단한 용량 테스트 (기존 community-post-test.js 스타일)
 * 목적: 몇 명의 사용자까지 버틸 수 있는지 빠르게 확인
 * 방법: stages를 사용하여 점진적으로 사용자 수 증가
 */
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '30s', target: 50 },   // 30초간 50명까지 증가
        { duration: '1m',  target: 50 },   // 1분간 50명 유지
        { duration: '30s', target: 100 },  // 30초간 100명까지 증가
        { duration: '1m',  target: 100 },   // 1분간 100명 유지
        { duration: '30s', target: 200 },  // 30초간 200명까지 증가
        { duration: '1m',  target: 200 },   // 1분간 200명 유지
        { duration: '30s', target: 500 },  // 30초간 500명까지 증가
        { duration: '2m',  target: 500 },  // 2분간 500명 유지
        { duration: '30s', target: 1000 }, // 30초간 1000명까지 증가
        { duration: '2m',  target: 1000 }, // 2분간 1000명 유지
        { duration: '30s', target: 0  },   // 30초간 0명으로 감소
    ],
    thresholds: {
        http_req_failed: ['rate<0.01'],    // 에러율 1% 미만 유지
        http_req_duration: ['p(95)<2000'], // p95 응답시간 2초 미만
    },
};

export default function () {
    const BASE_URL = __ENV.TARGET_BASE_URL || 'http://localhost:8080';
    const API_TOKEN = __ENV.API_TOKEN;

    // 환경 변수에서 엔드포인트 가져오기 (기본값: 커뮤니티 API)
    const endpoint = __ENV.TEST_ENDPOINT || '/api/home/community?category=POPULAR';
    const url = `${BASE_URL}${endpoint}`;

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    // API 토큰이 있으면 헤더에 추가
    if (API_TOKEN) {
        params.headers['Authorization'] = `Bearer ${API_TOKEN}`;
    }

    const res = http.get(url, params);

    check(res, {
        'is status 200': (r) => r.status === 200,
        'response time < 2s': (r) => r.timings.duration < 2000,
    });

    sleep(1);
}


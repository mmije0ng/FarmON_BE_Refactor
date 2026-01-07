import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '30s', target: 50 },
        { duration: '1m',  target: 100 },
        { duration: '2m',  target: 100 },
        { duration: '30s', target: 0  },
    ],
    thresholds: {
        http_req_failed: ['rate<0.01'],    // 에러율 1% 미만 유지
        http_req_duration: ['p(95)<1000'], // 사용자 증가에 따라 응답시간 임계치를 1s로 상향 조정
    },
};

export default function () {
    const BASE_URL = __ENV.TARGET_BASE_URL || 'http://52.78.68.185:8080';

    // 쿼리 파라미터가 포함된 URL
    const url = `${BASE_URL}/api/home/search?userId=1&name=%EA%B3%A1`;

    const params = {
        headers: {
            'Content-Type': 'application/json',
            // 제공해주신 토큰을 헤더에 포함
            'Authorization': `Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1bWNAZ21haWwuY29tIiwidXNlcklkIjoxLCJyb2xlIjoiRkFSTUVSIiwiaWF0IjoxNzY3NDA1MjE3LCJleHAiOjE3Njc0OTE2MTd9.beMluyOt5wK7jkUofeRKdUdTD364wPCNZ1_0fuF1zS8`,
        },
    };

    const res = http.get(url, params);

    check(res, {
        'is status 200': (r) => r.status === 200,
    });

    sleep(1);
}
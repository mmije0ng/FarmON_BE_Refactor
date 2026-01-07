import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '30s', target: 200 },
        { duration: '1m',  target: 500 },
        { duration: '2m',  target: 500 },
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
    const url = `${BASE_URL}/api/home/community?category=POPULAR`;

    const params = {
        headers: {
            'Content-Type': 'application/json',
            // 제공해주신 토큰을 헤더에 포함
            'Authorization': `Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1bWNAZ21haWwuY29tIiwidXNlcklkIjoxLCJyb2xlIjoiRkFSTUVSIiwiaWF0IjoxNzY3NDg2NDEwLCJleHAiOjE3Njc1NzI4MTB9.rmEpNLY0-kryaxPwS21GUvXrfHcM59oQ8G8GWimPJhY`,
        },
    };

    const res = http.get(url, params);

    check(res, {
        'is status 200': (r) => r.status === 200,
    });

    sleep(1);
}
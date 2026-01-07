import http from 'k6/http';
import { check, sleep } from 'k6';

// 1. 테스트 옵션 설정 (부하 단계 설정)
export const options = {
    stages: [
        { duration: '30s', target: 20 }, // 30초 동안 사용자를 20명까지 서서히 올림
        { duration: '1m',  target: 20 }, // 1분 동안 사용자 20명 유지
        { duration: '30s', target: 0  }, // 30초 동안 사용자 0명으로 내림
    ],
    thresholds: {
        http_req_failed: ['rate<0.01'], // 에러율 1% 미만 유지
        http_req_duration: ['p(95)<500'], // 95%의 응답 시간은 500ms 미만이어야 함
    },
};

// 2. 테스트 시나리오
export default function () {
    // 환경 변수에서 URL 가져오기 (기본값 설정)
    const BASE_URL = __ENV.TARGET_BASE_URL || 'http://host.docker.internal:8080';

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    // 실제 테스트할 엔드포인트 호출
    const res = http.get(`${BASE_URL}/actuator/health`, params);

    // 결과 검증
    check(res, {
        'is status 200': (r) => r.status === 200,
    });

    // 사용자 행동 간의 간격 (1초 쉬기)
    sleep(1);
}
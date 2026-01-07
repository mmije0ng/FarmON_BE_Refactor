/**
 * 용량 테스트 (API 공통 모듈 사용)
 * 목적: TEST_API_ENDPOINTS로 지정한 API들이 몇 명의 사용자까지 버틸 수 있는지 확인
 * 방법: 점진적으로 사용자 수를 늘려가며 에러율/응답시간 임계값을 넘는 지점 탐색
 */
import { executeApiOperation } from "./api-common.js";
import { Rate } from "k6/metrics";

const errorRate = new Rate("errors");

export const options = {
    scenarios: {
        capacity_test: {
            executor: "ramping-vus",
            startVUs: 10,
            stages: [
                { duration: "2m", target: 50 },
                { duration: "3m", target: 50 },
                { duration: "2m", target: 100 },
                { duration: "3m", target: 100 },
                { duration: "2m", target: 200 },
                { duration: "3m", target: 200 },
                { duration: "2m", target: 300 },
                { duration: "3m", target: 300 },
                { duration: "2m", target: 500 },
                { duration: "3m", target: 500 },
                { duration: "2m", target: 1000 },
                { duration: "3m", target: 1000 },
                { duration: "2m", target: 0 },
            ],
            gracefulRampDown: "30s",
        },
    },
    thresholds: {
        http_req_failed: ["rate<0.01"],
        http_req_duration: ["p(95)<2000", "p(99)<5000"],
        errors: ["rate<0.01"],
    },
};

export default function () {
    const result = executeApiOperation();
    errorRate.add(result === false ? 1 : 0);
}


/**
 * 용량 테스트 (Capacity Test)
 * 목적: 시스템이 몇 명의 동시 사용자까지 버틸 수 있는지 확인
 * 방법: 점진적으로 사용자 수를 늘려가며 에러율/응답시간 임계값을 넘는 지점 탐색
 * 
 * 실행 방식:
 * - 초기 사용자 수에서 시작하여 단계적으로 증가
 * - 각 단계에서 일정 시간 유지하며 안정성 확인
 * - 에러율이나 응답시간이 임계값을 넘으면 해당 단계를 최대 용량으로 기록
 */
import { executeApiOperation } from "./api-common.js";
import { Rate } from "k6/metrics";

const errorRate = new Rate("errors");

export const options = {
    scenarios: {
        // 단계별로 사용자 수를 늘려가며 테스트
        capacity_test: {
            executor: "ramping-vus",
            startVUs: 10,        // 시작 사용자 수
            stages: [
                { duration: "2m", target: 50 },   // 2분간 50명까지 증가
                { duration: "3m", target: 50 },   // 3분간 50명 유지 (안정성 확인)
                { duration: "2m", target: 100 },  // 2분간 100명까지 증가
                { duration: "3m", target: 100 },  // 3분간 100명 유지
                { duration: "2m", target: 200 },  // 2분간 200명까지 증가
                { duration: "3m", target: 200 },  // 3분간 200명 유지
                { duration: "2m", target: 300 },  // 2분간 300명까지 증가
                { duration: "3m", target: 300 },  // 3분간 300명 유지
                { duration: "2m", target: 500 },  // 2분간 500명까지 증가
                { duration: "3m", target: 500 },  // 3분간 500명 유지
                { duration: "2m", target: 1000 }, // 2분간 1000명까지 증가
                { duration: "3m", target: 1000 }, // 3분간 1000명 유지
                // { duration: "2m", target: 1500 }, // 2분간 1500명까지 증가
                // { duration: "3m", target: 1500 }, // 3분간 1500명 유지
                // { duration: "2m", target: 2000 }, // 2분간 2000명까지 증가
                // { duration: "3m", target: 2000 }, // 3분간 2000명 유지
                { duration: "2m", target: 0 },     // 2분간 0명으로 감소
            ],
            gracefulRampDown: "30s", // 종료 시 30초간 점진적으로 감소
        },
    },
    thresholds: {
        // 각 단계에서 에러율과 응답시간 확인
        http_req_failed: ["rate<0.01"], // 에러율 < 1% (임계값)
        http_req_duration: ["p(95)<2000", "p(99)<5000"], // p95 < 2초, p99 < 5초 (임계값)
        errors: ["rate<0.01"],
    },
};

export default function () {
    const result = executeApiOperation();
    errorRate.add(result === false ? 1 : 0);
}


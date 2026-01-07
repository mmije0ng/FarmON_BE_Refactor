/**
 * E. 내구 테스트
 * 목적: 장시간 테스트로 메모리 누수, 커넥션 누수, 캐시/큐 적체 여부 확인
 * 부하: 50~100 RPS 고정
 * 시간: 2시간 (예산/시간 여유에 따라 1~6시간)
 * 성공 기준: 시간이 지날수록 에러율이 증가하지 않고 p95가 서서히 악화되지 않음
 */
import { executeApiOperation } from "./api-common.js";
import { Rate } from "k6/metrics";

const errorRate = new Rate("errors");

export const options = {
    scenarios: {
        endurance: {
            executor: "constant-arrival-rate",
            rate: 75, // 50~100 범위의 중간값
            timeUnit: "1s",
            duration: "2h", // 2시간 (필요시 조정)
            preAllocatedVUs: 200,
            maxVUs: 500,
        },
    },
    thresholds: {
        http_req_failed: ["rate<0.01"], // 에러율 < 1%
        http_req_duration: ["p(95)<3000", "p(99)<5000"], // p95 < 3초, p99 < 5초
        errors: ["rate<0.01"],
    },
};

export default function () {
    const result = executeApiOperation();
    errorRate.add(result === false ? 1 : 0);
}

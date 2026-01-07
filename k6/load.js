/**
 * C. 로드 테스트
 * 목적: 안정 구간에서 지연/자원 사용 확인
 * 워밍업: 100 RPS 10분
 * 측정: 100 RPS 20분
 * 총 시간: 30분
 * 성공 기준: 에러율 < 1%, p95가 안정적이며 DBConnections가 풀 한계 근처에 장시간 머물지 않음
 */
import { executeApiOperation } from "./api-common.js";
import { Rate } from "k6/metrics";

const errorRate = new Rate("errors");

export const options = {
    scenarios: {
        warmup: {
            executor: "constant-arrival-rate",
            rate: 100,
            timeUnit: "1s",
            duration: "10m",
            preAllocatedVUs: 300,
            maxVUs: 800,
        },
        measure: {
            executor: "constant-arrival-rate",
            rate: 100,
            timeUnit: "1s",
            duration: "20m",
            preAllocatedVUs: 300,
            maxVUs: 800,
            startTime: "10m", // 워밍업 종료 후 시작
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

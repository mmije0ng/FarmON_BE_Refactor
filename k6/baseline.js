/**
 * B. 베이스라인 테스트
 * 목적: 리팩토링 전/후 성능 비교의 대표 지표 확보
 * 워밍업: 50 RPS 10분
 * 측정: 100 RPS 15분
 * 총 시간: 25분
 * 성공 기준: 에러율 < 1%, p95/p99가 기존 대비 악화되지 않음
 */
import { executeApiOperation } from "./api-common.js";
import { Rate } from "k6/metrics";

const errorRate = new Rate("errors");

export const options = {
    scenarios: {
        warmup: {
            executor: "constant-arrival-rate",
            rate: 50,
            timeUnit: "1s",
            duration: "10m",
            preAllocatedVUs: 200,
            maxVUs: 500,
        },
        measure: {
            executor: "constant-arrival-rate",
            rate: 100,
            timeUnit: "1s",
            duration: "15m",
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

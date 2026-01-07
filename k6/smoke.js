/**
 * A. 스모크 테스트
 * 목적: 배포 직후 정상 동작 확인
 * 부하: 1~5 RPS
 * 시간: 3~5분
 * 성공 기준: 에러율이 0%에 가깝고 p95가 비정상적으로 튀지 않음
 */
import { executeApiOperation } from "./api-common.js";
import { Rate } from "k6/metrics";

const errorRate = new Rate("errors");

export const options = {
    scenarios: {
        smoke: {
            executor: "constant-arrival-rate",
            rate: 3, // 평균 3 RPS (1~5 범위)
            timeUnit: "1s",
            duration: "4m", // 4분 (3~5분 범위)
            preAllocatedVUs: 5,
            maxVUs: 20,
        },
    },
    thresholds: {
        http_req_failed: ["rate<0.01"], // 에러율 < 1%
        http_req_duration: ["p(95)<2000"], // p95 < 2초
        errors: ["rate<0.01"],
    },
};

export default function () {
    const result = executeApiOperation();
    errorRate.add(result === false ? 1 : 0);
}

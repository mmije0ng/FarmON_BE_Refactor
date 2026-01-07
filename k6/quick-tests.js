/**
 * 빠른 통합 테스트 스크립트 (실험용)
 * 모든 테스트 시나리오를 짧은 시간으로 축소하여 실행
 * 
 * 주의사항:
 * - 각 테스트의 시간을 대폭 축소하여 빠르게 검증합니다
 * - 정확한 성능 측정보다는 전체적인 동작 확인에 적합합니다
 * - 총 소요 시간: 약 10분
 * 
 * 실행 순서:
 * 1. 스모크 테스트 (1분)
 * 2. 베이스라인 테스트 (3분)
 * 3. 로드 테스트 (3분)
 * 4. 스트레스 테스트 (3분)
 */
import { executeApiOperation } from "./api-common.js";
import { Rate } from "k6/metrics";

const errorRate = new Rate("errors");

export const options = {
    scenarios: {
        // A. 스모크 테스트 (축소)
        smoke: {
            executor: "constant-arrival-rate",
            rate: 3,
            timeUnit: "1s",
            duration: "1m",
            preAllocatedVUs: 5,
            maxVUs: 20,
        },
        // B. 베이스라인 테스트 (축소)
        baseline_warmup: {
            executor: "constant-arrival-rate",
            rate: 50,
            timeUnit: "1s",
            duration: "1m",
            preAllocatedVUs: 200,
            maxVUs: 500,
            startTime: "1m",
        },
        baseline_measure: {
            executor: "constant-arrival-rate",
            rate: 100,
            timeUnit: "1s",
            duration: "2m",
            preAllocatedVUs: 300,
            maxVUs: 800,
            startTime: "2m",
        },
        // C. 로드 테스트 (축소)
        load_warmup: {
            executor: "constant-arrival-rate",
            rate: 100,
            timeUnit: "1s",
            duration: "1m",
            preAllocatedVUs: 300,
            maxVUs: 800,
            startTime: "4m",
        },
        load_measure: {
            executor: "constant-arrival-rate",
            rate: 100,
            timeUnit: "1s",
            duration: "2m",
            preAllocatedVUs: 300,
            maxVUs: 800,
            startTime: "5m",
        },
        // D. 스트레스 테스트 (축소)
        stress_stage1: {
            executor: "constant-arrival-rate",
            rate: 100,
            timeUnit: "1s",
            duration: "1m",
            preAllocatedVUs: 300,
            maxVUs: 800,
            startTime: "7m",
        },
        stress_stage2: {
            executor: "constant-arrival-rate",
            rate: 150,
            timeUnit: "1s",
            duration: "1m",
            preAllocatedVUs: 400,
            maxVUs: 1000,
            startTime: "8m",
        },
        stress_stage3: {
            executor: "constant-arrival-rate",
            rate: 200,
            timeUnit: "1s",
            duration: "1m",
            preAllocatedVUs: 500,
            maxVUs: 1200,
            startTime: "9m",
        },
    },
    thresholds: {
        http_req_failed: ["rate<0.05"],
        http_req_duration: ["p(95)<5000", "p(99)<10000"],
        errors: ["rate<0.05"],
    },
};

export default function () {
    const result = executeApiOperation();
    errorRate.add(result === false ? 1 : 0);
}


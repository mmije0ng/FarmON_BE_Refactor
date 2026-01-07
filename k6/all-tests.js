/**
 * 통합 테스트 스크립트
 * 모든 테스트 시나리오를 순차적으로 실행
 * 
 * 주의사항:
 * - 각 테스트가 순차적으로 실행되므로 총 소요 시간이 길어집니다 (약 1시간 30분)
 * - 서버에 지속적인 부하를 주므로 운영 환경에서는 주의하세요
 * - 정확한 성능 측정을 위해서는 각 테스트를 독립적으로 실행하는 것을 권장합니다
 * 
 * 실행 순서:
 * 1. 스모크 테스트 (4분)
 * 2. 베이스라인 테스트 (25분)
 * 3. 로드 테스트 (30분)
 * 4. 스트레스 테스트 (25분)
 * 5. 내구 테스트는 시간이 길어 제외 (필요시 별도 실행)
 */
import { executeApiOperation } from "./api-common.js";
import { Rate } from "k6/metrics";

const errorRate = new Rate("errors");

export const options = {
    scenarios: {
        // A. 스모크 테스트
        smoke: {
            executor: "constant-arrival-rate",
            rate: 3,
            timeUnit: "1s",
            duration: "4m",
            preAllocatedVUs: 5,
            maxVUs: 20,
        },
        // B. 베이스라인 테스트
        baseline_warmup: {
            executor: "constant-arrival-rate",
            rate: 50,
            timeUnit: "1s",
            duration: "10m",
            preAllocatedVUs: 200,
            maxVUs: 500,
            startTime: "4m", // 스모크 테스트 종료 후 시작
        },
        baseline_measure: {
            executor: "constant-arrival-rate",
            rate: 100,
            timeUnit: "1s",
            duration: "15m",
            preAllocatedVUs: 300,
            maxVUs: 800,
            startTime: "14m", // 워밍업 종료 후 시작
        },
        // C. 로드 테스트
        load_warmup: {
            executor: "constant-arrival-rate",
            rate: 100,
            timeUnit: "1s",
            duration: "10m",
            preAllocatedVUs: 300,
            maxVUs: 800,
            startTime: "29m", // 베이스라인 종료 후 시작
        },
        load_measure: {
            executor: "constant-arrival-rate",
            rate: 100,
            timeUnit: "1s",
            duration: "20m",
            preAllocatedVUs: 300,
            maxVUs: 800,
            startTime: "39m", // 워밍업 종료 후 시작
        },
        // D. 스트레스 테스트
        stress_stage1: {
            executor: "constant-arrival-rate",
            rate: 100,
            timeUnit: "1s",
            duration: "5m",
            preAllocatedVUs: 300,
            maxVUs: 800,
            startTime: "59m", // 로드 테스트 종료 후 시작
        },
        stress_stage2: {
            executor: "constant-arrival-rate",
            rate: 150,
            timeUnit: "1s",
            duration: "5m",
            preAllocatedVUs: 400,
            maxVUs: 1000,
            startTime: "64m",
        },
        stress_stage3: {
            executor: "constant-arrival-rate",
            rate: 200,
            timeUnit: "1s",
            duration: "5m",
            preAllocatedVUs: 500,
            maxVUs: 1200,
            startTime: "69m",
        },
        stress_stage4: {
            executor: "constant-arrival-rate",
            rate: 250,
            timeUnit: "1s",
            duration: "5m",
            preAllocatedVUs: 600,
            maxVUs: 1500,
            startTime: "74m",
        },
        stress_stage5: {
            executor: "constant-arrival-rate",
            rate: 300,
            timeUnit: "1s",
            duration: "5m",
            preAllocatedVUs: 700,
            maxVUs: 1800,
            startTime: "79m",
        },
    },
    thresholds: {
        http_req_failed: ["rate<0.05"], // 통합 테스트이므로 임계값 완화
        http_req_duration: ["p(95)<5000", "p(99)<10000"],
        errors: ["rate<0.05"],
    },
};

export default function () {
    const result = executeApiOperation();
    errorRate.add(result === false ? 1 : 0);
}


/**
 * D. 스트레스 테스트
 * 목적: 에러율 증가 또는 p95 급등이 시작되는 임계점 확인 및 원인 기록
 * 부하: 100 → 150 → 200 → 250 → 300 RPS (각 5분)
 * 총 시간: 25분
 * 성공 기준: 임계 RPS와 원인(CPU/RDS/락/커넥션/타임아웃)을 식별하여 기록하는 것이 목표
 */
import { executeApiOperation } from "./api-common.js";
import { Rate } from "k6/metrics";

const errorRate = new Rate("errors");

export const options = {
    scenarios: {
        stage1: {
            executor: "constant-arrival-rate",
            rate: 100,
            timeUnit: "1s",
            duration: "5m",
            preAllocatedVUs: 300,
            maxVUs: 800,
        },
        stage2: {
            executor: "constant-arrival-rate",
            rate: 150,
            timeUnit: "1s",
            duration: "5m",
            preAllocatedVUs: 400,
            maxVUs: 1000,
            startTime: "5m",
        },
        stage3: {
            executor: "constant-arrival-rate",
            rate: 200,
            timeUnit: "1s",
            duration: "5m",
            preAllocatedVUs: 500,
            maxVUs: 1200,
            startTime: "10m",
        },
        stage4: {
            executor: "constant-arrival-rate",
            rate: 250,
            timeUnit: "1s",
            duration: "5m",
            preAllocatedVUs: 600,
            maxVUs: 1500,
            startTime: "15m",
        },
        stage5: {
            executor: "constant-arrival-rate",
            rate: 300,
            timeUnit: "1s",
            duration: "5m",
            preAllocatedVUs: 700,
            maxVUs: 1800,
            startTime: "20m",
        },
    },
    thresholds: {
        // 스트레스 테스트는 에러율이 증가할 수 있으므로 임계값을 완화
        http_req_failed: ["rate<0.05"], // 에러율 < 5%
        http_req_duration: ["p(95)<5000", "p(99)<10000"], // p95 < 5초, p99 < 10초
        errors: ["rate<0.05"],
    },
};

export default function () {
    const result = executeApiOperation();
    errorRate.add(result === false ? 1 : 0);
}

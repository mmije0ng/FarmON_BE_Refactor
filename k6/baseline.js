import { get } from "./common.js";

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
            startTime: "10m",
        },
    },
    thresholds: {
        http_req_failed: ["rate<0.01"],
    },
};

export default function () {
    get("/api/health");
}

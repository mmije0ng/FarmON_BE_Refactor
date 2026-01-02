import { get } from "./common.js";

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

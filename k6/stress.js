import { get } from "./common.js";

export const options = {
    scenarios: {
        stress: {
            executor: "ramping-arrival-rate",
            timeUnit: "1s",
            preAllocatedVUs: 400,
            maxVUs: 1500,
            stages: [
                { target: 100, duration: "5m" },
                { target: 150, duration: "5m" },
                { target: 200, duration: "5m" },
                { target: 250, duration: "5m" },
                { target: 300, duration: "5m" },
            ],
        },
    },
    thresholds: {
        http_req_failed: ["rate<0.03"],
    },
};

export default function () {
    get("/api/health");
}

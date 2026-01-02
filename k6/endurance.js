import { get } from "./common.js";

export const options = {
    scenarios: {
        soak: {
            executor: "constant-arrival-rate",
            rate: 80,
            timeUnit: "1s",
            duration: "2h",
            preAllocatedVUs: 400,
            maxVUs: 1500,
        },
    },
    thresholds: {
        http_req_failed: ["rate<0.01"],
    },
};

export default function () {
    get("/api/health");
}

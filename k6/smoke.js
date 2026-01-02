import { get, pause } from "./common.js";

export const options = {
    vus: 5,
    duration: "5m",
    thresholds: {
        http_req_failed: ["rate<0.01"],
    },
};

export default function () {
    get("/api/health");
    pause(1);
}

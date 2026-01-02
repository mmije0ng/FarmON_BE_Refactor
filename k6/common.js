import http from "k6/http";
import { check, sleep } from "k6";

export function baseUrl() {
    const url = __ENV.TARGET_BASE_URL;
    if (!url) throw new Error("TARGET_BASE_URL is required. e.g. http://<ec2-public-dns>:8080");
    return url.replace(/\/$/, "");
}

export function headers() {
    const h = { "Content-Type": "application/json" };
    if (__ENV.API_TOKEN) h["Authorization"] = `Bearer ${__ENV.API_TOKEN}`;
    return h;
}

export function get(path) {
    const res = http.get(`${baseUrl()}${path}`, { headers: headers() });
    check(res, {
        "status is 2xx/3xx": (r) => r.status >= 200 && r.status < 400,
    });
    return res;
}

export function pause(sec = 1) {
    sleep(sec);
}

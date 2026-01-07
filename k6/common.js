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

export function get(path, params = {}) {
    const url = `${baseUrl()}${path}`;
    const res = http.get(url, { headers: headers(), params: params });
    check(res, {
        "status is 2xx/3xx": (r) => r.status >= 200 && r.status < 400,
    });
    return res;
}

export function post(path, body = null, params = {}) {
    const url = `${baseUrl()}${path}`;
    const payload = body ? JSON.stringify(body) : null;
    const res = http.post(url, payload, { headers: headers(), params: params });
    check(res, {
        "status is 2xx/3xx": (r) => r.status >= 200 && r.status < 400,
    });
    return res;
}

export function del(path, params = {}) {
    const url = `${baseUrl()}${path}`;
    const res = http.del(url, null, { headers: headers(), params: params });
    check(res, {
        "status is 2xx/3xx": (r) => r.status >= 200 && r.status < 400,
    });
    return res;
}

export function pause(sec = 1) {
    sleep(sec);
}

// 테스트용 사용자 ID 목록 (환경변수로 오버라이드 가능)
export function getTestUserIds() {
    const userIds = __ENV.TEST_USER_IDS;
    if (userIds) {
        return userIds.split(",").map(id => parseInt(id.trim()));
    }
    // 기본값: 1~10
    return Array.from({ length: 10 }, (_, i) => i + 1);
}

// 테스트용 리소스 ID 목록 (환경변수로 오버라이드 가능, 범용적으로 사용)
export function getTestResourceIds(resourceName = "resource", defaultCount = 5) {
    const envKey = `TEST_${resourceName.toUpperCase()}_IDS`;
    const resourceIds = __ENV[envKey];
    if (resourceIds) {
        return resourceIds.split(",").map(id => parseInt(id.trim()));
    }
    // 기본값: 1~defaultCount
    return Array.from({ length: defaultCount }, (_, i) => i + 1);
}

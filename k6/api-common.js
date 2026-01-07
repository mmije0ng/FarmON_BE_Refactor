import { sleep } from "k6";
import { get, post, del, getTestUserIds } from "./common.js";

// ====== init 단계에서 1회 파싱 (중요) ======
function parseApiEndpoints() {
    const endpointsStr = __ENV.TEST_API_ENDPOINTS;
    if (!endpointsStr) {
        throw new Error("TEST_API_ENDPOINTS env var is required. e.g. 'GET:/api/a,POST:/api/b'");
    }

    return endpointsStr.split(",").map((raw) => {
        raw = raw.trim();
        const [method, pathWithParams] = raw.split(":");
        const [path, queryString] = pathWithParams.split("?");

        const params = {};
        if (queryString) {
            queryString.split("&").forEach((p) => {
                const [k, v] = p.split("=");
                params[k] = v || "";
            });
        }

        return {
            method: method.toUpperCase(),
            path: path.startsWith("/") ? path : `/${path}`,
            params,
        };
    });
}

const ENDPOINTS = parseApiEndpoints();
const READ_ENDPOINTS = ENDPOINTS.filter((e) => e.method === "GET");
const WRITE_ENDPOINTS = ENDPOINTS.filter((e) => ["POST", "PUT", "DELETE", "PATCH"].includes(e.method));

if (READ_ENDPOINTS.length === 0) {
    throw new Error("No GET endpoints found in TEST_API_ENDPOINTS");
}

function pickRandom(arr) {
    return arr[Math.floor(Math.random() * arr.length)];
}

function resolveParams(templateParams, userId) {
    const params = { ...templateParams };

    if (params.userId === "{userId}" || params.userId === "${userId}") params.userId = userId;
    if (params.page === "{page}" || params.page === "${page}") params.page = String(Math.floor(Math.random() * 5) + 1);

    return params;
}

// GET
export function readOperations() {
    const userIds = getTestUserIds();
    const userId = pickRandom(userIds);
    const endpoint = pickRandom(READ_ENDPOINTS);

    const params = resolveParams(endpoint.params, userId);
    return get(endpoint.path, params);
}

// POST/PUT/PATCH/DELETE
export function writeOperations() {
    if (WRITE_ENDPOINTS.length === 0) return readOperations();

    const userIds = getTestUserIds();
    const userId = pickRandom(userIds);
    const endpoint = pickRandom(WRITE_ENDPOINTS);

    const params = resolveParams(endpoint.params, userId);

    switch (endpoint.method) {
        case "POST":
            return post(endpoint.path, null, params);
        case "PUT":
        case "PATCH":
            // common.js에 put/patch가 있으면 그걸 쓰는 게 정석
            // 없으면 서버가 허용한다는 전제 하에 post로 대체 (지금처럼)
            return post(endpoint.path, null, params);
        case "DELETE":
            return del(endpoint.path, params);
        default:
            return get(endpoint.path, params);
    }
}

export function executeApiOperation() {
    const ratio = parseFloat(__ENV.READ_WRITE_RATIO || "0.9");

    const res = Math.random() < ratio ? readOperations() : writeOperations();

    sleep(0.2); 

    return res && res.status >= 200 && res.status < 400;
}

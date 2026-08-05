import http from "k6/http";
import { check } from "k6";

const scenario = __ENV.SCENARIO || "certificate";
const duration = __ENV.DURATION || "30m";
const baseUrl = __ENV.BASE_URL || "https://localhost:8443";
const bearerToken = __ENV.BEARER_TOKEN;
const fixturePath = __ENV.FIXTURE || `../fixtures/${scenario}.json`;
const payload = open(fixturePath);

const rates = {
  certificate: Number(__ENV.RATE || 50),
  signature: Number(__ENV.RATE || 20),
};

export const options = {
  insecureSkipTLSVerify: __ENV.INSECURE_SKIP_TLS_VERIFY === "true",
  scenarios: {
    validation: {
      executor: "constant-arrival-rate",
      rate: rates[scenario],
      timeUnit: "1s",
      duration,
      preAllocatedVUs: 100,
      maxVUs: 200,
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<2000"],
    checks: ["rate>0.99"],
  },
};

export default function () {
  const path = scenario === "signature"
    ? "/api/v1/validations/signatures"
    : "/api/v1/validations/certificates";
  const response = http.post(`${baseUrl}${path}`, payload, {
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${bearerToken}`,
    },
    tags: { operation: scenario },
    timeout: "10s",
  });
  check(response, {
    "HTTP 200": (item) => item.status === 200,
    "Problem Details veya stack trace sızmıyor": (item) =>
      !item.body.includes("java.lang.") && !item.body.includes("stackTrace"),
  });
}

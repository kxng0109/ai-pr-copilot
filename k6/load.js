// k6 load/SLO test for the AI PR Copilot analysis endpoints.
//
// Run:
//   k6 run --env=ENV=local --env=BASE_URL=http://localhost:8080 k6/load.js
//
// Targets below are PROPOSED, not authoritative: no industry SLO
// standard exists for AI code-review assistants. Adjust to your own
// infrastructure and user expectations, then re-run.
//
// Metrics captured:
//   http_req_duration  - end-to-end request latency (p50/p95/p99 auto)
//   http_req_waiting   - time to first byte; for the SSE endpoint this
//                        is the streaming time-to-first-token (TTFB)
//   http_req_failed    - failure rate; used to bound bulkhead/429 fallout
//   checks             - pass/fail of the SLO thresholds

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_KEY = __ENV.API_KEY || 'change-me';
const AUTH = { headers: { Authorization: `Bearer ${API_KEY}` } };

// A small but representative diff. Kept tiny so the test is cheap to run
// against a local Ollama while still exercising the full path.
const SMALL_DIFF = {
  diff: 'diff --git a/Foo.java b/Foo.java\n+++ b/Foo.java\n@@ -1,3 +1,3 @@\n-public void foo() { return null; }\n+public void foo() { return 1; }\n',
  language: 'java',
  style: 'conventional-commit',
  maxSummaryLength: 256,
  requestId: 'k6-load-1',
};

const LARGE_DIFF = Object.assign({}, SMALL_DIFF, {
  requestId: 'k6-load-2',
  diff: SMALL_DIFF.diff.repeat(40),
});

// SLO thresholds. Failing any threshold aborts the run early
// (abortOnFail) so a regression is visible in CI, not at soak end.
const THRESHOLDS = {
  'http_req_duration': ['p(95) < 3000', 'p(99) < 8000'],
  'http_req_waiting': ['p(95) < 1500'],
  'http_req_failed': ['rate < 0.05'],
  'checks': ['rate > 0.99'],
};

function analyze(diff, tag) {
  const res = http.post(
    `${BASE_URL}/api/v1/analyze-diff`,
    JSON.stringify(diff),
    Object.assign({}, AUTH, { tags: { endpoint: tag } }),
  );
  return check(res, {
    [`${tag} status 200`]: (r) => r.status === 200,
    [`${tag} has requestId`]: (r) => r.body && r.body.includes('requestId'),
  });
}

export const options = {
  thresholds: THRESHOLDS,
  scenarios: {
    // 1. Smoke - validate the script and a single happy path.
    smoke: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 3,
      maxDuration: '30s',
    },
    // 2. Load - hold at average production traffic.
    load: {
      executor: 'constant-arrival-rate',
      rate: 20,
      timeUnit: '1s',
      duration: '5m',
      preAllocatedVUs: 50,
    },
    // 3. Stress - push past capacity to observe bulkhead/429 behaviour.
    stress: {
      executor: 'ramping-arrival-rate',
      startRate: 20,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      stages: [
        { duration: '2m', target: 60 },
        { duration: '2m', target: 120 },
        { duration: '2m', target: 60 },
        { duration: '1m', target: 0 },
      ],
    },
  },
};

export default function () {
  // JSON analysis path.
  analyze(SMALL_DIFF, 'analyze');

  // Larger payload to exercise the size guard and bulkhead.
  analyze(LARGE_DIFF, 'analyze-large');

  // SSE streaming path: http_req_waiting captures time-to-first-token.
  const sse = http.post(
    `${BASE_URL}/api/v1/analyze-diff/stream`,
    JSON.stringify(SMALL_DIFF),
    Object.assign({}, AUTH, { tags: { endpoint: 'stream' } }),
  );
  check(sse, {
    'stream status 200': (r) => r.status === 200,
    'stream has event data': (r) => r.body && r.body.includes('data:'),
  });

  sleep(0.2);
}
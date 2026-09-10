# Load / SLO testing with k6

This directory holds the k6 load test for the AI PR Copilot analysis
endpoints. It is **not yet validated against a running service** — the
SLO numbers below are a proposed starting point, not a measurement.

## Endpoints under test

| Endpoint | Method | What it measures |
|---|---|---|
| `/api/v1/analyze-diff` | POST | end-to-end latency (`http_req_duration`) |
| `/api/v1/analyze-diff` (large) | POST | size guard + bulkhead behaviour |
| `/api/v1/analyze-diff/stream` | POST (SSE) | time-to-first-token (`http_req_waiting`) |

Auth: `Authorization: Bearer <key>`.

## Scenarios

| Scenario | Executor | Purpose |
|---|---|---|
| `smoke` | per-vu-iterations | baseline after every code change |
| `load` | constant-arrival-rate | confirm behaviour at average traffic |
| `stress` | ramping-arrival-rate | observe bulkhead / 429 fallback above capacity |

## Proposed SLOs

> **These numbers are not authoritative.** No industry SLO standard
> exists for AI code-review assistants. They are derived from the
> deployment's own constraints and must be confirmed against a real
> environment before being treated as targets.

| Metric | Threshold | Rationale |
|---|---|---|
| `http_req_duration` p95 | < 3 s | cache-hit analysis dispatch |
| `http_req_duration` p99 | < 8 s | tail allowance for provider latency |
| `http_req_waiting` p95 (SSE) | < 1.5 s | time-to-first-token |
| `http_req_failed` rate | < 0.05 | bound on bulkhead/429 fallout |
| `checks` rate | > 0.99 | script assertions stay healthy |

A failing threshold aborts the run early (`abortOnFail`), so a
regression fails CI rather than surfacing at soak end.

## Running

```bash
# Local compose
k6 run --env=ENV=local --env=BASE_URL=http://localhost:8080 \
            --env=API_KEY=change-me k6/load.js

# Staging
k6 run --env=ENV=staging --env=BASE_URL=https://staging.example.com \
            --env=API_KEY="$STAGING_KEY" k6/load.js
```

## Calibrating the thresholds

The numbers above are a starting point. Calibrate them once, then treat
them as targets. Do not run this while another load generator is active
on the same host — you will measure the other tool, not this service.

### Step 1 — smoke (warm service, ~1 min)

```bash
k6 run --env=BASE_URL=http://localhost:8080 --env=API_KEY=change-me \
  --scenarios smoke=1 k6/load.js
```

Record the real p50, p95, p99 and SSE `http_req_waiting` p50. If the
warm p95 is already above the proposed `http_req_duration` p95 threshold,
the threshold is wrong for this hardware — widen it before proceeding.

### Step 2 — load (5 minutes at average traffic)

```bash
k6 run --env=BASE_URL=http://localhost:8080 --env=API_KEY=change-me \
  --scenarios load=1 k6/load.js
```

Confirm p95/p99 against the proposed thresholds. Widen or tighten, and
record the final numbers below.

### Step 3 — stress (observe bulkhead / 429 behaviour)

```bash
k6 run --env=BASE_URL=http://localhost:8080 --env=API_KEY=change-me \
  --scenarios stress=1 k6/load.js
```

Confirm bulkhead rejections rise predictably as the rate exceeds capacity,
and `http_req_failed` stays below the floor. If 429s dominate, the rate
limiter is the bottleneck — raise the limiter or lower the stress target.

### Record your calibrated baseline

| Metric | Proposed | Calibrated | Date |
|---|---|---|---|
| `http_req_duration` p95 | < 3 s | | |
| `http_req_duration` p99 | < 8 s | | |
| `http_req_waiting` p95 (SSE) | < 1.5 s | | |
| `http_req_failed` rate | < 0.05 | | |
| `checks` rate | > 0.99 | | |

A threshold that always passes is decoration, not a guardrail. Delete or
adjust any you cannot meet, and commit the calibrated row with the
measurement date.
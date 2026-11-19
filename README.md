# ⚡ EventFlow — Production-Grade Webhook Delivery & Event Processing Platform

EventFlow is a resilient, enterprise-grade webhook delivery engine and event processing platform designed to reliably route, sign, throttle, and retry mission-critical events between distributed microservices and third-party webhooks.

Built with **Java 21 Virtual Threads**, **Spring Boot 3**, **PostgreSQL (`SELECT ... FOR UPDATE SKIP LOCKED`)**, **Flyway**, **HMAC-SHA256 signing**, **Token Bucket Rate Limiting**, **Real-Time Server-Sent Events (SSE)**, and **React + TypeScript**.

---

## 🏗️ Architecture & Core Pillars

Detailed architecture specification and Mermaid diagrams are available in [`ARCHITECTURE.md`](./ARCHITECTURE.md).

```
   ┌────────────────────────────────────────────────────────┐
   │                  Ingestion Microservices               │
   └──────────────────────────┬─────────────────────────────┘
                              │ POST /api/v1/events
                              │ (Idempotency-Key & X-API-Key)
                              ▼
   ┌────────────────────────────────────────────────────────┐
   │               EventFlow Ingestion Gateway              │
   └──────────────────────────┬─────────────────────────────┘
                              │ ACID Transaction
                              ▼
   ┌────────────────────────────────────────────────────────┐
   │       PostgreSQL 16 Engine (`event_deliveries`)        │
   │       `SELECT ... FOR UPDATE SKIP LOCKED`              │
   └──────────────────────────┬─────────────────────────────┘
                              │ Non-blocking lock claim
                              ▼
   ┌────────────────────────────────────────────────────────┐
   │      Delivery Worker Pool (Java 21 Virtual Threads)    │
   │  ┌──────────────────────────────────────────────────┐  │
   │  │ Token Bucket Rate Limiter (Per Endpoint)        │  │
   │  ├──────────────────────────────────────────────────┤  │
   │  │ HMAC-SHA256 Timestamped Request Signing         │  │
   │  ├──────────────────────────────────────────────────┤  │
   │  │ Full Jitter Exponential Backoff (1 to 3600s)     │  │
   │  ├──────────────────────────────────────────────────┤  │
   │  │ Dead-Letter Queue (DLQ) + Bulk Replay Engine     │  │
   │  ├──────────────────────────────────────────────────┤  │
   │  │ Micrometer Prometheus Metrics & Actuator        │  │
   │  └──────────────────────────────────────────────────┘  │
   └──────────────────────────┬─────────────────────────────┘
                              │
               ┌──────────────┴──────────────┐
               ▼                             ▼
   ┌───────────────────────┐     ┌────────────────────────┐
   │ Subscribed Webhook    │     │ Live Developer Console │
   │ Endpoints (HTTP/200)  │     │ (Server-Sent Events)   │
   └───────────────────────┘     └────────────────────────┘
```

---

## 🚀 Key Technical Features

### 1. Guaranteed At-Least-Once Delivery & Concurrency
- **Non-blocking Worker Claim:** Workers claim batches using PostgreSQL `SELECT ... FOR UPDATE SKIP LOCKED`, preventing race conditions and thread contention across horizontal replicas.
- **Java 21 Virtual Threads:** High-throughput outbound HTTP dispatches execute on lightweight virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`).

### 2. Security & Dual-Layer Authentication
- **Dual-Layer Auth:** Stateless JWT Bearer tokens for developer console management, SHA-256 hashed API Keys (`X-API-Key`) for machine-to-machine event ingestion.
- **HMAC-SHA256 Signing:** Outbound webhook payloads include `X-EventFlow-Signature: t={timestamp},v1={hex}` to prevent replay attacks and spoofing.

### 3. Reliability & Engineering Depth
- **Token Bucket Rate Limiting:** Enforces per-endpoint throughput limits (e.g., 50 req/s), deferring excess traffic gracefully without message loss.
- **Full Jitter Exponential Backoff:** Delay formula $T = \text{random}(1, \min(3600, \text{base} \cdot 2^{\text{attempt}}))$, mathematically eliminating thundering herd problem.
- **Idempotency Protection:** Support for `Idempotency-Key` / `X-Idempotency-Key` headers to prevent duplicate delivery dispatching on network retry.
- **Dead-Letter Queue (DLQ) & Bulk Replay:** Deliveries that exhaust retry attempts transition to `DEAD_LETTER` with complete audit trail and single-click bulk replay.
- **Real-Time Live Streaming:** Server-Sent Events (`/api/v1/deliveries/stream`) stream instant delivery state transitions to the developer console.

### 4. Telemetry & Observability
- **Prometheus Metrics:** Integrated Micrometer counters (`eventflow.deliveries.total`), latency summary timers (`eventflow.deliveries.duration`), and connection pool stats at `/actuator/prometheus`.
- **Grafana Dashboard:** Pre-configured dashboard specification in `monitoring/grafana/dashboards/eventflow-overview.json`.
- **Automated Load Testing:** Includes k6 test script (`loadtest/k6-load-test.js`) and native concurrent runner (`loadtest/run-loadtest.mjs`).

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| **Backend** | Java 21, Spring Boot 3.3.4, Spring Security, Spring Data JPA, Hibernate 6 |
| **Database** | PostgreSQL 16 (Alpine), Flyway DB Migrations |
| **Concurrency** | Java 21 Virtual Threads, Token Bucket Rate Limiting |
| **Frontend** | React 18, TypeScript, Vite, Lucide Icons, Dark-mode responsive UI |
| **Telemetry** | Spring Boot Actuator, Micrometer, Prometheus, Grafana |
| **Load Testing** | k6, Node.js concurrent runner |
| **Containers** | Docker, Docker Compose |

---

## ⚡ Quickstart Guide

### 1. Start Infrastructure
```bash
docker compose up -d
```
*Starts PostgreSQL on host port `5433` (container port 5432), Prometheus on `9090`, and Grafana on `3001`.*

### 2. Start Backend Server
```bash
cd backend
powershell -File .\mvnw.ps1 test
powershell -File .\mvnw.ps1 package -DskipTests
java -jar target/eventflow-backend-0.0.1-SNAPSHOT.jar
```
*Backend runs on port `8085`.*
- Swagger UI: [http://localhost:8085/swagger-ui/index.html](http://localhost:8085/swagger-ui/index.html)
- Actuator Health: [http://localhost:8085/actuator/health](http://localhost:8085/actuator/health)
- Prometheus Metrics: [http://localhost:8085/actuator/prometheus](http://localhost:8085/actuator/prometheus)

### 3. Start Developer Web Console
```bash
cd frontend
npm install
npm run dev
```
*Web console available at [http://localhost:3000](http://localhost:3000).*
- **Default Login:** `admin@acme.com` / `SuperSecretPassword123`

### 4. Run Concurrent Load Test
```bash
node loadtest/run-loadtest.mjs
```
*Dispatches 200 concurrent requests across 15 virtual workers and prints throughput and p50/p90/p95/p99 latency distribution.*

---

## 🧪 Testing Suite

Run all unit and algorithmic tests:
```bash
cd backend
powershell -File .\mvnw.ps1 test
```
- `JwtServiceTest`: Token generation, validation, and claim extraction.
- `HmacServiceTest`: HMAC-SHA256 signature formatting and verification.
- `DeliveryWorkerServiceTest`: Full Jitter exponential backoff mathematical bounds (attempt 1, attempt 2, ceiling clamping).
- `RateLimiterServiceTest`: Token Bucket capacity, throttling, and endpoint isolation.
- `DeliveryMetricsServiceTest`: Micrometer counters and dispatch latency percentiles.

---

## 📜 License
MIT License. Created by [Garvit Singh](https://github.com/GarvitSingh13).

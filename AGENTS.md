# AGENTS.md — Energy Communities Project Context

FH-Projekt (DISYS course), simulates an energy community where solar producers and households
share locally generated power before drawing from the public grid.

---

## Architecture Overview

Event-driven microservices connected via RabbitMQ. Only the REST API uses Spring Boot —
all other services are plain Java with the raw AMQP client.

```
EnergyProducerApp ──┐
                    ├──► [energy-queue] ──► UsageServiceApp ──► PostgreSQL (usage_bucket)
EnergyUserApp     ──┘                            │
                                                 └──► [update-queue] ──► PercentageServiceApp
                                                                               │
                                                                         PostgreSQL (percentage_record)
                                                                               │
                                                                    REST API (Spring Boot :8080)
                                                                               │
                                                                         GUI (JavaFX)
```

### Why only rest-api uses Spring Boot

Deliberate choice: the producers/consumers are standalone Java processes with a single
responsibility (publish or consume). Adding a Spring container for each would be overhead
with no benefit. Spring Boot is only justified where it earns its keep (REST + DB via JDBC).

---

## Module Breakdown

### `shared`
Common types shared across all modules. No logic, no framework.

| Class | Package | Purpose |
|-------|---------|---------|
| `RabbitMQConfig` | `config` | Static constants: host, port, exchange name, queue names, routing keys |
| `EnergyMessage` | `dto` | Wire format for producer→usage-service (type, association, kwh, datetime) |
| `UpdateMessage` | `dto` | Wire format for usage-service→percentage-service (bucketHour, produced, used, grid) |
| `UsageBucket` | `model` | REST API response object for `/energy/historical` |
| `PercentageRecord` | `model` | REST API response object for `/energy/current` |

`EnergyMessage` has an unused `@JsonProperty` import — cosmetic, no effect.

---

### `energy-producer`
Simulates a solar panel. Runs an infinite loop, publishes every 1–5 seconds.

**`WeatherClient`** — calls open-meteo API every 15 min:
```
GET https://api.open-meteo.com/v1/forecast?latitude=48.24&longitude=16.38&current=cloud_cover_low
```
Returns `cloud_cover_low` (0–100). Falls back to 50% on API error.
Coordinates are approximate FH location (API rounds to 2 decimal places).

**kWh calculation:**
```
sunFactor = 0.2 + 0.8 * (100 - cloudCover) / 100   // 0.2 minimum even in full cloud
jitter    = random(0.8 – 1.2)
kWh       = 0.005 * sunFactor * jitter
```

**Publishes:** `EnergyMessage(type="PRODUCER", association="COMMUNITY", kwh=..., datetime=now)`
**Queue:** `energy-queue` via `energy-exchange` with routing key `energy`

---

### `energy-user`
Simulates household consumption. Same loop pattern as producer.

**kWh by time of day:**
```
06–09:  0.010 kWh  (morning peak)
17–21:  0.0125 kWh (evening peak)
22–23, 00–05:  0.002 kWh  (night — uses || correctly, not a bug)
10–16:  0.0055 kWh (daytime base)
```
Note: `22 <= hour || hour <= 5` is intentional and correct — it covers two separate hour
ranges that wrap around midnight. `&&` would be impossible (no hour is both ≥22 and ≤5).

**Publishes:** `EnergyMessage(type="USER", association="COMMUNITY", kwh=..., datetime=now)`
**Queue:** same `energy-queue`

---

### `usage-service`
The core aggregation service. Consumes `energy-queue`, writes to `usage_bucket`, then
publishes to `update-queue`. Business logic (the PRODUCER/USER branching) lives in
`UsageCalculator.apply(...)`, a pure function with no I/O — extracted out of the
`DeliverCallback` on 2026-09-06 (see "Lecturer feedback" below).

**Per-message flow:**
```
                 RabbitMQ
                     │
                     │ EnergyMessage
                     ▼
             QUEUE_ENERGY
                     │
                     ▼
              DeliverCallback
                     │
                     ▼
          JSON → EnergyMessage
                     │
                     ▼
          Stunde bestimmen (bucketHour = datetime truncated to hour)
                     │
                     ▼
       aktuellen DB-Wert lesen (SELECT ... WHERE bucket_hour = ?, oder 0 falls neu)
                     │
                     ▼
        UsageCalculator.apply(produced, used, grid, type, kwh)
                     │
                     ▼
          DB UPSERT (usage_bucket, ON CONFLICT bucket_hour)
                     │
                     ▼
       UpdateMessage erzeugen
                     │
                     ▼
      RabbitMQ UPDATE publish (→ update-queue)
                     │
                     ▼
                basicAck()
```
On `SQLException` anywhere in the try block: `basicNack(deliveryTag, false, true)` instead
(logged, message requeued) — see "Lecturer feedback" below for what changed and why.

**Known issues in this service:**
- Race condition potential: two concurrent messages could both read the same DB state,
  calculate independently, and one write overwrites the other's result. Not fixed —
  would need a DB-level lock or a single-threaded consumer per bucket_hour to fully solve.

---

### `percentage-service`
Consumes `update-queue`, computes two percentage metrics, writes to `percentage_record`.
Business logic lives in `PercentageCalculator.calculate(...)`, a pure function with no I/O —
extracted out of the `DeliverCallback` on 2026-09-05 (see "Lecturer feedback" below).

**Calculations:**
```
communityDepleted = min(100, used / produced * 100)   // % of local production consumed
gridPortion       = grid / (used + grid) * 100         // % of total consumption from grid
```
Both guarded against division-by-zero.

**Known issues in this service:**
- ObjectMapper is not configured with `disable(WRITE_DATES_AS_TIMESTAMPS)` — but since
  this service only deserializes (never serializes) datetimes, this has no effect in practice.
- (Resource leaks and `autoAck=true` were fixed 2026-09-05 — see "Known Bugs" table below.)

---

### `rest-api`
Spring Boot 3.2.5, port 8080. Only module with `@SpringBootApplication`.

**Endpoints in `EnergyController`:**

```
GET /energy/current
  → queries percentage_record WHERE bucket_hour = current hour (truncated)
  → returns empty PercentageRecord with zeros if no data yet

GET /energy/historical?start=...&end=...
  → queries usage_bucket WHERE bucket_hour BETWEEN start AND end
  → start/end must be ISO format: 2024-01-01T14:00:00
  → returns List<UsageBucket> ordered by bucket_hour
```

**Known issue:** `LocalDateTime.parse(start)` and `LocalDateTime.parse(end)` have no
try-catch. A malformed date string causes an unhandled `DateTimeParseException` → HTTP 500
with no useful error message to the client.

**DB config** (`application.properties`):
```
spring.datasource.url=jdbc:postgresql://localhost:5432/energycommunities
spring.datasource.username=disysuser
spring.datasource.password=disyspw
```

---

### `gui`
JavaFX desktop app. Polls the REST API and displays live + historical data.
`ApiClient` uses `java.net.http.HttpClient` and Jackson to call the two endpoints.
Not relevant to backend work.

---

## RabbitMQ Topology

| Element | Name | Type |
|---------|------|------|
| Exchange | `energy-exchange` | direct, durable |
| Queue | `energy-queue` | durable |
| Queue | `update-queue` | durable |
| Routing key | `energy` | producer/user → energy-queue |
| Routing key | `update` | usage-service → update-queue |

All services declare the exchange and queue on startup (idempotent — safe to call if already exists).

---

## Database Schema

```sql
usage_bucket (
    id                BIGSERIAL PRIMARY KEY,
    bucket_hour       TIMESTAMP UNIQUE NOT NULL,   -- PK for upserts
    community_produced DOUBLE PRECISION DEFAULT 0,
    community_used    DOUBLE PRECISION DEFAULT 0,
    grid_used         DOUBLE PRECISION DEFAULT 0,
    created_at        TIMESTAMP DEFAULT NOW(),
    updated_at        TIMESTAMP DEFAULT NOW()
)

percentage_record (
    id                BIGSERIAL PRIMARY KEY,
    bucket_hour       TIMESTAMP UNIQUE NOT NULL,   -- PK for upserts
    community_depleted DOUBLE PRECISION DEFAULT 0,
    grid_portion      DOUBLE PRECISION DEFAULT 0,
    created_at        TIMESTAMP DEFAULT NOW(),
    updated_at        TIMESTAMP DEFAULT NOW()
)
```

Both tables use `bucket_hour` as the natural key. All writes are PostgreSQL-native UPSERTs
(`ON CONFLICT (bucket_hour) DO UPDATE`).

---

## Infrastructure

Docker Compose (`docker/docker-compose.yml`):
- PostgreSQL Alpine: port 5432, user `disysuser`, password `disyspw`, db `energycommunities`
- RabbitMQ management-alpine: ports 5672 (AMQP) and 15672 (management UI, guest/guest)
- `init.sql` is mounted into postgres init dir — tables are created on first container start

```bash
docker compose -f docker/docker-compose.yml up -d
```

---

## Known Bugs

| # | Location | Line | Issue | Status |
|---|----------|------|-------|--------|
| 1 | `PercentageServiceApp.java` | 34–35 (old) | `Connection`/`Channel` not in try-with-resources → resource leak on shutdown | **Fixed 2026-09-05** — wrapped in try-with-resources |
| 2 | `UsageServiceApp.java` | 32 (old) | `java.sql.Connection db` never closed | **Fixed 2026-09-06** — wrapped in try-with-resources (merged into the existing `connection`/`channel` try) |
| 3 | `PercentageServiceApp.java` | 26 (old) | Same — `db` never closed | **Fixed 2026-09-05** — wrapped in try-with-resources |
| 4 | `UsageServiceApp.java` | 113 (old) | `autoAck=true` — message lost if DB write fails | **Fixed 2026-09-06** — manual `basicAck` (after DB write + publish) / `basicNack` (requeues on `SQLException`) |
| 5 | `PercentageServiceApp.java` | 84 (old) | Same `autoAck=true` | **Fixed 2026-09-05** — manual `basicAck`/`basicNack` (nack requeues on `SQLException`) |
| 6 | `EnergyController.java` | 56–57 | `LocalDateTime.parse()` without try-catch → unhandled 500 on bad input | unfixed |
| 7 | `EnergyMessage.java` | 3 | Unused import: `@JsonProperty` | unfixed |

Note: try-with-resources on `db`/`connection`/`channel` only actually runs cleanup on a normal/exceptional
exit of the block — since both services block forever (`CountDownLatch.await()`, see below), a `Ctrl+C`/
`SIGTERM` still won't trigger it (no shutdown hook added). Discussed as a known limitation, not fixed
further — a `Runtime.getRuntime().addShutdownHook(...)` would be the complete fix if ever needed.

Both services also had `Thread.currentThread().join()` at the end (self-join trick to block forever) —
replaced with `new CountDownLatch(1).await()` in both files for readability. Verified against the
`amqp-client` 5.20.0 sources (in local `.m2`) that this is technically redundant either way: `ConnectionFactory`
defaults to `Executors.defaultThreadFactory()`, and `ConsumerWorkService` uses that factory for its consumer
dispatch thread pool — JDK's default thread factory creates non-daemon threads, so the RabbitMQ client's own
background thread already keeps the JVM alive once `basicConsume` is called, independent of this line.

## Lecturer feedback (2026-09-05) — code organization

Comment on the project: DB/REST code mixed in `EnergyController` (SQL + RowMapper directly in the
`@RestController`, no repository/service layer); RabbitMQ setup mixed with business logic in
`EnergyProducerApp`/`EnergyUserApp`; `WeatherClient` uses `objectMapper.readTree()` → `JsonNode` instead
of a proper DTO (inconsistent with the rest of the project, which always uses `readValue(json, X.class)`);
all logic (RabbitMQ, DB, business calc) in one file for `usage-service`/`percentage-service`. Plus a
separate, unrelated point: deduction for insufficient explanation during presentation.

**Remediation done (2026-09-05), low-risk subset given a same-week deadline:**
- `PercentageCalculator.java` (new) — pure `calculate(produced, used, grid) -> Result(communityDepleted, gridPortion)`,
  extracted out of `PercentageServiceApp`'s `DeliverCallback`. No behavior change, callback still calls it inline.
- `UsageCalculator.java` (new) — pure `apply(produced, used, grid, type, kwh) -> Result(produced, used, grid)`,
  extracted out of `UsageServiceApp`'s `DeliverCallback` the same way.

**Not done (explicitly deferred, higher risk/effort vs. deadline):**
- `EnergyController` → Service/Repository layering.
- `WeatherClient` → proper DTO instead of `JsonNode`.
- `EnergyProducerApp`/`EnergyUserApp` → extract kWh-calculation into their own pure classes (same pattern
  as above, just not done yet — quick win if there's time).
- The DB-read part of `UsageServiceApp` (the `SELECT` + `ResultSet` mapping) was deliberately left as I/O
  in the app, not pulled into a repository — that would be the next, bigger step (full repository pattern).

**Further candidates (not started), ordered by effort/risk:**
1. `WeatherClient` (energy-producer) → replace `objectMapper.readTree(...)` / `JsonNode` navigation with a
   proper DTO (e.g. `record WeatherResponse(Current current) { record Current(@JsonProperty("cloud_cover_low") double cloudCoverLow) {} }`).
   Smallest, most isolated fix — directly addresses the JsonNode-instead-of-DTO comment.
2. Extract the kWh-calculation out of `EnergyProducerApp` (sunFactor/jitter formula) and `EnergyUserApp`
   (hour-of-day branching) into their own pure calculator classes — same pattern as `PercentageCalculator`/
   `UsageCalculator` above.
3. `EnergyController` (rest-api) → introduce a Service/Repository layer instead of SQL + RowMapper directly
   in the `@RestController`. Biggest, riskiest change of the three — was explicitly deprioritized given the
   2026-09-06 deadline.

---

## Tech Stack

| Concern | Technology |
|---------|-----------|
| Build | Maven multi-module, Java 21 |
| Messaging | RabbitMQ via raw `amqp-client` 5.20.0 (no Spring AMQP) |
| Database | PostgreSQL 15 via plain JDBC (`DriverManager`) or Spring `JdbcTemplate` |
| REST | Spring Boot 3.2.5 (`spring-boot-starter-web` + `spring-boot-starter-jdbc`) |
| Serialization | Jackson 2.16.2 + `jackson-datatype-jsr310` for `LocalDateTime` |
| Logging | SLF4J + Logback in pom.xml but unused — services use `System.out.println` |
| UI | JavaFX 21 |

---

## Learning Plan: JDBC & JavaFX (for Philip, started 2026-08-30)

Goal: understand this project well enough to explain the code snippets (exam/presentation context).
Estimated total time: 4–6h (more like 6–8h if JDBC/JavaFX are completely new), best split over
2 sessions.

**Status (2026-09-05): step 1 (JDBC via `PercentageServiceApp.java`) done — full line-by-line
walkthrough incl. RabbitMQ connection/channel/exchange/queue/binding, `DeliverCallback`/`basicConsume`,
consumerTag, and Producer/Consumer roles across the pipeline. Bugs #1/#3/#5 fixed in that file as a
hands-on exercise (try-with-resources + manual ack/nack) — see "Known Bugs" table. Next: step 2, JavaFX
via the `gui` module (or, if preferred, first do the equivalent JDBC walkthrough of
`UsageServiceApp.java`, which was used for comparison but not fixed).**

### Plan / order
1. **JDBC** — walk through `percentage-service/.../PercentageServiceApp.java` line by line:
   - `DriverManager.getConnection(...)`, `PreparedStatement`, `setObject`/`setDouble`,
     `executeUpdate`, the `ON CONFLICT ... DO UPDATE` upsert pattern.
   - Tie it to the known bug: `Connection db` (line 26) is never closed — good example of why
     try-with-resources matters.
   - Optionally compare with `usage-service/.../UsageServiceApp.java` (same pattern, has a
     read-then-write race condition — see "Known Bugs" table above) and with `rest-api`'s
     Spring `JdbcTemplate` usage (higher-level, no manual Connection/PreparedStatement).
2. **JavaFX** — walk through `gui` module:
   - `GuiApplication.java`: `Application`, `FXMLLoader`, `Scene`, `Stage`.
   - `main-view.fxml` + `MainController.java`: how FXML wires to the controller.
   - `ApiClient.java`: how the GUI polls the REST API (`java.net.http.HttpClient` + Jackson).
3. Quick pass over `energy-producer` / `energy-user` / `rest-api` for the full picture (these
   are simple, low time investment).
4. Practice explaining out loud / to Claude.

### How to resume
Next session: just say "weiter mit dem Lernplan" or name the step (e.g. "lass uns mit JDBC an
PercentageServiceApp weitermachen") — update the Status line above as steps get done.
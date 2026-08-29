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
publishes to `update-queue`.

**Per-message logic:**
```
bucketHour = message.datetime truncated to hour

read current (produced, used, grid) for bucketHour from DB (or start at 0)

if PRODUCER:
    produced += kwh

if USER:
    available     = produced - used
    fromCommunity = min(kwh, available)   // consume local energy first
    fromGrid      = kwh - fromCommunity   // remainder from public grid
    used          += fromCommunity
    grid          += fromGrid

UPSERT into usage_bucket ON CONFLICT (bucket_hour)
publish UpdateMessage(bucketHour, produced, used, grid) → update-queue
```

**Known issues in this service:**
- `autoAck=true`: message is acknowledged immediately on delivery. If the DB write fails,
  the message is silently lost (not requeued).
- `java.sql.Connection db` is opened outside try-with-resources and never closed.
- Race condition potential: two concurrent messages could both read the same DB state,
  calculate independently, and one write overwrites the other's result.

---

### `percentage-service`
Consumes `update-queue`, computes two percentage metrics, writes to `percentage_record`.

**Calculations:**
```
communityDepleted = min(100, used / produced * 100)   // % of local production consumed
gridPortion       = grid / (used + grid) * 100         // % of total consumption from grid
```
Both guarded against division-by-zero.

**Known issues in this service:**
- `Connection` and `Channel` are NOT in try-with-resources (resource leak on shutdown).
- `java.sql.Connection db` is also not closed.
- `autoAck=true` same as usage-service.
- ObjectMapper is not configured with `disable(WRITE_DATES_AS_TIMESTAMPS)` — but since
  this service only deserializes (never serializes) datetimes, this has no effect in practice.

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

## Known Bugs (unfixed as of 2026-08-30)

| # | Location | Line | Issue |
|---|----------|------|-------|
| 1 | `PercentageServiceApp.java` | 34–35 | `Connection`/`Channel` not in try-with-resources → resource leak on shutdown |
| 2 | `UsageServiceApp.java` | 32 | `java.sql.Connection db` never closed |
| 3 | `PercentageServiceApp.java` | 26 | Same — `db` never closed |
| 4 | `UsageServiceApp.java` | 113 | `autoAck=true` — message lost if DB write fails |
| 5 | `PercentageServiceApp.java` | 84 | Same `autoAck=true` |
| 6 | `EnergyController.java` | 56–57 | `LocalDateTime.parse()` without try-catch → unhandled 500 on bad input |
| 7 | `EnergyMessage.java` | 3 | Unused import: `@JsonProperty` |

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
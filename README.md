| Module | Description |
|--------|-------------|
| `shared` | Common DTOs, database models, and RabbitMQ constants |
| `energy-producer` | Sends energy production messages to RabbitMQ (weather-based kWh) |
| `energy-user` | Sends energy usage messages to RabbitMQ (time-of-day-based kWh) |
| `usage-service` | Consumes energy messages, aggregates hourly data in PostgreSQL |
| `percentage-service` | Calculates community/grid percentages from usage data |
| `rest-api` | Spring Boot REST API — read-only access to the database |
| `gui` | JavaFX desktop application — displays data via the REST API |

## Setup Guide

### 1. Clone the repository

```bash
git clone <repository-url>
cd groupS_energyCommunities
```

### 2. Open in IntelliJ IDEA

1. Open IntelliJ IDEA
2. **File** → **Open** → select the root `pom.xml` → **Open as Project**
3. Wait for IntelliJ to finish indexing and downloading dependencies
4. Verify the SDK: **File** → **Project Structure** → **Project SDK** → select Java 21

### 3. Start Docker containers

All the information below was provided by the course:
```bash
docker compose -f docker/docker-compose.yml up -d
```

This starts:
- **PostgreSQL** on port `5432` (user: `disysuser`, password: `disyspw`, database: `energycommunities`)
- **RabbitMQ** on port `5672` (management UI at `http://localhost:15672`, login: `guest` / `guest`)

The database tables (`usage_bucket`, `percentage_record`) are created automatically via `docker/init.sql`.

### 4. Build the project

```bash
mvn clean install
```

This compiles all 7 modules and creates runnable JARs.

### 5. Verify everything works

Run each component to confirm the setup is correct (each in its own terminal):

```bash
# Standalone services (should print a startup message)
java -jar energy-producer/target/energy-producer-1.0-SNAPSHOT.jar
java -jar energy-user/target/energy-user-1.0-SNAPSHOT.jar
java -jar usage-service/target/usage-service-1.0-SNAPSHOT.jar
java -jar percentage-service/target/percentage-service-1.0-SNAPSHOT.jar

# REST API (starts on http://localhost:8080)
mvn spring-boot:run -pl rest-api

# GUI (opens a JavaFX window)
mvn javafx:run -pl gui
```

## Running the Components

### Start infrastructure

```bash
docker compose -f docker/docker-compose.yml up -d
```

### Stop infrastructure

```bash
docker compose -f docker/docker-compose.yml down
```

### Run services

| Component | Command |
|-----------|---------|
| Energy Producer | `java -jar energy-producer/target/energy-producer-1.0-SNAPSHOT.jar` |
| Energy User | `java -jar energy-user/target/energy-user-1.0-SNAPSHOT.jar` |
| Usage Service | `java -jar usage-service/target/usage-service-1.0-SNAPSHOT.jar` |
| Percentage Service | `java -jar percentage-service/target/percentage-service-1.0-SNAPSHOT.jar` |
| REST API | `mvn spring-boot:run -pl rest-api` |
| GUI | `mvn javafx:run -pl gui` |

### Ports

| Service | Port |
|---------|------|
| PostgreSQL | 5432 |
| RabbitMQ (AMQP) | 5672 |
| RabbitMQ (Management UI) | 15672 |
| REST API | 8080 |

## Git Workflow

### Rules

- **Never push directly to `main`** — use feature branches and Pull Requests (pushing in)

### How to work on a new feature

```bash
# 1. Make sure you're on main and up to date
git checkout main
git pull origin main

# 2. Create a feature branch
git checkout -b feature/your-feature-name

# 3. Work on your changes, commit regularly
git add .
git commit -m "description"

# 4. Push your feature branch
git push -u origin feature/your-feature-name

# 5. Create a Pull Request on GitHub
#    - Go to the repository on GitHub
#    - Click "Compare & pull request"
#    - Add a description of your changes
#    - Request a review from a teammate

# 6. After approval, merge the PR on GitHub

# 7. Clean up locally
git checkout main
git pull origin main
git branch -d feature/your-feature-name --> keep the branches on github for now, do not delete them
```

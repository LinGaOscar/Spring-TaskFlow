# Dev Setup

Quick start for local development. Full architecture, env var reference, test accounts, and production notes: [`docs/dev.md`](docs/dev.md).

- **Requirements**: JDK 21, Maven (or use Docker and skip local Maven entirely), Docker (for SQL Server 2022)

```bash
cp .env.example .env              # set MSSQL_SA_PASSWORD

# Option A: everything in Docker (app + db)
docker compose up --build -d      # http://localhost:8050

# Option B: db in Docker, app locally (hot reload)
docker compose up -d db
mvn spring-boot:run

# Tests (H2 in-memory, no Docker needed)
mvn test
```

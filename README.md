# AInalyzing

An AI assistant that compares supplier quotes for perishable stock and calculates the maximum price or minimum remaining shelf life that makes a deal worthwhile, based on forecast demand and budget.

Team F3CodeIsLife · LIKELION AI for Business Innovation 2026. Requirements, design and the working rules live in the team Google Doc; the API contract is copied to [docs/api.md](docs/api.md).

## Versions

| Part | Version | Pinned in |
|---|---|---|
| Java | 21 (Temurin 21.0.13+9) | `api/pom.xml` (`java.version`) |
| Spring Boot | 4.1.2 | `api/pom.xml` parent |
| Maven | 3.9.16 via wrapper | `api/.mvn/wrapper/maven-wrapper.properties` |
| Node.js / npm | 24.22.2 / 11.19.1 | `web/.nvmrc` |
| React, React DOM | 19.3.1 | `web/package.json` + `web/package-lock.json` |
| Vite / @vitejs/plugin-react | 8.3.7 / 6.2.1 | `web/package.json` + `web/package-lock.json` |
| PostgreSQL | 18.6 | `compose.yaml` |

Changing a version: one PR that updates this table, the file in the third column and the Google Doc Convention §2.

## Layout

```
api/   Spring Boot. Packages under vn.ainalyzing.api:
       web (controllers, DTO)  analysis (FEFO, ranking, conditions)  csv (CSV, forecast, sales summary)
       llm (Groq)  demo (demo cases from PostgreSQL)
web/   React + Vite. Components in src/components, API calls in src/api.
data/demo/  Synthetic CSV files.
docs/  api.md (contract), deploy.md, qa.md.
```

## Run locally

```bash
cp .env.example .env            # set DB_PASSWORD
docker compose -f compose.yaml -f compose.local.yaml up -d db
cd api && ./mvnw spring-boot:run       # Windows: .\mvnw.cmd spring-boot:run
cd web && npm ci && npm run dev        # http://localhost:5173, /api goes to :8080
```

Spring Boot reads `DB_URL`, `DB_USER`, `DB_PASSWORD` from the environment (defaults point at the local container). Flyway creates the table and seeds the four demo cases on start-up. Without `GROQ_API_KEY` the app still runs and uses the rule-based fallback card.

## Test

```bash
cd api && ./mvnw verify      # unit tests, no database needed
cd web && npm run build
```

`PurchaseCalculatorTest` checks every number in the Specification's check table (Phụ lục A.2); `CsvForecastTest` checks that `data/demo/yogurt_56d.csv` gives the demand series in Phụ lục A.5.

## Git

No direct pushes to `main`. One branch per task, `<area>/<task-id>-<short-name>` with area `backend`, `frontend`, `llm` or `docs`, e.g. `backend/04-analyze`. Conventional Commits (`feat(csv): ...`). Open a draft PR early; another member reviews before squash-merge.

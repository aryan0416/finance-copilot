# Finance Copilot — working notes for Claude sessions

AI-powered personal finance app (resume project). Users upload Indian bank statements (CSV/PDF, INR, UPI
descriptions); the backend parses, categorises (rules first, AI last), detects subscriptions, serves
dashboards and answers plain-English questions via validated text-to-SQL.

## Layout
- `backend/` Spring Boot 3.5 (Java 21 target), Maven wrapper. Package root `app.financecopilot`:
  `controller` · `service` · `repository` · `domain` · `dto` (records) · `config` · `security` (JWT) ·
  `exception` (RFC 7807 ProblemDetail) · `parsing` · `categorization` · `ai` (+ `ai.sql`)
- `frontend/` React 18 + Vite + TS + Tailwind v4 (+ shadcn/ui, Recharts, TanStack Query, React Router, Axios)
- `docs/` architecture (Mermaid), screenshots · `sample-data/` generated statements

## Commands
```bash
# backend (reads secrets from backend/.env via spring.config.import; run from backend/)
cd backend && ./mvnw spring-boot:run          # default profile: dev (seeds demo user)
cd backend && ./mvnw verify                    # unit + Testcontainers tests (needs Docker running)
SPRING_PROFILES_ACTIVE=dev,local ./mvnw spring-boot:run   # use local docker Postgres instead of Supabase

# frontend
cd frontend && npm install && npm run dev      # http://localhost:5173, proxies /api -> :8080
cd frontend && npm run lint && npm run build

# full stack in containers (UI :3000, API :8080); add --profile local-db + SPRING_PROFILES_ACTIVE=dev,local for local PG
docker compose up --build
```
CI (`.github/workflows/ci.yml`): `./mvnw verify` (unit + Testcontainers) and frontend lint/build.
Integration tests extend `support/IntegrationTest` (singleton Postgres container, stub `LlmClient` via
`LLM_ANSWER`); they are skipped automatically when Docker is not running.
Swagger UI: http://localhost:8080/swagger-ui.html · Demo login (dev profile): demo@financecopilot.app / Demo@123

## Conventions
- Secrets only via env vars (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `GEMINI_API_KEY`,
  `AI_PROVIDER`). `backend/.env` is git-ignored; `backend/.env.example` documents them. Never log secrets.
- Schema changes = new Flyway migration (`V<n>__desc.sql`). `ddl-auto=validate`; never edit applied migrations.
- Every user-owned query takes `userId` from `@AuthenticationPrincipal AuthenticatedUser`. No endpoint
  accepts a user id from the client.
- Controllers speak DTO records only; entities never leave the service layer.
- Errors: throw `ApiException` (status + safe message); `GlobalExceptionHandler` renders problem+json.
- Conventional commits (`feat:`, `fix:`, `test:`, `docs:`, `chore:`), one logical change each.

## Key design (interview talking points)
- **Categorisation pipeline** (`categorization/`): user rules → built-in merchant keyword rules
  (`category_rules` with `user_id NULL`) → shared `merchant_category_cache` → batched LLM (≤50 merchants
  per call, strict JSON validated against the category list, results cached). 429 → exponential backoff;
  any AI failure → "Uncategorised", never a failed upload.
- **Text-to-SQL safety** (`ai/sql/`): LLM sees one virtual table `my_transactions`; JSqlParser allows a
  single SELECT over that table with whitelisted functions only; query is wrapped in a CTE filtered by a
  bound `user_id`, run READ ONLY with `statement_timeout = 3s`, auto `LIMIT 500`, logged to `ai_query_log`.
- **LLM provider**: `LlmClient` interface; Spring AI 1.1 with both `google-genai` and `ollama` starters on
  the classpath, selected by `spring.ai.model.chat` (`AI_PROVIDER`).

## Decisions & assumptions (ambiguities resolved)
- Spring Boot **3.5.x** (spec says 3.x; Boot 4 exists) → Spring AI **1.1.x** (2.x requires Boot 4).
  Gemini via `spring-ai-starter-model-google-genai` (Gemini Developer API key; no GCP project needed).
- Local JDK is 24; code targets `release 21`. CI uses Temurin 21.
- React pinned to 18.3 and TypeScript to 5.9 (Vite template defaults to React 19 / TS 7).
- `transactions.txn_date` instead of `date` (avoids a keyword column); exposed as `date` in `my_transactions`.
- `category_source` adds `CACHE` (hit in shared merchant cache) and `NONE` (AI unavailable → Uncategorised)
  to the spec's RULE/AI/USER, so "% categorised without an AI call" = (RULE + CACHE) / total is exact.
- `accounts` table (optional in spec) omitted for the MVP; one implicit account per user.
- Merchant cache is global and stores only system categories (never user-defined ones), so it is safe to share.
- Categories: system rows have `user_id NULL`; uniqueness via `UNIQUE NULLS NOT DISTINCT` (Postgres 15+).
- Built-in rules: KEYWORD = case-insensitive substring; REGEX = Java regex (short tokens like `\bola\b`
  are word-bounded). Lower `priority` wins.
- Supabase free-tier pooler has few connections → Hikari pool size 5.
- Gemini model default `gemini-3.5-flash-lite` (`GEMINI_MODEL`); 2.5-series models are closed to new keys.
- LLM categorisation sends only merchant + direction + digit-masked narration (no amounts/balances).
  Only system categories are offered to the model, so the shared cache never holds user categories.
- Subscriptions: re-derived from the full debit history after every import (delete + insert). "Active" and
  the dashboard's "upcoming" window use the latest transaction date as reference, not wall-clock today,
  so older statements still show meaningful results.
- Dashboard money semantics: TRANSFER-kind categories (Transfers, Investments) are neither income nor spend;
  refunds (credits in expense categories) reduce spend; income = credits in INCOME or Uncategorised.
- Anomaly = debit > mean + 2·stddev of the user's debits in that category (needs ≥5 samples), last 3 months.
- Ask-AI: user SQL may not use WITH (the executor's CTE is the only one), comments, SELECT INTO or FOR
  UPDATE; functions are allowlisted in `SqlValidator.ALLOWED_FUNCTIONS`. One automatic repair round feeds
  the validator/DB error back to the model. Pie answers with >8 rows are downgraded to bar server-side.
- Gemini may split one answer over several content parts; `SpringAiLlmClient` joins all generations.
- Insights: aggregates only, cached in memory per user + month + figures hash; partial months are flagged
  (`partialMonthDataThrough`) so the model does not report a fake drop.
- Auth: single access token (24h) stored in localStorage; `/api/auth/me` restores the session on reload.
  No refresh-token rotation (documented as a future improvement).

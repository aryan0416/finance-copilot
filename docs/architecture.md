# Architecture

## System overview

```mermaid
flowchart LR
    subgraph Browser
        UI["React 18 SPA<br/>TanStack Query · Recharts · shadcn/ui"]
    end

    subgraph Backend["Spring Boot 3.5 (Java 21)"]
        SEC["Spring Security<br/>stateless JWT filter"]
        API["REST controllers<br/>DTO records · problem+json"]
        IMP["StatementImportService<br/>CSV / PDF parsers · MerchantNormalizer"]
        CAT["CategorizationService<br/>rules → cache → LLM"]
        SUB["SubscriptionDetector<br/>pure Java"]
        DASH["DashboardService<br/>SQL aggregates"]
        ASK["TextToSqlService<br/>SqlValidator · SafeQueryExecutor"]
        INS["InsightService<br/>aggregates only"]
        LLM["LlmClient<br/>RetryingLlmClient → Spring AI"]
    end

    DB[("PostgreSQL<br/>Supabase · Flyway")]
    GEM["Gemini<br/>(or Ollama)"]

    UI -- "HTTPS + Bearer JWT" --> SEC --> API
    API --> IMP --> CAT
    IMP -- "StatementImported event" --> SUB
    API --> DASH
    API --> ASK
    API --> INS
    CAT --> LLM
    ASK --> LLM
    INS --> LLM
    LLM --> GEM
    IMP & CAT & SUB & DASH & ASK & INS --> DB
```

## Upload → categorisation pipeline

```mermaid
flowchart TD
    F["Uploaded file"] --> T{"PDF magic bytes?"}
    T -- yes --> P["PdfStatementParser<br/>PDFBox text + line regex"]
    T -- no --> C["CsvStatementParser<br/>header scan + column auto-detect"]
    P & C --> N["MerchantNormalizer<br/>UPI/POS/NEFT noise → 'Swiggy'"]
    N --> H["SHA-256 row hash<br/>skip rows already imported"]
    H --> A["(a) User rules"]
    A -- unresolved --> B["(b) Built-in merchant rules"]
    B -- unresolved --> M["(c) Shared merchant cache"]
    M -- unresolved --> L["(d) LLM, deduplicated + batched ≤50,<br/>strict JSON validated against category list"]
    L -- answers --> M2[("merchant_category_cache")]
    L -- failure / 429 after retries --> U["'Uncategorised' (upload still succeeds)"]
    A & B & M & L --> W["JDBC batch insert<br/>ON CONFLICT DO NOTHING"]
    W --> S["Subscription re-detection"]
```

## Ask-AI request path

```mermaid
sequenceDiagram
    participant U as User
    participant API as AskController
    participant T as TextToSqlService
    participant G as Gemini
    participant V as SqlValidator
    participant X as SafeQueryExecutor
    participant PG as Postgres

    U->>API: POST /api/ask {question}
    API->>T: ask(userId from JWT, question)
    T->>G: schema of my_transactions + today's date + question
    G-->>T: {sql, chartType, explanation}
    T->>V: validate(sql)
    Note over V: single SELECT · no comments/WITH/INTO/FOR UPDATE<br/>only my_transactions · function allowlist · LIMIT ≤ 500
    alt rejected
        V-->>T: UnsafeSqlException
        T->>G: one repair attempt with the reason
    end
    T->>X: execute(userId, validated)
    X->>PG: BEGIN READ ONLY; SET LOCAL statement_timeout='3s'
    X->>PG: WITH my_transactions AS (… WHERE user_id = ?) <validated SELECT>
    PG-->>X: rows (max 500)
    T->>PG: insert ai_query_log
    T-->>U: columns, rows, chartType, explanation, sql
```

## Data model

```mermaid
erDiagram
    users ||--o{ statements : uploads
    users ||--o{ transactions : owns
    users ||--o{ category_rules : "defines (user rules)"
    users ||--o{ subscriptions : has
    users ||--o{ ai_query_log : "audited"
    statements ||--o{ transactions : contains
    categories ||--o{ transactions : classifies
    categories ||--o{ category_rules : targets
    categories ||--o{ merchant_category_cache : "cached answer"
    transactions {
        bigint user_id
        date txn_date
        text description_raw
        varchar merchant_normalized
        numeric amount
        varchar direction "DEBIT | CREDIT"
        varchar category_source "RULE | CACHE | AI | USER | NONE"
        char hash "unique per user"
    }
```

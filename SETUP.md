# Setup, Run & Test Guide

End-to-end instructions for the NL → SL → DSL → AST → IR → Runtime pipeline.

---

## 1. Dependencies

| Tool | Version | Why | Where to get it |
|------|---------|-----|-----------------|
| **JDK** | **25** (required, LTS) | Built with `--release 25` + Spring Boot 3.5.13 — the compiler itself must be JDK 25 | https://adoptium.net/temurin/releases/?version=25 |
| **Maven** | 3.9+ | Backend build tool | https://maven.apache.org/download.cgi |
| **Node.js** | 18+ (includes npm) | Frontend build + dev server | https://nodejs.org/ |
| **Git** | any recent | Source control | https://git-scm.com/download/win |
| Anthropic API key | optional | Only for the Claude NL→SL strategy | https://console.anthropic.com/ |

The database is **H2 in-memory** — bundled, nothing to install.

### Verify the toolchain

```
java -version      # must report 25.x
mvn -version       # the "Java version:" line must also be 25.x
node -version      # must be 18+
git --version
```

The build uses `--release 25`, so the JDK running the compile **must be JDK 25**
(you cannot target 25 from an older JDK). If `mvn -version` shows anything below
25, point `JAVA_HOME` at JDK 25:

```
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.1.9-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -version
```

(Adjust the folder name to the one actually under `C:\Program Files\Eclipse Adoptium\`
— run `dir "C:\Program Files\Eclipse Adoptium"` to see the exact `jdk-25...` name.)

---

## 2. Backend — build & run

PowerShell does not support `&&` — run each line separately.

```
cd "C:\Users\Rohit Rajwansi\Downloads\March_v6\dsl-ir-pipeline-standalone\backend"
mvn clean package -DskipTests
mvn spring-boot:run
```

The backend is ready when the log shows `Started DslPipelineApplication`. It listens on
**http://localhost:8090**. Leave this window open.

Health check (second window):

```
curl http://localhost:8090/api/pipeline/health
```

Expected: `{"status":"UP","claudeAvailable":false,"extensionFunctions":...}`

Observability — confirm metrics are exposed:

```
curl http://localhost:8090/actuator/health
curl http://localhost:8090/actuator/prometheus
```

The Prometheus output includes `dslpipeline_*` counters/timers. Wire it into
Prometheus + Grafana with the files in the `observability/` folder.

### Optional — enable the Claude strategy

Set the key in the **same window** before `mvn spring-boot:run`:

```
$env:ANTHROPIC_API_KEY = "sk-ant-...your-key..."
mvn spring-boot:run
```

`claudeAvailable` then becomes `true`. The pipeline works fully without it (rule-based parser).

---

## 3. Frontend — run

Open a **second** PowerShell window:

```
cd "C:\Users\Rohit Rajwansi\Downloads\March_v6\dsl-ir-pipeline-standalone\frontend"
npm install
npm run dev
```

Open **http://localhost:5173**. The Vite dev server proxies `/api/*` to the backend, so CORS just works.

---

## 4. Test

### Unit + integration tests (backend)

```
cd "C:\Users\Rohit Rajwansi\Downloads\March_v6\dsl-ir-pipeline-standalone\backend"
mvn test
```

- `PipelineE2ETest` — every stage of the canonical example `customer age < 18 decline the loan`,
  the function-expression path (`calculateAge`), decimal exactness, validation failure cases.
- `ComponentUnitTest` — date functions (incl. leap-day cases), term parser, condition parser,
  decimal rounding modes, AST optimizer constant folding.
- `DataLayerTest` — deep-debug per data stage: tenant/project backbone, `config_schema`
  resolution, `config_databag` seeding, `custom_extension_function` (SpEL) registration +
  invocation, and three-table rule persistence (`rule_definition`/`dsl`/`ir`) + execution.

On startup the app seeds a demo tenant `acme`, project `lending`, an ACTIVE schema, a
`pricing` databag, and a `custom.incomeRiskBand` SpEL function — so the data-layer
endpoints have content immediately. Browse it in the H2 console:
`SELECT * FROM rule_definition;`, `SELECT * FROM config_databag;`,
`SELECT * FROM custom_extension_function;`.

### Manual test in the UI

1. Backend + frontend running. Open http://localhost:5173.
2. NL box is pre-filled with `customer age < 18 decline the loan`; payload has `customer.age = 16`.
3. Click **Run pipeline** — all 8 stages render; Stage 10 shows **FAILED**, error `[LOAN_DECLINED]`,
   and a Why/Why-Not condition tree.
4. Change payload age to `25`, rerun — now **PASSED**.
5. Switch to **DSL → IR (direct)** mode, click *Load calculateAge example*, run — exercises a
   function-expression rule end to end.

### cURL smoke test

```
curl.exe -X POST http://localhost:8090/api/pipeline/end-to-end -H "Content-Type: application/json" -d "{\"nl\":\"customer age < 18 decline the loan\",\"strategy\":\"rule\",\"schema\":{\"customer.age\":\"number\"},\"payload\":{\"customer\":{\"age\":16}},\"generateScenarios\":true}"
```

Expect `"status":"OK"`, `"passed":false`, `"code":"LOAN_DECLINED"`.

### Inspect persisted IR (H2 console)

Open http://localhost:8090/h2-console — JDBC URL `jdbc:h2:mem:dslirdb`, user `sa`, blank password.

```
SELECT rule_id, version, dsl_hash, compiled_at FROM ir_artifact;
```

---

## 5. Production build (optional)

```
# backend → runnable jar
cd backend
mvn clean package
java -jar target/dsl-ir-pipeline-backend-1.0.0.jar

# frontend → static bundle in frontend/dist
cd frontend
npm run build
```

---

## 6. For the QA team (cloning from git)

The repo is committed **without any API key** — `llm-secret.properties` is
gitignored. A QA engineer who clones the repo does the following:

```
git clone <repo-url>
cd <repo>/backend
mvn clean package          # compiles + runs all 5 test suites
mvn spring-boot:run        # starts the backend on :8090
```

That alone exercises ~95% of the system with **no key required** — the
deterministic NL→SL→DSL→AST→IR pipeline (rule-based parser), the money
conformance suite, the Claims collection pack, the data layer, and observability.

To also test the **Claude LLM features** (`/api/llm/*` endpoints, the "Claude
API" parser option), QA supplies their **own** Anthropic key — they do not need
yours:

```
cd backend
Copy-Item llm-secret.properties.example llm-secret.properties
# edit llm-secret.properties → set a real anthropic.api.key
mvn spring-boot:run
```

Summary: **do not upload the project with your key.** Push it key-free (the
gitignore already ensures this). QA tests the deterministic engine with no key,
and uses their own key only if they need to exercise the LLM layer.

## 7. Troubleshooting

| Symptom | Fix |
|---------|-----|
| `release version 25 not supported` | `mvn -version` shows Java < 25 — install JDK 25, point `JAVA_HOME` at it, reopen the shell |
| `invalid target release: 25` | Same cause — the compiling JDK is older than 25 |
| `The token '&&' is not a valid statement separator` | PowerShell — run commands on separate lines |
| Frontend network errors | Backend not on :8090 — `curl http://localhost:8090/api/pipeline/health` |
| Claude option disabled in UI | `ANTHROPIC_API_KEY` not set — set it, restart backend |
| `Port 8090 already in use` | Edit `server.port` in `backend/src/main/resources/application.yml`, update Vite proxy in `frontend/vite.config.ts` |
| A rule shows `BLOCKED_AT_SL_LINT` | The deterministic parser couldn't reduce the NL — rephrase as `if <condition> then <action>`, or use the Claude strategy |

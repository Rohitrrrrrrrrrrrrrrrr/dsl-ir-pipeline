# NL → SL → DSL → AST → IR → Runtime — Compiler-Grade Rule Pipeline

A standalone, deeply-engineered reference implementation of the RulesGen / ZenLogIQ
translation pipeline described in the project documentation. It treats rule
authoring as a **compiler**: every layer has one job, every stage is a gate, and
the runtime executes only the canonical IR.

> **One-line principle:** *DSL is for humans, AST is for correctness, IR is for machines.*

This project is **separate** from the ZenLogIQ Git repo — that repo was the
reference; nothing here depends on it.

---

## The pipeline

```
NL ──▶ SL ──▶ SL-lint ──▶ DSL ──▶ DSL-validate ──▶ AST ──▶ type-check
                                                              │
                                          AST-optimise ◀──────┘
                                                │
                                                ▼
                                          Canonical IR ──▶ (persist) ──▶ Runtime
                                                                              │
                                                                       Test Engine
```

| Stage | Layer | Responsibility |
|-------|-------|----------------|
| 1 | **NL → SL** | Interpretation. Rule-based parser **or** Claude API. Vague terms become *ambiguities*, never silent guesses. Confidence scored. |
| 2 | **SL lint** | Ambiguity removal + DAL alignment (unknown field / type-operator mismatch / unknown function). Runs **before** any LLM call. |
| 3 | **SL → DSL** | Formalisation into the deterministic, diff-friendly `RuleDSL` contract. |
| 4 | **DSL validate** | Grammar + schema + function whitelist + arity. "If it cannot be parsed deterministically → it is invalid DSL." |
| 5 | **DSL → AST** | Parsed syntactic tree: `RULE` → `WHEN` / `THEN` / `ELSE`. |
| 6 | **Type check** | Function arg types, ordered-comparison types, schema resolution. *AST is where correctness is enforced.* |
| 7 | **AST optimise** | Constant folding (`calculateAge("2000-01-01","2020-01-01")` → `20`) + dead-branch elimination. |
| 8 | **AST → IR** | Canonicalisation: paths → segment arrays, operator aliases collapsed, literals tagged, numeric profile + provenance attached. |
| — | **Runtime** | Deterministic interpreter — **IR only**, no DSL/AST at runtime. Decimal-safe, UTC date-only. Emits a Why/Why-Not explanation + golden trace. |
| — | **Test engine** | Mechanically generates boundary test scenarios from the IR; golden-trace parity. |

---

## What makes this build "compiler-grade"

- **Sealed type system** — `ConditionNode` (9 types) and `DslAction` (4 types)
  are sealed interfaces; every traversal is an exhaustive `switch`.
- **Extension registry** — core packs (`date`, `collection`, `string`, `logic`)
  + an example project pack (`acme`), namespaced and merged
  (`MergedRegistry = Core ⊕ Project`). Functions carry typed signatures used by
  the validator, the type checker and the trace.
- **Function expressions in condition terms** — a leaf's `left` can be
  `calculateAge(applicant.dateOfBirth, loan.startDate)`. A recursive-descent
  `TermParser` parses it; the executor evaluates it.
- **Deterministic decimal numeric profile** — `dec("100.00")` literals,
  `HALF_UP` / `HALF_EVEN`, explicit scale; **no binary float** anywhere in the
  rule path. This is the "Validate Once, Execute Anywhere" foundation.
- **THEN / ELSE branches** — supports both authoring styles: violation-style
  (`age < 18 → addError`) and eligibility-style (`age >= 18 → ensure ELSE raise`).
- **Explainability** — every execution returns a structured *Why / Why-Not*
  condition tree and a narrative.
- **Provenance** — IR embeds `dslHash`, compiler version, source NL, SL strategy,
  SL confidence and (when Claude is used) the prompt hash.
- **Golden traces + mechanical scenario generation** — boundary cases are
  generated from the IR thresholds and executed to pin behaviour.

---

## Project layout

```
dsl-ir-pipeline-standalone/
├── backend/   Spring Boot 3.5.13 · Java 25
│   └── src/main/java/com/dslpipeline/
│       ├── numeric/      NumericProfile · DecimalMath
│       ├── term/         Term (sealed) · TermParser · TermEvaluator
│       ├── extensions/   ExtensionFunction · ExtensionRegistry · Core/Project packs
│       ├── vocabulary/   Vocabulary (aliases + synonyms)
│       ├── schema/       DomainSchema (the DAL anchor)
│       ├── model/        sl · dsl (ConditionNode, DslAction, RuleDSL) · ast · ir
│       ├── pipeline/     NlToSlConverter · SlLinter · ConditionParser ·
│       │                 SlToDslCompiler · DslToAstBuilder · TypeChecker ·
│       │                 AstOptimizer · IrBuilder
│       ├── validator/    DslValidator
│       ├── executor/     IrExecutor (runtime + explainability)
│       ├── testengine/   ScenarioGenerator · GoldenTrace
│       ├── llm/          ClaudeClient
│       ├── service/      PipelineService (orchestrator)
│       └── controller/   PipelineController · GlobalExceptionHandler
└── frontend/  React 18 · Vite · TypeScript
```

---

## Prerequisites

- **JDK 25** (LTS — `java --version` must report 25.x; the build compiles with `--release 25`)
- **Maven 3.9+**
- **Node 18+**
- *(optional)* `ANTHROPIC_API_KEY` for the Claude NL→SL strategy

The database is **H2 in-memory** — nothing to install. Console at
`http://localhost:8090/h2-console` (JDBC `jdbc:h2:mem:dslirdb`, user `sa`, blank password).

---

## Run it

**Backend** (PowerShell):
```
cd "C:\Users\Rohit Rajwansi\Downloads\March_v6\dsl-ir-pipeline-standalone\backend"
mvn clean package -DskipTests
mvn spring-boot:run
```
Backend starts on `http://localhost:8090`. Verify:
```
curl http://localhost:8090/api/pipeline/health
```

**Frontend** (second window):
```
cd "C:\Users\Rohit Rajwansi\Downloads\March_v6\dsl-ir-pipeline-standalone\frontend"
npm install
npm run dev
```
Open `http://localhost:5173`.

**Enable Claude (optional):**
```
$env:ANTHROPIC_API_KEY = "sk-ant-..."
mvn spring-boot:run
```
The header then shows `Claude on` and the parser dropdown unlocks. The pipeline
is fully testable without it — the rule-based parser is the default.

---

## Using the app

The UI has two modes:

- **NL → IR pipeline** — type a business rule; watch all 8 stages render, the
  optimisation transformations, the IR, the runtime Why/Why-Not explanation and
  the mechanically generated test scenarios.
- **DSL → IR (direct)** — paste a `RuleDSL` JSON (function expressions,
  quantifiers, decision tables) and run it straight through validate → AST →
  type-check → optimise → IR → execute. Use the *Load calculateAge example*
  button for a function-expression rule.

Pre-loaded NL examples include the canonical `customer age < 18 decline the loan`
and the documented `Customers under 18 are not eligible unless special approval
is granted`.

---

## QA test guide (step by step)

### A. The canonical example, end to end
1. Backend + frontend running. Open `http://localhost:5173`.
2. NL box: `customer age < 18 decline the loan`. Payload: `customer.age = 16`.
3. Click **Run pipeline**. Expect: 8 green stage cards; Stage 10 shows
   **✗ FAILED**, error `[LOAN_DECLINED]`, and a Why/Why-Not tree
   `customer.age < 18 → true`.
4. Change payload age to `25`, rerun → **✓ PASSED**, conditions not met.

### B. Function expression (DSL mode)
1. Switch to **DSL → IR (direct)**, click *Load calculateAge example*.
2. Run. The IR shows a `CALL` node for `calculateAge`; execution computes the
   applicant's age from `dateOfBirth` + `loan.startDate` and declines if under 18.

### C. cURL smoke test
```
curl.exe -X POST http://localhost:8090/api/pipeline/end-to-end ^
  -H "Content-Type: application/json" ^
  -d "{\"nl\":\"customer age < 18 decline the loan\",\"strategy\":\"rule\",\"schema\":{\"customer.age\":\"number\"},\"payload\":{\"customer\":{\"age\":16}},\"generateScenarios\":true}"
```
Expect `"status":"OK"`, `"passed":false`, `"code":"LOAN_DECLINED"`, and a
`stage11_scenarios` array of boundary cases.

### D. JUnit suite
```
cd backend
mvn test
```
- `PipelineE2ETest` — every stage of the canonical example, the function-expression
  path, decimal exactness, validation failure cases.
- `ComponentUnitTest` — date functions (incl. leap-day per spec), term parser,
  condition parser, decimal rounding modes, AST optimizer folding.

### E. Persisted artifacts
H2 console (`/h2-console`) → `SELECT rule_id, version, dsl_hash, compiled_at FROM ir_artifact;`
Each end-to-end run stores the full SL/DSL/AST/IR JSON for traceability.

---

## REST API

| Endpoint | Purpose |
|----------|---------|
| `GET /api/pipeline/health` | Liveness + Claude availability + function count |
| `GET /api/pipeline/extensions` | List every registered extension function |
| `POST /api/pipeline/nl-to-sl` | Stage 1 |
| `POST /api/pipeline/lint-sl` | Stage 2 (`{sl, schema}`) |
| `POST /api/pipeline/sl-to-dsl` | Stage 3 |
| `POST /api/pipeline/validate-dsl` | Stage 4 |
| `POST /api/pipeline/dsl-to-ast` | Stage 5 |
| `POST /api/pipeline/type-check` | Stage 6 (`{ast, schema}`) |
| `POST /api/pipeline/optimize` | Stage 7 |
| `POST /api/pipeline/build-ir` | Stage 8 |
| `POST /api/pipeline/execute-ir` | Runtime (`{ir, payload}`) |
| `POST /api/pipeline/generate-scenarios` | Test engine (`{ir}`) |
| `POST /api/pipeline/end-to-end` | Whole NL → IR → run pipeline |
| `POST /api/pipeline/from-dsl` | DSL → IR → run (skips NL/SL) |
| `GET /api/pipeline/artifacts` | All persisted IR artifacts |

---

## Extension registry

Core packs (always available, namespaced + bare aliases):

- **date** — `calculateAge`, `compareDates`, `diffDays`, `daysBetween`,
  `addDays`, `addMonths`, `isBefore`, `isAfter`, `isOnOrAfter`, `isBetween`,
  `isWeekend`, `startOfMonth`, `endOfMonth`, `withinDays`
- **collection** — `count`, `sum`, `avg`, `min`, `max`, `isEmpty`, `distinctCount`
- **string** — `length`, `upper`, `lower`, `trim`, `contains`, `startsWith`, `concat`
- **logic** — `isNull`, `isBlank`, `coalesce`

Project pack `acme` — `acme.riskBand`, `acme.isHighValue` (project packs are
namespaced and may not shadow a core namespace).

All functions are pure, deterministic and UTC date-only — no system clock, no
locale surprises.

---

## Data layer — faithful to the real ZenLogIQ schema

The pipeline above is backed by a persistence layer that mirrors the actual
ZenLogIQ tables (verified against `database/migrations/V1__initial_schema.sql`
and the databag/schema/function migrations):

| Table | Purpose |
|-------|---------|
| `core_tenant` / `core_project` | Multi-tenant backbone — everything hangs off a project |
| `rule_definition` | The authored rule record (identity, intent, structured content) |
| `rule_dsl_artifact` | Versioned human-authored DSL form of a rule |
| `rule_ir_artifact` | Canonical compiled IR form (sourced from a DSL artifact) |
| `config_schema` | Domain/DAL schema with a DRAFT→ACTIVE lifecycle |
| `config_databag` | Declared runtime variables (`fields[]` JSON) |
| `custom_extension_function` | Project functions stored as **SpEL** expressions |

Key point on faithfulness: a rule is **not** stored as `rule_conditions` /
`rule_actions` rows. ZenLogIQ stores rules as **DSL artifacts compiled to IR
artifacts** — `rule_definition → rule_dsl_artifact → rule_ir_artifact`. This
project implements that exactly.

**DataBags** declare `fields[]` ({path, type, defaultValue}); at execution start
those defaults seed `payload["dataBag"]`. **Custom functions** are SpEL bodies
loaded from the DB and registered into the `ExtensionRegistry` at runtime — no
redeployment to add a project function.

On startup a `DataInitializer` seeds a demo tenant (`acme`), project (`lending`),
an ACTIVE schema, a `pricing` databag and a `custom.incomeRiskBand` SpEL function.

### Data-layer REST API

| Endpoint | Purpose |
|----------|---------|
| `GET/POST /api/tenants`, `…/{tenant}/projects` | Tenant + project CRUD |
| `GET/POST/PUT /api/schemas`, `…/{id}/transition` | config_schema CRUD + lifecycle |
| `GET …/schemas/{tenant}/{project}/resolve` | Effective ACTIVE schema |
| `GET/POST/PUT/DELETE /api/databags` | config_databag CRUD |
| `POST /api/databags/seed-preview` | Preview payload after databag seeding |
| `GET/POST/PUT/DELETE /api/functions` | custom_extension_function CRUD |
| `POST /api/functions/{id}/test` | Invoke a SpEL function with args |
| `GET /api/rules?tenant=&project=` | List authored rules |
| `GET /api/rules/{tenant}/{project}/{ruleKey}` | Rule + DSL/IR artifact history |
| `POST /api/rules/{tenant}/{project}/{ruleKey}/execute` | Execute a stored rule |
| `POST /api/pipeline/end-to-end` with `tenant`,`project`,`saveAsRuleKey` | Compile + persist a rule across the 3 tables |

### LLM integration (multi-intent, faithful to `24-live-llm-integration.md`)

The LLM only ever **translates / proposes / explains** — it never executes a
rule or makes the decision. Per-intent model routing mirrors `ai_model_policy`.

| Endpoint | Intent |
|----------|--------|
| `POST /api/llm/normalize` | `normalize_nl` — NL → Structured Logic |
| `POST /api/llm/translate` | `translate_dsl` — Structured Logic → RuleDSL |
| `POST /api/llm/narrate` | `structured_to_nl` — RuleDSL → plain English |
| `POST /api/llm/fix` | `fix_dsl` — self-healing DSL repair loop (max 3 attempts) |
| `POST /api/llm/explain` | `explain_decision` — "why" narrative from a trace |
| `GET/PUT /api/llm/model-policy` | Per-intent model routing table |

All LLM endpoints require `ANTHROPIC_API_KEY`; the deterministic pipeline works
fully without it.

## Observability (Micrometer → Prometheus → Grafana)

The backend exposes Micrometer metrics under the `dslpipeline.*` namespace via
Spring Boot Actuator at **`/actuator/prometheus`**. `PipelineMetrics` instruments
every run: pipeline status, stage blocks, DSL-validation outcome, IR execution
pass/fail, rules persisted, LLM calls by intent, self-heal retry attempts +
success rate, and an end-to-end duration timer (p50/p95).

The `observability/` folder ships a drop-in `prometheus-scrape.yml`, an
importable `grafana-dashboard.json` (7 panels), and a metric-catalogue `README.md`.
Quick check once the backend is up:

```
curl http://localhost:8090/actuator/prometheus | findstr dslpipeline
```

## Conformance & regression test packs (QA deploy-gate)

Two test packs harden the engine for QA:

**Money conformance suite** (`MoneyConformanceTest`) — the mandatory deploy-gate.
A `Money` value type (BigDecimal amount + ISO-4217 currency), `CurrencyProfile`
(per-currency scale, **HALF_EVEN** rounding) and `MoneyMath` (currency-safe
add/subtract/multiply/percentage/sum) are exercised across 10 sections: JSON↔
BigDecimal fidelity (no double leakage), scale invariants, negative-floor
subtraction, the classic rounding traps (`1.005`→`1.00`, `2.675`→`2.68`),
aggregation, null defaults, **mixed-currency rejection**, ordering/idempotency,
large-number stress, and JSON round-trip. Every test asserts all four money
invariants: numeric value, scale, rounding mode, currency safety. *If any test
here fails, do not deploy.*

**Claims collection regression pack** (`ClaimsCollectionTest`) — exercises the
collection rule patterns from the QA matrix that the engine evaluates
deterministically: existence (`ANY`), absence (`NONE` = `NOT EXISTS`), universal
(`ALL`), and counting (`COUNT WHERE`). Each rule (`ZR_COLL_1_ANY`,
`ZR_COLL_2_NONE`, `ZR_COLL_3_EXISTS`, `R6`, `R7`, `R8`, …) is validated,
compiled to IR and run against canonical trigger / no-trigger claim payloads,
including the empty-collection edge cases (`ANY`→false, `ALL`→vacuously true).

## How this compares to the reference Git repo

The ZenLogIQ repo is a large production system (32 controllers, 50+ tables,
multi-tenant). This standalone build is **not** a replacement — it is a focused,
hardened reference for the *translation pipeline itself*, built strictly to the
documentation:

- It makes the **AST a real, separate stage** (the docs flag this as essential —
  "AST is the semantic bridge"); the corrected pipeline `NL→SL→DSL→AST→IR` is
  implemented exactly.
- It adds the **AST optimisation pass** (constant folding, dead-branch
  elimination) the docs call out as the path to a high-performance runtime.
- It implements the **deterministic decimal numeric profile** end to end.
- It implements **mechanical test-scenario generation from IR** and
  **golden-trace parity**, the doc's "Validate Once, Execute Anywhere" tooling.
- It keeps the LLM strictly in the *translate-only* role with structured output
  (assumptions / ambiguities / confidence) — "LLMs propose, the platform decides."

It is intentionally single-rule and single-tenant so the pipeline mechanics are
inspectable and exhaustively testable by QA.

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `release version 25 not supported` / `invalid target release: 25` | The compiling JDK is older than 25 — install JDK 25, point `JAVA_HOME` at it, reopen the shell |
| Frontend network errors | Confirm backend on `:8090` (`curl .../health`) |
| Claude option disabled | `ANTHROPIC_API_KEY` not set — `export`, restart backend |
| `Port 8090 in use` | Change `server.port` in `application.yml` + Vite proxy |
| A rule "BLOCKED_AT_SL_LINT" | The deterministic parser couldn't reduce the NL — rephrase as `if <condition> then <action>`, or use the Claude strategy |

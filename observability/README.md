# Observability

The DSL → IR pipeline exposes Micrometer counters and timers under the
`dslpipeline.*` namespace via Spring Boot Actuator at `/actuator/prometheus`.
The two artefacts here wire that into a standard Prometheus + Grafana stack.

## Files

| File | Purpose |
|---|---|
| `prometheus-scrape.yml` | Drop-in `scrape_configs` block for Prometheus. Adjust `targets:` and the `env:` label for your deployment. |
| `grafana-dashboard.json` | Grafana dashboard: pipeline runs by status, stage-block breakdown, IR execution pass rate, LLM calls by intent, self-heal retry success, pipeline duration p95. Import via Grafana → Dashboards → Import → upload JSON, then pick your Prometheus datasource. |

## Metric catalogue

`com.dslpipeline.observability.PipelineMetrics` is the source of truth. Counters
are exported with a `_total` suffix and tags become Prometheus labels.

| Micrometer name | Type | Tags | Meaning |
|---|---|---|---|
| `dslpipeline.pipeline.runs` | counter | `status` | Every pipeline run, by terminal status (`OK`, `BLOCKED_AT_*`, `ERROR`) |
| `dslpipeline.pipeline.stage_blocked` | counter | `stage` | A run blocked at a gate (`SL_LINT`, `DSL_VALIDATION`, `TYPE_CHECK`) |
| `dslpipeline.pipeline.duration` | timer | — | End-to-end run duration; percentiles 0.5 / 0.95 |
| `dslpipeline.dsl.validation` | counter | `result` | DSL validation outcome (`ok` / `failed`) |
| `dslpipeline.ir.executions` | counter | `passed` | IR runtime executions, by pass/fail |
| `dslpipeline.rules.saved` | counter | — | Rules persisted to the 3-table model |
| `dslpipeline.llm.calls` | counter | `intent` | LLM calls, by intent (`normalize_nl`, `translate_dsl`, …) |
| `dslpipeline.llm.retry_attempts` | counter | `attempt` | DSL self-healing retry attempts, by attempt number |
| `dslpipeline.llm.retry_success` / `_failure` | counter | — | Self-heal loop final outcome |

Spring Boot also exposes the standard JVM / HTTP / Hibernate / Caffeine meters
under the same endpoint.

## Wiring

1. Backend running — confirm the endpoint:
   ```
   curl http://localhost:8090/actuator/prometheus | findstr dslpipeline
   ```
2. Apply `prometheus-scrape.yml` to your Prometheus config and reload.
3. Import `grafana-dashboard.json` into Grafana, picking your Prometheus datasource.

## Useful PromQL idioms

```promql
# pipeline run rate by status
sum by (status) (rate(dslpipeline_pipeline_runs_total[5m]))

# IR execution pass ratio
sum(rate(dslpipeline_ir_executions_total{passed="true"}[5m]))
  / sum(rate(dslpipeline_ir_executions_total[5m]))

# pipeline p95 latency
dslpipeline_pipeline_duration_seconds{quantile="0.95"}
```

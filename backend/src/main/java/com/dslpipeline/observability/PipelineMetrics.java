package com.dslpipeline.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Micrometer instrumentation for the pipeline.
 *
 * All counters live under the {@code dslpipeline.*} namespace and are exposed
 * via Spring Boot Actuator at {@code /actuator/prometheus}. This is the source
 * of truth for the metric catalogue — keep {@code observability/README.md} in sync.
 *
 *   dslpipeline.pipeline.runs            tag: status
 *   dslpipeline.pipeline.stage_blocked   tag: stage
 *   dslpipeline.pipeline.duration        timer (percentiles 0.5 / 0.95)
 *   dslpipeline.dsl.validation           tag: result
 *   dslpipeline.ir.executions            tag: passed
 *   dslpipeline.rules.saved              counter
 *   dslpipeline.llm.calls                tag: intent
 *   dslpipeline.llm.retry_attempts       tag: attempt
 *   dslpipeline.llm.retry_success / _failure  counters
 *
 * @author Nikunj Malik
 */
@Component
public class PipelineMetrics {

    private final MeterRegistry registry;
    private final Timer pipelineTimer;

    public PipelineMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.pipelineTimer = Timer.builder("dslpipeline.pipeline.duration")
                .description("End-to-end pipeline run duration")
                .publishPercentiles(0.5, 0.95)
                .register(registry);
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void stopTimer(Timer.Sample sample) {
        if (sample != null) sample.stop(pipelineTimer);
    }

    public void recordPipelineRun(String status) {
        registry.counter("dslpipeline.pipeline.runs", "status", safe(status)).increment();
    }

    public void recordStageBlocked(String stage) {
        registry.counter("dslpipeline.pipeline.stage_blocked", "stage", safe(stage)).increment();
    }

    public void recordDslValidation(boolean ok) {
        registry.counter("dslpipeline.dsl.validation", "result", ok ? "ok" : "failed").increment();
    }

    public void recordExecution(boolean passed) {
        registry.counter("dslpipeline.ir.executions", "passed", String.valueOf(passed)).increment();
    }

    public void recordRuleSaved() {
        registry.counter("dslpipeline.rules.saved").increment();
    }

    public void recordLlmCall(String intent) {
        registry.counter("dslpipeline.llm.calls", "intent", safe(intent)).increment();
    }

    public void recordRetryAttempt(int attempt) {
        registry.counter("dslpipeline.llm.retry_attempts", "attempt", String.valueOf(attempt))
                .increment();
    }

    public void recordRetryOutcome(boolean healed) {
        registry.counter(healed
                ? "dslpipeline.llm.retry_success"
                : "dslpipeline.llm.retry_failure").increment();
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "unknown" : s;
    }
}

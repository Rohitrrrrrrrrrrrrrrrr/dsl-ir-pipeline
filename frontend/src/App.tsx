/** @author Nikunj Malik */
import { useEffect, useState } from 'react';
import { ExtensionFn, PipelineResponse, health, listExtensions, runEndToEnd, runFromDsl } from './services/api';
import { StageCard } from './components/StageCard';
import { ExecutionPanel } from './components/ExecutionPanel';
import { ExtensionPanel } from './components/ExtensionPanel';
import { ScenarioPanel } from './components/ScenarioPanel';

const NL_EXAMPLES = [
  'customer age < 18 decline the loan',
  'Customers under 18 are not eligible unless special approval is granted',
  'if claim.amount > 10000 then flag for review',
  'Applicant income must be greater than 50000. Applicant age must be less than 65.'
];

const DEFAULT_SCHEMA = {
  'customer.age': 'number',
  'customer.income': 'number',
  'customer.region': 'string',
  'claim.amount': 'decimal',
  'override.approved': 'boolean'
};

const DEFAULT_PAYLOAD = {
  customer: { age: 16, income: 60000, region: 'AU' },
  claim: { amount: 5000 },
  override: { approved: false }
};

// A DSL that uses an extension function — for the "from DSL" mode.
const DSL_EXAMPLE = {
  ruleId: 'AGE_AT_LOAN_START',
  priority: 100,
  conditions: [
    { type: 'leaf', left: 'calculateAge(applicant.dateOfBirth, loan.startDate)', op: '<', right: 18 }
  ],
  actions: [
    { type: 'addError', code: 'LOAN_DECLINED', message: 'Applicant is under 18 at loan start date' }
  ],
  haltOnViolation: true,
  roundingMode: 'HALF_UP'
};

const DSL_PAYLOAD = {
  applicant: { dateOfBirth: '2010-06-01' },
  loan: { startDate: '2026-05-20' }
};

export default function App() {
  const [mode, setMode] = useState<'nl' | 'dsl'>('nl');
  const [nl, setNl] = useState(NL_EXAMPLES[0]);
  const [strategy, setStrategy] = useState<'rule' | 'claude'>('rule');
  const [schemaText, setSchemaText] = useState(JSON.stringify(DEFAULT_SCHEMA, null, 2));
  const [payloadText, setPayloadText] = useState(JSON.stringify(DEFAULT_PAYLOAD, null, 2));
  const [dslText, setDslText] = useState(JSON.stringify(DSL_EXAMPLE, null, 2));
  const [genScenarios, setGenScenarios] = useState(true);
  const [resp, setResp] = useState<PipelineResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [claudeAvail, setClaudeAvail] = useState(false);
  const [extensions, setExtensions] = useState<ExtensionFn[]>([]);

  useEffect(() => {
    health().then(h => setClaudeAvail(h.claudeAvailable)).catch(() => {});
    listExtensions().then(setExtensions).catch(() => {});
  }, []);

  const run = async () => {
    setLoading(true);
    setError(null);
    setResp(null);
    try {
      let schema, payload;
      try { schema = JSON.parse(schemaText); } catch { throw new Error('Schema is not valid JSON.'); }
      try { payload = JSON.parse(payloadText); } catch { throw new Error('Payload is not valid JSON.'); }
      let r: PipelineResponse;
      if (mode === 'nl') {
        r = await runEndToEnd({ nl, strategy, schema, payload, persist: true, generateScenarios: genScenarios });
      } else {
        let dsl;
        try { dsl = JSON.parse(dslText); } catch { throw new Error('DSL is not valid JSON.'); }
        r = await runFromDsl({ dsl, schema, payload, persist: true, generateScenarios: genScenarios });
      }
      setResp(r);
    } catch (e: any) {
      setError(e?.response?.data?.message || e?.message || 'Pipeline request failed.');
    } finally {
      setLoading(false);
    }
  };

  const sl = resp?.stage1_sl;
  const opt = resp?.stage7_optimization;

  return (
    <div className="app">
      <header>
        <h1>NL → SL → DSL → AST → IR → Runtime</h1>
        <div className="health">
          Backend <strong>:8090</strong> · Claude <strong>{claudeAvail ? 'on' : 'off'}</strong>
          {' '}· Extensions <strong>{extensions.length}</strong>
        </div>
      </header>

      <div className="mode-tabs">
        <button className={mode === 'nl' ? 'tab active' : 'tab'} onClick={() => setMode('nl')}>
          NL → IR pipeline
        </button>
        <button className={mode === 'dsl' ? 'tab active' : 'tab'} onClick={() => setMode('dsl')}>
          DSL → IR (direct)
        </button>
      </div>

      {mode === 'nl' ? (
        <>
          <div className="examples">
            {NL_EXAMPLES.map(ex => (
              <button key={ex} className="example-chip" onClick={() => setNl(ex)}>{ex}</button>
            ))}
          </div>
          <div className="input-bar">
            <textarea value={nl} onChange={e => setNl(e.target.value)}
              placeholder="Enter a business rule in natural language..." />
            <div className="controls">
              <label>NL Parser
                <select value={strategy} onChange={e => setStrategy(e.target.value as any)}>
                  <option value="rule">Rule-based (deterministic)</option>
                  <option value="claude" disabled={!claudeAvail}>
                    Claude API {claudeAvail ? '' : '(set ANTHROPIC_API_KEY)'}
                  </option>
                </select>
              </label>
              <label className="chk">
                <input type="checkbox" checked={genScenarios}
                  onChange={e => setGenScenarios(e.target.checked)} /> Generate test scenarios
              </label>
              <button onClick={run} disabled={loading || !nl.trim()}>
                {loading ? 'Running…' : 'Run pipeline'}
              </button>
            </div>
          </div>
        </>
      ) : (
        <div className="input-bar">
          <div style={{ flex: 1, minWidth: 320 }}>
            <label className="ed-label">RuleDSL (JSON) — supports function expressions, quantifiers, decision tables</label>
            <textarea value={dslText} onChange={e => setDslText(e.target.value)}
              style={{ width: '100%', minHeight: 200 }} />
          </div>
          <div className="controls">
            <button className="example-chip" onClick={() => { setDslText(JSON.stringify(DSL_EXAMPLE, null, 2)); setPayloadText(JSON.stringify(DSL_PAYLOAD, null, 2)); }}>
              Load calculateAge example
            </button>
            <label className="chk">
              <input type="checkbox" checked={genScenarios}
                onChange={e => setGenScenarios(e.target.checked)} /> Generate test scenarios
            </label>
            <button onClick={run} disabled={loading}>
              {loading ? 'Running…' : 'Run pipeline'}
            </button>
          </div>
        </div>
      )}

      <div className="input-bar">
        <div style={{ flex: 1, minWidth: 280 }}>
          <label className="ed-label">Domain Schema / DAL (path → type)</label>
          <textarea value={schemaText} onChange={e => setSchemaText(e.target.value)}
            style={{ width: '100%', minHeight: 110 }} />
        </div>
        <div style={{ flex: 1, minWidth: 280 }}>
          <label className="ed-label">Execution Payload</label>
          <textarea value={payloadText} onChange={e => setPayloadText(e.target.value)}
            style={{ width: '100%', minHeight: 110 }} />
        </div>
      </div>

      <ExtensionPanel functions={extensions} />

      {error && <div className="error-banner">{error}</div>}

      {resp && (
        <>
          <div className="status-line">
            <strong>Pipeline status:</strong>{' '}
            <span className={resp.status === 'OK' ? 'status-ok' : 'status-err'}>{resp.status}</span>
            {resp.stage9_runLogId != null && (
              <span style={{ marginLeft: 16, color: 'var(--muted)' }}>
                run-log #{resp.stage9_runLogId}
              </span>
            )}
            {resp.stage8b_savedRule != null && (
              <span style={{ marginLeft: 16, color: 'var(--ok)' }}>
                saved rule: {resp.stage8b_savedRule.ruleKey} v{resp.stage8b_savedRule.version}
              </span>
            )}
          </div>

          <div className="stages">
            {sl && (
              <div className="stage">
                <h3>Stage 1 — NL → Structured Logic
                  <span className="badge">conf {sl.confidence}</span>
                </h3>
                <pre>{resp.stage1_slRendered}</pre>
                {sl.ambiguities?.length > 0 && (
                  <div className="issues">
                    {sl.ambiguities.map((a: string, i: number) => (
                      <div key={i} className="issue-warn">! {a}</div>
                    ))}
                  </div>
                )}
                {sl.assumptions?.length > 0 && (
                  <div className="issues">
                    {sl.assumptions.map((a: string, i: number) => (
                      <div key={i} style={{ color: 'var(--muted)', fontSize: 12 }}>assumed: {a}</div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {resp.stage2_lint && (
              <StageCard title="Stage 2 — SL Lint (DAL alignment, ambiguity)" badge="lint"
                issues={resp.stage2_lint} />
            )}
            {resp.stage3_dsl && (
              <StageCard title="Stage 3 — SL → DSL" badge="DSL" data={resp.stage3_dsl} />
            )}
            {resp.stage4_dslValidation && (
              <StageCard title="Stage 4 — DSL Validation" badge="validate"
                issues={resp.stage4_dslValidation} />
            )}
            {resp.stage5_ast && (
              <StageCard title="Stage 5 — DSL → AST" badge="AST" data={resp.stage5_ast} />
            )}
            {resp.stage6_typeCheck && (
              <StageCard title="Stage 6 — Type Check + Schema Resolution" badge="types"
                issues={resp.stage6_typeCheck} />
            )}
            {opt && (
              <div className="stage">
                <h3>Stage 7 — AST Optimisation
                  <span className="badge">{opt.changed ? 'optimised' : 'no-op'}</span>
                </h3>
                {opt.transformations?.length > 0 ? (
                  <div className="issues">
                    {opt.transformations.map((t: string, i: number) => (
                      <div key={i} style={{ color: 'var(--accent)', fontSize: 12 }}>↳ {t}</div>
                    ))}
                  </div>
                ) : (
                  <div style={{ fontSize: 12, color: 'var(--muted)' }}>
                    Nothing to fold — no constant subexpressions or dead branches.
                  </div>
                )}
              </div>
            )}
            {resp.stage8_ir && (
              <StageCard title="Stage 8 — Canonical IR (execution contract)" badge="IR"
                data={resp.stage8_ir} />
            )}
            <ExecutionPanel data={resp.stage10_execution} />
          </div>

          <ScenarioPanel scenarios={resp.stage11_scenarios} />
        </>
      )}

      <footer>
        Compiler-grade rule pipeline · NL → SL → DSL → AST → IR → Runtime ·
        Spring Boot 3 · React 18 · H2 · deterministic decimal semantics
      </footer>
    </div>
  );
}

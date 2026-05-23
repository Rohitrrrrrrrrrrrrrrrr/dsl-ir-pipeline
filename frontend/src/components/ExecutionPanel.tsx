/** @author Nikunj Malik */
import React from 'react';
import { ConditionTree, EvalNode } from './ConditionTree';

interface Outcome { code: string; message: string; }

interface ExecutionResult {
  ruleId: string;
  passed: boolean;
  conditionsMet: boolean;
  branchTaken: string;
  errors: Outcome[];
  warnings: Outcome[];
  firedActions: string[];
  trace: string[];
  conditionTrace?: EvalNode;
  explanation?: string;
  outputPayload: any;
}

export const ExecutionPanel: React.FC<{ data?: ExecutionResult }> = ({ data }) => {
  if (!data) {
    return (
      <div className="stage" style={{ gridColumn: '1 / -1' }}>
        <h3>Stage 10 — IR Execution<span className="badge">runtime</span></h3>
        <div style={{ fontSize: 12, color: 'var(--muted)' }}>Not executed.</div>
      </div>
    );
  }
  return (
    <div className="stage" style={{ gridColumn: '1 / -1' }}>
      <h3>Stage 10 — IR Execution<span className="badge">runtime</span></h3>

      <div className="exec-summary">
        <span className={data.passed ? 'pill pill-pass' : 'pill pill-fail'}>
          {data.passed ? '✓ PASSED' : '✗ FAILED'}
        </span>
        <span className="pill">Conditions met: <strong>{String(data.conditionsMet)}</strong></span>
        <span className="pill">Branch: <strong>{data.branchTaken}</strong></span>
        <span className="pill">Errors: <strong>{data.errors.length}</strong></span>
        <span className="pill">Warnings: <strong>{data.warnings.length}</strong></span>
      </div>

      {data.explanation && (
        <div className="explanation">
          <strong>Why / Why-Not:</strong> {data.explanation}
        </div>
      )}

      {data.errors.length > 0 && (
        <div className="issues">
          {data.errors.map((e, i) => (
            <div key={i} className="issue-err">✗ [{e.code}] {e.message}</div>
          ))}
        </div>
      )}
      {data.warnings.length > 0 && (
        <div className="issues">
          {data.warnings.map((w, i) => (
            <div key={i} className="issue-warn">⚠ [{w.code}] {w.message}</div>
          ))}
        </div>
      )}

      <div className="exec-grid">
        <div>
          <div className="sub-label">Condition Evaluation (Why / Why-Not)</div>
          <ConditionTree root={data.conditionTrace} />
        </div>
        <div>
          <div className="sub-label">Execution Trace</div>
          <pre>{data.trace.join('\n') || '(no actions fired)'}</pre>
        </div>
      </div>

      <div className="exec-grid">
        <div>
          <div className="sub-label">Fired Actions</div>
          <pre>{data.firedActions.join('\n') || '(none)'}</pre>
        </div>
        <div>
          <div className="sub-label">Output Payload</div>
          <pre>{JSON.stringify(data.outputPayload, null, 2)}</pre>
        </div>
      </div>
    </div>
  );
};

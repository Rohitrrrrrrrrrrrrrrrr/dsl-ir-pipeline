/** @author Nikunj Malik */
import React from 'react';

interface Scenario {
  name: string;
  description: string;
  category: string;
  payload: any;
  expectedConditionsMet: boolean;
  expectedPassed: boolean;
  expectedBranch: string;
  expectedErrorCodes: string[];
}

export const ScenarioPanel: React.FC<{ scenarios?: Scenario[] }> = ({ scenarios }) => {
  if (!scenarios || scenarios.length === 0) return null;
  return (
    <div className="stage" style={{ gridColumn: '1 / -1' }}>
      <h3>Stage 11 — Mechanically Generated Test Scenarios
        <span className="badge">{scenarios.length} cases</span>
      </h3>
      <table className="scenario-table">
        <thead>
          <tr>
            <th>#</th><th>Scenario</th><th>Category</th><th>Payload</th>
            <th>Cond. met</th><th>Branch</th><th>Passed</th><th>Errors</th>
          </tr>
        </thead>
        <tbody>
          {scenarios.map((s, i) => (
            <tr key={i}>
              <td>{i + 1}</td>
              <td>{s.name}<div className="scn-desc">{s.description}</div></td>
              <td><span className={'cat cat-' + s.category}>{s.category}</span></td>
              <td><code>{JSON.stringify(s.payload)}</code></td>
              <td>{String(s.expectedConditionsMet)}</td>
              <td>{s.expectedBranch}</td>
              <td className={s.expectedPassed ? 'status-ok' : 'status-err'}>
                {String(s.expectedPassed)}
              </td>
              <td>{s.expectedErrorCodes.join(', ') || '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

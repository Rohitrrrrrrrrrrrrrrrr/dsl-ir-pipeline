/** @author Nikunj Malik */
import React from 'react';

interface Issue { code: string; message: string; }
interface IssuesPayload { errors?: Issue[] | string[]; warnings?: Issue[] | string[]; ok?: boolean; }

interface Props {
  title: string;
  badge: string;
  data?: any;
  text?: string;
  issues?: IssuesPayload;
}

export const StageCard: React.FC<Props> = ({ title, badge, data, text, issues }) => {
  const errorsArr = issues?.errors ?? [];
  const warningsArr = issues?.warnings ?? [];
  const hasErrors = errorsArr.length > 0;
  const hasWarnings = warningsArr.length > 0;

  return (
    <div className="stage">
      <h3>
        {title}
        <span className="badge">{badge}</span>
        {issues && (
          <span
            className={hasErrors ? 'status-err' : hasWarnings ? 'status-warn' : 'status-ok'}
            style={{ marginLeft: 'auto', fontSize: 11 }}
          >
            {hasErrors ? `${errorsArr.length} error(s)` :
             hasWarnings ? `${warningsArr.length} warning(s)` : 'clean'}
          </span>
        )}
      </h3>
      {text && <pre>{text}</pre>}
      {data && <pre>{JSON.stringify(data, null, 2)}</pre>}
      {issues && (
        <div className="issues">
          {errorsArr.map((e, i) => (
            <div key={'e' + i} className="issue-err">
              ✖ {typeof e === 'string' ? e : `[${e.code}] ${e.message}`}
            </div>
          ))}
          {warningsArr.map((w, i) => (
            <div key={'w' + i} className="issue-warn">
              ⚠ {typeof w === 'string' ? w : `[${w.code}] ${w.message}`}
            </div>
          ))}
          {!hasErrors && !hasWarnings && (
            <div className="status-ok" style={{ fontSize: 12 }}>✓ No issues</div>
          )}
        </div>
      )}
    </div>
  );
};

/** @author Nikunj Malik */
import React from 'react';

export interface EvalNode {
  kind: string;
  label: string;
  result?: boolean;
  detail?: string;
  children: EvalNode[];
}

const NodeView: React.FC<{ node: EvalNode; depth: number }> = ({ node, depth }) => {
  const color = node.result === true ? 'var(--ok)' : node.result === false ? 'var(--err)' : 'var(--muted)';
  const mark = node.result === true ? '✓' : node.result === false ? '✗' : '·';
  return (
    <div style={{ marginLeft: depth * 16 }}>
      <div style={{ fontSize: 12, fontFamily: 'monospace' }}>
        <span style={{ color, fontWeight: 700 }}>{mark}</span>{' '}
        <span style={{ color }}>{node.label || node.kind}</span>
        {node.detail && <span style={{ color: 'var(--muted)' }}> — {node.detail}</span>}
      </div>
      {node.children?.map((c, i) => (
        <NodeView key={i} node={c} depth={depth + 1} />
      ))}
    </div>
  );
};

export const ConditionTree: React.FC<{ root?: EvalNode }> = ({ root }) => {
  if (!root) return <div style={{ fontSize: 12, color: 'var(--muted)' }}>No condition trace.</div>;
  return (
    <div style={{ background: '#0b1220', border: '1px solid var(--border)', borderRadius: 6, padding: 10 }}>
      <NodeView node={root} depth={0} />
    </div>
  );
};

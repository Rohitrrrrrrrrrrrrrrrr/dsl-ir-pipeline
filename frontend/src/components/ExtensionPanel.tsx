/** @author Nikunj Malik */
import React, { useState } from 'react';
import { ExtensionFn } from '../services/api';

export const ExtensionPanel: React.FC<{ functions: ExtensionFn[] }> = ({ functions }) => {
  const [open, setOpen] = useState(false);
  const packs = Array.from(new Set(functions.map(f => f.pack)));

  return (
    <div className="stage">
      <h3 onClick={() => setOpen(!open)} style={{ cursor: 'pointer' }}>
        Extension Registry<span className="badge">{functions.length} functions</span>
        <span style={{ marginLeft: 'auto', fontSize: 12 }}>{open ? '▾' : '▸'}</span>
      </h3>
      {open && (
        <div style={{ maxHeight: 320, overflow: 'auto' }}>
          {packs.map(pack => (
            <div key={pack} style={{ marginBottom: 8 }}>
              <div style={{ fontSize: 12, color: 'var(--accent)', marginBottom: 4 }}>
                {pack} pack
              </div>
              {functions.filter(f => f.pack === pack).map(f => (
                <div key={f.name} style={{ fontSize: 11, fontFamily: 'monospace', marginBottom: 2 }}>
                  <span style={{ color: 'var(--ok)' }}>{f.signature}</span>
                  <div style={{ color: 'var(--muted)', marginLeft: 12 }}>{f.description}</div>
                </div>
              ))}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

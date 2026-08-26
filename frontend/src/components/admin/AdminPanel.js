import { useState } from 'react';
import AdminUsers from './AdminUsers';
import AdminRuleConfigs from './AdminRuleConfigs';
import AdminAuditLogs from './AdminAuditLogs';

const SECTIONS = [
  { key: 'users', label: 'Users', Component: AdminUsers },
  { key: 'rule-configs', label: 'Rule configs', Component: AdminRuleConfigs },
  { key: 'audit-logs', label: 'Audit logs', Component: AdminAuditLogs },
];

export default function AdminPanel() {
  const [section, setSection] = useState('users');
  const Active = SECTIONS.find((s) => s.key === section).Component;

  return (
    <div className="Card">
      <div className="CardTitle">Admin</div>
      <div className="Tabs" role="tablist" aria-label="Admin sections">
        {SECTIONS.map((s) => (
          <button
            key={s.key}
            type="button"
            role="tab"
            aria-selected={section === s.key}
            className={`Tab ${section === s.key ? 'Tab--active' : ''}`}
            onClick={() => setSection(s.key)}
          >
            {s.label}
          </button>
        ))}
      </div>
      <div style={{ marginTop: 14 }}>
        <Active />
      </div>
    </div>
  );
}

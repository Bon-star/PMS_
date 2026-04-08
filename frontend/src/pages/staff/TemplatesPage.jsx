import { useState, useEffect } from 'react';
import { staffAPI } from '../../api';
import { useToast } from '../../components/Toast';
import { HiTemplate, HiPlus, HiRefresh } from 'react-icons/hi';
import '../../layouts/DashboardLayout.css';

export default function TemplatesPage() {
  const toast = useToast();
  const [templates, setTemplates] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => { load(); }, []);

  const load = async () => {
    setLoading(true);
    try {
      const res = await staffAPI.getTemplates({});
      setTemplates(res.data?.templates || res.data || []);
    } catch { setTemplates([]); }
    finally { setLoading(false); }
  };

  if (loading) return <div className="page-loader"><div className="spinner spinner-lg"></div></div>;

  return (
    <div>
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', flexWrap: 'wrap', gap: 16 }}>
        <div><h1 className="page-title">Project Templates</h1><p className="page-subtitle">Manage reusable project templates</p></div>
        <button className="btn btn-ghost btn-sm" onClick={load}><HiRefresh /></button>
      </div>

      {templates.length === 0 ? (
        <div className="card"><div className="card-body"><div className="empty-state"><div className="empty-state-icon">📋</div><div className="empty-state-title">No Templates</div><p style={{ color: 'var(--text-secondary)', fontSize: 'var(--font-size-sm)' }}>Project templates will appear here once created.</p></div></div></div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: 16 }}>
          {templates.map(t => (
            <div key={t.templateId} className="card">
              <div className="card-body">
                <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
                  <div className="stat-icon" style={{ background: 'var(--warning-light)', color: 'var(--warning)', width: 40, height: 40 }}><HiTemplate /></div>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontWeight: 600, marginBottom: 4 }}>{t.projectName || 'Untitled'}</div>
                    <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--text-secondary)', lineHeight: 1.5 }}>{(t.description || 'No description').substring(0, 100)}</div>
                    {t.lecturerName && <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--text-muted)', marginTop: 6 }}>Lecturer: {t.lecturerName}</div>}
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

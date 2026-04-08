import { useState, useEffect } from 'react';
import { staffAPI } from '../../api';
import { useToast } from '../../components/Toast';
import { HiMail, HiCheckCircle, HiXCircle, HiRefresh } from 'react-icons/hi';
import '../../layouts/DashboardLayout.css';

export default function RequestsPage() {
  const toast = useToast();
  const [requests, setRequests] = useState({ editRequests: [], changeRequests: [] });
  const [loading, setLoading] = useState(true);

  useEffect(() => { load(); }, []);

  const load = async () => {
    setLoading(true);
    try {
      const res = await staffAPI.getRequests('');
      setRequests(res.data || { editRequests: [], changeRequests: [] });
    } catch { setRequests({ editRequests: [], changeRequests: [] }); }
    finally { setLoading(false); }
  };

  const editReqs = requests.editRequests || [];
  const changeReqs = requests.changeRequests || [];
  const allEmpty = editReqs.length === 0 && changeReqs.length === 0;

  if (loading) return <div className="page-loader"><div className="spinner spinner-lg"></div></div>;

  return (
    <div>
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', flexWrap: 'wrap', gap: 16 }}>
        <div><h1 className="page-title">Requests</h1><p className="page-subtitle">Review edit and change requests</p></div>
        <button className="btn btn-ghost btn-sm" onClick={load}><HiRefresh /></button>
      </div>

      {allEmpty ? (
        <div className="card"><div className="card-body"><div className="empty-state"><div className="empty-state-icon">📬</div><div className="empty-state-title">No Pending Requests</div></div></div></div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
          {editReqs.length > 0 && (
            <div className="card">
              <div className="card-header"><h3 className="section-title" style={{ margin: 0 }}>Edit Requests</h3><span className="badge badge-warning">{editReqs.length}</span></div>
              <div className="card-body" style={{ padding: 0 }}>
                {editReqs.map(r => (
                  <div key={r.requestId} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '14px 20px', borderBottom: '1px solid var(--border-light)' }}>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontWeight: 500, fontSize: 'var(--font-size-sm)' }}>{r.projectName || 'Project'}</div>
                      <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--text-muted)' }}>{r.studentName} · {r.reason || 'No reason'}</div>
                    </div>
                    <span className={`badge ${r.status === 'Approved' ? 'badge-success' : r.status === 'Rejected' ? 'badge-danger' : 'badge-warning'}`}>{r.status || 'Pending'}</span>
                  </div>
                ))}
              </div>
            </div>
          )}
          {changeReqs.length > 0 && (
            <div className="card">
              <div className="card-header"><h3 className="section-title" style={{ margin: 0 }}>Change Requests</h3><span className="badge badge-primary">{changeReqs.length}</span></div>
              <div className="card-body" style={{ padding: 0 }}>
                {changeReqs.map(r => (
                  <div key={r.requestId} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '14px 20px', borderBottom: '1px solid var(--border-light)' }}>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontWeight: 500, fontSize: 'var(--font-size-sm)' }}>{r.proposedProjectName || 'New Topic'}</div>
                      <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--text-muted)' }}>{r.groupName} · {r.reason || 'No reason'}</div>
                    </div>
                    <span className={`badge ${r.status === 'Approved' ? 'badge-success' : r.status === 'Rejected' ? 'badge-danger' : 'badge-warning'}`}>{r.status || 'Pending'}</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

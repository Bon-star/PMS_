import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { lecturerAPI } from '../../api';
import { useToast } from '../../components/Toast';
import { HiFolder, HiCheckCircle, HiXCircle, HiEye, HiRefresh, HiCalendar } from 'react-icons/hi';
import '../../layouts/DashboardLayout.css';

export default function LecturerProjectsPage() {
  const toast = useToast();
  const [data, setData] = useState({ pendingProjects: [], pendingChangeRequests: [], trackedProjects: [] });
  const [loading, setLoading] = useState(true);
  const [approveModal, setApproveModal] = useState(null);
  const [rejectModal, setRejectModal] = useState(null);
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [rejectReason, setRejectReason] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => { load(); }, []);

  const load = async () => {
    setLoading(true);
    try {
      const res = await lecturerAPI.getProjects();
      setData(res.data || { pendingProjects: [], pendingChangeRequests: [], trackedProjects: [] });
    } catch { setData({ pendingProjects: [], pendingChangeRequests: [], trackedProjects: [] }); }
    finally { setLoading(false); }
  };

  const handleApprove = async () => {
    if (!startDate || !endDate) { toast.error('Please select start & end dates.'); return; }
    setSaving(true);
    try {
      const res = await lecturerAPI.approveProject(approveModal.projectId, startDate, endDate);
      if (res.data.success !== false) { toast.success('Project approved!'); setApproveModal(null); load(); }
      else toast.error(res.data.message);
    } catch (err) { toast.error(err.response?.data?.message || 'Failed.'); }
    finally { setSaving(false); }
  };

  const handleReject = async () => {
    if (!rejectReason.trim()) { toast.error('Please provide a reason.'); return; }
    setSaving(true);
    try {
      await lecturerAPI.rejectProject(rejectModal.projectId, rejectReason.trim());
      toast.success('Project rejected.');
      setRejectModal(null);
      load();
    } catch (err) { toast.error(err.response?.data?.message || 'Failed.'); }
    finally { setSaving(false); }
  };

  if (loading) return <div className="page-loader"><div className="spinner spinner-lg"></div></div>;

  const pending = data.pendingProjects || [];
  const tracked = data.trackedProjects || [];
  const changeReqs = data.pendingChangeRequests || [];

  return (
    <div>
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', flexWrap: 'wrap', gap: 16 }}>
        <div><h1 className="page-title">Projects</h1><p className="page-subtitle">Review and track student projects</p></div>
        <button className="btn btn-ghost btn-sm" onClick={load}><HiRefresh /> Refresh</button>
      </div>

      {/* Pending Projects */}
      {pending.length > 0 && (
        <div className="card" style={{ marginBottom: 24 }}>
          <div className="card-header"><h3 className="section-title" style={{ margin: 0 }}>Pending Approval</h3><span className="badge badge-warning">{pending.length}</span></div>
          <div className="card-body" style={{ padding: 0 }}>
            {pending.map(p => (
              <div key={p.projectId} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '16px 20px', borderBottom: '1px solid var(--border-light)' }}>
                <div style={{ flex: 1 }}>
                  <div style={{ fontWeight: 600 }}>{p.projectName}</div>
                  <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--text-muted)', marginTop: 2 }}>{p.groupName} · {p.description?.substring(0, 80)}</div>
                </div>
                <div style={{ display: 'flex', gap: 8 }}>
                  <button className="btn btn-success btn-sm" onClick={() => { setApproveModal(p); setStartDate(''); setEndDate(''); }}><HiCheckCircle /> Approve</button>
                  <button className="btn btn-danger btn-sm" onClick={() => { setRejectModal(p); setRejectReason(''); }}><HiXCircle /> Reject</button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Change Requests */}
      {changeReqs.length > 0 && (
        <div className="card" style={{ marginBottom: 24 }}>
          <div className="card-header"><h3 className="section-title" style={{ margin: 0 }}>Change Requests</h3><span className="badge badge-primary">{changeReqs.length}</span></div>
          <div className="card-body" style={{ padding: 0 }}>
            {changeReqs.map(r => (
              <div key={r.requestId} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '14px 20px', borderBottom: '1px solid var(--border-light)' }}>
                <div style={{ flex: 1 }}>
                  <div style={{ fontWeight: 500, fontSize: 'var(--font-size-sm)' }}>{r.proposedProjectName || 'New Topic'}</div>
                  <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--text-muted)' }}>{r.groupName}</div>
                </div>
                <span className="badge badge-warning">Pending</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Tracked Projects */}
      <div className="card">
        <div className="card-header"><h3 className="section-title" style={{ margin: 0 }}>Tracked Projects</h3><span className="badge badge-neutral">{tracked.length}</span></div>
        <div className="card-body" style={{ padding: 0 }}>
          {tracked.length === 0 ? (
            <div style={{ padding: 40, textAlign: 'center', color: 'var(--text-muted)' }}>No approved projects to track yet.</div>
          ) : tracked.map(p => (
            <div key={p.projectId} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '14px 20px', borderBottom: '1px solid var(--border-light)' }}>
              <div className="stat-icon" style={{ background: 'var(--success-light)', color: 'var(--success)', width: 36, height: 36 }}><HiFolder /></div>
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: 500, fontSize: 'var(--font-size-sm)' }}>{p.projectName}</div>
                <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--text-muted)' }}>{p.groupName}</div>
              </div>
              <span className="badge badge-success">Approved</span>
            </div>
          ))}
        </div>
      </div>

      {/* Approve Modal */}
      {approveModal && (
        <div className="modal-backdrop" onClick={() => setApproveModal(null)}>
          <div className="modal-content" onClick={e => e.stopPropagation()}>
            <div className="modal-header"><h3 style={{ fontWeight: 600 }}>Approve Project</h3><button className="btn btn-ghost btn-icon" onClick={() => setApproveModal(null)}>✕</button></div>
            <div className="modal-body">
              <div className="auth-info-box">Approving: <strong>{approveModal.projectName}</strong></div>
              <div className="form-group"><label className="form-label">Start Date</label><input type="date" className="form-input" value={startDate} onChange={e => setStartDate(e.target.value)} /></div>
              <div className="form-group"><label className="form-label">End Date</label><input type="date" className="form-input" value={endDate} onChange={e => setEndDate(e.target.value)} /></div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-secondary btn-sm" onClick={() => setApproveModal(null)}>Cancel</button>
              <button className="btn btn-success btn-sm" onClick={handleApprove} disabled={saving}>{saving ? 'Approving...' : 'Approve'}</button>
            </div>
          </div>
        </div>
      )}

      {/* Reject Modal */}
      {rejectModal && (
        <div className="modal-backdrop" onClick={() => setRejectModal(null)}>
          <div className="modal-content" onClick={e => e.stopPropagation()}>
            <div className="modal-header"><h3 style={{ fontWeight: 600 }}>Reject Project</h3><button className="btn btn-ghost btn-icon" onClick={() => setRejectModal(null)}>✕</button></div>
            <div className="modal-body">
              <div className="auth-info-box" style={{ background: 'var(--danger-light)', borderColor: '#FCA5A5', color: 'var(--danger)' }}>Rejecting: <strong>{rejectModal.projectName}</strong></div>
              <div className="form-group"><label className="form-label">Rejection Reason</label><textarea className="form-input" rows={3} value={rejectReason} onChange={e => setRejectReason(e.target.value)} placeholder="Explain why..." style={{ resize: 'vertical' }} /></div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-secondary btn-sm" onClick={() => setRejectModal(null)}>Cancel</button>
              <button className="btn btn-danger btn-sm" onClick={handleReject} disabled={saving}>{saving ? 'Rejecting...' : 'Reject'}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

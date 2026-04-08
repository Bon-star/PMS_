import { useState, useEffect } from 'react';
import { staffAPI } from '../../api';
import { useToast } from '../../components/Toast';
import { HiOfficeBuilding, HiPlus, HiTrash, HiPencil, HiRefresh } from 'react-icons/hi';
import '../../layouts/DashboardLayout.css';

export default function ClassroomsPage() {
  const toast = useToast();
  const [classes, setClasses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [className, setClassName] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => { load(); }, []);

  const load = async () => {
    setLoading(true);
    try {
      const res = await staffAPI.getClasses();
      setClasses(res.data?.classes || res.data || []);
    } catch { setClasses([]); }
    finally { setLoading(false); }
  };

  const handleCreate = async (e) => {
    e.preventDefault();
    if (!className.trim()) return;
    setSaving(true);
    try {
      const res = await staffAPI.createClass({ className: className.trim() });
      if (res.data.success) { toast.success('Class created!'); setShowCreate(false); setClassName(''); load(); }
      else toast.error(res.data.message);
    } catch (err) { toast.error(err.response?.data?.message || 'Failed.'); }
    finally { setSaving(false); }
  };

  const handleDelete = async (id) => {
    if (!confirm('Delete this class?')) return;
    try {
      await staffAPI.deleteClass(id);
      toast.success('Class deleted.');
      load();
    } catch (err) { toast.error(err.response?.data?.message || 'Failed.'); }
  };

  if (loading) return <div className="page-loader"><div className="spinner spinner-lg"></div></div>;

  return (
    <div>
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', flexWrap: 'wrap', gap: 16 }}>
        <div><h1 className="page-title">Classrooms</h1><p className="page-subtitle">Manage class sections</p></div>
        <div style={{ display: 'flex', gap: 10 }}>
          <button className="btn btn-ghost btn-sm" onClick={load}><HiRefresh /></button>
          <button className="btn btn-primary btn-sm" onClick={() => setShowCreate(true)} id="btn-add-class"><HiPlus /> Add Class</button>
        </div>
      </div>

      {showCreate && (
        <div className="modal-backdrop" onClick={() => setShowCreate(false)}>
          <div className="modal-content" onClick={e => e.stopPropagation()}>
            <div className="modal-header"><h3 style={{ fontWeight: 600 }}>Create Class</h3><button className="btn btn-ghost btn-icon" onClick={() => setShowCreate(false)}>✕</button></div>
            <form onSubmit={handleCreate}>
              <div className="modal-body"><div className="form-group"><label className="form-label">Class Name</label><input className="form-input" value={className} onChange={e => setClassName(e.target.value)} placeholder="e.g. SE1801" autoFocus required /></div></div>
              <div className="modal-footer"><button type="button" className="btn btn-secondary btn-sm" onClick={() => setShowCreate(false)}>Cancel</button><button type="submit" className="btn btn-primary btn-sm" disabled={saving}>{saving ? 'Creating...' : 'Create'}</button></div>
            </form>
          </div>
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: 16 }}>
        {classes.length === 0 ? (
          <div className="card" style={{ gridColumn: '1 / -1' }}><div className="card-body"><div className="empty-state"><div className="empty-state-icon">🏫</div><div className="empty-state-title">No Classes</div></div></div></div>
        ) : classes.map(c => (
          <div key={c.classId} className="card">
            <div className="card-body" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                <div className="stat-icon" style={{ background: 'var(--primary-100)', color: 'var(--primary-600)', width: 40, height: 40 }}><HiOfficeBuilding /></div>
                <div>
                  <div style={{ fontWeight: 600 }}>{c.className}</div>
                  <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--text-muted)' }}>{c.studentCount ?? 0} students</div>
                </div>
              </div>
              <button className="btn btn-ghost btn-icon" onClick={() => handleDelete(c.classId)} style={{ color: 'var(--danger)' }}><HiTrash /></button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

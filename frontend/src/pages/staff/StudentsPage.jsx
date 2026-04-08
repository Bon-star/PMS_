import { useState, useEffect } from 'react';
import { staffAPI } from '../../api';
import { useToast } from '../../components/Toast';
import { HiAcademicCap, HiPlus, HiSearch, HiUpload, HiPencil, HiRefresh } from 'react-icons/hi';
import '../../layouts/DashboardLayout.css';

export default function StudentsPage() {
  const toast = useToast();
  const [students, setStudents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState({ fullName: '', email: '', phone: '', studentCode: '', classId: '' });
  const [saving, setSaving] = useState(false);

  useEffect(() => { load(); }, []);

  const load = async () => {
    setLoading(true);
    try {
      const res = await staffAPI.getStudents('', 1);
      setStudents(res.data?.students || res.data || []);
    } catch { setStudents([]); }
    finally { setLoading(false); }
  };

  const handleCreate = async (e) => {
    e.preventDefault();
    setSaving(true);
    try {
      const res = await staffAPI.createStudent(form);
      if (res.data.success) { toast.success('Student created!'); setShowCreate(false); setForm({ fullName: '', email: '', phone: '', studentCode: '', classId: '' }); load(); }
      else toast.error(res.data.message);
    } catch (err) { toast.error(err.response?.data?.message || 'Failed.'); }
    finally { setSaving(false); }
  };

  const filtered = students.filter(s =>
    !search || (s.fullName || '').toLowerCase().includes(search.toLowerCase()) || (s.schoolEmail || '').toLowerCase().includes(search.toLowerCase()) || (s.studentCode || '').toLowerCase().includes(search.toLowerCase())
  );

  if (loading) return <div className="page-loader"><div className="spinner spinner-lg"></div></div>;

  return (
    <div>
      <div className="page-header" style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: 16 }}>
        <div><h1 className="page-title">Students</h1><p className="page-subtitle">Manage student records</p></div>
        <div style={{ display: 'flex', gap: 10 }}>
          <button className="btn btn-ghost btn-sm" onClick={load}><HiRefresh /></button>
          <button className="btn btn-primary btn-sm" onClick={() => setShowCreate(true)} id="btn-add-student"><HiPlus /> Add Student</button>
        </div>
      </div>

      <div style={{ marginBottom: 20, position: 'relative' }}>
        <HiSearch style={{ position: 'absolute', left: 14, top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
        <input className="form-input" style={{ paddingLeft: 42, width: '100%', maxWidth: 400 }} placeholder="Search students..." value={search} onChange={e => setSearch(e.target.value)} />
      </div>

      {/* Create Modal */}
      {showCreate && (
        <div className="modal-backdrop" onClick={() => setShowCreate(false)}>
          <div className="modal-content" onClick={e => e.stopPropagation()}>
            <div className="modal-header"><h3 style={{ fontWeight: 600 }}>Add Student</h3><button className="btn btn-ghost btn-icon" onClick={() => setShowCreate(false)}>✕</button></div>
            <form onSubmit={handleCreate}>
              <div className="modal-body">
                {['fullName', 'email', 'phone', 'studentCode', 'classId'].map(field => (
                  <div className="form-group" key={field}>
                    <label className="form-label">{field === 'classId' ? 'Class ID' : field.replace(/([A-Z])/g, ' $1').replace(/^./, s => s.toUpperCase())}</label>
                    <input className="form-input" value={form[field]} onChange={e => setForm({ ...form, [field]: e.target.value })} required={field !== 'classId'} />
                  </div>
                ))}
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary btn-sm" onClick={() => setShowCreate(false)}>Cancel</button>
                <button type="submit" className="btn btn-primary btn-sm" disabled={saving}>{saving ? 'Saving...' : 'Save'}</button>
              </div>
            </form>
          </div>
        </div>
      )}

      <div className="table-container">
        <table className="table">
          <thead><tr><th>Name</th><th>Email</th><th>Student Code</th><th>Phone</th><th>Class</th></tr></thead>
          <tbody>
            {filtered.length === 0 ? (
              <tr><td colSpan={5} style={{ textAlign: 'center', padding: 40, color: 'var(--text-muted)' }}>No students found</td></tr>
            ) : filtered.map(s => (
              <tr key={s.studentId}>
                <td><div style={{ display: 'flex', alignItems: 'center', gap: 10 }}><div className="avatar avatar-sm">{(s.fullName || '?')[0]}</div>{s.fullName}</div></td>
                <td style={{ color: 'var(--text-secondary)' }}>{s.schoolEmail}</td>
                <td><span className="badge badge-neutral">{s.studentCode}</span></td>
                <td style={{ color: 'var(--text-secondary)' }}>{s.phoneNumber}</td>
                <td>{s.className || s.classId || '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

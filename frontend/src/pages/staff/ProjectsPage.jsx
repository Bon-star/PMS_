import { useState, useEffect } from 'react';
import { staffAPI } from '../../api';
import { useToast } from '../../components/Toast';
import { HiFolder, HiSearch, HiRefresh, HiEye } from 'react-icons/hi';
import '../../layouts/DashboardLayout.css';

const statusColors = {
  'Pending': 'badge-warning',
  'Approved': 'badge-success',
  'Rejected': 'badge-danger',
  'In Progress': 'badge-primary',
};

export default function ProjectsPage() {
  const toast = useToast();
  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');

  useEffect(() => { load(); }, []);

  const load = async () => {
    setLoading(true);
    try {
      const res = await staffAPI.getProjects('');
      setProjects(res.data?.projects || res.data || []);
    } catch { setProjects([]); }
    finally { setLoading(false); }
  };

  const filtered = projects.filter(p =>
    !search || (p.projectName || '').toLowerCase().includes(search.toLowerCase()) || (p.groupName || '').toLowerCase().includes(search.toLowerCase())
  );

  if (loading) return <div className="page-loader"><div className="spinner spinner-lg"></div></div>;

  return (
    <div>
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', flexWrap: 'wrap', gap: 16 }}>
        <div><h1 className="page-title">Projects</h1><p className="page-subtitle">Overview of all projects</p></div>
        <button className="btn btn-ghost btn-sm" onClick={load}><HiRefresh /> Refresh</button>
      </div>

      <div style={{ marginBottom: 20, position: 'relative' }}>
        <HiSearch style={{ position: 'absolute', left: 14, top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
        <input className="form-input" style={{ paddingLeft: 42, maxWidth: 400, width: '100%' }} placeholder="Search projects..." value={search} onChange={e => setSearch(e.target.value)} />
      </div>

      <div className="table-container">
        <table className="table">
          <thead><tr><th>Project</th><th>Group</th><th>Status</th><th>Start</th><th>End</th></tr></thead>
          <tbody>
            {filtered.length === 0 ? (
              <tr><td colSpan={5} style={{ textAlign: 'center', padding: 40, color: 'var(--text-muted)' }}>No projects found</td></tr>
            ) : filtered.map(p => (
              <tr key={p.projectId}>
                <td><div style={{ fontWeight: 500 }}>{p.projectName || 'Untitled'}</div>{p.description && <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--text-muted)', marginTop: 2 }}>{p.description.substring(0, 60)}...</div>}</td>
                <td>{p.groupName || '—'}</td>
                <td><span className={`badge ${statusColors[p.approvalStatusLabel] || 'badge-neutral'}`}>{p.approvalStatusLabel || 'Unknown'}</span></td>
                <td style={{ color: 'var(--text-secondary)', fontSize: 'var(--font-size-sm)' }}>{p.startDate ? new Date(p.startDate).toLocaleDateString() : '—'}</td>
                <td style={{ color: 'var(--text-secondary)', fontSize: 'var(--font-size-sm)' }}>{p.endDate ? new Date(p.endDate).toLocaleDateString() : '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

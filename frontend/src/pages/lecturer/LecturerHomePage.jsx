import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { lecturerAPI } from '../../api';
import { HiFolder, HiClipboardCheck, HiClock, HiArrowRight } from 'react-icons/hi';
import '../../layouts/DashboardLayout.css';

export default function LecturerHomePage() {
  const { user } = useAuth();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    lecturerAPI.getHome().then(res => setData(res.data)).catch(() => setData(null)).finally(() => setLoading(false));
  }, []);

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">Lecturer Dashboard 📚</h1>
        <p className="page-subtitle">Welcome back, {user?.fullName}. Review and track student projects.</p>
      </div>

      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--warning-light)', color: 'var(--warning)' }}><HiClock /></div>
          <div><div className="stat-value">{data?.pendingProjects ?? '—'}</div><div className="stat-label">Pending Review</div></div>
        </div>
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--success-light)', color: 'var(--success)' }}><HiClipboardCheck /></div>
          <div><div className="stat-value">{data?.approvedProjects ?? '—'}</div><div className="stat-label">Approved Projects</div></div>
        </div>
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--primary-100)', color: 'var(--primary-600)' }}><HiFolder /></div>
          <div><div className="stat-value">{data?.totalProjects ?? '—'}</div><div className="stat-label">Total Projects</div></div>
        </div>
      </div>

      <div className="card">
        <div className="card-header"><h3 className="section-title" style={{ margin: 0 }}>Quick Actions</h3></div>
        <div className="card-body">
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <Link to="/lecturer/projects" style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '14px 16px', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-color)', color: 'var(--text-primary)', fontWeight: 500, fontSize: 'var(--font-size-sm)', textDecoration: 'none' }}>
              <HiFolder style={{ color: 'var(--primary-500)' }} /><span>Review Projects</span><HiArrowRight style={{ marginLeft: 'auto', color: 'var(--text-muted)' }} />
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}

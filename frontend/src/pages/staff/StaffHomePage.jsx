import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { staffAPI } from '../../api';
import { HiAcademicCap, HiFolder, HiTemplate, HiMail, HiOfficeBuilding, HiArrowRight } from 'react-icons/hi';
import '../../layouts/DashboardLayout.css';

export default function StaffHomePage() {
  const { user } = useAuth();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    staffAPI.getHome().then(res => setData(res.data)).catch(() => setData(null)).finally(() => setLoading(false));
  }, []);

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">Staff Dashboard 🎓</h1>
        <p className="page-subtitle">Welcome back, {user?.fullName}. Manage students, projects and more.</p>
      </div>

      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--primary-100)', color: 'var(--primary-600)' }}><HiAcademicCap /></div>
          <div><div className="stat-value">{data?.totalStudents ?? '—'}</div><div className="stat-label">Students</div></div>
        </div>
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--info-light)', color: 'var(--info)' }}><HiOfficeBuilding /></div>
          <div><div className="stat-value">{data?.totalClasses ?? '—'}</div><div className="stat-label">Classes</div></div>
        </div>
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--success-light)', color: 'var(--success)' }}><HiFolder /></div>
          <div><div className="stat-value">{data?.totalProjects ?? '—'}</div><div className="stat-label">Projects</div></div>
        </div>
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--warning-light)', color: 'var(--warning)' }}><HiMail /></div>
          <div><div className="stat-value">{data?.pendingRequests ?? '—'}</div><div className="stat-label">Pending Requests</div></div>
        </div>
      </div>

      <div className="content-grid">
        <div className="card">
          <div className="card-header"><h3 className="section-title" style={{ margin: 0 }}>Quick Actions</h3></div>
          <div className="card-body">
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {[
                { to: '/staff/students', icon: <HiAcademicCap />, label: 'Manage Students', color: 'var(--primary-500)' },
                { to: '/staff/classrooms', icon: <HiOfficeBuilding />, label: 'Manage Classrooms', color: 'var(--info)' },
                { to: '/staff/projects', icon: <HiFolder />, label: 'View Projects', color: 'var(--success)' },
                { to: '/staff/templates', icon: <HiTemplate />, label: 'Project Templates', color: 'var(--warning)' },
                { to: '/staff/requests', icon: <HiMail />, label: 'Review Requests', color: 'var(--danger)' },
              ].map(item => (
                <Link key={item.to} to={item.to} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '14px 16px', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-color)', color: 'var(--text-primary)', fontWeight: 500, fontSize: 'var(--font-size-sm)', textDecoration: 'none', transition: 'all 0.15s' }}>
                  <span style={{ color: item.color }}>{item.icon}</span>
                  <span>{item.label}</span>
                  <HiArrowRight style={{ marginLeft: 'auto', color: 'var(--text-muted)' }} />
                </Link>
              ))}
            </div>
          </div>
        </div>
        <div className="card">
          <div className="card-header"><h3 className="section-title" style={{ margin: 0 }}>Recent Activity</h3></div>
          <div className="card-body">
            <div className="empty-state" style={{ padding: '40px 20px' }}>
              <div className="empty-state-icon">📊</div>
              <div className="empty-state-title">Activity Feed</div>
              <p style={{ color: 'var(--text-secondary)', fontSize: 'var(--font-size-sm)' }}>Recent project activities and updates will appear here.</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

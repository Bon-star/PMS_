import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { studentAPI } from '../../api';
import { useToast } from '../../components/Toast';
import { HiFolder, HiUserGroup, HiClipboardCheck, HiBell, HiArrowRight, HiCalendar } from 'react-icons/hi';
import '../../layouts/DashboardLayout.css';

export default function StudentHomePage() {
  const { user } = useAuth();
  const toast = useToast();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      const res = await studentAPI.getHome();
      setData(res.data);
    } catch {
      // API may not be ready yet, show placeholder
      setData(null);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">Welcome back, {user?.fullName || 'Student'} 👋</h1>
        <p className="page-subtitle">Here's what's happening with your projects today.</p>
      </div>

      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--primary-100)', color: 'var(--primary-600)' }}>
            <HiUserGroup />
          </div>
          <div>
            <div className="stat-value">{data?.groupName || '—'}</div>
            <div className="stat-label">My Group</div>
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--info-light)', color: 'var(--info)' }}>
            <HiFolder />
          </div>
          <div>
            <div className="stat-value">{data?.projectName ? '1' : '0'}</div>
            <div className="stat-label">Active Project</div>
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--success-light)', color: 'var(--success)' }}>
            <HiClipboardCheck />
          </div>
          <div>
            <div className="stat-value">{data?.tasksDone ?? 0}</div>
            <div className="stat-label">Tasks Completed</div>
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--warning-light)', color: 'var(--warning)' }}>
            <HiBell />
          </div>
          <div>
            <div className="stat-value">{data?.notificationCount ?? 0}</div>
            <div className="stat-label">Notifications</div>
          </div>
        </div>
      </div>

      <div className="content-grid">
        <div className="card">
          <div className="card-header">
            <h3 className="section-title" style={{ margin: 0 }}>Quick Actions</h3>
          </div>
          <div className="card-body">
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <Link to="/student/groups" className="quick-action-link">
                <HiUserGroup style={{ color: 'var(--primary-500)' }} />
                <span>View My Group</span>
                <HiArrowRight style={{ marginLeft: 'auto', color: 'var(--text-muted)' }} />
              </Link>
              <Link to="/student/project" className="quick-action-link">
                <HiFolder style={{ color: 'var(--success)' }} />
                <span>Open Project Workspace</span>
                <HiArrowRight style={{ marginLeft: 'auto', color: 'var(--text-muted)' }} />
              </Link>
              <Link to="/student/notifications" className="quick-action-link">
                <HiBell style={{ color: 'var(--warning)' }} />
                <span>Check Notifications</span>
                <HiArrowRight style={{ marginLeft: 'auto', color: 'var(--text-muted)' }} />
              </Link>
            </div>
          </div>
        </div>

        <div className="card">
          <div className="card-header">
            <h3 className="section-title" style={{ margin: 0 }}>Current Project</h3>
          </div>
          <div className="card-body">
            {data?.projectName ? (
              <div>
                <h4 style={{ fontWeight: 600, marginBottom: 8 }}>{data.projectName}</h4>
                <p style={{ color: 'var(--text-secondary)', fontSize: 'var(--font-size-sm)', marginBottom: 16 }}>{data.projectDescription || 'No description'}</p>
                {data.projectEndDate && (
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: 'var(--text-secondary)', fontSize: 'var(--font-size-sm)' }}>
                    <HiCalendar />
                    <span>Due: {data.projectEndDate}</span>
                  </div>
                )}
              </div>
            ) : (
              <div className="empty-state" style={{ padding: '40px 20px' }}>
                <div className="empty-state-icon">📋</div>
                <div className="empty-state-title">No Active Project</div>
                <p style={{ color: 'var(--text-secondary)', fontSize: 'var(--font-size-sm)' }}>Join a group and wait for project assignment.</p>
              </div>
            )}
          </div>
        </div>
      </div>

      <style>{`
        .quick-action-link {
          display: flex;
          align-items: center;
          gap: 12px;
          padding: 14px 16px;
          border-radius: var(--radius-md);
          border: 1px solid var(--border-color);
          color: var(--text-primary);
          font-weight: 500;
          font-size: var(--font-size-sm);
          transition: all var(--transition-fast);
          text-decoration: none;
        }
        .quick-action-link:hover {
          background: var(--gray-50);
          border-color: var(--primary-200);
          transform: translateX(4px);
          color: var(--text-primary);
        }
      `}</style>
    </div>
  );
}

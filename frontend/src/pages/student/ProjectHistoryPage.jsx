import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { HiArrowLeft, HiCalendar, HiChevronRight, HiClock, HiFolder, HiRefresh, HiStar, HiCheckCircle, HiXCircle } from 'react-icons/hi';
import { studentAPI } from '../../api';
import '../../layouts/DashboardLayout.css';

function StatCard({ label, value, icon, tone = 'var(--primary-600)', bg = 'var(--primary-100)' }) {
  return (
    <div className="stat-card">
      <div className="stat-icon" style={{ background: bg, color: tone }}>{icon}</div>
      <div>
        <div className="stat-value">{value}</div>
        <div className="stat-label">{label}</div>
      </div>
    </div>
  );
}

export default function ProjectHistoryPage() {
  const [data, setData] = useState({ entries: [] });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    load();
  }, []);

  const load = async () => {
    setLoading(true);
    try {
      const res = await studentAPI.getProjectHistory();
      setData(res.data || { entries: [] });
    } catch {
      setData({ entries: [] });
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return <div className="page-loader"><div className="spinner spinner-lg"></div><span>Loading project history...</span></div>;
  }

  const entries = data.entries || [];

  return (
    <div>
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', gap: 16, alignItems: 'flex-start', flexWrap: 'wrap' }}>
        <div>
          <div style={{ marginBottom: 8 }}>
            <Link to="/student/project" className="btn btn-ghost btn-sm"><HiArrowLeft /> Back to Project</Link>
          </div>
          <h1 className="page-title">Project History</h1>
          <p className="page-subtitle">Review previous semesters, project outcomes, and your published scores.</p>
        </div>
        <button className="btn btn-ghost btn-sm" onClick={load}><HiRefresh /> Refresh</button>
      </div>

      <div className="stats-grid" style={{ marginBottom: 24 }}>
        <StatCard label="Projects" value={entries.length} icon={<HiFolder />} />
        <StatCard label="Current Semester" value={entries.filter((entry) => entry.currentSemester).length} icon={<HiCalendar />} tone="var(--info)" bg="var(--info-light)" />
        <StatCard label="Published Scores" value={entries.filter((entry) => entry.scorePublished).length} icon={<HiStar />} tone="var(--warning)" bg="var(--warning-light)" />
        <StatCard label="Completed Tasks" value={entries.reduce((sum, entry) => sum + (entry.doneTasks || 0), 0)} icon={<HiCheckCircle />} tone="var(--success)" bg="var(--success-light)" />
      </div>

      {entries.length === 0 ? (
        <div className="card">
          <div className="card-body">
            <div className="empty-state" style={{ padding: '48px 20px' }}>
              <div className="empty-state-icon">📚</div>
              <div className="empty-state-title">No Project History Yet</div>
              <p style={{ color: 'var(--text-secondary)', fontSize: 'var(--font-size-sm)' }}>
                Your previous project records will appear here after you join a group and work on a project.
              </p>
            </div>
          </div>
        </div>
      ) : (
        <div style={{ display: 'grid', gap: 16 }}>
          {entries.map((entry) => {
            const project = entry.project || {};
            return (
              <div key={project.projectId} className="card">
                <div className="card-body">
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16, alignItems: 'flex-start', flexWrap: 'wrap' }}>
                    <div style={{ flex: '1 1 420px' }}>
                      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 10 }}>
                        <span className={`badge ${entry.currentSemester ? 'badge-primary' : 'badge-neutral'}`}>
                          {entry.currentSemester ? 'Current Semester' : (project.semesterName || 'Semester')}
                        </span>
                        <span className={`badge ${
                          project.approvalStatus === 1 ? 'badge-success' :
                          project.approvalStatus === 3 ? 'badge-danger' :
                          'badge-warning'
                        }`}>
                          {project.approvalStatusLabel || 'Unknown'}
                        </span>
                      </div>

                      <h3 style={{ fontSize: 'var(--font-size-xl)', fontWeight: 700, marginBottom: 8 }}>
                        {project.projectName || 'Untitled project'}
                      </h3>

                      <div style={{ color: 'var(--text-secondary)', fontSize: 'var(--font-size-sm)', display: 'grid', gap: 4 }}>
                        <div>{project.groupName || 'Unknown group'} {project.className ? `· ${project.className}` : ''}</div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                          <HiClock />
                          <span>
                            {project.startDate ? new Date(project.startDate).toLocaleString() : 'No start date'}
                            {' '}to{' '}
                            {project.endDate ? new Date(project.endDate).toLocaleString() : 'No end date'}
                          </span>
                        </div>
                      </div>

                      {project.description && (
                        <p style={{ color: 'var(--text-primary)', marginTop: 12, whiteSpace: 'pre-wrap' }}>
                          {project.description}
                        </p>
                      )}
                    </div>

                    <div style={{ minWidth: 220, display: 'grid', gap: 10 }}>
                      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 10 }}>
                        <div className="stat-card" style={{ padding: 14 }}>
                          <div>
                            <div className="stat-value" style={{ fontSize: 'var(--font-size-lg)' }}>{entry.sprintCount || 0}</div>
                            <div className="stat-label">Sprints</div>
                          </div>
                        </div>
                        <div className="stat-card" style={{ padding: 14 }}>
                          <div>
                            <div className="stat-value" style={{ fontSize: 'var(--font-size-lg)' }}>{entry.totalTasks || 0}</div>
                            <div className="stat-label">Tasks</div>
                          </div>
                        </div>
                        <div className="stat-card" style={{ padding: 14 }}>
                          <div>
                            <div className="stat-value" style={{ fontSize: 'var(--font-size-lg)', color: 'var(--success)' }}>{entry.doneTasks || 0}</div>
                            <div className="stat-label">Done</div>
                          </div>
                        </div>
                        <div className="stat-card" style={{ padding: 14 }}>
                          <div>
                            <div className="stat-value" style={{ fontSize: 'var(--font-size-lg)', color: 'var(--danger)' }}>{entry.failedTasks || 0}</div>
                            <div className="stat-label">Failed</div>
                          </div>
                        </div>
                      </div>

                      <div style={{ padding: 14, border: '1px solid var(--border-color)', borderRadius: 'var(--radius-md)', background: 'var(--gray-50)' }}>
                        <div style={{ fontSize: 'var(--font-size-xs)', textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 4 }}>Score</div>
                        <div style={{ fontWeight: 700, fontSize: 'var(--font-size-lg)' }}>
                          {entry.scorePublished && entry.finalScore != null ? entry.finalScore : '-'}
                        </div>
                        <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--text-secondary)' }}>
                          {entry.scorePublished ? 'Published' : 'Not published yet'}
                        </div>
                      </div>

                      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                        <Link to={`/student/project/history-ui/${project.projectId}`} className="btn btn-primary btn-sm">
                          View Details <HiChevronRight />
                        </Link>
                        {entry.currentSemester && (
                          <Link to="/student/project" className="btn btn-ghost btn-sm">Open Workspace</Link>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

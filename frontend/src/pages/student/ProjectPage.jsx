import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { studentAPI } from '../../api';
import { HiFolder, HiCalendar, HiClock, HiClipboardList, HiRefresh } from 'react-icons/hi';
import '../../layouts/DashboardLayout.css';

const statusConfig = {
  0: { label: 'Pending', color: 'var(--gray-500)', bg: 'var(--gray-100)' },
  1: { label: 'In Progress', color: 'var(--info)', bg: 'var(--info-light)' },
  2: { label: 'Awaiting Review', color: 'var(--warning)', bg: 'var(--warning-light)' },
  3: { label: 'Done', color: 'var(--success)', bg: 'var(--success-light)' },
  4: { label: 'Returned', color: 'var(--danger)', bg: 'var(--danger-light)' },
  5: { label: 'Failed', color: '#7C3AED', bg: '#EDE9FE' },
  6: { label: 'Cancelled', color: 'var(--gray-400)', bg: 'var(--gray-100)' },
};

export default function ProjectPage() {
  const [project, setProject] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => { loadProject(); }, []);

  const loadProject = async () => {
    setLoading(true);
    try {
      const res = await studentAPI.getProject();
      setProject(res.data);
    } catch { setProject(null); }
    finally { setLoading(false); }
  };

  if (loading) return <div className="page-loader"><div className="spinner spinner-lg"></div><span>Loading project...</span></div>;

  if (!project || project.noGroup) {
    return (
      <div>
        <div className="page-header">
          <h1 className="page-title">Project Workspace</h1>
        </div>
        <div className="card"><div className="card-body">
          <div className="empty-state">
            <div className="empty-state-icon">📋</div>
            <div className="empty-state-title">{project?.noGroup ? 'Join a Group First' : 'No Project Assigned'}</div>
            <p style={{ color: 'var(--text-secondary)', fontSize: 'var(--font-size-sm)' }}>
              {project?.noGroup ? 'You need to be in a group before accessing the project workspace.' : 'Your group does not have a project assigned yet. Please wait for staff to assign one.'}
            </p>
          </div>
        </div></div>
      </div>
    );
  }

  const p = project.project || {};
  const sprints = project.sprints || [];
  const tasks = project.tasks || [];

  return (
    <div>
      <div className="page-header" style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: 16 }}>
        <div>
          <h1 className="page-title">{p.projectName || 'Project Workspace'}</h1>
          <p className="page-subtitle">{p.description || 'Manage sprints, tasks, and deliverables'}</p>
        </div>
        <button className="btn btn-ghost btn-sm" onClick={loadProject}><HiRefresh /> Refresh</button>
      </div>

      {/* Project Info */}
      <div className="stats-grid" style={{ marginBottom: 24 }}>
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--primary-100)', color: 'var(--primary-600)' }}><HiFolder /></div>
          <div>
            <div className="stat-value" style={{ fontSize: 'var(--font-size-xl)' }}>{p.approvalStatusLabel || 'Pending'}</div>
            <div className="stat-label">Project Status</div>
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--success-light)', color: 'var(--success)' }}><HiClipboardList /></div>
          <div>
            <div className="stat-value">{tasks.filter(t => t.status === 3).length}/{tasks.length}</div>
            <div className="stat-label">Tasks Completed</div>
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--info-light)', color: 'var(--info)' }}><HiClock /></div>
          <div>
            <div className="stat-value">{sprints.length}</div>
            <div className="stat-label">Sprints</div>
          </div>
        </div>
        {p.endDate && (
          <div className="stat-card">
            <div className="stat-icon" style={{ background: 'var(--warning-light)', color: 'var(--warning)' }}><HiCalendar /></div>
            <div>
              <div className="stat-value" style={{ fontSize: 'var(--font-size-lg)' }}>{new Date(p.endDate).toLocaleDateString()}</div>
              <div className="stat-label">Deadline</div>
            </div>
          </div>
        )}
      </div>

      <div className="card" style={{ marginBottom: 24 }}>
        <div className="card-header">
          <h3 className="section-title" style={{ margin: 0 }}>Migration Workspace</h3>
        </div>
        <div className="card-body" style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ color: 'var(--text-secondary)', fontSize: 'var(--font-size-sm)', maxWidth: 640 }}>
            React parity pages are now available for the task board and project history, including sprint planning, task planning, replanning failed work, and final link updates.
          </div>
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
            <Link to="/student/project/tasks-ui" className="btn btn-primary btn-sm">Open Task Board</Link>
            <Link to="/student/project/history-ui" className="btn btn-ghost btn-sm">Open History</Link>
          </div>
        </div>
      </div>

      {/* Sprints */}
      <div className="card" style={{ marginBottom: 24 }}>
        <div className="card-header">
          <h3 className="section-title" style={{ margin: 0 }}>Sprints & Tasks</h3>
        </div>
        <div className="card-body">
          {sprints.length === 0 && tasks.length === 0 ? (
            <div className="empty-state" style={{ padding: '40px 20px' }}>
              <div className="empty-state-icon">🏃</div>
              <div className="empty-state-title">No Sprints Yet</div>
              <p style={{ color: 'var(--text-secondary)', fontSize: 'var(--font-size-sm)' }}>Create your first sprint to start tracking tasks.</p>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              {sprints.map(sprint => {
                const sprintTasks = tasks.filter(t => t.sprintId === sprint.sprintId);
                return (
                  <div key={sprint.sprintId} style={{ border: '1px solid var(--border-color)', borderRadius: 'var(--radius-md)', overflow: 'hidden' }}>
                    <div style={{ padding: '12px 16px', background: 'var(--gray-50)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <strong style={{ fontSize: 'var(--font-size-sm)' }}>{sprint.sprintName || `Sprint #${sprint.sprintId}`}</strong>
                        {sprint.startDate && <span style={{ fontSize: 'var(--font-size-xs)', color: 'var(--text-muted)', marginLeft: 12 }}>{new Date(sprint.startDate).toLocaleDateString()} — {sprint.endDate ? new Date(sprint.endDate).toLocaleDateString() : '...'}</span>}
                      </div>
                      <span className="badge badge-neutral">{sprintTasks.length} tasks</span>
                    </div>
                    <div>
                      {sprintTasks.length === 0 ? (
                        <div style={{ padding: 16, textAlign: 'center', color: 'var(--text-muted)', fontSize: 'var(--font-size-sm)' }}>No tasks in this sprint</div>
                      ) : (
                        sprintTasks.map(task => {
                          const cfg = statusConfig[task.status] || statusConfig[0];
                          return (
                            <div key={task.taskId} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 16px', borderTop: '1px solid var(--border-light)' }}>
                              <div style={{ flex: 1 }}>
                                <div style={{ fontWeight: 500, fontSize: 'var(--font-size-sm)' }}>{task.taskName}</div>
                                <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--text-muted)', marginTop: 2 }}>
                                  {task.assigneeName || 'Unassigned'} · {task.estimatedPoints ? `${task.estimatedPoints} pts` : '—'}
                                </div>
                              </div>
                              <span className="badge" style={{ background: cfg.bg, color: cfg.color }}>{cfg.label}</span>
                            </div>
                          );
                        })
                      )}
                    </div>
                  </div>
                );
              })}
              {/* Backlog tasks (no sprint) */}
              {tasks.filter(t => !t.sprintId).length > 0 && (
                <div style={{ border: '1px solid var(--border-color)', borderRadius: 'var(--radius-md)', overflow: 'hidden' }}>
                  <div style={{ padding: '12px 16px', background: 'var(--gray-50)' }}><strong style={{ fontSize: 'var(--font-size-sm)' }}>Backlog</strong></div>
                  {tasks.filter(t => !t.sprintId).map(task => {
                    const cfg = statusConfig[task.status] || statusConfig[0];
                    return (
                      <div key={task.taskId} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 16px', borderTop: '1px solid var(--border-light)' }}>
                        <div style={{ flex: 1 }}>
                          <div style={{ fontWeight: 500, fontSize: 'var(--font-size-sm)' }}>{task.taskName}</div>
                        </div>
                        <span className="badge" style={{ background: cfg.bg, color: cfg.color }}>{cfg.label}</span>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

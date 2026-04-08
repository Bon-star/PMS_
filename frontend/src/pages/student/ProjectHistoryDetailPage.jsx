import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { HiArrowLeft, HiCalendar, HiCheckCircle, HiClock, HiDocumentText, HiDownload, HiExternalLink, HiFolder, HiRefresh, HiUserGroup, HiXCircle } from 'react-icons/hi';
import { studentAPI } from '../../api';
import '../../layouts/DashboardLayout.css';

function Badge({ children, tone = 'neutral' }) {
  const className =
    tone === 'success' ? 'badge badge-success'
      : tone === 'danger' ? 'badge badge-danger'
      : tone === 'warning' ? 'badge badge-warning'
      : tone === 'primary' ? 'badge badge-primary'
      : 'badge badge-neutral';
  return <span className={className}>{children}</span>;
}

export default function ProjectHistoryDetailPage() {
  const { id } = useParams();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    load();
  }, [id]);

  const load = async () => {
    setLoading(true);
    setError('');
    try {
      const res = await studentAPI.getProjectHistoryDetail(id);
      setData(res.data);
    } catch (err) {
      setData(null);
      setError(err.response?.data?.message || 'Unable to load project history detail.');
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return <div className="page-loader"><div className="spinner spinner-lg"></div><span>Loading project detail...</span></div>;
  }

  if (error || !data) {
    return (
      <div>
        <div className="page-header">
          <Link to="/student/project/history-ui" className="btn btn-ghost btn-sm"><HiArrowLeft /> Back to History</Link>
        </div>
        <div className="card">
          <div className="card-body">
            <div className="empty-state" style={{ padding: '48px 20px' }}>
              <div className="empty-state-icon">⚠️</div>
              <div className="empty-state-title">Detail Unavailable</div>
              <p style={{ color: 'var(--text-secondary)', fontSize: 'var(--font-size-sm)' }}>{error || 'This project could not be loaded.'}</p>
            </div>
          </div>
        </div>
      </div>
    );
  }

  const project = data.project || {};
  const score = data.score;
  const tasks = data.tasks || [];
  const members = data.members || [];
  const sprints = data.sprints || [];
  const comments = data.lecturerComments || [];
  const overallPerformance = data.overallPerformance || [];

  return (
    <div>
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', gap: 16, alignItems: 'flex-start', flexWrap: 'wrap' }}>
        <div>
          <div style={{ marginBottom: 8, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <Link to="/student/project/history-ui" className="btn btn-ghost btn-sm"><HiArrowLeft /> Back to History</Link>
            <button className="btn btn-ghost btn-sm" onClick={load}><HiRefresh /> Refresh</button>
          </div>
          <h1 className="page-title">{project.projectName || 'Untitled project'}</h1>
          <p className="page-subtitle">{project.groupName || 'Unknown group'} {project.className ? `· ${project.className}` : ''}</p>
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <Badge tone={data.currentSemester ? 'primary' : 'neutral'}>{data.currentSemester ? 'Current Semester' : (project.semesterName || 'Semester')}</Badge>
          <Badge tone={
            project.approvalStatus === 1 ? 'success'
              : project.approvalStatus === 3 ? 'danger'
              : 'warning'
          }>{project.approvalStatusLabel || 'Unknown'}</Badge>
        </div>
      </div>

      <div className="stats-grid" style={{ marginBottom: 24 }}>
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--primary-100)', color: 'var(--primary-600)' }}><HiFolder /></div>
          <div><div className="stat-value">{data.sprintCount || 0}</div><div className="stat-label">Sprints</div></div>
        </div>
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--success-light)', color: 'var(--success)' }}><HiCheckCircle /></div>
          <div><div className="stat-value">{data.doneTaskCount || 0}</div><div className="stat-label">Done Tasks</div></div>
        </div>
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--danger-light)', color: 'var(--danger)' }}><HiXCircle /></div>
          <div><div className="stat-value">{data.failedTaskCount || 0}</div><div className="stat-label">Failed Tasks</div></div>
        </div>
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--warning-light)', color: 'var(--warning)' }}><HiDocumentText /></div>
          <div>
            <div className="stat-value">{score?.published && score?.finalScore != null ? score.finalScore : '-'}</div>
            <div className="stat-label">{score?.published ? 'Published Score' : 'Score Pending'}</div>
          </div>
        </div>
      </div>

      <div className="content-grid" style={{ alignItems: 'start' }}>
        <div style={{ display: 'grid', gap: 16 }}>
          <div className="card">
            <div className="card-header"><h3 className="section-title" style={{ margin: 0 }}>Project Overview</h3></div>
            <div className="card-body" style={{ display: 'grid', gap: 14 }}>
              <div style={{ color: 'var(--text-secondary)', fontSize: 'var(--font-size-sm)', display: 'grid', gap: 6 }}>
                <div><strong>Topic source:</strong> {project.topicSource || '-'}</div>
                <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                  <HiCalendar />
                  <span>{project.startDate ? new Date(project.startDate).toLocaleString() : 'No start date'} to {project.endDate ? new Date(project.endDate).toLocaleString() : 'No end date'}</span>
                </div>
              </div>

              {project.description && (
                <div style={{ whiteSpace: 'pre-wrap', color: 'var(--text-primary)' }}>{project.description}</div>
              )}

              <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                {project.requirementDownloadUrl && <a className="btn btn-ghost btn-sm" href={project.requirementDownloadUrl}><HiDownload /> Requirement File</a>}
                {project.sourceCodeUrl && <a className="btn btn-ghost btn-sm" href={project.sourceCodeUrl} target="_blank" rel="noreferrer"><HiExternalLink /> Source Link</a>}
                {project.documentUrl && <a className="btn btn-ghost btn-sm" href={project.documentUrl} target="_blank" rel="noreferrer"><HiExternalLink /> Document Link</a>}
              </div>
            </div>
          </div>

          <div className="card">
            <div className="card-header"><h3 className="section-title" style={{ margin: 0 }}>Tasks</h3></div>
            <div className="card-body" style={{ display: 'grid', gap: 12 }}>
              {tasks.length === 0 ? (
                <div className="empty-state" style={{ padding: '24px 16px' }}>
                  <div className="empty-state-title">No Tasks Recorded</div>
                </div>
              ) : tasks.map((task) => (
                <div key={task.taskId} style={{ border: '1px solid var(--border-color)', borderRadius: 'var(--radius-md)', padding: 14, background: 'var(--gray-50)' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
                    <div>
                      <div style={{ fontWeight: 700 }}>{task.taskName}</div>
                      <div style={{ color: 'var(--text-secondary)', fontSize: 'var(--font-size-xs)', marginTop: 4 }}>
                        {task.sprintName || 'Unknown sprint'} · {task.estimatedPoints || 0} pts
                      </div>
                    </div>
                    <Badge tone={
                      task.status === 3 ? 'success'
                        : task.status === 4 || task.status === 5 || task.status === 6 ? 'danger'
                        : task.status === 2 ? 'warning'
                        : 'neutral'
                    }>{task.statusLabel || 'Unknown'}</Badge>
                  </div>

                  <div style={{ color: 'var(--text-secondary)', fontSize: 'var(--font-size-sm)', display: 'grid', gap: 4, marginTop: 10 }}>
                    <div>Assignee: <strong>{task.assigneeName || '-'}</strong> {task.assigneeCode ? `(${task.assigneeCode})` : ''}</div>
                    <div>Reviewer: <strong>{task.reviewerName || 'Unassigned'}</strong></div>
                  </div>

                  {task.description && (
                    <div style={{ marginTop: 10, whiteSpace: 'pre-wrap' }}>{task.description}</div>
                  )}

                  {(task.submissionNote || task.submissionUrl || (task.attachments || []).length > 0 || task.hasCode) && (
                    <div style={{ marginTop: 12, paddingTop: 12, borderTop: '1px dashed var(--border-color)', display: 'grid', gap: 8 }}>
                      <div style={{ fontWeight: 600, fontSize: 'var(--font-size-sm)' }}>Submission Evidence</div>
                      {task.submissionNote && <div style={{ whiteSpace: 'pre-wrap' }}>{task.submissionNote}</div>}
                      {task.submissionUrl && <a href={task.submissionUrl} target="_blank" rel="noreferrer" className="btn btn-ghost btn-sm" style={{ width: 'fit-content' }}><HiExternalLink /> Open Reference Link</a>}
                      {(task.attachments || []).length > 0 && (
                        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                          {task.attachments.map((attachment) => (
                            <span key={attachment.storedName} style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                              <a href={attachment.downloadUrl} className="btn btn-ghost btn-sm">
                                <HiDownload /> {attachment.displayName}
                              </a>
                              {attachment.codeViewUrl && (
                                <Link to={attachment.codeViewUrl} className="btn btn-ghost btn-sm">
                                  <HiDocumentText /> Preview
                                </Link>
                              )}
                            </span>
                          ))}
                        </div>
                      )}
                      {task.hasCode && task.codeViewUrl && (
                        <Link to={task.codeViewUrl} className="btn btn-ghost btn-sm" style={{ width: 'fit-content' }}>
                          <HiDocumentText /> Open Code View
                        </Link>
                      )}
                    </div>
                  )}

                  {task.reviewComment && (
                    <div style={{ marginTop: 12, padding: 12, borderRadius: 'var(--radius-md)', background: 'white', border: '1px solid var(--border-color)' }}>
                      <div style={{ fontWeight: 600, marginBottom: 4 }}>Review Comment</div>
                      <div style={{ whiteSpace: 'pre-wrap' }}>{task.reviewComment}</div>
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>
        </div>

        <div style={{ display: 'grid', gap: 16 }}>
          <div className="card">
            <div className="card-header"><h3 className="section-title" style={{ margin: 0 }}>Members</h3></div>
            <div className="card-body" style={{ display: 'grid', gap: 10 }}>
              {members.map((member) => (
                <div key={member.studentId} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10, border: '1px solid var(--border-color)', borderRadius: 'var(--radius-md)', padding: 12 }}>
                  <div>
                    <div style={{ fontWeight: 600 }}>{member.fullName}</div>
                    <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--text-secondary)' }}>{member.studentCode || '-'} · {member.schoolEmail || '-'}</div>
                  </div>
                  {member.isLeader && <Badge tone="warning">Leader</Badge>}
                </div>
              ))}
            </div>
          </div>

          <div className="card">
            <div className="card-header"><h3 className="section-title" style={{ margin: 0 }}>Resources</h3></div>
            <div className="card-body" style={{ display: 'grid', gap: 10 }}>
              {[...(data.templateAttachments || []), ...(data.projectFiles || [])].length === 0 ? (
                <div style={{ color: 'var(--text-secondary)', fontSize: 'var(--font-size-sm)' }}>No attached files.</div>
              ) : (
                [...(data.templateAttachments || []), ...(data.projectFiles || [])].map((file) => (
                  <a key={`${file.attachmentId}-${file.fileName}`} href={file.fileUrl} className="btn btn-ghost btn-sm" style={{ justifyContent: 'flex-start' }}>
                    <HiDownload /> {file.fileName}
                  </a>
                ))
              )}
            </div>
          </div>

          <div className="card">
            <div className="card-header"><h3 className="section-title" style={{ margin: 0 }}>Performance</h3></div>
            <div className="card-body" style={{ display: 'grid', gap: 10 }}>
              {overallPerformance.length === 0 ? (
                <div style={{ color: 'var(--text-secondary)', fontSize: 'var(--font-size-sm)' }}>No performance data.</div>
              ) : overallPerformance.map((item) => (
                <div key={item.studentId} style={{ border: '1px solid var(--border-color)', borderRadius: 'var(--radius-md)', padding: 12 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                    <div>
                      <div style={{ fontWeight: 600 }}>{item.studentName}</div>
                      <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--text-secondary)' }}>{item.studentCode}</div>
                    </div>
                    <Badge tone="primary">{Number(item.doneRatePercent || 0).toFixed(1)}%</Badge>
                  </div>
                  <div style={{ marginTop: 10, display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 8, fontSize: 'var(--font-size-sm)', color: 'var(--text-secondary)' }}>
                    <div>Done: <strong style={{ color: 'var(--success)' }}>{item.doneTasks}</strong></div>
                    <div>Failed: <strong style={{ color: 'var(--danger)' }}>{item.failedTasks}</strong></div>
                    <div>Submitted: <strong>{item.submittedTasks}</strong></div>
                    <div>In progress: <strong>{item.inProgressTasks}</strong></div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="card">
            <div className="card-header"><h3 className="section-title" style={{ margin: 0 }}>Lecturer Comments</h3></div>
            <div className="card-body" style={{ display: 'grid', gap: 10 }}>
              {comments.length === 0 ? (
                <div style={{ color: 'var(--text-secondary)', fontSize: 'var(--font-size-sm)' }}>No lecturer comments yet.</div>
              ) : comments.map((comment) => (
                <div key={comment.commentId} style={{ border: '1px solid var(--border-color)', borderRadius: 'var(--radius-md)', padding: 12 }}>
                  <div style={{ fontWeight: 600 }}>{comment.lecturerName} {comment.lecturerCode ? `(${comment.lecturerCode})` : ''}</div>
                  <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--text-secondary)', marginTop: 2 }}>{comment.createdAt ? new Date(comment.createdAt).toLocaleString() : '-'}</div>
                  <div style={{ marginTop: 8, whiteSpace: 'pre-wrap' }}>{comment.commentContent}</div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

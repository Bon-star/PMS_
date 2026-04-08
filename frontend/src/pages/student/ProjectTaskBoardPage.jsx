import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  HiArrowLeft,
  HiCalendar,
  HiCheckCircle,
  HiClock,
  HiDocumentText,
  HiDownload,
  HiExternalLink,
  HiFilter,
  HiRefresh,
  HiXCircle,
} from 'react-icons/hi';
import { studentAPI } from '../../api';
import { useToast } from '../../components/Toast';
import '../../layouts/DashboardLayout.css';

const emptyTaskForm = {
  taskName: '',
  description: '',
  taskImage: '',
  estimatedPoints: '',
  assigneeId: '',
  reviewerId: '',
};

function StatusBadge({ status, label }) {
  const className =
    status === 3 ? 'badge badge-success'
      : status === 4 || status === 5 || status === 6 ? 'badge badge-danger'
      : status === 2 ? 'badge badge-warning'
      : status === 1 ? 'badge badge-primary'
      : 'badge badge-neutral';
  return <span className={className}>{label}</span>;
}

function formatDate(value) {
  if (!value) return '-';
  try {
    return new Date(value).toLocaleDateString();
  } catch {
    return '-';
  }
}

function formatDateTime(value) {
  if (!value) return '-';
  try {
    return new Date(value).toLocaleString();
  } catch {
    return '-';
  }
}

export default function ProjectTaskBoardPage() {
  const toast = useToast();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [filters, setFilters] = useState({ status: '', assigneeId: '' });
  const [newSprintName, setNewSprintName] = useState('');
  const [taskForm, setTaskForm] = useState(emptyTaskForm);
  const [editingTaskId, setEditingTaskId] = useState(null);
  const [finalLinks, setFinalLinks] = useState({ sourceCodeUrl: '', documentUrl: '' });
  const [replanForms, setReplanForms] = useState({});
  const [submitForms, setSubmitForms] = useState({});
  const [reviewForms, setReviewForms] = useState({});

  useEffect(() => {
    load();
  }, []);

  const syncDerivedState = (payload) => {
    const project = payload?.project || {};
    setFinalLinks({
      sourceCodeUrl: project.sourceCodeUrl || '',
      documentUrl: project.documentUrl || '',
    });

    const nextReplan = {};
    const nextSubmit = {};
    const nextReview = {};
    for (const task of payload?.failedTasks || []) {
      nextReplan[task.taskId] = {
        assigneeId: task.assigneeId ? String(task.assigneeId) : '',
        reviewerId: task.reviewerId ? String(task.reviewerId) : '',
      };
    }
    for (const task of payload?.tasks || []) {
      nextSubmit[task.taskId] = {
        submissionNote: task.submissionNote || '',
        submissionUrl: task.submissionUrl || '',
        submissionCode: task.submissionCode || '',
        clearFiles: false,
        clearCode: false,
        files: [],
      };
      nextReview[task.taskId] = {
        reviewComment: '',
      };
    }
    setReplanForms(nextReplan);
    setSubmitForms(nextSubmit);
    setReviewForms(nextReview);
  };

  const load = async (nextFilters = filters) => {
    setLoading(true);
    try {
      const params = {};
      if (nextFilters.status !== '') params.status = nextFilters.status;
      if (nextFilters.assigneeId !== '') params.assigneeId = nextFilters.assigneeId;
      const res = await studentAPI.getProjectTasks(params);
      setData(res.data);
      syncDerivedState(res.data);
    } catch {
      setData(null);
    } finally {
      setLoading(false);
    }
  };

  const resetTaskEditor = () => {
    setEditingTaskId(null);
    setTaskForm(emptyTaskForm);
  };

  const runAction = async (request, options = {}) => {
    const { resetEditor = false } = options;
    setSaving(true);
    try {
      const res = await request();
      toast.success(res.data?.message || 'Saved successfully.');
      if (resetEditor) {
        resetTaskEditor();
      }
      await load(filters);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Action failed.');
    } finally {
      setSaving(false);
    }
  };

  const handleCreateSprint = async () => {
    await runAction(() => studentAPI.createProjectSprint({ sprintName: newSprintName }));
    setNewSprintName('');
  };

  const handleRenameSprint = async (sprint) => {
    const sprintName = window.prompt('Enter the new sprint name.', sprint.sprintName || sprint.displayName || '');
    if (sprintName === null) return;
    await runAction(() => studentAPI.renameProjectSprint(sprint.sprintId, sprintName));
  };

  const handleDeleteSprint = async (sprint) => {
    if (!window.confirm(`Delete ${sprint.displayName || `Sprint #${sprint.sprintId}`}?`)) return;
    await runAction(() => studentAPI.deleteProjectSprint(sprint.sprintId));
  };

  const handleCancelSprint = async (sprint) => {
    const cancelReason = window.prompt(`Why should ${sprint.displayName || `Sprint #${sprint.sprintId}`} be cancelled?`, '');
    if (cancelReason === null) return;
    await runAction(() => studentAPI.cancelProjectSprint(sprint.sprintId, cancelReason));
  };

  const handleTaskInput = (field, value) => {
    setTaskForm((current) => ({ ...current, [field]: value }));
  };

  const handleEditTask = (task) => {
    setEditingTaskId(task.taskId);
    setTaskForm({
      taskName: task.taskName || '',
      description: task.description || '',
      taskImage: task.taskImage || '',
      estimatedPoints: task.estimatedPoints != null ? String(task.estimatedPoints) : '',
      assigneeId: task.assigneeId ? String(task.assigneeId) : '',
      reviewerId: task.reviewerId ? String(task.reviewerId) : '',
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handleSaveTask = async () => {
    if (!taskForm.taskName.trim()) {
      toast.error('Task name cannot be empty.');
      return;
    }
    if (!taskForm.estimatedPoints.toString().trim()) {
      toast.error('Estimated points are required.');
      return;
    }
    if (!taskForm.assigneeId || !taskForm.reviewerId) {
      toast.error('Please choose both an assignee and a reviewer.');
      return;
    }

    const payload = {
      taskName: taskForm.taskName,
      description: taskForm.description,
      taskImage: taskForm.taskImage,
      estimatedPoints: taskForm.estimatedPoints,
      assigneeId: Number(taskForm.assigneeId),
      reviewerId: Number(taskForm.reviewerId),
    };

    if (editingTaskId) {
      await runAction(() => studentAPI.updateProjectTask(editingTaskId, payload), { resetEditor: true });
      return;
    }

    if (!data?.canCreateTask || !data?.openSprint?.sprintId) {
      toast.error('Create an open sprint before adding a new task.');
      return;
    }

    await runAction(() => studentAPI.createProjectTask({
      ...payload,
      sprintId: data.openSprint.sprintId,
    }), { resetEditor: true });
  };

  const handleDeleteTask = async (task) => {
    if (!window.confirm(`Delete task "${task.taskName}"?`)) return;
    await runAction(() => studentAPI.deleteProjectTask(task.taskId), {
      resetEditor: editingTaskId === task.taskId,
    });
  };

  const handleCancelTask = async (task) => {
    const cancelReason = window.prompt(`Why should "${task.taskName}" be cancelled?`, '');
    if (cancelReason === null) return;
    await runAction(() => studentAPI.cancelProjectTask(task.taskId, cancelReason), {
      resetEditor: editingTaskId === task.taskId,
    });
  };

  const handleStartTask = async (task) => {
    await runAction(() => studentAPI.startProjectTask(task.taskId));
  };

  const handleSubmitInput = (taskId, field, value) => {
    setSubmitForms((current) => ({
      ...current,
      [taskId]: {
        ...(current[taskId] || {}),
        [field]: value,
      },
    }));
  };

  const handleSubmitFiles = (taskId, fileList) => {
    setSubmitForms((current) => ({
      ...current,
      [taskId]: {
        ...(current[taskId] || {}),
        files: Array.from(fileList || []),
      },
    }));
  };

  const handleSubmitTask = async (task) => {
    const form = submitForms[task.taskId] || {};
    const payload = new FormData();
    if ((form.submissionNote || '').trim()) payload.append('submissionNote', form.submissionNote);
    if ((form.submissionUrl || '').trim()) payload.append('submissionUrl', form.submissionUrl);
    if ((form.submissionCode || '').trim()) payload.append('submissionCode', form.submissionCode);
    if (form.clearFiles) payload.append('clearFiles', 'true');
    if (form.clearCode) payload.append('clearCode', 'true');
    for (const file of form.files || []) {
      payload.append('submissionFiles', file);
    }
    await runAction(() => studentAPI.submitProjectTask(task.taskId, payload));
  };

  const handleUnsubmitTask = async (task) => {
    await runAction(() => studentAPI.unsubmitProjectTask(task.taskId));
  };

  const handleReviewInput = (taskId, value) => {
    setReviewForms((current) => ({
      ...current,
      [taskId]: {
        ...(current[taskId] || {}),
        reviewComment: value,
      },
    }));
  };

  const handleReviewTask = async (task, action) => {
    const form = reviewForms[task.taskId] || {};
    await runAction(() => studentAPI.reviewProjectTask(task.taskId, {
      action,
      reviewComment: form.reviewComment || '',
    }));
  };

  const handleReplanInput = (taskId, field, value) => {
    setReplanForms((current) => ({
      ...current,
      [taskId]: {
        ...(current[taskId] || {}),
        [field]: value,
      },
    }));
  };

  const handleReplanTask = async (task) => {
    const form = replanForms[task.taskId] || {};
    if (!form.assigneeId || !form.reviewerId) {
      toast.error('Choose both assignee and reviewer before replanning.');
      return;
    }
    if (!data?.openSprint?.sprintId) {
      toast.error('There is no open sprint to receive failed tasks.');
      return;
    }

    await runAction(() => studentAPI.replanFailedTask(data.openSprint.sprintId, {
      taskId: task.taskId,
      assigneeId: Number(form.assigneeId),
      reviewerId: Number(form.reviewerId),
    }));
  };

  const handleFinalizeLinks = async () => {
    if (!finalLinks.sourceCodeUrl.trim() || !finalLinks.documentUrl.trim()) {
      toast.error('Both source code and document links are required.');
      return;
    }

    await runAction(() => studentAPI.finalizeProjectLinks(finalLinks));
  };

  if (loading) {
    return <div className="page-loader"><div className="spinner spinner-lg"></div><span>Loading task board...</span></div>;
  }

  if (!data || data.noGroup) {
    return (
      <div>
        <div className="page-header">
          <Link to="/student/project" className="btn btn-ghost btn-sm"><HiArrowLeft /> Back to Project</Link>
        </div>
        <div className="card"><div className="card-body">
          <div className="empty-state" style={{ padding: '48px 20px' }}>
            <div className="empty-state-title">Join a Group First</div>
            <p style={{ color: 'var(--text-secondary)', fontSize: 'var(--font-size-sm)' }}>You need to be in a group before using the task board.</p>
          </div>
        </div></div>
      </div>
    );
  }

  if (data.noProject) {
    return (
      <div>
        <div className="page-header">
          <Link to="/student/project" className="btn btn-ghost btn-sm"><HiArrowLeft /> Back to Project</Link>
        </div>
        <div className="card"><div className="card-body">
          <div className="empty-state" style={{ padding: '48px 20px' }}>
            <div className="empty-state-title">No Project Assigned</div>
            <p style={{ color: 'var(--text-secondary)', fontSize: 'var(--font-size-sm)' }}>Your group does not have a project yet.</p>
          </div>
        </div></div>
      </div>
    );
  }

  const project = data.project || {};
  const tasks = data.tasks || [];
  const sprints = data.sprints || [];
  const failedTasks = data.failedTasks || [];
  const members = data.members || [];
  const showTaskForm = Boolean(editingTaskId || data.canCreateTask);
  const showFinalizeCard = Boolean(data.canFinalizeLinks || project.sourceCodeUrl || project.documentUrl || data.withinFinalLinkGrace);

  return (
    <div>
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', gap: 16, alignItems: 'flex-start', flexWrap: 'wrap' }}>
        <div>
          <div style={{ marginBottom: 8, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <Link to="/student/project" className="btn btn-ghost btn-sm"><HiArrowLeft /> Back to Project</Link>
          </div>
          <h1 className="page-title">Task Board</h1>
          <p className="page-subtitle">{project.projectName || 'Project workspace'} - {data.group?.groupName || 'Unknown group'}</p>
        </div>
        <button className="btn btn-ghost btn-sm" onClick={() => load()} disabled={saving}><HiRefresh /> Refresh</button>
      </div>

      <div style={{ display: 'grid', gap: 12, marginBottom: 20 }}>
        {data.projectNotStarted && (
          <div className="card"><div className="card-body" style={{ color: 'var(--info)', fontWeight: 500 }}>The project has not started yet. Sprint planning is available, but work execution is still locked.</div></div>
        )}
        {data.projectLockedForWork && (
          <div className="card"><div className="card-body" style={{ color: 'var(--danger)', fontWeight: 500 }}>The project work period has ended. This page is now mostly read-only.</div></div>
        )}
        {data.projectChangeOpen && (
          <div className="card"><div className="card-body" style={{ color: 'var(--warning)', fontWeight: 500 }}>There is an open project change request. Planning and task actions are locked until that request is resolved.</div></div>
        )}
      </div>

      <div className="stats-grid" style={{ marginBottom: 24 }}>
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--primary-100)', color: 'var(--primary-600)' }}><HiCalendar /></div>
          <div><div className="stat-value">{sprints.length}</div><div className="stat-label">Sprints</div></div>
        </div>
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--success-light)', color: 'var(--success)' }}><HiCheckCircle /></div>
          <div><div className="stat-value">{tasks.filter((task) => task.status === 3).length}</div><div className="stat-label">Done</div></div>
        </div>
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--danger-light)', color: 'var(--danger)' }}><HiXCircle /></div>
          <div><div className="stat-value">{failedTasks.length}</div><div className="stat-label">Failed</div></div>
        </div>
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--warning-light)', color: 'var(--warning)' }}><HiClock /></div>
          <div><div className="stat-value">{data.openSprint?.displayName || 'None'}</div><div className="stat-label">Open Sprint</div></div>
        </div>
      </div>

      <div style={{ display: 'grid', gap: 16, marginBottom: 24 }}>
        {data.canCreateSprint && (
          <div className="card">
            <div className="card-header"><h3 className="section-title" style={{ margin: 0 }}>Create Sprint</h3></div>
            <div className="card-body" style={{ display: 'grid', gap: 12 }}>
              <div className="form-group">
                <label className="form-label">Sprint Name</label>
                <input
                  className="form-input"
                  value={newSprintName}
                  onChange={(e) => setNewSprintName(e.target.value)}
                  placeholder="Leave blank to auto-generate Sprint 1, Sprint 2, ..."
                />
              </div>
              <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                <button className="btn btn-primary btn-sm" onClick={handleCreateSprint} disabled={saving}>Create Sprint</button>
                <span style={{ color: 'var(--text-secondary)', fontSize: 'var(--font-size-xs)', alignSelf: 'center' }}>
                  New sprints follow the project timeline automatically.
                </span>
              </div>
            </div>
          </div>
        )}

        {showTaskForm && (
          <div className="card">
            <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
              <h3 className="section-title" style={{ margin: 0 }}>
                {editingTaskId ? 'Edit Task' : `Create Task${data.openSprint ? ` in ${data.openSprint.displayName}` : ''}`}
              </h3>
              {editingTaskId && <button className="btn btn-ghost btn-sm" onClick={resetTaskEditor} disabled={saving}>Cancel Edit</button>}
            </div>
            <div className="card-body" style={{ display: 'grid', gap: 12 }}>
              {!editingTaskId && !data.canCreateTask && (
                <div style={{ color: 'var(--text-secondary)', fontSize: 'var(--font-size-sm)' }}>
                  Create an open sprint first to add new tasks.
                </div>
              )}
              <div className="form-group">
                <label className="form-label">Task Name</label>
                <input className="form-input" value={taskForm.taskName} onChange={(e) => handleTaskInput('taskName', e.target.value)} />
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
                <div className="form-group">
                  <label className="form-label">Estimated Points</label>
                  <input className="form-input" value={taskForm.estimatedPoints} onChange={(e) => handleTaskInput('estimatedPoints', e.target.value)} placeholder="Example: 2.5" />
                </div>
                <div className="form-group">
                  <label className="form-label">Assignee</label>
                  <select className="form-input" value={taskForm.assigneeId} onChange={(e) => handleTaskInput('assigneeId', e.target.value)}>
                    <option value="">Choose assignee</option>
                    {members.map((member) => (
                      <option key={member.studentId} value={member.studentId}>{member.fullName}</option>
                    ))}
                  </select>
                </div>
                <div className="form-group">
                  <label className="form-label">Reviewer</label>
                  <select className="form-input" value={taskForm.reviewerId} onChange={(e) => handleTaskInput('reviewerId', e.target.value)}>
                    <option value="">Choose reviewer</option>
                    {members.map((member) => (
                      <option key={member.studentId} value={member.studentId}>{member.fullName}</option>
                    ))}
                  </select>
                </div>
              </div>
              <div className="form-group">
                <label className="form-label">Description</label>
                <textarea className="form-input" rows="4" value={taskForm.description} onChange={(e) => handleTaskInput('description', e.target.value)} />
              </div>
              <div className="form-group">
                <label className="form-label">Task Image URL</label>
                <input className="form-input" value={taskForm.taskImage} onChange={(e) => handleTaskInput('taskImage', e.target.value)} placeholder="Optional image URL" />
              </div>
              <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                <button className="btn btn-primary btn-sm" onClick={handleSaveTask} disabled={saving || (!editingTaskId && !data.canCreateTask)}>
                  {editingTaskId ? 'Save Task' : 'Create Task'}
                </button>
                <button className="btn btn-ghost btn-sm" onClick={resetTaskEditor} disabled={saving}>Reset</button>
              </div>
            </div>
          </div>
        )}

        {showFinalizeCard && (
          <div className="card">
            <div className="card-header"><h3 className="section-title" style={{ margin: 0 }}>Final Delivery Links</h3></div>
            <div className="card-body" style={{ display: 'grid', gap: 12 }}>
              <div className="form-group">
                <label className="form-label">Source Code URL</label>
                <input
                  className="form-input"
                  value={finalLinks.sourceCodeUrl}
                  onChange={(e) => setFinalLinks((current) => ({ ...current, sourceCodeUrl: e.target.value }))}
                  disabled={!data.canFinalizeLinks}
                />
              </div>
              <div className="form-group">
                <label className="form-label">Document URL</label>
                <input
                  className="form-input"
                  value={finalLinks.documentUrl}
                  onChange={(e) => setFinalLinks((current) => ({ ...current, documentUrl: e.target.value }))}
                  disabled={!data.canFinalizeLinks}
                />
              </div>
              <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center' }}>
                {data.canFinalizeLinks && (
                  <button className="btn btn-primary btn-sm" onClick={handleFinalizeLinks} disabled={saving}>Save Final Links</button>
                )}
                <span style={{ color: 'var(--text-secondary)', fontSize: 'var(--font-size-xs)' }}>
                  {data.canFinalizeLinks
                    ? 'You are inside the 1-day grace window after project end.'
                    : 'Links can only be edited by the leader during the final grace window.'}
                </span>
              </div>
            </div>
          </div>
        )}
      </div>

      <div className="card" style={{ marginBottom: 24 }}>
        <div className="card-header"><h3 className="section-title" style={{ margin: 0 }}>Filters</h3></div>
        <div className="card-body" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12 }}>
          <div className="form-group">
            <label className="form-label">Status</label>
            <select className="form-input" value={filters.status} onChange={(e) => setFilters((current) => ({ ...current, status: e.target.value }))}>
              <option value="">All statuses</option>
              {(data.statusOptions || []).map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
          </div>
          <div className="form-group">
            <label className="form-label">Assignee</label>
            <select className="form-input" value={filters.assigneeId} onChange={(e) => setFilters((current) => ({ ...current, assigneeId: e.target.value }))}>
              <option value="">All members</option>
              {members.map((member) => (
                <option key={member.studentId} value={member.studentId}>{member.fullName} {member.studentCode ? `(${member.studentCode})` : ''}</option>
              ))}
            </select>
          </div>
          <div style={{ display: 'flex', gap: 10, alignItems: 'flex-end', flexWrap: 'wrap' }}>
            <button className="btn btn-primary btn-sm" onClick={() => load(filters)} disabled={saving}><HiFilter /> Apply</button>
            <button
              className="btn btn-ghost btn-sm"
              onClick={() => {
                const next = { status: '', assigneeId: '' };
                setFilters(next);
                load(next);
              }}
              disabled={saving}
            >
              Clear
            </button>
          </div>
        </div>
      </div>

      <div className="content-grid" style={{ alignItems: 'start' }}>
        <div style={{ display: 'grid', gap: 16 }}>
          <div className="card">
            <div className="card-header"><h3 className="section-title" style={{ margin: 0 }}>Task List</h3></div>
            <div className="card-body" style={{ display: 'grid', gap: 12 }}>
              {tasks.length === 0 ? (
                <div className="empty-state" style={{ padding: '36px 20px' }}>
                  <div className="empty-state-title">No Tasks Match the Filters</div>
                </div>
              ) : tasks.map((task) => {
                const submitForm = submitForms[task.taskId] || {
                  submissionNote: '',
                  submissionUrl: '',
                  submissionCode: '',
                  clearFiles: false,
                  clearCode: false,
                  files: [],
                };
                const reviewForm = reviewForms[task.taskId] || { reviewComment: '' };
                return (
                <div
                  key={task.taskId}
                  style={{
                    border: editingTaskId === task.taskId ? '1px solid var(--primary-500)' : '1px solid var(--border-color)',
                    borderRadius: 'var(--radius-md)',
                    padding: 14,
                    background: editingTaskId === task.taskId ? 'var(--primary-50)' : 'var(--gray-50)',
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'flex-start', flexWrap: 'wrap' }}>
                    <div>
                      <div style={{ fontWeight: 700 }}>{task.taskName}</div>
                      <div style={{ marginTop: 4, color: 'var(--text-secondary)', fontSize: 'var(--font-size-xs)' }}>
                        {task.sprintName || 'Unknown sprint'} - {task.estimatedPoints || 0} pts
                      </div>
                    </div>
                    <StatusBadge status={task.status} label={task.statusLabel || 'Unknown'} />
                  </div>

                  <div style={{ marginTop: 10, color: 'var(--text-secondary)', fontSize: 'var(--font-size-sm)', display: 'grid', gap: 4 }}>
                    <div>Assignee: <strong>{task.assigneeName || '-'}</strong> {task.assigneeCode ? `(${task.assigneeCode})` : ''}</div>
                    <div>Reviewer: <strong>{task.reviewerName || 'Unassigned'}</strong></div>
                    <div>Started: {formatDateTime(task.actualStartTime)} - Expected end: {formatDateTime(task.expectedEndTime)}</div>
                  </div>

                  {task.description && (
                    <div style={{ marginTop: 10, whiteSpace: 'pre-wrap' }}>{task.description}</div>
                  )}

                  {(task.canStart || task.canEdit || task.canDelete || task.canCancel || task.canUnsubmit) && (
                    <div style={{ marginTop: 12, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                      {task.canStart && <button className="btn btn-primary btn-sm" onClick={() => handleStartTask(task)} disabled={saving}>Start Task</button>}
                      {task.canEdit && <button className="btn btn-ghost btn-sm" onClick={() => handleEditTask(task)} disabled={saving}>Edit</button>}
                      {task.canDelete && <button className="btn btn-ghost btn-sm" onClick={() => handleDeleteTask(task)} disabled={saving}>Delete</button>}
                      {task.canCancel && <button className="btn btn-ghost btn-sm" onClick={() => handleCancelTask(task)} disabled={saving}>Cancel</button>}
                      {task.canUnsubmit && <button className="btn btn-ghost btn-sm" onClick={() => handleUnsubmitTask(task)} disabled={saving}>Cancel Submission</button>}
                    </div>
                  )}

                  {task.canSubmit && (
                    <div style={{ marginTop: 12, padding: 12, borderRadius: 'var(--radius-md)', border: '1px solid var(--border-color)', background: 'white', display: 'grid', gap: 10 }}>
                      <div style={{ fontWeight: 600 }}>Submit Work</div>
                      <div className="form-group" style={{ marginBottom: 0 }}>
                        <label className="form-label">Submission Note</label>
                        <textarea
                          className="form-input"
                          rows="3"
                          value={submitForm.submissionNote}
                          onChange={(e) => handleSubmitInput(task.taskId, 'submissionNote', e.target.value)}
                        />
                      </div>
                      <div className="form-group" style={{ marginBottom: 0 }}>
                        <label className="form-label">Reference URL</label>
                        <input
                          className="form-input"
                          value={submitForm.submissionUrl}
                          onChange={(e) => handleSubmitInput(task.taskId, 'submissionUrl', e.target.value)}
                          placeholder="Optional repository, demo, or document link"
                        />
                      </div>
                      <div className="form-group" style={{ marginBottom: 0 }}>
                        <label className="form-label">Inline Code</label>
                        <textarea
                          className="form-input"
                          rows="6"
                          value={submitForm.submissionCode}
                          onChange={(e) => handleSubmitInput(task.taskId, 'submissionCode', e.target.value)}
                          placeholder="Paste a snippet if needed"
                        />
                      </div>
                      <div className="form-group" style={{ marginBottom: 0 }}>
                        <label className="form-label">Attachments</label>
                        <input className="form-input" type="file" multiple onChange={(e) => handleSubmitFiles(task.taskId, e.target.files)} />
                        {(submitForm.files || []).length > 0 && (
                          <div style={{ marginTop: 6, color: 'var(--text-secondary)', fontSize: 'var(--font-size-xs)' }}>
                            {(submitForm.files || []).map((file) => file.name).join(', ')}
                          </div>
                        )}
                      </div>
                      <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
                        {(task.attachments || []).length > 0 && (
                          <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 'var(--font-size-xs)' }}>
                            <input
                              type="checkbox"
                              checked={Boolean(submitForm.clearFiles)}
                              onChange={(e) => handleSubmitInput(task.taskId, 'clearFiles', e.target.checked)}
                            />
                            Replace or clear existing files
                          </label>
                        )}
                        {task.submissionCode && (
                          <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 'var(--font-size-xs)' }}>
                            <input
                              type="checkbox"
                              checked={Boolean(submitForm.clearCode)}
                              onChange={(e) => handleSubmitInput(task.taskId, 'clearCode', e.target.checked)}
                            />
                            Clear previous inline code
                          </label>
                        )}
                      </div>
                      <div>
                        <button className="btn btn-primary btn-sm" onClick={() => handleSubmitTask(task)} disabled={saving}>Submit for Review</button>
                      </div>
                    </div>
                  )}

                  {task.submissionNote && (
                    <div style={{ marginTop: 12, padding: 12, borderRadius: 'var(--radius-md)', border: '1px solid var(--border-color)', background: 'white' }}>
                      <div style={{ fontWeight: 600, marginBottom: 4 }}>Submission Note</div>
                      <div style={{ whiteSpace: 'pre-wrap' }}>{task.submissionNote}</div>
                    </div>
                  )}

                  {(task.submissionUrl || (task.attachments || []).length > 0 || task.hasCode) && (
                    <div style={{ marginTop: 12, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                      {task.submissionUrl && <a href={task.submissionUrl} target="_blank" rel="noreferrer" className="btn btn-ghost btn-sm"><HiExternalLink /> Submission Link</a>}
                      {(task.attachments || []).map((attachment) => (
                        <span key={attachment.storedName} style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                          <a href={attachment.downloadUrl} className="btn btn-ghost btn-sm"><HiDownload /> {attachment.displayName}</a>
                          {attachment.codeViewUrl && <Link to={attachment.codeViewUrl} className="btn btn-ghost btn-sm"><HiDocumentText /> Preview</Link>}
                        </span>
                      ))}
                      {task.hasCode && task.codeViewUrl && <Link to={task.codeViewUrl} className="btn btn-ghost btn-sm"><HiDocumentText /> Code View</Link>}
                    </div>
                  )}

                  {task.reviewComment && (
                    <div style={{ marginTop: 12, padding: 12, borderRadius: 'var(--radius-md)', border: '1px solid var(--border-color)', background: 'white' }}>
                      <div style={{ fontWeight: 600, marginBottom: 4 }}>Reviewer Comment</div>
                      <div style={{ whiteSpace: 'pre-wrap' }}>{task.reviewComment}</div>
                    </div>
                  )}

                  {task.canReview && (
                    <div style={{ marginTop: 12, padding: 12, borderRadius: 'var(--radius-md)', border: '1px solid var(--border-color)', background: 'white', display: 'grid', gap: 10 }}>
                      <div style={{ fontWeight: 600 }}>Review Submission</div>
                      <div className="form-group" style={{ marginBottom: 0 }}>
                        <label className="form-label">Review Comment</label>
                        <textarea
                          className="form-input"
                          rows="3"
                          value={reviewForm.reviewComment}
                          onChange={(e) => handleReviewInput(task.taskId, e.target.value)}
                          placeholder="Required when returning the task"
                        />
                      </div>
                      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                        <button className="btn btn-primary btn-sm" onClick={() => handleReviewTask(task, 'approve')} disabled={saving}>Approve</button>
                        <button className="btn btn-ghost btn-sm" onClick={() => handleReviewTask(task, 'reject')} disabled={saving}>Return for Fixes</button>
                      </div>
                    </div>
                  )}
                </div>
              );
              })}
            </div>
          </div>
        </div>

        <div style={{ display: 'grid', gap: 16 }}>
          <div className="card">
            <div className="card-header"><h3 className="section-title" style={{ margin: 0 }}>Sprints</h3></div>
            <div className="card-body" style={{ display: 'grid', gap: 10 }}>
              {sprints.length === 0 ? (
                <div style={{ color: 'var(--text-secondary)', fontSize: 'var(--font-size-sm)' }}>No sprints yet.</div>
              ) : sprints.map((sprint) => (
                <div key={sprint.sprintId} style={{ border: '1px solid var(--border-color)', borderRadius: 'var(--radius-md)', padding: 12 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, flexWrap: 'wrap' }}>
                    <div style={{ fontWeight: 600 }}>{sprint.displayName}</div>
                    <StatusBadge
                      status={sprint.cancelled ? 6 : sprint.closed ? 2 : 1}
                      label={sprint.cancelled ? 'Cancelled' : sprint.closed ? 'Closed' : 'Open'}
                    />
                  </div>
                  <div style={{ marginTop: 6, color: 'var(--text-secondary)', fontSize: 'var(--font-size-xs)' }}>
                    {formatDate(sprint.startDate)} to {formatDate(sprint.endDate)} - {sprint.taskCount || 0} tasks
                  </div>
                  {sprint.cancelReason && <div style={{ marginTop: 8, color: 'var(--danger)', fontSize: 'var(--font-size-sm)' }}>{sprint.cancelReason}</div>}
                  {(sprint.canRename || sprint.canDelete || sprint.canCancel) && (
                    <div style={{ marginTop: 10, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                      {sprint.canRename && <button className="btn btn-ghost btn-sm" onClick={() => handleRenameSprint(sprint)} disabled={saving}>Rename</button>}
                      {sprint.canDelete && <button className="btn btn-ghost btn-sm" onClick={() => handleDeleteSprint(sprint)} disabled={saving}>Delete</button>}
                      {sprint.canCancel && <button className="btn btn-ghost btn-sm" onClick={() => handleCancelSprint(sprint)} disabled={saving}>Cancel Sprint</button>}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>

          <div className="card">
            <div className="card-header"><h3 className="section-title" style={{ margin: 0 }}>Failed Tasks</h3></div>
            <div className="card-body" style={{ display: 'grid', gap: 10 }}>
              {failedTasks.length === 0 ? (
                <div style={{ color: 'var(--text-secondary)', fontSize: 'var(--font-size-sm)' }}>No failed tasks.</div>
              ) : failedTasks.map((task) => {
                const replanForm = replanForms[task.taskId] || { assigneeId: '', reviewerId: '' };
                return (
                  <div key={task.taskId} style={{ border: '1px solid var(--border-color)', borderRadius: 'var(--radius-md)', padding: 12, background: 'var(--danger-light)' }}>
                    <div style={{ fontWeight: 600 }}>{task.taskName}</div>
                    <div style={{ marginTop: 4, color: 'var(--text-secondary)', fontSize: 'var(--font-size-xs)' }}>
                      {task.assigneeName || '-'} / {task.reviewerName || 'No reviewer'}
                    </div>
                    <div style={{ marginTop: 8, color: 'var(--danger)', fontSize: 'var(--font-size-sm)' }}>
                      {task.reviewComment || 'Task was not completed before the sprint ended.'}
                    </div>

                    {task.canReplan && data.openSprint && (
                      <div style={{ marginTop: 12, display: 'grid', gap: 10 }}>
                        <div style={{ color: 'var(--text-secondary)', fontSize: 'var(--font-size-xs)' }}>
                          Move this task into {data.openSprint.displayName}.
                        </div>
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 10 }}>
                          <select className="form-input" value={replanForm.assigneeId} onChange={(e) => handleReplanInput(task.taskId, 'assigneeId', e.target.value)}>
                            <option value="">Choose assignee</option>
                            {members.map((member) => (
                              <option key={member.studentId} value={member.studentId}>{member.fullName}</option>
                            ))}
                          </select>
                          <select className="form-input" value={replanForm.reviewerId} onChange={(e) => handleReplanInput(task.taskId, 'reviewerId', e.target.value)}>
                            <option value="">Choose reviewer</option>
                            {members.map((member) => (
                              <option key={member.studentId} value={member.studentId}>{member.fullName}</option>
                            ))}
                          </select>
                        </div>
                        <div>
                          <button className="btn btn-primary btn-sm" onClick={() => handleReplanTask(task)} disabled={saving}>Replan to Open Sprint</button>
                        </div>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

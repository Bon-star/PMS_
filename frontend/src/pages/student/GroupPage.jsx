import { useState, useEffect } from 'react';
import { studentAPI } from '../../api';
import { useToast } from '../../components/Toast';
import { useAuth } from '../../contexts/AuthContext';
import { HiUserGroup, HiPlus, HiUserAdd, HiStar, HiTrash, HiLogout, HiRefresh, HiPencil, HiX, HiCheck, HiSwitchHorizontal } from 'react-icons/hi';
import '../../layouts/DashboardLayout.css';

export default function GroupPage() {
  const { user } = useAuth();
  const toast = useToast();
  const [groups, setGroups] = useState(null);
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [newGroupName, setNewGroupName] = useState('');
  const [creating, setCreating] = useState(false);
  const [inviteRef, setInviteRef] = useState('');
  const [inviting, setInviting] = useState(false);
  const [editingName, setEditingName] = useState(false);
  const [editGroupName, setEditGroupName] = useState('');
  const [transferTarget, setTransferTarget] = useState(null);

  const [invitableStudents, setInvitableStudents] = useState([]);
  const [invitablePage, setInvitablePage] = useState(1);
  const invitablePageSize = 5;

  useEffect(() => { loadGroups(); }, []);

  useEffect(() => {
    if (groups?.myGroup && !groups.myGroup.groupHasProject && !groups.myGroup.membershipLocked && groups.myGroup.memberCount < 6) {
      loadInvitableStudents(groups.myGroup.groupId);
    }
  }, [groups]);

  const loadInvitableStudents = async (groupId) => {
    try {
      const res = await studentAPI.getInvitableStudents(groupId);
      if (res.data.success) {
        setInvitableStudents(res.data.students || []);
        setInvitablePage(1);
      }
    } catch {
      setInvitableStudents([]);
    }
  };

  const loadGroups = async () => {
    setLoading(true);
    try {
      const res = await studentAPI.getGroups();
      setGroups(res.data);
    } catch { setGroups(null); }
    finally { setLoading(false); }
  };

  const handleCreateGroup = async (e) => {
    e.preventDefault();
    if (!newGroupName.trim()) { toast.error('Please enter a group name.'); return; }
    setCreating(true);
    try {
      const res = await studentAPI.createGroup({ groupName: newGroupName.trim() });
      if (res.data.success) { toast.success(res.data.message); setShowCreate(false); setNewGroupName(''); loadGroups(); }
      else toast.error(res.data.message);
    } catch (err) { toast.error(err.response?.data?.message || 'Failed.'); }
    finally { setCreating(false); }
  };

  const handleRename = async (groupId) => {
    if (!editGroupName.trim()) { toast.error('Group name cannot be empty.'); return; }
    try {
      const res = await studentAPI.renameGroup(groupId, editGroupName.trim());
      if (res.data.success) { toast.success(res.data.message); setEditingName(false); loadGroups(); }
      else toast.error(res.data.message);
    } catch (err) { toast.error(err.response?.data?.message || 'Failed.'); }
  };

  const handleInvite = async (groupId) => {
    if (!inviteRef.trim()) { toast.error('Enter student email, code, or ID'); return; }
    setInviting(true);
    try {
      const res = await studentAPI.inviteMember(groupId, inviteRef.trim());
      if (res.data.success) {
        toast.success(res.data.message);
        setInviteRef('');
        loadInvitableStudents(groupId);
      }
      else toast.error(res.data.message);
    } catch (err) { toast.error(err.response?.data?.message || 'Failed.'); }
    finally { setInviting(false); }
  };

  const handleKick = async (groupId, studentId, studentName) => {
    if (!confirm(`Remove "${studentName}" from the group?`)) return;
    try {
      const res = await studentAPI.kickMember(groupId, studentId);
      if (res.data.success) { toast.success(res.data.message); loadGroups(); }
      else toast.error(res.data.message);
    } catch (err) { toast.error(err.response?.data?.message || 'Failed.'); }
  };

  const handleTransferLeader = async (groupId, newLeaderId, name) => {
    if (!confirm(`Transfer leadership to "${name}"? You will no longer be the leader.`)) return;
    try {
      const res = await studentAPI.transferLeader(groupId, newLeaderId);
      if (res.data.success) { toast.success(res.data.message); setTransferTarget(null); loadGroups(); }
      else toast.error(res.data.message);
    } catch (err) { toast.error(err.response?.data?.message || 'Failed.'); }
  };

  const handleLeave = async (groupId) => {
    if (!confirm('Are you sure you want to leave this group?')) return;
    try {
      const res = await studentAPI.leaveGroup(groupId);
      if (res.data.success) { toast.success(res.data.message); loadGroups(); }
      else toast.error(res.data.message);
    } catch (err) { toast.error(err.response?.data?.message || 'Failed.'); }
  };

  const handleDelete = async (groupId) => {
    if (!confirm('Delete this group? This action cannot be undone!')) return;
    try {
      const res = await studentAPI.deleteGroup(groupId);
      if (res.data.success) { toast.success(res.data.message); loadGroups(); }
      else toast.error(res.data.message);
    } catch (err) { toast.error(err.response?.data?.message || 'Failed.'); }
  };

  const handleJoinRequest = async (groupId) => {
    try {
      const res = await studentAPI.joinRequest(groupId);
      if (res.data.success) { toast.success(res.data.message); loadGroups(); }
      else toast.error(res.data.message);
    } catch (err) { toast.error(err.response?.data?.message || 'Failed.'); }
  };

  const handleReviewInvite = async (groupId, invId, action) => {
    try {
      const res = await studentAPI.reviewInvite(groupId, invId, action);
      if (res.data.success) { toast.success(res.data.message); loadGroups(); }
      else toast.error(res.data.message);
    } catch (err) { toast.error(err.response?.data?.message || 'Failed.'); }
  };

  if (loading) return <div className="page-loader"><div className="spinner spinner-lg"></div><span>Loading groups...</span></div>;

  const myGroup = groups?.myGroup;
  const availableGroups = groups?.availableGroups || [];

  return (
    <div>
      <div className="page-header" style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: 16 }}>
        <div>
          <h1 className="page-title">My Group</h1>
        </div>
        <div style={{ display: 'flex', gap: 10 }}>
          <button className="btn btn-ghost btn-sm" onClick={loadGroups}><HiRefresh /> Refresh</button>
          {!myGroup && <button className="btn btn-primary btn-sm" onClick={() => setShowCreate(true)} id="btn-create-group"><HiPlus /> Create Group</button>}
        </div>
      </div>

      {/* Create Group Modal */}
      {showCreate && (
        <div className="modal-backdrop" onClick={() => setShowCreate(false)}>
          <div className="modal-content" onClick={e => e.stopPropagation()} id="modal-create-group">
            <div className="modal-header">
              <h3 style={{ fontWeight: 600 }}>Create New Group</h3>
              <button className="btn btn-ghost btn-icon" onClick={() => setShowCreate(false)}>✕</button>
            </div>
            <form onSubmit={handleCreateGroup}>
              <div className="modal-body">
                <div className="form-group">
                  <label className="form-label">Group Name</label>
                  <input className="form-input" placeholder="Enter group name" value={newGroupName} onChange={e => setNewGroupName(e.target.value)} autoFocus />
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary btn-sm" onClick={() => setShowCreate(false)}>Cancel</button>
                <button type="submit" className="btn btn-primary btn-sm" disabled={creating}>{creating ? 'Creating...' : 'Create Group'}</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {myGroup ? (
        <div className="card" style={{ marginBottom: 24 }}>
          <div className="card-header">
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, flex: 1 }}>
              <div className="avatar" style={{ background: 'linear-gradient(135deg, var(--primary-400), var(--primary-700))' }}>
                <HiUserGroup />
              </div>
              <div style={{ flex: 1 }}>
                {editingName ? (
                  <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                    <input className="form-input" style={{ width: 200, fontSize: 'var(--font-size-md)', padding: '4px 8px' }}
                      value={editGroupName} onChange={e => setEditGroupName(e.target.value)} autoFocus
                      onKeyDown={e => { if (e.key === 'Enter') handleRename(myGroup.groupId); if (e.key === 'Escape') setEditingName(false); }} />
                    <button className="btn btn-primary btn-icon btn-sm" onClick={() => handleRename(myGroup.groupId)} title="Save"><HiCheck /></button>
                    <button className="btn btn-ghost btn-icon btn-sm" onClick={() => setEditingName(false)} title="Cancel"><HiX /></button>
                  </div>
                ) : (
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <h3 style={{ fontWeight: 700, fontSize: 'var(--font-size-lg)' }}>{myGroup.groupName}</h3>
                    {myGroup.isLeader && (
                      <button className="btn btn-ghost btn-icon btn-sm" onClick={() => { setEditGroupName(myGroup.groupName); setEditingName(true); }} title="Rename group"><HiPencil /></button>
                    )}
                  </div>
                )}
                <span className="badge badge-primary" style={{ marginTop: 4 }}>{myGroup.memberCount || 0} / 6 members</span>
                {myGroup.groupHasProject && <span className="badge badge-success" style={{ marginTop: 4, marginLeft: 6 }}>Has Project</span>}
              </div>
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              {myGroup.canDeleteGroup && <button className="btn btn-danger btn-sm" onClick={() => handleDelete(myGroup.groupId)}><HiTrash /> Delete Group</button>}
              {!myGroup.isLeader && !myGroup.membershipLocked && <button className="btn btn-secondary btn-sm" onClick={() => handleLeave(myGroup.groupId)}><HiLogout /> Leave</button>}
            </div>
          </div>
          <div className="card-body">
            {/* Members list */}
            <h4 style={{ fontWeight: 600, marginBottom: 12 }}>Members</h4>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 20 }}>
              {(myGroup.members || []).map(m => (
                <div key={m.studentId} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '10px 14px', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-color)' }}>
                  <div className="avatar avatar-sm">{(m.fullName || '?')[0]}</div>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontWeight: 500, fontSize: 'var(--font-size-sm)' }}>{m.fullName}</div>
                    <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--text-muted)' }}>{m.studentCode} · {m.schoolEmail}</div>
                  </div>
                  {m.isLeader && <span className="badge badge-warning"><HiStar style={{ marginRight: 4 }} />Leader</span>}

                  {/* Actions for leader on non-leader members */}
                  {myGroup.isLeader && !m.isLeader && !myGroup.membershipLocked && (
                    <div style={{ display: 'flex', gap: 4 }}>
                      <button className="btn btn-ghost btn-sm" title="Transfer leadership" onClick={() => handleTransferLeader(myGroup.groupId, m.studentId, m.fullName)}>
                        <HiSwitchHorizontal />
                      </button>
                      {!myGroup.groupHasProject && (
                        <button className="btn btn-ghost btn-sm" style={{ color: 'var(--danger-500)' }} title="Remove member" onClick={() => handleKick(myGroup.groupId, m.studentId, m.fullName)}>
                          <HiX />
                        </button>
                      )}
                    </div>
                  )}
                </div>
              ))}
            </div>

            {/* Pending join requests for leader */}
            {myGroup.isLeader && myGroup.pendingInvitations && myGroup.pendingInvitations.length > 0 && (
              <div style={{ marginBottom: 20 }}>
                <h4 style={{ fontWeight: 600, marginBottom: 8 }}>Pending Join Requests</h4>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  {myGroup.pendingInvitations.map(inv => (
                    <div key={inv.invitationId} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '10px 14px', borderRadius: 'var(--radius-md)', border: '1px solid var(--warning-200)', background: 'var(--warning-50, #fff7ed)' }}>
                      <div className="avatar avatar-sm">{(inv.studentName || '?')[0]}</div>
                      <div style={{ flex: 1 }}>
                        <div style={{ fontWeight: 500, fontSize: 'var(--font-size-sm)' }}>{inv.studentName}</div>
                        <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--text-muted)' }}>{inv.studentCode}</div>
                      </div>
                      <button className="btn btn-primary btn-sm" onClick={() => handleReviewInvite(myGroup.groupId, inv.invitationId, 'approve')}><HiCheck /> Approve</button>
                      <button className="btn btn-secondary btn-sm" onClick={() => handleReviewInvite(myGroup.groupId, inv.invitationId, 'reject')}><HiX /> Reject</button>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Invite member - available to all members (non-locked, no project, not full) */}
            {!myGroup.groupHasProject && !myGroup.membershipLocked && myGroup.memberCount < 6 && (
              <div style={{ marginBottom: 20 }}>
                <h4 style={{ fontWeight: 600, marginBottom: 8 }}>Invite Member</h4>
                <div style={{ display: 'flex', gap: 8 }}>
                  <input className="form-input" style={{ flex: 1 }} placeholder="Student code, email, or ID" value={inviteRef} onChange={e => setInviteRef(e.target.value)}
                    onKeyDown={e => { if (e.key === 'Enter') handleInvite(myGroup.groupId); }} />
                  <button className="btn btn-primary btn-sm" onClick={() => handleInvite(myGroup.groupId)} disabled={inviting}>
                    <HiUserAdd /> {inviting ? 'Sending...' : 'Invite'}
                  </button>
                </div>
              </div>
            )}

            {/* List of Invitable Students */}
            {!myGroup.groupHasProject && !myGroup.membershipLocked && myGroup.memberCount < 6 && invitableStudents.length > 0 && (
              <div style={{ marginBottom: 20 }}>
                <h4 style={{ fontWeight: 600, marginBottom: 12, fontSize: '13px', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Suggested Students (Without Group)</h4>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  {invitableStudents.slice((invitablePage - 1) * invitablePageSize, invitablePage * invitablePageSize).map(s => (
                    <div key={s.studentId} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '8px 12px', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-color)', background: '#fafafa' }}>
                      <div className="avatar avatar-sm" style={{ width: 32, height: 32 }}>{(s.fullName || '?')[0]}</div>
                      <div style={{ flex: 1 }}>
                        <div style={{ fontWeight: 600, fontSize: 'var(--font-size-sm)' }}>{s.fullName}</div>
                        <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--text-muted)' }}>{s.studentCode} · Classmate</div>
                      </div>
                      <button className="btn btn-outline btn-sm" onClick={() => {
                        setInviteRef(s.studentCode);
                        // Note: setting state is async, so we directly pass it to avoid race condition 
                        // Or we can just call studentAPI.inviteMember directly if we want
                        studentAPI.inviteMember(myGroup.groupId, s.studentCode).then(res => {
                          if (res.data.success) {
                            toast.success('Invitation sent to ' + s.fullName);
                            setInviteRef('');
                            loadInvitableStudents(myGroup.groupId);
                          } else { toast.error(res.data.message); }
                        }).catch(err => toast.error(err.response?.data?.message || 'Failed.'));
                      }} disabled={inviting}>
                        <HiUserAdd /> Invite
                      </button>
                    </div>
                  ))}
                </div>
                {/* Pagination Controls */}
                {invitableStudents.length > invitablePageSize && (
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 12 }}>
                    <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                      Showing {(invitablePage - 1) * invitablePageSize + 1} - {Math.min(invitablePage * invitablePageSize, invitableStudents.length)} of {invitableStudents.length}
                    </span>
                    <div style={{ display: 'flex', gap: 4 }}>
                      <button className="btn btn-ghost btn-sm" disabled={invitablePage === 1} onClick={() => setInvitablePage(p => p - 1)}>Prev</button>
                      <button className="btn btn-ghost btn-sm" disabled={invitablePage * invitablePageSize >= invitableStudents.length} onClick={() => setInvitablePage(p => p + 1)}>Next</button>
                    </div>
                  </div>
                )}
              </div>
            )}

            {myGroup.membershipLocked && (
              <div className="alert" style={{ marginTop: 12, padding: '10px 14px', borderRadius: 'var(--radius-md)', background: 'var(--warning-50, #fff7ed)', border: '1px solid var(--warning-200)', fontSize: 'var(--font-size-sm)', color: 'var(--warning-700, #9a3412)' }}>
                ⚠️ The project has started. Group membership is locked.
              </div>
            )}
          </div>
        </div>
      ) : null}

      {!myGroup && availableGroups.length > 0 && (
        <div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: 16 }}>
            {availableGroups.map(g => (
              <div key={g.groupId} className="card">
                <div className="card-body" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <div>
                    <div style={{ fontWeight: 600, fontSize: 'var(--font-size-md)' }}>{g.groupName}</div>
                    <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--text-muted)', marginTop: 4 }}>
                      {g.memberCount}/6 members · Leader: {g.leaderName}
                    </div>
                    {g.hasProject && <span className="badge badge-success" style={{ marginTop: 6 }}>Has Project</span>}
                  </div>
                  {g.hasPendingRequest ? (
                    <span className="badge badge-warning">Request Pending</span>
                  ) : g.hasProject || g.memberCount >= 6 ? (
                    <span className="badge badge-secondary">Full / Has Project</span>
                  ) : (
                    <button className="btn btn-primary btn-sm" onClick={() => handleJoinRequest(g.groupId)}>
                      Request Join
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {!myGroup && availableGroups.length === 0 && (
        <div className="card">
          <div className="card-body" style={{ textAlign: 'center', padding: '20px', color: 'var(--text-muted)', fontSize: 'var(--font-size-sm)' }}>
            No available groups in your class. Create a group to get started.
          </div>
        </div>
      )}
    </div>
  );
}

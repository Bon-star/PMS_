import { useState, useEffect } from 'react';
import { studentAPI } from '../../api';
import { useToast } from '../../components/Toast';
import { HiBell, HiCheckCircle, HiExclamation, HiInformationCircle, HiCheck, HiX, HiUserGroup } from 'react-icons/hi';
import '../../layouts/DashboardLayout.css';

export default function NotificationsPage() {
  const [notifications, setNotifications] = useState([]);
  const [invitations, setInvitations] = useState([]);
  const [loading, setLoading] = useState(true);
  const toast = useToast();

  useEffect(() => {
    loadNotifications();
  }, []);

  const loadNotifications = async () => {
    try {
      const res = await studentAPI.getNotifications(1);
      setNotifications(res.data?.notifications || []);
      setInvitations(res.data?.invitations || []);
    } catch {
      setNotifications([]);
      setInvitations([]);
    }
    finally { setLoading(false); }
  };

  const handleRespond = async (groupId, invId, action) => {
    try {
      const res = await studentAPI.respondInvite(groupId, invId, action);
      if (res.data.success) {
        toast.success(res.data.message);
        loadNotifications(); // Reload to remove the processed invitation
      } else {
        toast.error(res.data.message);
      }
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to respond to invitation.');
    }
  };

  const handleReview = async (groupId, invId, action) => {
    try {
      const res = await studentAPI.reviewInvite(groupId, invId, action);
      if (res.data.success) {
        toast.success(res.data.message);
        loadNotifications();
      } else {

        toast.error(res.data.message);
      }
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to process request.');
    }
  };

  if (loading) return <div className="page-loader"><div className="spinner spinner-lg"></div></div>;

  const hasActivity = notifications.length > 0 || invitations.length > 0;

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">Notifications</h1>
        <p className="page-subtitle">Stay updated with your group and project activities</p>
      </div>

      {!hasActivity ? (
        <div className="card"><div className="card-body">
          <div className="empty-state">
            <div className="empty-state-icon">🔔</div>
            <div className="empty-state-title">No Notifications</div>
            <p style={{ color: 'var(--text-secondary)', fontSize: 'var(--font-size-sm)' }}>You're all caught up!</p>
          </div>
        </div></div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>

          {/* Actionable Invitations and Requests */}
          {invitations.length > 0 && (
            <div className="card">
              <div className="card-header" style={{ padding: '16px 20px', background: 'var(--primary-50)' }}>
                <h3 style={{ fontSize: 'var(--font-size-md)', fontWeight: 600, color: 'var(--primary-800)', display: 'flex', alignItems: 'center', gap: 8 }}>
                  <HiUserGroup /> Group Invitations & Requests
                </h3>
              </div>
              <div className="card-body" style={{ padding: 0 }}>
                {invitations.map((inv, i) => {
                  const isPending = inv.status === 'PENDING';
                  const isJoinRequest = inv.type === 'join_request';
                  const titleName = isJoinRequest ? inv.requesterName : inv.inviterName;

                  return (
                    <div key={inv.invitationId || i} style={{
                      display: 'flex', alignItems: 'center', gap: 14, padding: '16px 20px',
                      borderBottom: i < invitations.length - 1 ? '1px solid var(--border-light)' : 'none',
                      opacity: isPending ? 1 : 0.7,
                      background: isPending ? 'transparent' : 'var(--gray-50)',
                    }} className="notif-item">
                      <div style={{ width: 40, height: 40, borderRadius: '50%', background: isPending ? 'linear-gradient(135deg, var(--primary-400), var(--primary-600))' : 'var(--gray-300)', color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, fontWeight: 'bold' }}>
                        {(titleName || '?')[0]}
                      </div>
                      <div style={{ flex: 1 }}>
                        <div style={{ fontWeight: 500, fontSize: 'var(--font-size-md)' }}>
                          {isJoinRequest ? (
                            <><strong>{inv.requesterName}</strong> requested to join your group <strong>"{inv.groupName}"</strong></>
                          ) : (
                            <><strong>{inv.inviterName}</strong> invited you to join <strong>"{inv.groupName}"</strong></>
                          )}
                        </div>
                        <div style={{ fontSize: 'var(--font-size-sm)', color: 'var(--text-muted)', marginTop: 4 }}>
                          {isPending ? 'Please respond to this request.' : (inv.status === 'ACCEPTED' ? (isJoinRequest ? 'You approved this request.' : 'You accepted this invitation.') : (isJoinRequest ? 'You rejected this request.' : 'You declined this invitation.'))}
                        </div>
                      </div>
                      {isPending ? (
                        <div style={{ display: 'flex', gap: 8 }}>
                          <button className="btn btn-primary btn-sm" onClick={() => isJoinRequest ? handleReview(inv.groupId, inv.invitationId, 'accept') : handleRespond(inv.groupId, inv.invitationId, 'accept')}>
                            <HiCheck /> {isJoinRequest ? 'Approve' : 'Accept'}
                          </button>
                          <button className="btn btn-secondary btn-sm" onClick={() => isJoinRequest ? handleReview(inv.groupId, inv.invitationId, 'decline') : handleRespond(inv.groupId, inv.invitationId, 'decline')}>
                            <HiX /> {isJoinRequest ? 'Reject' : 'Decline'}
                          </button>
                        </div>
                      ) : (
                        <span className={`badge ${inv.status === 'ACCEPTED' ? 'badge-success' : 'badge-danger'}`}>
                          {inv.status === 'ACCEPTED' ? (isJoinRequest ? 'Approved' : 'Accepted') : (isJoinRequest ? 'Rejected' : 'Declined')}
                        </span>
                      )}
                    </div>
                  )
                })}
              </div>
            </div>
          )}

          {/* Regular Notifications */}
          {notifications.length > 0 && (
            <div className="card">
              <div className="card-body" style={{ padding: 0 }}>
                {notifications.map((n, i) => (
                  <div key={n.notificationId || i} style={{
                    display: 'flex', alignItems: 'flex-start', gap: 14, padding: '16px 20px',
                    borderBottom: i < notifications.length - 1 ? '1px solid var(--border-light)' : 'none',
                    transition: 'background var(--transition-fast)'
                  }} className="notif-item">
                    <div style={{ width: 36, height: 36, borderRadius: 'var(--radius-md)', background: n.type === 'warning' ? 'var(--warning-light)' : n.type === 'success' ? 'var(--success-light)' : 'var(--info-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                      {n.type === 'warning' ? <HiExclamation style={{ color: 'var(--warning)' }} /> : n.type === 'success' ? <HiCheckCircle style={{ color: 'var(--success)' }} /> : <HiInformationCircle style={{ color: 'var(--info)' }} />}
                    </div>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontWeight: 500, fontSize: 'var(--font-size-sm)' }}>{n.message || n.title}</div>
                      <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--text-muted)', marginTop: 4 }}>{n.createdDate ? new Date(n.createdDate).toLocaleString() : ''}</div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
      <style>{`.notif-item:hover { background: var(--gray-50); }`}</style>
    </div>
  );
}

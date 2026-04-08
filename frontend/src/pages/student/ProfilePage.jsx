import { useState } from 'react';
import { useAuth } from '../../contexts/AuthContext';
import { studentAPI } from '../../api';
import { useToast } from '../../components/Toast';
import { HiUser, HiMail, HiLockClosed, HiEye, HiEyeOff } from 'react-icons/hi';
import '../../layouts/DashboardLayout.css';

export default function ProfilePage() {
  const { user } = useAuth();
  const toast = useToast();
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPw, setShowPw] = useState(false);
  const [loading, setLoading] = useState(false);

  const handleChangePassword = async (e) => {
    e.preventDefault();
    if (!oldPassword || !newPassword || !confirmPassword) { toast.error('Please fill all fields.'); return; }
    if (newPassword !== confirmPassword) { toast.error('Passwords do not match.'); return; }
    setLoading(true);
    try {
      const res = await studentAPI.changePassword({ oldPassword, newPassword, confirmPassword });
      if (res.data.success) { toast.success('Password changed!'); setOldPassword(''); setNewPassword(''); setConfirmPassword(''); }
      else toast.error(res.data.message);
    } catch (err) { toast.error(err.response?.data?.message || 'Failed.'); }
    finally { setLoading(false); }
  };

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">Profile</h1>
        <p className="page-subtitle">Manage your account information</p>
      </div>

      <div className="content-grid">
        <div className="card">
          <div className="card-header"><h3 style={{ fontWeight: 600 }}>Account Information</h3></div>
          <div className="card-body">
            <div style={{ display: 'flex', alignItems: 'center', gap: 20, marginBottom: 28 }}>
              <div className="avatar avatar-lg">{(user?.fullName || '?')[0]}</div>
              <div>
                <div style={{ fontWeight: 700, fontSize: 'var(--font-size-xl)' }}>{user?.fullName}</div>
                <div style={{ color: 'var(--text-secondary)', fontSize: 'var(--font-size-sm)', marginTop: 4 }}>{user?.email}</div>
                <span className="badge badge-primary" style={{ marginTop: 8 }}>{user?.role}</span>
              </div>
            </div>
          </div>
        </div>

        <div className="card">
          <div className="card-header"><h3 style={{ fontWeight: 600 }}>Change Password</h3></div>
          <div className="card-body">
            <form onSubmit={handleChangePassword}>
              <div className="form-group">
                <label className="form-label">Current Password</label>
                <input type="password" className="form-input" value={oldPassword} onChange={e => setOldPassword(e.target.value)} placeholder="Enter current password" />
              </div>
              <div className="form-group">
                <label className="form-label">New Password</label>
                <input type={showPw ? 'text' : 'password'} className="form-input" value={newPassword} onChange={e => setNewPassword(e.target.value)} placeholder="Enter new password" />
              </div>
              <div className="form-group">
                <label className="form-label">Confirm Password</label>
                <input type="password" className="form-input" value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} placeholder="Re-enter new password" />
              </div>
              <button type="submit" className="btn btn-primary" disabled={loading}>
                {loading ? 'Changing...' : 'Change Password'}
              </button>
            </form>
          </div>
        </div>
      </div>
    </div>
  );
}

import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { authAPI } from '../../api';
import { useToast } from '../../components/Toast';
import { HiLockClosed, HiEye, HiEyeOff } from 'react-icons/hi';
import './Auth.css';

export default function ResetPasswordPage() {
  const [password, setPassword] = useState('');
  const [repassword, setRepassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const toast = useToast();

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!password || !repassword) { toast.error('Please fill in all fields.'); return; }
    if (password !== repassword) { toast.error('Passwords do not match.'); return; }
    setLoading(true);
    try {
      const res = await authAPI.resetPassword(password, repassword);
      if (res.data.success) { toast.success('Password changed!'); navigate('/login'); }
      else toast.error(res.data.message);
    } catch (err) { toast.error(err.response?.data?.message || 'Failed.'); }
    finally { setLoading(false); }
  };

  return (
    <div className="auth-page">
      <div className="auth-container">
        
        {/* Left Side: Form */}
        <div className="auth-form-panel">
          <h2 className="auth-form-title">NEW PASSWORD</h2>
          <p style={{ color: '#64748b', marginBottom: '25px', fontSize: '14px' }}>
            Enter your new password to secure your account.
          </p>
          
          <form onSubmit={handleSubmit} className="auth-form-light">
            <div className="form-group-light">
              <div className="input-icon-wrapper">
                <HiLockClosed className="input-icon-blue" />
                <input
                  type={showPassword ? 'text' : 'password'}
                  className="form-input-light"
                  placeholder="New password"
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  required
                />
                <button type="button" className="input-toggle-light" onClick={() => setShowPassword(!showPassword)} tabIndex={-1}>
                  {showPassword ? <HiEyeOff /> : <HiEye />}
                </button>
              </div>
            </div>

            <div className="form-group-light">
              <div className="input-icon-wrapper">
                <HiLockClosed className="input-icon-blue" />
                <input
                  type="password"
                  className="form-input-light"
                  placeholder="Confirm new password"
                  value={repassword}
                  onChange={e => setRepassword(e.target.value)}
                  required
                />
              </div>
            </div>
            
            <button type="submit" className="btn-primary-blue" disabled={loading}>
              {loading ? 'RESETTING...' : 'RESET PASSWORD'}
            </button>
          </form>
          
          <div style={{ marginTop: '20px', textAlign: 'left' }}>
            <Link to="/login" className="auth-forgot-link">← Back to login</Link>
          </div>
        </div>

        {/* Right Side: Visual */}
        <div className="auth-promo-panel" style={{ background: 'linear-gradient(135deg, #10b981 0%, #059669 100%)' }}>
          <div className="auth-promo-icon-box">
            <span style={{ fontSize: '40px' }}>🔒</span>
          </div>
          <h2 className="auth-promo-title">SECURE YOUR ACCOUNT</h2>
          <p className="auth-promo-desc">Make sure to choose a strong password that you haven't used before.</p>
        </div>

      </div>
    </div>
  );
}

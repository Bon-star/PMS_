import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authAPI } from '../../api';
import { useToast } from '../../components/Toast';
import { HiMail } from 'react-icons/hi';
import './Auth.css';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const toast = useToast();

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!email.trim()) { toast.error('Please enter your email.'); return; }
    setLoading(true);
    try {
      const res = await authAPI.forgot(email.trim());
      if (res.data.success) {
        toast.success('OTP sent to your email!');
        navigate('/verify-otp', { state: { email: email.trim(), type: 'RESET' } });
      } else  toast.error(res.data.message);
    } catch (err) { toast.error(err.response?.data?.message || 'Failed.'); }
    finally { setLoading(false); }
  };

  return (
    <div className="auth-page">
      <div className="auth-container">
        
        {/* Left Side: Form */}
        <div className="auth-form-panel">
          <h2 className="auth-form-title">FORGOT PASSWORD</h2>
          <p style={{ color: '#64748b', marginBottom: '25px', fontSize: '14px' }}>
            Enter your email to receive a verification code.
          </p>
          
          <form onSubmit={handleSubmit} className="auth-form-light">
            <div className="form-group-light">
              <div className="input-icon-wrapper">
                <HiMail className="input-icon-blue" />
                <input
                  type="email"
                  className="form-input-light"
                  placeholder="Registered email"
                  value={email}
                  onChange={e => setEmail(e.target.value)}
                  required
                />
              </div>
            </div>
            
            <button type="submit" className="btn-primary-blue" disabled={loading}>
              {loading ? 'SENDING...' : 'SEND OTP'}
            </button>
          </form>
          
          <div style={{ marginTop: '20px', textAlign: 'left' }}>
            <Link to="/login" className="auth-forgot-link">← Back to login</Link>
          </div>
        </div>

        {/* Right Side: Visual */}
        <div className="auth-promo-panel">
          <div className="auth-promo-icon-box">
            <span style={{ fontSize: '40px' }}>🔑</span>
          </div>
          <h2 className="auth-promo-title">ACCOUNT RECOVERY</h2>
          <p className="auth-promo-desc">Don't worry, we'll help you regain access.</p>
        </div>

      </div>
    </div>
  );
}

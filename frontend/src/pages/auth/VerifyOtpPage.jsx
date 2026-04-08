import { useState, useRef, useEffect } from 'react';
import { useLocation, useNavigate, Link } from 'react-router-dom';
import { authAPI } from '../../api';
import { useToast } from '../../components/Toast';
import './Auth.css';

export default function VerifyOtpPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const toast = useToast();
  const { email, password, type } = location.state || {};
  const [otp, setOtp] = useState(['', '', '', '', '', '']);
  const [loading, setLoading] = useState(false);
  const [resending, setResending] = useState(false);
  const [countdown, setCountdown] = useState(60);
  const inputRefs = useRef([]);

  useEffect(() => {
    if (!email) navigate('/register');
  }, [email, navigate]);

  useEffect(() => {
    if (countdown > 0) {
      const timer = setTimeout(() => setCountdown(c => c - 1), 1000);
      return () => clearTimeout(timer);
    }
  }, [countdown]);

  const handleChange = (index, value) => {
    if (value.length > 1) value = value.slice(-1);
    const newOtp = [...otp];
    newOtp[index] = value;
    setOtp(newOtp);
    if (value && index < 5) inputRefs.current[index + 1]?.focus();
  };

  const handleKeyDown = (index, e) => {
    if (e.key === 'Backspace' && !otp[index] && index > 0) {
      inputRefs.current[index - 1]?.focus();
    }
  };

  const handlePaste = (e) => {
    e.preventDefault();
    const pasted = e.clipboardData.getData('text').trim().slice(0, 6);
    if (pasted.length === 6 && /^\d+$/.test(pasted)) {
      setOtp(pasted.split(''));
      inputRefs.current[5]?.focus();
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const otpCode = otp.join('');
    if (otpCode.length !== 6) { toast.error('Please enter complete 6-digit OTP.'); return; }
    setLoading(true);
    try {
      if (type === 'REGISTER') {
        const res = await authAPI.verifyOtp(email, otpCode, password);
        if (res.data.success) { toast.success('Account activated!'); navigate('/login'); }
        else toast.error(res.data.message);
      } else {
        const res = await authAPI.verifyReset(otpCode);
        if (res.data.success) { toast.success('OTP verified!'); navigate('/reset-password'); }
        else toast.error(res.data.message);
      }
    } catch (err) { toast.error(err.response?.data?.message || 'Verification failed.'); }
    finally { setLoading(false); }
  };

  const handleResend = async () => {
    setResending(true);
    try {
      await authAPI.resendOtp(email, type || 'REGISTER', password);
      toast.success('New OTP sent!');
      setCountdown(60);
      setOtp(['', '', '', '', '', '']);
      inputRefs.current[0]?.focus();
    } catch (err) { toast.error('Failed to resend OTP.'); }
    finally { setResending(false); }
  };

  return (
    <div className="auth-page">
      <div className="auth-container">
        
        {/* Left Side: Notice */}
        <div className="auth-promo-panel" style={{ background: 'linear-gradient(135deg, #0ea5e9 0%, #2563eb 100%)' }}>
          <div className="auth-promo-icon-box">
            <span style={{ fontSize: '40px' }}>✉️</span>
          </div>
          <h2 className="auth-promo-title">CHECK YOUR EMAIL</h2>
          <p className="auth-promo-desc" style={{ marginBottom: '10px' }}>
            A verification code has been sent to:
          </p>
          <p style={{ fontWeight: 'bold', fontSize: '18px', color: '#e0f2fe', marginBottom: '30px' }}>
            {email}
          </p>
          <p style={{ fontSize: '14px', color: '#bae6fd' }}>
            Please check your spam folder if you don't see it.
          </p>
        </div>

        {/* Right Side: Form */}
        <div className="auth-form-panel">
          <h2 className="auth-form-title">OTP VERIFICATION</h2>
          
          <form onSubmit={handleSubmit} className="auth-form-light" style={{ marginBottom: '20px' }}>
            <div className="form-group-light" style={{ display: 'flex', justifyContent: 'center', gap: '8px', marginBottom: '30px' }} onPaste={handlePaste}>
              {otp.map((digit, i) => (
                <input 
                  key={i} 
                  ref={el => inputRefs.current[i] = el} 
                  type="text" 
                  inputMode="numeric" 
                  maxLength={1} 
                  style={{
                    width: '45px', height: '55px',
                    fontSize: '24px', fontWeight: 'bold',
                    textAlign: 'center', borderRadius: '8px',
                    border: '1px solid #cbd5e1', backgroundColor: '#f8fafc',
                    color: '#1e293b'
                  }} 
                  value={digit} 
                  onChange={e => handleChange(i, e.target.value)} 
                  onKeyDown={e => handleKeyDown(i, e)} 
                  onFocus={e => e.target.style.borderColor = '#3b82f6'}
                  onBlur={e => e.target.style.borderColor = '#cbd5e1'}
                />
              ))}
            </div>
            
            <button type="submit" className="btn-primary-blue" disabled={loading}>
              {loading ? 'VERIFYING...' : 'CONFIRM →'}
            </button>
          </form>

          <div style={{ textAlign: 'center', fontSize: '14px', color: '#64748b' }}>
            Didn't receive a code?{' '}
            <button 
              onClick={handleResend} 
              disabled={countdown > 0 || resending}
              style={{
                background: 'none', border: 'none',
                color: countdown > 0 ? '#94a3b8' : '#3b82f6',
                fontWeight: 'bold', cursor: countdown > 0 ? 'not-allowed' : 'pointer',
                padding: '0'
              }}
            >
              {countdown > 0 ? `Resend in ${countdown}s` : resending ? 'Sending...' : 'Resend OTP'}
            </button>
          </div>
          
          <div style={{ marginTop: '30px', textAlign: 'center' }}>
            <Link to="/login" className="auth-forgot-link">← Back to Sign In</Link>
          </div>
        </div>

      </div>
    </div>
  );
}

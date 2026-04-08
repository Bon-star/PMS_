import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authAPI } from '../../api';
import { useToast } from '../../components/Toast';
import { HiMail, HiLockClosed, HiPhone, HiEye, HiEyeOff, HiUser } from 'react-icons/hi';
import './Auth.css';

export default function RegisterPage() {
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [repassword, setRepassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showRepassword, setShowRepassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const toast = useToast();

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!email.trim() || !phone.trim() || !password || !repassword) {
      toast.error('Please fill in all fields.');
      return;
    }
    if (password !== repassword) {
      toast.error('Passwords do not match!');
      return;
    }
    setLoading(true);
    try {
      const res = await authAPI.register(email.trim(), phone.trim(), password, repassword);
      if (res.data.success) {
        toast.success('OTP sent to your email!');
        navigate('/verify-otp', { state: { email: email.trim(), password, type: 'REGISTER' } });
      } else {
        toast.error(res.data.message);
      }
    } catch (err) {
      toast.error(err.response?.data?.message || 'Registration failed.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-container">
        
        {/* Left Side: Login Promo */}
        <div className="auth-promo-panel" style={{ background: 'linear-gradient(135deg, #1e3a8a 0%, #3b82f6 100%)' }}>
          <div className="auth-promo-icon-box">
            <HiUser style={{ fontSize: '40px', color: '#fff' }} />
          </div>
          <h2 className="auth-promo-title">ALREADY HAVE AN ACCOUNT?</h2>
          <p className="auth-promo-desc">Go to the login page</p>
          <button className="btn-promo-outline" onClick={() => navigate('/login')}>
            LOGIN
          </button>
        </div>

        {/* Right Side: Register Form */}
        <div className="auth-form-panel">
          <h2 className="auth-form-title">REGISTER</h2>
          
          <form onSubmit={handleSubmit} className="auth-form-light">
            <div className="form-group-light">
              <div className="input-icon-wrapper">
                <HiMail className="input-icon-blue" />
                <input
                  type="email"
                  className="form-input-light"
                  placeholder="Email"
                  value={email}
                  onChange={e => setEmail(e.target.value)}
                  required
                />
              </div>
            </div>

            <div className="form-group-light">
              <div className="input-icon-wrapper">
                <HiPhone className="input-icon-blue" />
                <input
                  type="tel"
                  className="form-input-light"
                  placeholder="Phone number"
                  value={phone}
                  onChange={e => setPhone(e.target.value)}
                  required
                />
              </div>
            </div>

            <div className="form-group-light" style={{ marginBottom: '15px' }}>
              <div className="input-icon-wrapper">
                <HiLockClosed className="input-icon-blue" />
                <input
                  type={showPassword ? 'text' : 'password'}
                  className="form-input-light"
                  placeholder="Password"
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
                  type={showRepassword ? 'text' : 'password'}
                  className="form-input-light"
                  placeholder="Confirm password"
                  value={repassword}
                  onChange={e => setRepassword(e.target.value)}
                  required
                />
                <button type="button" className="input-toggle-light" onClick={() => setShowRepassword(!showRepassword)} tabIndex={-1}>
                  {showRepassword ? <HiEyeOff /> : <HiEye />}
                </button>
              </div>
            </div>

            <button type="submit" className="btn-primary-blue" disabled={loading} style={{ marginTop: '20px' }}>
              {loading ? 'REGISTERING...' : 'REGISTER & GET OTP'}
            </button>
          </form>
        </div>

      </div>
    </div>
  );
}

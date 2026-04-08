import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { useToast } from '../../components/Toast';
import { HiMail, HiLockClosed, HiEye, HiEyeOff, HiUserAdd } from 'react-icons/hi';
import './Auth.css';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!email.trim() || !password.trim()) {
      toast.error('Please fill in all fields.');
      return;
    }
    setLoading(true);
    try {
      const result = await login(email.trim(), password);
      if (result.success) {
        toast.success(`Welcome back, ${result.fullName}!`);
        const roleRoutes = { Staff: '/staff/home', Lecturer: '/lecturer/home', Student: '/student/home' };
        navigate(roleRoutes[result.role] || '/student/home');
      } else {
        toast.error(result.message || 'Login failed.');
      }
    } catch (err) {
      toast.error(err.response?.data?.message || 'Login failed. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-container">
        
        {/* Left Side: Login Form */}
        <div className="auth-form-panel">
          <h2 className="auth-form-title">LOGIN</h2>
          
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
                  autoComplete="email"
                />
              </div>
            </div>

            <div className="form-group-light">
              <div className="input-icon-wrapper">
                <HiLockClosed className="input-icon-blue" />
                <input
                  type={showPassword ? 'text' : 'password'}
                  className="form-input-light"
                  placeholder="Password"
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  autoComplete="current-password"
                />
                <button type="button" className="input-toggle-light" onClick={() => setShowPassword(!showPassword)} tabIndex={-1}>
                  {showPassword ? <HiEyeOff /> : <HiEye />}
                </button>
              </div>
            </div>

            <div className="auth-actions" style={{ marginBottom: '24px', marginTop: '10px' }}>
              <Link to="/forgot-password" className="auth-forgot-link">Forgot password?</Link>
            </div>

            <button type="submit" className="btn-primary-blue" disabled={loading}>
              {loading ? 'SIGNING IN...' : 'LOGIN'}
            </button>
          </form>
        </div>

        {/* Right Side: Register Promo */}
        <div className="auth-promo-panel">
          <div className="auth-promo-icon-box">
            <HiUserAdd style={{ fontSize: '40px', color: '#fff' }} />
          </div>
          <h2 className="auth-promo-title">DON'T HAVE A PMS ACCOUNT?</h2>
          <p className="auth-promo-desc">Click register if you don't have an account.</p>
          <button className="btn-promo-outline" onClick={() => navigate('/register')}>
            REGISTER
          </button>
        </div>

      </div>
    </div>
  );
}

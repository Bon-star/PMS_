import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { studentAPI } from '../api';
import { HiBell, HiUser, HiLogout } from 'react-icons/hi';
import './Topbar.css';

export default function Topbar() {
  const { user, logout, role } = useAuth();
  const navigate = useNavigate();
  const [unreadCount, setUnreadCount] = useState(0);
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const dropdownRef = useRef(null);

  useEffect(() => {
    if (role === 'Student') {
      const fetchCount = async () => {
        try {
          const res = await studentAPI.getNotificationCount();
          setUnreadCount(res.data?.count || 0);
        } catch (err) {
          console.log('Error fetching notification count');
        }
      };
      
      fetchCount();
      
      // Setup polling every 30 seconds to update the badge dynamically
      const interval = setInterval(fetchCount, 30000);
      return () => clearInterval(interval);
    }
  }, [role]);

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setDropdownOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  const initials = user?.fullName ? user.fullName.split(' ').map(n => n[0]).join('').slice(0, 2).toUpperCase() : '?';

  return (
    <header className="topbar">
      <div className="topbar-right">
        {role === 'Student' && (
          <button className="topbar-icon-btn" onClick={() => navigate('/student/notifications')} title="Notifications">
            <HiBell style={{ fontSize: '22px' }} />
            {unreadCount > 0 && (
              <span className="topbar-badge">{unreadCount > 9 ? '9+' : unreadCount}</span>
            )}
          </button>
        )}

        {/* Profile Dropdown */}
        <div className="topbar-profile-container" ref={dropdownRef}>
          <button className="topbar-profile-btn" onClick={() => setDropdownOpen(!dropdownOpen)}>
            <div className="topbar-avatar">{initials}</div>
            <div className="topbar-avatar-badge">
              <svg viewBox="0 0 24 24" width="12" height="12" fill="currentColor"><path d="M7 10l5 5 5-5z"/></svg>
            </div>
          </button>
          
          {dropdownOpen && (
            <div className="topbar-dropdown">
              <div className="dropdown-header">
                <div className="topbar-avatar avatar-lg">{initials}</div>
                <div className="dropdown-user-info">
                  <strong>{user?.fullName || 'User'}</strong>
                  <span>{role}</span>
                </div>
              </div>
              <div className="dropdown-divider"></div>
              {role === 'Student' && (
                <button className="dropdown-item" onClick={() => { setDropdownOpen(false); navigate('/student/profile'); }}>
                  <HiUser className="dropdown-icon" /> Profile Settings
                </button>
              )}
              <button className="dropdown-item" onClick={handleLogout}>
                <HiLogout className="dropdown-icon" /> Log Out
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}

import { NavLink } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { HiHome, HiUserGroup, HiFolder, HiAcademicCap, HiOfficeBuilding, HiTemplate, HiMail, HiClock } from 'react-icons/hi';
import './Sidebar.css';

const studentLinks = [
  { to: '/student/home', icon: <HiHome />, label: 'Dashboard' },
  { to: '/student/groups', icon: <HiUserGroup />, label: 'My Group' },
  { to: '/student/project', icon: <HiFolder />, label: 'Project' },
  { to: '/student/project/history-ui', icon: <HiClock />, label: 'History' },
];

const staffLinks = [
  { to: '/staff/home', icon: <HiHome />, label: 'Dashboard' },
  { to: '/staff/students', icon: <HiAcademicCap />, label: 'Students' },
  { to: '/staff/classrooms', icon: <HiOfficeBuilding />, label: 'Classrooms' },
  { to: '/staff/projects', icon: <HiFolder />, label: 'Projects' },
  { to: '/staff/templates', icon: <HiTemplate />, label: 'Templates' },
  { to: '/staff/requests', icon: <HiMail />, label: 'Requests' },
];

const lecturerLinks = [
  { to: '/lecturer/home', icon: <HiHome />, label: 'Dashboard' },
  { to: '/lecturer/projects', icon: <HiFolder />, label: 'Projects' },
];

export default function Sidebar() {
  const { role } = useAuth();
  
  const links = role === 'Staff' ? staffLinks : role === 'Lecturer' ? lecturerLinks : studentLinks;

  return (
    <aside className="sidebar" id="main-sidebar">
      <div className="sidebar-header">
        <div className="sidebar-logo">
          <div className="sidebar-logo-icon">P</div>
          <div className="sidebar-logo-text">
            <span className="sidebar-brand">PMS</span>
            <span className="sidebar-subtitle">Project Management</span>
          </div>
        </div>
      </div>

      <nav className="sidebar-nav">
        <div className="sidebar-nav-label">Menu</div>
        {links.map(link => (
          <NavLink key={link.to} to={link.to} className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`} id={`nav-${link.label.toLowerCase().replace(/\s/g, '-')}`}>
            <span className="sidebar-link-icon">{link.icon}</span>
            <span className="sidebar-link-text">{link.label}</span>
          </NavLink>
        ))}
      </nav>
    </aside>
  );
}

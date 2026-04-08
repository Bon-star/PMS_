import { Outlet } from 'react-router-dom';
import Sidebar from '../components/Sidebar';
import ChatWidget from '../components/ChatWidget';
import './DashboardLayout.css';

import Topbar from '../components/Topbar';
import { useAuth } from '../contexts/AuthContext';

export default function DashboardLayout() {
  const { role } = useAuth();

  return (
    <div className="dashboard-layout">
      <Sidebar />
      <main className="dashboard-main" id="main-content">
        <Topbar />
        <div className="dashboard-content">
          <Outlet />
        </div>
      </main>
      {role === 'Student' && <ChatWidget />}
    </div>
  );
}

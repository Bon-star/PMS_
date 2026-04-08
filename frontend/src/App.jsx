import { BrowserRouter, Routes, Route, Navigate, Outlet } from 'react-router-dom';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { ToastProvider } from './components/Toast';
import DashboardLayout from './layouts/DashboardLayout';

// Auth pages
import LoginPage from './pages/auth/LoginPage';
import RegisterPage from './pages/auth/RegisterPage';
import VerifyOtpPage from './pages/auth/VerifyOtpPage';
import ForgotPasswordPage from './pages/auth/ForgotPasswordPage';
import ResetPasswordPage from './pages/auth/ResetPasswordPage';

// Student pages
import StudentHomePage from './pages/student/StudentHomePage';
import GroupPage from './pages/student/GroupPage';
import ProjectPage from './pages/student/ProjectPage';
import NotificationsPage from './pages/student/NotificationsPage';
import ProfilePage from './pages/student/ProfilePage';
import ProjectTaskBoardPage from './pages/student/ProjectTaskBoardPage';
import ProjectHistoryPage from './pages/student/ProjectHistoryPage';
import ProjectHistoryDetailPage from './pages/student/ProjectHistoryDetailPage';
import ProjectTaskCodeViewPage from './pages/student/ProjectTaskCodeViewPage';

// Staff pages
import StaffHomePage from './pages/staff/StaffHomePage';
import StudentsPage from './pages/staff/StudentsPage';
import ClassroomsPage from './pages/staff/ClassroomsPage';
import ProjectsPage from './pages/staff/ProjectsPage';
import TemplatesPage from './pages/staff/TemplatesPage';
import RequestsPage from './pages/staff/RequestsPage';

// Lecturer pages
import LecturerHomePage from './pages/lecturer/LecturerHomePage';
import LecturerProjectsPage from './pages/lecturer/LecturerProjectsPage';

function ProtectedRoute({ allowedRoles }) {
  const { isAuthenticated, loading, role } = useAuth();

  if (loading) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh', gap: 12 }}>
        <div className="spinner spinner-lg"></div>
        <span style={{ color: 'var(--text-secondary)' }}>Loading...</span>
      </div>
    );
  }

  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (allowedRoles && !allowedRoles.includes(role)) {
    const home = role === 'Staff' ? '/staff/home' : role === 'Lecturer' ? '/lecturer/home' : '/student/home';
    return <Navigate to={home} replace />;
  }

  return <Outlet />;
}

function PublicRoute() {
  const { isAuthenticated, loading, role } = useAuth();

  if (loading) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh', gap: 12 }}>
        <div className="spinner spinner-lg"></div>
      </div>
    );
  }

  if (isAuthenticated) {
    const home = role === 'Staff' ? '/staff/home' : role === 'Lecturer' ? '/lecturer/home' : '/student/home';
    return <Navigate to={home} replace />;
  }

  return <Outlet />;
}

export default function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <Routes>
            {/* Public auth routes */}
            <Route element={<PublicRoute />}>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />
              <Route path="/verify-otp" element={<VerifyOtpPage />} />
              <Route path="/forgot-password" element={<ForgotPasswordPage />} />
              <Route path="/reset-password" element={<ResetPasswordPage />} />
            </Route>

            {/* Student routes */}
            <Route element={<ProtectedRoute allowedRoles={['Student']} />}>
              <Route element={<DashboardLayout />}>
                <Route path="/student/home" element={<StudentHomePage />} />
                <Route path="/student/groups" element={<GroupPage />} />
                <Route path="/student/project" element={<ProjectPage />} />
                <Route path="/student/project/tasks-ui" element={<ProjectTaskBoardPage />} />
                <Route path="/student/project/code-ui/:taskId" element={<ProjectTaskCodeViewPage />} />
                <Route path="/student/project/history-ui" element={<ProjectHistoryPage />} />
                <Route path="/student/project/history-ui/:id" element={<ProjectHistoryDetailPage />} />
                <Route path="/student/notifications" element={<NotificationsPage />} />
                <Route path="/student/profile" element={<ProfilePage />} />
              </Route>
            </Route>

            {/* Staff routes */}
            <Route element={<ProtectedRoute allowedRoles={['Staff']} />}>
              <Route element={<DashboardLayout />}>
                <Route path="/staff/home" element={<StaffHomePage />} />
                <Route path="/staff/students" element={<StudentsPage />} />
                <Route path="/staff/classrooms" element={<ClassroomsPage />} />
                <Route path="/staff/projects" element={<ProjectsPage />} />
                <Route path="/staff/templates" element={<TemplatesPage />} />
                <Route path="/staff/requests" element={<RequestsPage />} />
              </Route>
            </Route>

            {/* Lecturer routes */}
            <Route element={<ProtectedRoute allowedRoles={['Lecturer']} />}>
              <Route element={<DashboardLayout />}>
                <Route path="/lecturer/home" element={<LecturerHomePage />} />
                <Route path="/lecturer/projects" element={<LecturerProjectsPage />} />
              </Route>
            </Route>

            {/* Fallback */}
            <Route path="/" element={<Navigate to="/login" replace />} />
            <Route path="*" element={<Navigate to="/login" replace />} />
          </Routes>
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  );
}

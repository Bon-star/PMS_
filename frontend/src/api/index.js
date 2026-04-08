import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const currentPath = window.location.pathname;
      if (!currentPath.startsWith('/login') && !currentPath.startsWith('/register') && !currentPath.startsWith('/forgot')) {
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

export const authAPI = {
  login: (email, password) => api.post('/auth/login', { email, password }),
  register: (email, phone, password, repassword) => api.post('/auth/register', { email, phone, password, repassword }),
  verifyOtp: (email, otp, password) => api.post('/auth/verify-otp', { email, otp, password }),
  resendOtp: (email, type, password) => api.post('/auth/resend-otp', { email, type, password }),
  forgot: (email) => api.post('/auth/forgot', { email }),
  verifyReset: (otp) => api.post('/auth/verify-reset', { otp }),
  resetPassword: (password, repassword) => api.post('/auth/reset-password', { password, repassword }),
  me: () => api.get('/auth/me'),
  logout: () => api.post('/auth/logout'),
};

export const studentAPI = {
  getHome: () => api.get('/student/home'),
  getProfile: () => api.get('/student/profile'),
  changePassword: (data) => api.post('/student/profile/change-password', data),
  // Groups
  getGroups: () => api.get('/student/groups'),
  createGroup: (data) => api.post('/student/groups', data),
  getGroupDetail: (id) => api.get(`/student/groups/${id}`),
  renameGroup: (id, groupName) => api.post(`/student/groups/${id}/rename`, { groupName }),
  inviteMember: (id, studentRef) => api.post(`/student/groups/${id}/invite`, { studentRef }),
  joinRequest: (id) => api.post(`/student/groups/${id}/join-request`),
  reviewInvite: (groupId, invId, action) => api.post(`/student/groups/${groupId}/invite/${invId}/review`, { action }),
  respondInvite: (groupId, invId, action) => api.post(`/student/groups/${groupId}/invite/${invId}/respond`, { action }),
  kickMember: (id, studentId) => api.post(`/student/groups/${id}/kick`, { studentId }),
  leaveGroup: (id) => api.post(`/student/groups/${id}/leave`),
  deleteGroup: (id) => api.post(`/student/groups/${id}/delete`),
  transferLeader: (id, newLeaderId) => api.post(`/student/groups/${id}/transfer-leader`, { newLeaderId }),
  getInvitableStudents: (groupId) => api.get(`/student/groups/${groupId}/invitable`),
  getNotifications: (page) => api.get(`/student/notifications?page=${page || 1}`),
  getNotificationCount: () => api.get('/student/notifications/count'),
  // Project
  getProject: () => api.get('/student/project'),
  getProjectTasks: (params = {}) => api.get('/student/project/tasks', { params }),
  createProjectSprint: (data) => api.post('/student/project/sprints', data),
  renameProjectSprint: (id, sprintName) => api.post(`/student/project/sprints/${id}/rename`, { sprintName }),
  deleteProjectSprint: (id) => api.post(`/student/project/sprints/${id}/delete`),
  cancelProjectSprint: (id, cancelReason) => api.post(`/student/project/sprints/${id}/cancel`, { cancelReason }),
  createProjectTask: (data) => api.post('/student/project/tasks', data),
  updateProjectTask: (id, data) => api.post(`/student/project/tasks/${id}/update`, data),
  deleteProjectTask: (id) => api.post(`/student/project/tasks/${id}/delete`),
  cancelProjectTask: (id, cancelReason) => api.post(`/student/project/tasks/${id}/cancel`, { cancelReason }),
  startProjectTask: (id) => api.post(`/student/project/tasks/${id}/start`),
  submitProjectTask: (id, formData) => api.post(`/student/project/tasks/${id}/submit`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }),
  unsubmitProjectTask: (id) => api.post(`/student/project/tasks/${id}/unsubmit`),
  reviewProjectTask: (id, data) => api.post(`/student/project/tasks/${id}/review`, data),
  getProjectTaskCode: (id, file) => api.get(`/student/project/tasks/${id}/code-preview`, {
    params: file ? { file } : {},
  }),
  replanFailedTask: (sprintId, data) => api.post(`/student/project/sprints/${sprintId}/replan`, data),
  finalizeProjectLinks: (data) => api.post('/student/project/finalize-links', data),
  getProjectHistory: () => api.get('/student/project/history'),
  getProjectHistoryDetail: (id) => api.get(`/student/project/history/${id}`),
};

export const staffAPI = {
  getHome: () => api.get('/staff/home'),
  // Students
  getStudents: (classId, page) => api.get(`/staff/students?classId=${classId || ''}&page=${page || 1}`),
  createStudent: (data) => api.post('/staff/students', data),
  updateStudent: (data) => api.post('/staff/students/update', data),
  importStudents: (formData) => api.post('/staff/students/import', formData, { headers: { 'Content-Type': 'multipart/form-data' } }),
  // Classes
  getClasses: () => api.get('/staff/classrooms'),
  getClassDetail: (id) => api.get(`/staff/classrooms/${id}`),
  createClass: (data) => api.post('/staff/classrooms', data),
  updateClass: (data) => api.post('/staff/classrooms/update', data),
  deleteClass: (id) => api.post('/staff/classrooms/delete', { classId: id }),
  // Projects
  getProjects: (semesterId) => api.get(`/staff/projects?semesterId=${semesterId || ''}`),
  assignProject: (data) => api.post('/staff/projects/assign', data),
  createProject: (data) => api.post('/staff/projects/create', data),
  // Templates
  getTemplates: (params) => api.get('/staff/projects/templates-manager', { params }),
  createTemplate: (formData) => api.post('/staff/projects/templates/create', formData, { headers: { 'Content-Type': 'multipart/form-data' } }),
  // Requests
  getRequests: (semesterId) => api.get(`/staff/projects/requests?semesterId=${semesterId || ''}`),
  approveEditRequest: (id, semesterId) => api.post(`/staff/projects/requests/${id}/approve`, { semesterId }),
  rejectEditRequest: (id, reason, semesterId) => api.post(`/staff/projects/requests/${id}/reject`, { reason, semesterId }),
  approveChangeRequest: (id, semesterId) => api.post(`/staff/projects/change-requests/${id}/approve`, { semesterId }),
  rejectChangeRequest: (id, reason, semesterId) => api.post(`/staff/projects/change-requests/${id}/reject`, { reason, semesterId }),
  // Performance
  getPerformance: (projectId, semesterId) => api.get(`/staff/projects/${projectId}/performance?semesterId=${semesterId || ''}`),
};

export const lecturerAPI = {
  getHome: () => api.get('/lecturer/home'),
  getProjects: () => api.get('/lecturer/projects'),
  approveProject: (id, startDate, endDate) => api.post(`/lecturer/projects/${id}/approve`, { startDate, endDate }),
  rejectProject: (id, reason) => api.post(`/lecturer/projects/${id}/reject`, { reason }),
  getProgress: (id) => api.get(`/lecturer/projects/${id}/progress`),
  addComment: (id, commentContent) => api.post(`/lecturer/projects/${id}/comments`, { commentContent }),
  approveChangeRequest: (id, startDate, endDate) => api.post(`/lecturer/projects/change-requests/${id}/approve`, { startDate, endDate }),
  rejectChangeRequest: (id, reason) => api.post(`/lecturer/projects/change-requests/${id}/reject`, { reason }),
};

export default api;

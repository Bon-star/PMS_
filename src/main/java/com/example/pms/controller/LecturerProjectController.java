package com.example.pms.controller;

import com.example.pms.model.Account;
import com.example.pms.model.Lecturer;
import com.example.pms.model.ProjectComment;
import com.example.pms.model.Project;
import com.example.pms.model.ProjectChangeRequest;
import com.example.pms.repository.LecturerRepository;
import com.example.pms.repository.ProjectCommentRepository;
import com.example.pms.repository.ProjectChangeRequestRepository;
import com.example.pms.repository.ProjectRepository;
import com.example.pms.repository.ProjectTaskRepository;
import com.example.pms.repository.SprintRepository;
import com.example.pms.util.RoleDisplayUtil;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/lecturer/projects")
public class LecturerProjectController {

    @Autowired
    private LecturerRepository lecturerRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectChangeRequestRepository projectChangeRequestRepository;

    @Autowired
    private ProjectCommentRepository projectCommentRepository;

    @Autowired
    private ProjectTaskRepository projectTaskRepository;

    @Autowired
    private SprintRepository sprintRepository;

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private Account getSessionAccount(HttpSession session) {
        return (Account) session.getAttribute("account");
    }

    private Lecturer getSessionLecturer(HttpSession session) {
        Account account = getSessionAccount(session);
        if (account == null || !"Lecturer".equalsIgnoreCase(account.getRole())) {
            return null;
        }
        return lecturerRepository.findByAccountId(account.getId());
    }

    private boolean canReviewProject(int lecturerId, int projectId) {
        List<Project> pendingProjects = projectRepository.findPendingForLecturer(lecturerId);
        for (Project pending : pendingProjects) {
            if (pending.getProjectId() == projectId) {
                return true;
            }
        }
        return false;
    }

    private boolean canReviewChangeRequest(int lecturerId, int requestId) {
        List<ProjectChangeRequest> pendingRequests = projectChangeRequestRepository.findPendingForLecturer(lecturerId);
        for (ProjectChangeRequest pending : pendingRequests) {
            if (pending.getRequestId() == requestId) {
                return true;
            }
        }
        return false;
    }

    private void bindLayout(Model model, HttpSession session, Lecturer lecturer) {
        Object fullName = session.getAttribute("fullName");
        model.addAttribute("displayName", fullName != null ? fullName : lecturer.getFullName());
        model.addAttribute("displayRole", RoleDisplayUtil.toDisplayRole("Lecturer"));
    }

    @GetMapping
    public String index(Model model, HttpSession session) {
        Lecturer lecturer = getSessionLecturer(session);
        if (lecturer == null) {
            return "redirect:/acc/log";
        }

        bindLayout(model, session, lecturer);
        model.addAttribute("pendingProjects", projectRepository.findPendingForLecturer(lecturer.getLecturerId()));
        model.addAttribute("pendingChangeRequests", projectChangeRequestRepository.findPendingForLecturer(lecturer.getLecturerId()));
        model.addAttribute("trackedProjects", projectRepository.findApprovedForLecturer(lecturer.getLecturerId()));
        return "lecturer/projects";
    }

    @GetMapping("/{projectId}/progress")
    public String progress(@PathVariable("projectId") int projectId,
            Model model,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Lecturer lecturer = getSessionLecturer(session);
        if (lecturer == null) {
            return "redirect:/acc/log";
        }

        if (!projectRepository.canLecturerAccessProject(lecturer.getLecturerId(), projectId)) {
            redirectAttributes.addFlashAttribute("error", "Bạn không có quyền xem tiến độ của project này.");
            return "redirect:/lecturer/projects";
        }

        Project project = projectRepository.findById(projectId);
        if (project == null || project.getProjectId() <= 0) {
            redirectAttributes.addFlashAttribute("error", "Project không tồn tại.");
            return "redirect:/lecturer/projects";
        }

        bindLayout(model, session, lecturer);
        sprintRepository.closeExpiredSprintsAndFailTasks(project.getProjectId());
        List<ProjectComment> comments = projectCommentRepository.findByProject(project.getProjectId());

        model.addAttribute("project", project);
        model.addAttribute("sprints", sprintRepository.findByProject(project.getProjectId()));
        model.addAttribute("taskHistory", projectTaskRepository.findByProject(project.getProjectId()));
        model.addAttribute("overallPerformance",
                projectTaskRepository.findMemberPerformanceOverallByProject(project.getProjectId()));
        model.addAttribute("sprintPerformance",
                projectTaskRepository.findMemberPerformanceBySprint(project.getProjectId()));
        model.addAttribute("lecturerComments", comments);
        return "lecturer/progress";
    }

    @PostMapping("/{projectId}/comments")
    public String addProgressComment(@PathVariable("projectId") int projectId,
            @RequestParam("commentContent") String commentContent,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Lecturer lecturer = getSessionLecturer(session);
        if (lecturer == null) {
            return "redirect:/acc/log";
        }

        if (!projectRepository.canLecturerAccessProject(lecturer.getLecturerId(), projectId)) {
            redirectAttributes.addFlashAttribute("error", "Bạn không có quyền bình luận cho project này.");
            return "redirect:/lecturer/projects";
        }

        Project project = projectRepository.findById(projectId);
        if (project == null || project.getProjectId() <= 0) {
            redirectAttributes.addFlashAttribute("error", "Project không tồn tại.");
            return "redirect:/lecturer/projects";
        }

        String normalizedComment = normalize(commentContent);
        if (normalizedComment.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng nhập nội dung bình luận.");
            return "redirect:/lecturer/projects/" + projectId + "/progress";
        }

        int created = projectCommentRepository.create(projectId, lecturer.getLecturerId(), normalizedComment);
        if (created <= 0) {
            redirectAttributes.addFlashAttribute("error", "Không thể lưu bình luận tiến độ.");
            return "redirect:/lecturer/projects/" + projectId + "/progress";
        }

        redirectAttributes.addFlashAttribute("success", "Đã lưu bình luận tiến độ.");
        return "redirect:/lecturer/projects/" + projectId + "/progress";
    }

    @PostMapping("/{projectId}/approve")
    public String approveProject(@PathVariable("projectId") int projectId,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Lecturer lecturer = getSessionLecturer(session);
        if (lecturer == null) {
            return "redirect:/acc/log";
        }

        if (!canReviewProject(lecturer.getLecturerId(), projectId)) {
            redirectAttributes.addFlashAttribute("error", "Bạn không có quyền duyệt project này hoặc project không còn chờ duyệt.");
            return "redirect:/lecturer/projects";
        }

        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(normalize(startDate));
            end = LocalDate.parse(normalize(endDate));
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Ngày bắt đầu/kết thúc không hợp lệ.");
            return "redirect:/lecturer/projects";
        }

        if (!end.isAfter(start)) {
            redirectAttributes.addFlashAttribute("error", "Ngày kết thúc phải sau ngày bắt đầu.");
            return "redirect:/lecturer/projects";
        }

        LocalDateTime startTime = start.atStartOfDay();
        LocalDateTime endTime = end.atTime(LocalTime.of(23, 59, 59));
        int updated = projectRepository.approveByLecturer(projectId, startTime, endTime);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Không thể duyệt project.");
            return "redirect:/lecturer/projects";
        }

        redirectAttributes.addFlashAttribute("success", "Đã duyệt project thành công.");
        return "redirect:/lecturer/projects";
    }

    @PostMapping("/{projectId}/reject")
    public String rejectProject(@PathVariable("projectId") int projectId,
            @RequestParam("reason") String reason,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Lecturer lecturer = getSessionLecturer(session);
        if (lecturer == null) {
            return "redirect:/acc/log";
        }

        if (!canReviewProject(lecturer.getLecturerId(), projectId)) {
            redirectAttributes.addFlashAttribute("error", "Bạn không có quyền từ chối project này hoặc project không còn chờ duyệt.");
            return "redirect:/lecturer/projects";
        }

        String normalizedReason = normalize(reason);
        if (normalizedReason.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng nhập lý do từ chối.");
            return "redirect:/lecturer/projects";
        }

        int updated = projectRepository.rejectByLecturer(projectId, normalizedReason);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Không thể từ chối project.");
            return "redirect:/lecturer/projects";
        }

        redirectAttributes.addFlashAttribute("success", "Đã từ chối project và gửi lý do cho học viên.");
        return "redirect:/lecturer/projects";
    }

    @Transactional
    @PostMapping("/change-requests/{requestId}/approve")
    public String approveChangeRequest(@PathVariable("requestId") int requestId,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Lecturer lecturer = getSessionLecturer(session);
        if (lecturer == null) {
            return "redirect:/acc/log";
        }

        if (!canReviewChangeRequest(lecturer.getLecturerId(), requestId)) {
            redirectAttributes.addFlashAttribute("error", "Bạn không có quyền duyệt yêu cầu đổi project này hoặc yêu cầu không còn chờ duyệt.");
            return "redirect:/lecturer/projects";
        }

        ProjectChangeRequest request = projectChangeRequestRepository.findById(requestId);
        if (request == null || !ProjectChangeRequest.STATUS_PENDING_LECTURER.equalsIgnoreCase(request.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "Yêu cầu đổi project không hợp lệ hoặc đã xử lý.");
            return "redirect:/lecturer/projects";
        }

        Project project = projectRepository.findById(request.getProjectId());
        if (project == null) {
            redirectAttributes.addFlashAttribute("error", "Project không còn tồn tại.");
            return "redirect:/lecturer/projects";
        }
        if (projectTaskRepository.hasDoneTasksByProject(project.getProjectId())) {
            redirectAttributes.addFlashAttribute("error", "Project đã có công việc hoàn thành nên không thể đổi đề tài.");
            return "redirect:/lecturer/projects";
        }

        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(normalize(startDate));
            end = LocalDate.parse(normalize(endDate));
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Ngày bắt đầu/kết thúc cho đề tài mới không hợp lệ.");
            return "redirect:/lecturer/projects";
        }
        if (!end.isAfter(start)) {
            redirectAttributes.addFlashAttribute("error", "Ngày kết thúc phải sau ngày bắt đầu.");
            return "redirect:/lecturer/projects";
        }

        LocalDateTime startTime = start.atStartOfDay();
        LocalDateTime endTime = end.atTime(LocalTime.of(23, 59, 59));
        int updatedProject = projectRepository.applyApprovedChange(
                project.getProjectId(),
                request.getProposedProjectName(),
                request.getProposedDescription(),
                startTime,
                endTime);
        if (updatedProject <= 0) {
            throw new IllegalStateException("Không thể cập nhật project theo đề tài mới.");
        }

        String taskCancelReason = "Kế hoạch cũ bị hủy do project đã được duyệt đổi đề tài mới.";
        String sprintCancelReason = "Đợt làm việc cũ bị hủy do project đã được duyệt đổi đề tài mới.";
        projectTaskRepository.cancelAllNonCompletedByProject(project.getProjectId(), taskCancelReason);
        sprintRepository.cancelAllByProject(project.getProjectId(), sprintCancelReason);

        int updatedRequest = projectChangeRequestRepository.approveByLecturer(requestId, lecturer.getLecturerId());
        if (updatedRequest <= 0) {
            throw new IllegalStateException("Không thể cập nhật trạng thái duyệt yêu cầu đổi project.");
        }

        redirectAttributes.addFlashAttribute("success", "Đã duyệt đổi project và hủy toàn bộ kế hoạch cũ chưa hoàn thành.");
        return "redirect:/lecturer/projects";
    }

    @PostMapping("/change-requests/{requestId}/reject")
    public String rejectChangeRequest(@PathVariable("requestId") int requestId,
            @RequestParam("reason") String reason,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Lecturer lecturer = getSessionLecturer(session);
        if (lecturer == null) {
            return "redirect:/acc/log";
        }

        if (!canReviewChangeRequest(lecturer.getLecturerId(), requestId)) {
            redirectAttributes.addFlashAttribute("error", "Bạn không có quyền từ chối yêu cầu đổi project này hoặc yêu cầu không còn chờ duyệt.");
            return "redirect:/lecturer/projects";
        }

        String normalizedReason = normalize(reason);
        if (normalizedReason.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng nhập lý do từ chối yêu cầu đổi project.");
            return "redirect:/lecturer/projects";
        }

        ProjectChangeRequest request = projectChangeRequestRepository.findById(requestId);
        if (request == null || !ProjectChangeRequest.STATUS_PENDING_LECTURER.equalsIgnoreCase(request.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "Yêu cầu đổi project không hợp lệ hoặc đã xử lý.");
            return "redirect:/lecturer/projects";
        }

        int updated = projectChangeRequestRepository.rejectByLecturer(requestId, lecturer.getLecturerId(), normalizedReason);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Không thể từ chối yêu cầu đổi project.");
            return "redirect:/lecturer/projects";
        }

        redirectAttributes.addFlashAttribute("success", "Đã từ chối yêu cầu đổi project.");
        return "redirect:/lecturer/projects";
    }
}

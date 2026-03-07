package com.example.pms.controller;

import com.example.pms.model.Account;
import com.example.pms.model.Project;
import com.example.pms.model.ProjectEditRequest;
import com.example.pms.model.Semester;
import com.example.pms.model.Staff;
import com.example.pms.repository.ProjectEditRequestRepository;
import com.example.pms.repository.ProjectRepository;
import com.example.pms.repository.ProjectTaskRepository;
import com.example.pms.repository.SemesterRepository;
import com.example.pms.repository.SprintRepository;
import com.example.pms.repository.StaffRepository;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/staff/projects")
public class StaffProjectController {

    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectEditRequestRepository projectEditRequestRepository;

    @Autowired
    private ProjectTaskRepository projectTaskRepository;

    @Autowired
    private SprintRepository sprintRepository;

    private Account getSessionAccount(HttpSession session) {
        return (Account) session.getAttribute("account");
    }

    private boolean isStaff(HttpSession session) {
        Account account = getSessionAccount(session);
        return account != null && "Staff".equalsIgnoreCase(account.getRole());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private LocalDateTime parseDateTimeInput(String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(normalized);
    }

    private int resolveSemesterId(Integer semesterId) {
        if (semesterId != null && semesterId > 0) {
            return semesterId;
        }
        Semester current = semesterRepository.findCurrentSemester();
        if (current != null) {
            return current.getSemesterId();
        }
        Semester fallback = semesterRepository.findById(1);
        return fallback != null ? fallback.getSemesterId() : 1;
    }

    private void bindCommon(Model model, HttpSession session, int semesterId) {
        Object fullName = session.getAttribute("fullName");
        model.addAttribute("staffName", fullName != null ? fullName : "Nhân viên");
        model.addAttribute("selectedSemesterId", semesterId);
        model.addAttribute("semesters", semesterRepository.findAll());
        model.addAttribute("projectOverview", projectRepository.findProjectOverviewBySemester(semesterId));
        model.addAttribute("pendingEditRequests", projectEditRequestRepository.findPendingRequests());
    }

    @GetMapping
    public String index(@RequestParam(name = "semesterId", required = false) Integer semesterId,
            Model model,
            HttpSession session) {
        if (!isStaff(session)) {
            return "redirect:/acc/log";
        }
        int resolvedSemesterId = resolveSemesterId(semesterId);
        bindCommon(model, session, resolvedSemesterId);
        return "staff/projects";
    }

    @PostMapping("/create")
    public String createProject(@RequestParam("groupId") int groupId,
            @RequestParam("topicSource") String topicSource,
            @RequestParam(name = "projectName", required = false) String projectName,
            @RequestParam(name = "description", required = false) String description,
            @RequestParam(name = "startDate", required = false) String startDate,
            @RequestParam(name = "endDate", required = false) String endDate,
            @RequestParam(name = "semesterId", required = false) Integer semesterId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isStaff(session)) {
            return "redirect:/acc/log";
        }

        int resolvedSemesterId = resolveSemesterId(semesterId);
        String normalizedSource = normalize(topicSource).toUpperCase();
        String normalizedName = normalize(projectName);
        String normalizedDescription = normalize(description);
        LocalDateTime normalizedStartDate;
        LocalDateTime normalizedEndDate;
        try {
            normalizedStartDate = parseDateTimeInput(startDate);
            normalizedEndDate = parseDateTimeInput(endDate);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Thời gian bắt đầu/kết thúc không hợp lệ.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        if (!"INDIA".equals(normalizedSource)
                && !"LECTURER".equals(normalizedSource)
                && !"STUDENT".equals(normalizedSource)) {
            redirectAttributes.addFlashAttribute("error", "Nguồn nội dung project không hợp lệ.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        Project existed = projectRepository.findByGroupId(groupId);
        if (existed != null && existed.getProjectId() > 0) {
            redirectAttributes.addFlashAttribute("error", "Nhóm này đã có project.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        int approvalStatus;
        boolean studentCanEdit = false;
        if ("STUDENT".equals(normalizedSource)) {
            approvalStatus = Project.STATUS_WAITING_STUDENT_CONTENT;
            if (normalizedName.isEmpty()) {
                normalizedName = "Đề tài do học viên đề xuất";
            }
        } else {
            approvalStatus = Project.STATUS_APPROVED;
            if (normalizedName.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Vui lòng nhập tên project.");
                return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
            }
            if (normalizedStartDate == null || normalizedEndDate == null) {
                redirectAttributes.addFlashAttribute("error", "Project đã duyệt phải có thời gian bắt đầu và kết thúc.");
                return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
            }
            if (!normalizedEndDate.isAfter(normalizedStartDate)) {
                redirectAttributes.addFlashAttribute("error", "Thời gian kết thúc phải sau thời gian bắt đầu.");
                return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
            }
        }

        int projectId = projectRepository.createProjectByStaff(
                groupId,
                normalizedName,
                normalizedDescription,
                normalizedSource,
                approvalStatus,
                approvalStatus == Project.STATUS_APPROVED ? normalizedStartDate : null,
                approvalStatus == Project.STATUS_APPROVED ? normalizedEndDate : null,
                studentCanEdit);
        if (projectId <= 0) {
            redirectAttributes.addFlashAttribute("error", "Không thể tạo project. Vui lòng kiểm tra dữ liệu.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        redirectAttributes.addFlashAttribute("success", "Đã tạo project thành công cho nhóm.");
        return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
    }

    @PostMapping("/requests/{requestId}/approve")
    public String approveEditRequest(@PathVariable("requestId") int requestId,
            @RequestParam(name = "semesterId", required = false) Integer semesterId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isStaff(session)) {
            return "redirect:/acc/log";
        }

        int resolvedSemesterId = resolveSemesterId(semesterId);
        ProjectEditRequest request = projectEditRequestRepository.findById(requestId);
        if (request == null || !"PENDING".equalsIgnoreCase(request.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "Yêu cầu cấp quyền không hợp lệ hoặc đã xử lý.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        Account account = getSessionAccount(session);
        Staff staff = account != null ? staffRepository.findByAccountId(account.getId()) : null;
        int staffId = staff != null ? staff.getStaffId() : 0;

        int updated = projectEditRequestRepository.approve(requestId, staffId);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Không thể duyệt yêu cầu cấp quyền.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        projectRepository.setStudentCanEdit(request.getProjectId(), true);
        redirectAttributes.addFlashAttribute("success", "Đã cấp quyền cập nhật nội dung project cho học viên.");
        return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
    }

    @PostMapping("/requests/{requestId}/reject")
    public String rejectEditRequest(@PathVariable("requestId") int requestId,
            @RequestParam("reason") String reason,
            @RequestParam(name = "semesterId", required = false) Integer semesterId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isStaff(session)) {
            return "redirect:/acc/log";
        }

        int resolvedSemesterId = resolveSemesterId(semesterId);
        String normalizedReason = normalize(reason);
        if (normalizedReason.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng nhập lý do từ chối.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        ProjectEditRequest request = projectEditRequestRepository.findById(requestId);
        if (request == null || !"PENDING".equalsIgnoreCase(request.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "Yêu cầu cấp quyền không hợp lệ hoặc đã xử lý.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        Account account = getSessionAccount(session);
        Staff staff = account != null ? staffRepository.findByAccountId(account.getId()) : null;
        int staffId = staff != null ? staff.getStaffId() : 0;

        int updated = projectEditRequestRepository.reject(requestId, staffId, normalizedReason);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Không thể từ chối yêu cầu cấp quyền.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        projectRepository.setStudentCanEdit(request.getProjectId(), false);
        redirectAttributes.addFlashAttribute("success", "Đã từ chối yêu cầu cấp quyền cập nhật project.");
        return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
    }

    @GetMapping("/{projectId}/performance")
    public String performance(@PathVariable("projectId") int projectId,
            @RequestParam(name = "semesterId", required = false) Integer semesterId,
            Model model,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isStaff(session)) {
            return "redirect:/acc/log";
        }

        Project project = projectRepository.findById(projectId);
        if (project == null || project.getProjectId() <= 0) {
            int resolvedSemesterId = resolveSemesterId(semesterId);
            redirectAttributes.addFlashAttribute("error", "Project không tồn tại.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        int resolvedSemesterId = resolveSemesterId(
                semesterId != null ? semesterId : project.getSemesterId());
        Object fullName = session.getAttribute("fullName");
        model.addAttribute("staffName", fullName != null ? fullName : "Nhân viên");
        model.addAttribute("selectedSemesterId", resolvedSemesterId);
        model.addAttribute("project", project);

        sprintRepository.closeExpiredSprintsAndFailTasks(project.getProjectId());
        model.addAttribute("sprints", sprintRepository.findByProject(project.getProjectId()));
        model.addAttribute("taskHistory", projectTaskRepository.findByProject(project.getProjectId()));
        model.addAttribute("overallPerformance",
                projectTaskRepository.findMemberPerformanceOverallByProject(project.getProjectId()));
        model.addAttribute("sprintPerformance",
                projectTaskRepository.findMemberPerformanceBySprint(project.getProjectId()));
        return "staff/performance";
    }
}

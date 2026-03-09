package com.example.pms.controller;

import com.example.pms.model.Account;
import com.example.pms.model.Project;
import com.example.pms.model.ProjectChangeRequest;
import com.example.pms.model.ProjectEditRequest;
import com.example.pms.model.Semester;
import com.example.pms.model.Staff;
import com.example.pms.repository.ProjectChangeRequestRepository;
import com.example.pms.repository.ProjectEditRequestRepository;
import com.example.pms.repository.ProjectRepository;
import com.example.pms.repository.ProjectTaskRepository;
import com.example.pms.repository.SemesterRepository;
import com.example.pms.repository.SprintRepository;
import com.example.pms.repository.StaffRepository;
import com.example.pms.service.MailService;
import com.example.pms.util.RoleDisplayUtil;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
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
    private ProjectChangeRequestRepository projectChangeRequestRepository;

    @Autowired
    private ProjectTaskRepository projectTaskRepository;

    @Autowired
    private SprintRepository sprintRepository;

    @Autowired
    private MailService mailService;

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
        model.addAttribute("staffName", fullName != null ? fullName : "Nh\u00e2n vi\u00ean");
        model.addAttribute("displayName", fullName != null ? fullName : "Nh\u00e2n vi\u00ean");
        model.addAttribute("displayRole", RoleDisplayUtil.toDisplayRole("Staff"));
        model.addAttribute("selectedSemesterId", semesterId);
        model.addAttribute("semesters", semesterRepository.findAll());
        model.addAttribute("projectOverview", projectRepository.findProjectOverviewBySemester(semesterId));
        model.addAttribute("pendingEditRequests", projectEditRequestRepository.findPendingRequests());
        model.addAttribute("pendingChangeRequests", projectChangeRequestRepository.findPendingForStaff());
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
            redirectAttributes.addFlashAttribute("error", "Thá»i gian báº¯t Ä‘áº§u/káº¿t thÃºc khÃ´ng há»£p lá»‡.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        if (!"INDIA".equals(normalizedSource)
                && !"LECTURER".equals(normalizedSource)
                && !"STUDENT".equals(normalizedSource)) {
            redirectAttributes.addFlashAttribute("error", "Nguá»“n ná»™i dung project khÃ´ng há»£p lá»‡.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        Project existed = projectRepository.findByGroupId(groupId);
        if (existed != null && existed.getProjectId() > 0) {
            redirectAttributes.addFlashAttribute("error", "NhÃ³m nÃ y Ä‘Ã£ cÃ³ project.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        int approvalStatus;
        boolean studentCanEdit = false;
        if ("STUDENT".equals(normalizedSource)) {
            approvalStatus = Project.STATUS_WAITING_STUDENT_CONTENT;
            if (normalizedName.isEmpty()) {
                normalizedName = "Äá» tÃ i do há»c viÃªn Ä‘á» xuáº¥t";
            }
        } else {
            approvalStatus = Project.STATUS_APPROVED;
            if (normalizedName.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Vui lÃ²ng nháº­p tÃªn project.");
                return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
            }
            if (normalizedStartDate == null || normalizedEndDate == null) {
                redirectAttributes.addFlashAttribute("error", "Project Ä‘Ã£ duyá»‡t pháº£i cÃ³ thá»i gian báº¯t Ä‘áº§u vÃ  káº¿t thÃºc.");
                return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
            }
            if (!normalizedEndDate.isAfter(normalizedStartDate)) {
                redirectAttributes.addFlashAttribute("error", "Thá»i gian káº¿t thÃºc pháº£i sau thá»i gian báº¯t Ä‘áº§u.");
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
            redirectAttributes.addFlashAttribute("error", "KhÃ´ng thá»ƒ táº¡o project. Vui lÃ²ng kiá»ƒm tra dá»¯ liá»‡u.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        redirectAttributes.addFlashAttribute("success", "ÄÃ£ táº¡o project thÃ nh cÃ´ng cho nhÃ³m.");
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
            redirectAttributes.addFlashAttribute("error", "YÃªu cáº§u cáº¥p quyá»n khÃ´ng há»£p lá»‡ hoáº·c Ä‘Ã£ xá»­ lÃ½.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        Account account = getSessionAccount(session);
        Staff staff = account != null ? staffRepository.findByAccountId(account.getId()) : null;
        int staffId = staff != null ? staff.getStaffId() : 0;

        int updated = projectEditRequestRepository.approve(requestId, staffId);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "KhÃ´ng thá»ƒ duyá»‡t yÃªu cáº§u cáº¥p quyá»n.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        projectRepository.setStudentCanEdit(request.getProjectId(), true);
        redirectAttributes.addFlashAttribute("success", "ÄÃ£ cáº¥p quyá»n cáº­p nháº­t ná»™i dung project cho há»c viÃªn.");
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
            redirectAttributes.addFlashAttribute("error", "Vui lÃ²ng nháº­p lÃ½ do tá»« chá»‘i.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        ProjectEditRequest request = projectEditRequestRepository.findById(requestId);
        if (request == null || !"PENDING".equalsIgnoreCase(request.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "YÃªu cáº§u cáº¥p quyá»n khÃ´ng há»£p lá»‡ hoáº·c Ä‘Ã£ xá»­ lÃ½.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        Account account = getSessionAccount(session);
        Staff staff = account != null ? staffRepository.findByAccountId(account.getId()) : null;
        int staffId = staff != null ? staff.getStaffId() : 0;

        int updated = projectEditRequestRepository.reject(requestId, staffId, normalizedReason);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "KhÃ´ng thá»ƒ tá»« chá»‘i yÃªu cáº§u cáº¥p quyá»n.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        projectRepository.setStudentCanEdit(request.getProjectId(), false);
        redirectAttributes.addFlashAttribute("success", "ÄÃ£ tá»« chá»‘i yÃªu cáº§u cáº¥p quyá»n cáº­p nháº­t project.");
        return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
    }

    @PostMapping("/change-requests/{requestId}/approve")
    public String approveChangeRequest(@PathVariable("requestId") int requestId,
            @RequestParam(name = "semesterId", required = false) Integer semesterId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isStaff(session)) {
            return "redirect:/acc/log";
        }

        int resolvedSemesterId = resolveSemesterId(semesterId);
        ProjectChangeRequest request = projectChangeRequestRepository.findById(requestId);
        if (request == null || !ProjectChangeRequest.STATUS_PENDING_STAFF.equalsIgnoreCase(request.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "YÃªu cáº§u Ä‘á»•i project khÃ´ng há»£p lá»‡ hoáº·c Ä‘Ã£ xá»­ lÃ½.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        Project project = projectRepository.findById(request.getProjectId());
        if (project == null) {
            redirectAttributes.addFlashAttribute("error", "Project khÃ´ng cÃ²n tá»“n táº¡i.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }
        if (projectTaskRepository.hasDoneTasksByProject(project.getProjectId())) {
            redirectAttributes.addFlashAttribute("error", "Project Ä‘Ã£ cÃ³ cÃ´ng viá»‡c hoÃ n thÃ nh nÃªn khÃ´ng thá»ƒ chuyá»ƒn yÃªu cáº§u Ä‘á»•i Ä‘á» tÃ i cho giáº£ng viÃªn.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        Account account = getSessionAccount(session);
        Staff staff = account != null ? staffRepository.findByAccountId(account.getId()) : null;
        int staffId = staff != null ? staff.getStaffId() : 0;

        int updated = projectChangeRequestRepository.approveByStaff(requestId, staffId);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "KhÃ´ng thá»ƒ chuyá»ƒn yÃªu cáº§u Ä‘á»•i project sang giáº£ng viÃªn.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        Set<String> uniqueEmails = new LinkedHashSet<>();
        for (String email : projectRepository.findLecturerEmailsForProject(project.getProjectId())) {
            String normalizedEmail = normalize(email).toLowerCase();
            if (!normalizedEmail.isEmpty()) {
                uniqueEmails.add(normalizedEmail);
            }
        }
        int sent = 0;
        for (String email : uniqueEmails) {
            try {
                mailService.sendProjectChangeReviewRequest(
                        email,
                        normalize(project.getProjectName()).isEmpty() ? "Project chÆ°a cÃ³ tÃªn" : project.getProjectName(),
                        request.getProposedProjectName(),
                        request.getGroupName());
                sent++;
            } catch (Exception ex) {
                // Keep workflow successful even if some email fails.
            }
        }

        if (uniqueEmails.isEmpty()) {
            redirectAttributes.addFlashAttribute("success",
                    "ÄÃ£ chuyá»ƒn yÃªu cáº§u Ä‘á»•i project sang bÆ°á»›c giáº£ng viÃªn duyá»‡t. Hiá»‡n chÆ°a cÃ³ giáº£ng viÃªn nÃ o nháº­n Ä‘Æ°á»£c email.");
        } else {
            redirectAttributes.addFlashAttribute("success",
                    "ÄÃ£ chuyá»ƒn yÃªu cáº§u Ä‘á»•i project sang giáº£ng viÃªn duyá»‡t. Email Ä‘Ã£ gá»­i: " + sent + "/" + uniqueEmails.size() + ".");
        }
        return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
    }

    @PostMapping("/change-requests/{requestId}/reject")
    public String rejectChangeRequest(@PathVariable("requestId") int requestId,
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
            redirectAttributes.addFlashAttribute("error", "Vui lÃ²ng nháº­p lÃ½ do tá»« chá»‘i yÃªu cáº§u Ä‘á»•i project.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        ProjectChangeRequest request = projectChangeRequestRepository.findById(requestId);
        if (request == null || !ProjectChangeRequest.STATUS_PENDING_STAFF.equalsIgnoreCase(request.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "YÃªu cáº§u Ä‘á»•i project khÃ´ng há»£p lá»‡ hoáº·c Ä‘Ã£ xá»­ lÃ½.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        Account account = getSessionAccount(session);
        Staff staff = account != null ? staffRepository.findByAccountId(account.getId()) : null;
        int staffId = staff != null ? staff.getStaffId() : 0;

        int updated = projectChangeRequestRepository.rejectByStaff(requestId, staffId, normalizedReason);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "KhÃ´ng thá»ƒ tá»« chá»‘i yÃªu cáº§u Ä‘á»•i project.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        redirectAttributes.addFlashAttribute("success", "ÄÃ£ tá»« chá»‘i yÃªu cáº§u Ä‘á»•i project.");
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
            redirectAttributes.addFlashAttribute("error", "Project khÃ´ng tá»“n táº¡i.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        int resolvedSemesterId = resolveSemesterId(
                semesterId != null ? semesterId : project.getSemesterId());
        Object fullName = session.getAttribute("fullName");
        model.addAttribute("staffName", fullName != null ? fullName : "Nh\u00e2n vi\u00ean");
        model.addAttribute("displayName", fullName != null ? fullName : "Nh\u00e2n vi\u00ean");
        model.addAttribute("displayRole", RoleDisplayUtil.toDisplayRole("Staff"));
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

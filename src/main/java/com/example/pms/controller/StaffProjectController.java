package com.example.pms.controller;

import com.example.pms.dto.AssignProjectDTO;
import com.example.pms.dto.CreateTemplateDTO;
import com.example.pms.model.Account;
import com.example.pms.model.Project;
import com.example.pms.model.ProjectChangeRequest;
import com.example.pms.model.ProjectEditRequest;
import com.example.pms.model.Semester;
import com.example.pms.model.Staff;
import com.example.pms.repository.GroupRepository;
import com.example.pms.repository.ProjectChangeRequestRepository;
import com.example.pms.repository.ProjectEditRequestRepository;
import com.example.pms.repository.ProjectRepository;
import com.example.pms.repository.ProjectTaskRepository;
import com.example.pms.repository.SemesterRepository;
import com.example.pms.repository.SprintRepository;
import com.example.pms.repository.StaffRepository;
import com.example.pms.service.MailService;
import com.example.pms.service.ProjectTemplateService;
import com.example.pms.service.StudentNotificationService;
import com.example.pms.util.RoleDisplayUtil;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/staff/projects")
public class StaffProjectController {

    private static final long MAX_TEMPLATE_IMAGE_BYTES = 5L * 1024L * 1024L;
    private static final String TEMPLATE_IMAGE_DIR = "uploads/project-templates";
    private static final Set<String> ALLOWED_TEMPLATE_IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp");

    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectTemplateService projectTemplateService;

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

    @Autowired
    private StudentNotificationService studentNotificationService;

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

    private String normalizeFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return "image";
        }
        return fileName.trim().replaceAll("[\\\\/]+", "_").replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String extractExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase();
    }

    private boolean isAllowedTemplateImage(String fileName) {
        return ALLOWED_TEMPLATE_IMAGE_EXTENSIONS.contains(extractExtension(fileName));
    }

    private Path resolveTemplateImageDir() throws IOException {
        Path dir = Paths.get(TEMPLATE_IMAGE_DIR);
        Files.createDirectories(dir);
        return dir;
    }

    private String storeTemplateImage(MultipartFile imageFile) throws IOException {
        if (imageFile == null || imageFile.isEmpty()) {
            return null;
        }
        String originalName = imageFile.getOriginalFilename();
        if (!isAllowedTemplateImage(originalName)) {
            throw new IOException("Unsupported image format.");
        }
        if (imageFile.getSize() > MAX_TEMPLATE_IMAGE_BYTES) {
            throw new IOException("Image is too large.");
        }
        String storedName = UUID.randomUUID().toString().replace("-", "") + "_" + normalizeFileName(originalName);
        Path target = resolveTemplateImageDir().resolve(storedName);
        Files.copy(imageFile.getInputStream(), target);
        return "/staff/projects/templates/images/" + storedName;
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
        model.addAttribute("projectTemplates", projectTemplateService.findTemplates(semesterId));
        model.addAttribute("availableGroups", projectTemplateService.findAvailableGroups(semesterId));
        java.util.List<Project> overview = projectRepository.findProjectOverviewBySemester(semesterId);
        model.addAttribute("projectOverview", overview);
        Map<Integer, String> classOptions = new LinkedHashMap<>();
        for (Project item : overview) {
            if (item == null || item.getProjectId() > 0) {
                continue;
            }
            Integer classId = item.getClassId();
            String className = item.getClassName();
            if (classId != null && className != null && !classOptions.containsKey(classId)) {
                classOptions.put(classId, className);
            }
        }
        model.addAttribute("classOptions", classOptions);
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

    @PostMapping("/templates/create")
        public String createTemplate(CreateTemplateDTO dto,
            @RequestParam(name = "imageFile", required = false) MultipartFile imageFile,
            @RequestParam(name = "semesterId", required = false) Integer semesterId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isStaff(session)) {
            return "redirect:/acc/log";
        }

        int resolvedSemesterId = resolveSemesterId(semesterId != null ? semesterId : dto.getSemesterId());
        String normalizedName = normalize(dto.getName());
        String normalizedSource = normalize(dto.getSource()).toUpperCase();
        String normalizedDescription = normalize(dto.getDescription());
        if (normalizedName.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please enter a template name.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }
        if (!"INDIA".equals(normalizedSource)
                && !"LECTURER".equals(normalizedSource)
                && !"STUDENT".equals(normalizedSource)) {
            redirectAttributes.addFlashAttribute("error", "Invalid template source.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        Account account = getSessionAccount(session);
        Staff staff = account != null ? staffRepository.findByAccountId(account.getId()) : null;
        dto.setName(normalizedName);
        dto.setDescription(normalizedDescription);
        dto.setSource(normalizedSource);
        dto.setSemesterId(resolvedSemesterId);
        dto.setStaffId(staff != null ? staff.getStaffId() : 0);

        try {
            dto.setImageUrl(storeTemplateImage(imageFile));
        } catch (IOException ex) {
            redirectAttributes.addFlashAttribute("error", "Unable to save template image: " + ex.getMessage());
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        int templateId = projectTemplateService.createTemplate(dto);
        if (templateId <= 0) {
            redirectAttributes.addFlashAttribute("error", "Unable to create project template.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        redirectAttributes.addFlashAttribute("success", "Project template created successfully.");
        return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
    }

    @GetMapping("/templates/images/{fileName:.+}")
    public ResponseEntity<Resource> serveTemplateImage(@PathVariable("fileName") String fileName) {
        try {
            Path filePath = Paths.get(TEMPLATE_IMAGE_DIR).resolve(fileName).normalize();
            Path basePath = Paths.get(TEMPLATE_IMAGE_DIR).toAbsolutePath().normalize();
            if (!filePath.toAbsolutePath().normalize().startsWith(basePath) || !Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }
            String contentType = Files.probeContentType(filePath);
            MediaType mediaType = contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM;
            return ResponseEntity.ok().contentType(mediaType).body(resource);
        } catch (Exception ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/assign")
    public String assignProjects(AssignProjectDTO dto,
            @RequestParam(name = "semesterId", required = false) Integer semesterId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isStaff(session)) {
            return "redirect:/acc/log";
        }

        int resolvedSemesterId = resolveSemesterId(semesterId != null ? semesterId : dto.getSemesterId());
        dto.setSemesterId(resolvedSemesterId);
        if (dto.getGroupIds() == null || dto.getGroupIds().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please select at least one group.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        List<Integer> createdProjectIds = projectTemplateService.assignProjects(dto);
        if (createdProjectIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "No new project was created. The selected groups may already have a project or a pending assignment.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        int notified = 0;
        for (Integer projectId : createdProjectIds) {
            Project createdProject = projectRepository.findById(projectId);
            if (createdProject != null) {
                studentNotificationService.notifyProjectAssigned(createdProject);
                notified++;
            }
        }

        redirectAttributes.addFlashAttribute("success", "Assigned template to " + createdProjectIds.size() + " group(s). Notifications sent: " + notified + ".");
        return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
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
            redirectAttributes.addFlashAttribute("error", "Invalid start/end time.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        if (!"INDIA".equals(normalizedSource)
                && !"LECTURER".equals(normalizedSource)
                && !"STUDENT".equals(normalizedSource)) {
            redirectAttributes.addFlashAttribute("error", "Invalid project content source.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        Project existed = projectRepository.findByGroupId(groupId);
        if (existed != null && existed.getProjectId() > 0) {
            redirectAttributes.addFlashAttribute("error", "This group already has a project.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        int approvalStatus;
        boolean studentCanEdit = false;
        if ("STUDENT".equals(normalizedSource)) {
            approvalStatus = Project.STATUS_WAITING_STUDENT_CONTENT;
            if (normalizedName.isEmpty()) {
                normalizedName = "Student-proposed topic";
            }
        } else {
            approvalStatus = Project.STATUS_APPROVED;
            if (normalizedName.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Please enter a project name.");
                return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
            }
            if (normalizedStartDate == null || normalizedEndDate == null) {
                redirectAttributes.addFlashAttribute("error", "Approved projects must have start and end dates.");
                return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
            }
            if (!normalizedEndDate.isAfter(normalizedStartDate)) {
                redirectAttributes.addFlashAttribute("error", "End time must be after start time.");
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
            redirectAttributes.addFlashAttribute("error", "Unable to create project. Please check the data.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        Project createdProject = projectRepository.findById(projectId);
        if (createdProject != null) {
            studentNotificationService.notifyProjectAssigned(createdProject);
        }
        redirectAttributes.addFlashAttribute("success", "Project created successfully for the group.");
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
            redirectAttributes.addFlashAttribute("error", "Edit access request is invalid or already processed.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        Account account = getSessionAccount(session);
        Staff staff = account != null ? staffRepository.findByAccountId(account.getId()) : null;
        int staffId = staff != null ? staff.getStaffId() : 0;

        int updated = projectEditRequestRepository.approve(requestId, staffId);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Unable to approve edit access request.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        projectRepository.setStudentCanEdit(request.getProjectId(), true);
        Project project = projectRepository.findById(request.getProjectId());
        if (project != null) {
            studentNotificationService.notifyEditAccessGranted(project);
        }
        redirectAttributes.addFlashAttribute("success", "Edit access granted to students for project content.");
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
            redirectAttributes.addFlashAttribute("error", "Please provide a rejection reason.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        ProjectEditRequest request = projectEditRequestRepository.findById(requestId);
        if (request == null || !"PENDING".equalsIgnoreCase(request.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "Edit access request is invalid or already processed.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        Account account = getSessionAccount(session);
        Staff staff = account != null ? staffRepository.findByAccountId(account.getId()) : null;
        int staffId = staff != null ? staff.getStaffId() : 0;

        int updated = projectEditRequestRepository.reject(requestId, staffId, normalizedReason);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Unable to reject edit access request.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        projectRepository.setStudentCanEdit(request.getProjectId(), false);
        Project project = projectRepository.findById(request.getProjectId());
        if (project != null) {
            studentNotificationService.notifyEditAccessRejected(project, normalizedReason);
        }
        redirectAttributes.addFlashAttribute("success", "Edit access request rejected.");
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
            redirectAttributes.addFlashAttribute("error", "Project change request is invalid or already processed.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        Project project = projectRepository.findById(request.getProjectId());
        if (project == null) {
            redirectAttributes.addFlashAttribute("error", "Project no longer exists.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }
        if (projectTaskRepository.hasDoneTasksByProject(project.getProjectId())) {
            redirectAttributes.addFlashAttribute("error", "Projects with completed tasks cannot forward change requests to the lecturer.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        Account account = getSessionAccount(session);
        Staff staff = account != null ? staffRepository.findByAccountId(account.getId()) : null;
        int staffId = staff != null ? staff.getStaffId() : 0;

        int updated = projectChangeRequestRepository.approveByStaff(requestId, staffId);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Unable to forward the project change request to the lecturer.");
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
                        normalize(project.getProjectName()).isEmpty() ? "Untitled project" : project.getProjectName(),
                        request.getProposedProjectName(),
                        request.getGroupName());
                sent++;
            } catch (Exception ex) {
                // Keep workflow successful even if some email fails.
            }
        }

        if (uniqueEmails.isEmpty()) {
            redirectAttributes.addFlashAttribute("success",
                    "Project change request forwarded for lecturer approval. No lecturer has received the email yet.");
        } else {
            redirectAttributes.addFlashAttribute("success",
                    "Project change request forwarded for lecturer approval. Emails sent: " + sent + "/" + uniqueEmails.size() + ".");
        }
        studentNotificationService.notifyProjectChangeForwarded(project);
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
            redirectAttributes.addFlashAttribute("error", "Please provide a rejection reason for the project change request.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        ProjectChangeRequest request = projectChangeRequestRepository.findById(requestId);
        if (request == null || !ProjectChangeRequest.STATUS_PENDING_STAFF.equalsIgnoreCase(request.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "Project change request is invalid or already processed.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        Account account = getSessionAccount(session);
        Staff staff = account != null ? staffRepository.findByAccountId(account.getId()) : null;
        int staffId = staff != null ? staff.getStaffId() : 0;

        int updated = projectChangeRequestRepository.rejectByStaff(requestId, staffId, normalizedReason);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Unable to reject the project change request.");
            return "redirect:/staff/projects?semesterId=" + resolvedSemesterId;
        }

        Project project = projectRepository.findById(request.getProjectId());
        if (project != null) {
            studentNotificationService.notifyProjectChangeRejected(project, normalizedReason);
        }
        redirectAttributes.addFlashAttribute("success", "Project change request rejected.");
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
            redirectAttributes.addFlashAttribute("error", "Project does not exist.");
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

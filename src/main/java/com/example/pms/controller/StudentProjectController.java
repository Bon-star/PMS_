package com.example.pms.controller;

import com.example.pms.model.Account;
import com.example.pms.model.Classes;
import com.example.pms.model.Group;
import com.example.pms.model.Project;
import com.example.pms.model.ProjectChangeRequest;
import com.example.pms.model.ProjectEditRequest;
import com.example.pms.model.ProjectTask;
import com.example.pms.model.Semester;
import com.example.pms.model.Sprint;
import com.example.pms.model.Student;
import com.example.pms.repository.ClassRepository;
import com.example.pms.repository.GroupInvitationRepository;
import com.example.pms.repository.GroupMemberRepository;
import com.example.pms.repository.GroupRepository;
import com.example.pms.repository.ProjectChangeRequestRepository;
import com.example.pms.repository.ProjectCommentRepository;
import com.example.pms.repository.ProjectEditRequestRepository;
import com.example.pms.repository.ProjectRepository;
import com.example.pms.repository.ProjectTaskRepository;
import com.example.pms.repository.SemesterRepository;
import com.example.pms.repository.SprintRepository;
import com.example.pms.service.MailService;
import com.example.pms.service.StudentNotificationService;
import com.example.pms.util.RoleDisplayUtil;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/student/project")
public class StudentProjectController {

    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private GroupInvitationRepository groupInvitationRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectEditRequestRepository projectEditRequestRepository;

    @Autowired
    private ProjectChangeRequestRepository projectChangeRequestRepository;

    @Autowired
    private ProjectCommentRepository projectCommentRepository;

    @Autowired
    private ProjectTaskRepository projectTaskRepository;

    @Autowired
    private SprintRepository sprintRepository;

    @Autowired
    private MailService mailService;

    @Autowired
    private StudentNotificationService studentNotificationService;

    private static final long MAX_UPLOAD_BYTES = 10L * 1024L * 1024L;
    private static final int MAX_UPLOAD_FILES = 50;
    private static final long MAX_CODE_VIEW_BYTES = 200L * 1024L;
    private static final String TASK_UPLOAD_DIR = "uploads/task-submissions";

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "zip", "rar", "7z",
            "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx",
            "txt", "md", "java", "js", "ts", "py", "html", "css", "json", "xml", "yml", "yaml", "sql",
            "png", "jpg", "jpeg", "gif", "webp");

    private static final Set<String> CODE_EXTENSIONS = Set.of(
            "txt", "md", "java", "js", "ts", "py", "html", "css", "json", "xml", "yml", "yaml", "sql");

    public static class TaskAttachment {
        private final String displayName;
        private final String storedName;
        private final String contentType;
        private final long size;
        private final boolean viewable;

        public TaskAttachment(String displayName, String storedName, String contentType, long size, boolean viewable) {
            this.displayName = displayName;
            this.storedName = storedName;
            this.contentType = contentType;
            this.size = size;
            this.viewable = viewable;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getStoredName() {
            return storedName;
        }

        public String getContentType() {
            return contentType;
        }

        public long getSize() {
            return size;
        }

        public boolean isViewable() {
            return viewable;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalizeFileName(String originalName) {
        String safe = originalName == null ? "file" : Paths.get(originalName).getFileName().toString();
        safe = safe.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.length() > 80) {
            safe = safe.substring(0, 80);
        }
        if (safe.isEmpty()) {
            safe = "file";
        }
        return safe;
    }

    private String normalizeDisplayPath(String originalName) {
        if (originalName == null) {
            return "file";
        }
        String normalized = originalName.replace("\\", "/");
        String[] parts = normalized.split("/");
        List<String> cleaned = new ArrayList<>();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            String safe = part.replaceAll("[^A-Za-z0-9._-]", "_");
            if (safe.isBlank() || ".".equals(safe) || "..".equals(safe)) {
                continue;
            }
            if (safe.length() > 80) {
                safe = safe.substring(0, 80);
            }
            cleaned.add(safe);
        }
        if (cleaned.isEmpty()) {
            return "file";
        }
        String joined = String.join("/", cleaned);
        if (joined.length() > 160) {
            joined = joined.substring(joined.length() - 160);
        }
        return joined;
    }

    private String fileExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        String baseName = fileName;
        try {
            baseName = Paths.get(fileName).getFileName().toString();
        } catch (Exception ex) {
            baseName = fileName;
        }
        int idx = baseName.lastIndexOf('.');
        if (idx < 0 || idx == baseName.length() - 1) {
            return "";
        }
        return baseName.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isAllowedFile(String fileName) {
        String ext = fileExtension(fileName);
        return ext.isEmpty() ? false : ALLOWED_EXTENSIONS.contains(ext);
    }

    private boolean isViewableCodeFile(String fileName) {
        String ext = fileExtension(fileName);
        return ext.isEmpty() ? false : CODE_EXTENSIONS.contains(ext);
    }

    private Path resolveTaskUploadDir(int taskId) throws IOException {
        Path baseDir = Paths.get(System.getProperty("user.dir"), TASK_UPLOAD_DIR, String.valueOf(taskId));
        if (!Files.exists(baseDir)) {
            Files.createDirectories(baseDir);
        }
        return baseDir;
    }

    private String serializeAttachments(List<TaskAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (TaskAttachment attachment : attachments) {
            if (attachment == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append("name=").append(URLEncoder.encode(attachment.getDisplayName(), StandardCharsets.UTF_8));
            builder.append("&stored=").append(URLEncoder.encode(attachment.getStoredName(), StandardCharsets.UTF_8));
            builder.append("&type=").append(URLEncoder.encode(
                    attachment.getContentType() == null ? "" : attachment.getContentType(), StandardCharsets.UTF_8));
            builder.append("&size=").append(attachment.getSize());
        }
        return builder.toString();
    }

    private List<TaskAttachment> parseAttachments(String raw) {
        List<TaskAttachment> result = new ArrayList<>();
        if (isBlank(raw)) {
            return result;
        }
        String[] lines = raw.split("\\r?\\n");
        for (String line : lines) {
            if (isBlank(line)) {
                continue;
            }
            String displayName = "";
            String storedName = "";
            String type = "";
            long size = 0L;
            String[] parts = line.split("&");
            for (String part : parts) {
                int eq = part.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String key = part.substring(0, eq);
                String value = part.substring(eq + 1);
                String decoded = URLDecoder.decode(value, StandardCharsets.UTF_8);
                switch (key) {
                    case "name":
                        displayName = decoded;
                        break;
                    case "stored":
                        storedName = decoded;
                        break;
                    case "type":
                        type = decoded;
                        break;
                    case "size":
                        try {
                            size = Long.parseLong(decoded);
                        } catch (NumberFormatException ex) {
                            size = 0L;
                        }
                        break;
                    default:
                        break;
                }
            }
            if (!isBlank(storedName)) {
                boolean viewable = isViewableCodeFile(displayName);
                result.add(new TaskAttachment(displayName, storedName, type, size, viewable));
            }
        }
        return result;
    }

    private void deleteAttachments(int taskId, List<TaskAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        for (TaskAttachment attachment : attachments) {
            if (attachment == null || isBlank(attachment.getStoredName())) {
                continue;
            }
            try {
                Path path = Paths.get(System.getProperty("user.dir"), TASK_UPLOAD_DIR,
                        String.valueOf(taskId), attachment.getStoredName());
                Files.deleteIfExists(path);
            } catch (Exception ex) {
                // best-effort cleanup
            }
        }
    }

    private List<TaskAttachment> storeAttachments(int taskId, MultipartFile[] files, RedirectAttributes redirectAttributes) {
        List<TaskAttachment> saved = new ArrayList<>();
        if (files == null || files.length == 0) {
            return saved;
        }

        long totalBytes = 0L;
        int fileCount = 0;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            fileCount++;
            totalBytes += file.getSize();
            if (file.getSize() > MAX_UPLOAD_BYTES) {
                String displayName = normalizeDisplayPath(file.getOriginalFilename());
                redirectAttributes.addFlashAttribute("error", "File " + displayName + " is too large.");
                return null;
            }
            String originalName = file.getOriginalFilename();
            String displayName = normalizeDisplayPath(originalName);
            if (!isAllowedFile(originalName)) {
                redirectAttributes.addFlashAttribute("error",
                        "File " + displayName + " is not in the allowed list.");
                return null;
            }
        }
        if (fileCount == 0) {
            return saved;
        }
        if (fileCount > MAX_UPLOAD_FILES) {
            redirectAttributes.addFlashAttribute("error",
                    "You can upload at most " + MAX_UPLOAD_FILES + " files per submission.");
            return null;
        }
        if (totalBytes > MAX_UPLOAD_BYTES * 2) {
            redirectAttributes.addFlashAttribute("error", "Total file size is too large.");
            return null;
        }

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String originalName = file.getOriginalFilename();
            String displayName = normalizeDisplayPath(originalName);
            String storedName = UUID.randomUUID().toString().replace("-", "") + "_" + normalizeFileName(originalName);
            try {
                Path dir = resolveTaskUploadDir(taskId);
                Path target = dir.resolve(storedName);
                Files.copy(file.getInputStream(), target);
                boolean viewable = isViewableCodeFile(originalName);
                saved.add(new TaskAttachment(displayName, storedName, file.getContentType(), file.getSize(), viewable));
            } catch (IOException ex) {
                redirectAttributes.addFlashAttribute("error", "Unable to save file " + displayName + ".");
                return null;
            }
        }
        return saved;
    }

    private Student getSessionStudent(HttpSession session) {
        Account account = (Account) session.getAttribute("account");
        Object profile = session.getAttribute("userProfile");
        if (account == null || !(profile instanceof Student) || !"Student".equalsIgnoreCase(account.getRole())) {
            return null;
        }
        return (Student) profile;
    }

    private int resolveCurrentSemesterId() {
        Semester semester = semesterRepository.findCurrentSemester();
        if (semester == null) {
            semester = semesterRepository.findById(1);
        }
        return semester != null ? semester.getSemesterId() : 1;
    }

    private Group resolveCurrentGroup(Student student) {
        if (student == null) {
            return null;
        }
        int semesterId = resolveCurrentSemesterId();
        List<Group> myGroups = groupRepository.findByStudentAndSemester(student.getStudentId(), semesterId);
        return myGroups.isEmpty() ? null : myGroups.get(0);
    }

    private String resolveClassName(Student student) {
        if (student == null || student.getClassId() == null) {
            return "PMS";
        }
        Classes classObj = classRepository.findById(student.getClassId());
        return classObj != null ? classObj.getClassName() : "PMS";
    }

    private void bindLayout(Model model, HttpSession session, Student student) {
        Object fullName = session.getAttribute("fullName");
        Object role = session.getAttribute("role");
        model.addAttribute("studentName", fullName != null ? fullName : (student != null ? student.getFullName() : "Student"));
        model.addAttribute("userRole", RoleDisplayUtil.toDisplayRole(role != null ? role : "Student"));
        model.addAttribute("className", resolveClassName(student));

        boolean invitationEnabled = groupInvitationRepository.isInvitationTableAvailable();
        model.addAttribute("invitationFeatureEnabled", invitationEnabled);
        model.addAttribute("notificationCount",
                studentNotificationService.countHeaderNotifications(student, invitationEnabled));
    }

    private boolean isStudentSourceProject(Project project) {
        return project != null && "STUDENT".equalsIgnoreCase(normalize(project.getTopicSource()));
    }

    private boolean isLeader(Group group, Student student) {
        return group != null
                && student != null
                && group.getLeaderId() != null
                && group.getLeaderId() == student.getStudentId();
    }

    private ProjectTask findTaskInProject(Project project, int taskId) {
        if (project == null) {
            return null;
        }
        ProjectTask task = projectTaskRepository.findById(taskId);
        if (task == null || task.getProjectId() != project.getProjectId()) {
            return null;
        }
        return task;
    }

    private Sprint findSprintInProject(Project project, int sprintId) {
        if (project == null) {
            return null;
        }
        Sprint sprint = sprintRepository.findById(sprintId);
        if (sprint == null || sprint.getProjectId() != project.getProjectId()) {
            return null;
        }
        return sprint;
    }

    private boolean canReviewTask(ProjectTask task, Group group, Student student) {
        if (task == null || group == null || student == null) {
            return false;
        }
        if (task.getAssigneeId() == student.getStudentId()) {
            int memberCount = groupMemberRepository.countMembers(group.getGroupId());
            return memberCount <= 1;
        }
        if (task.getReviewerId() != null && task.getReviewerId() == student.getStudentId()) {
            return true;
        }
        return isLeader(group, student);
    }

    private void refreshSprintState(Project project) {
        if (project == null) {
            return;
        }
        Map<Integer, Integer> previousStatuses = new HashMap<>();
        for (ProjectTask task : projectTaskRepository.findByProject(project.getProjectId())) {
            if (task != null) {
                previousStatuses.put(task.getTaskId(), task.getStatus());
            }
        }
        sprintRepository.closeExpiredSprintsAndFailTasks(project.getProjectId());
        for (ProjectTask task : projectTaskRepository.findByProject(project.getProjectId())) {
            if (task == null) {
                continue;
            }
            Integer previousStatus = previousStatuses.get(task.getTaskId());
            if (previousStatus != null
                    && previousStatus != ProjectTask.STATUS_FAILED_SPRINT
                    && task.getStatus() == ProjectTask.STATUS_FAILED_SPRINT) {
                studentNotificationService.notifyTaskFailedSprint(project, task);
            }
        }
    }

    private boolean isProjectApproved(Project project) {
        return project != null && project.getApprovalStatus() == Project.STATUS_APPROVED;
    }

    private boolean isProjectNotStarted(Project project) {
        return isProjectApproved(project)
                && project.getStartDate() != null
                && LocalDateTime.now().isBefore(project.getStartDate());
    }

    private boolean isProjectEnded(Project project) {
        return isProjectApproved(project)
                && project.getEndDate() != null
                && LocalDateTime.now().isAfter(project.getEndDate());
    }

    private boolean isWithinFinalLinkGrace(Project project) {
        if (!isProjectApproved(project) || project.getEndDate() == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return now.isAfter(project.getEndDate()) && !now.isAfter(project.getEndDate().plusDays(1));
    }

    private boolean isProjectLockedForWork(Project project) {
        return isProjectEnded(project);
    }

    private boolean hasOpenChangeRequest(Project project) {
        return project != null && projectChangeRequestRepository.existsOpenByProject(project.getProjectId());
    }

    private boolean canOperateTask(Project project, boolean projectChangeOpen) {
        return isProjectApproved(project)
                && !isProjectNotStarted(project)
                && !isProjectLockedForWork(project)
                && !projectChangeOpen;
    }

    private LocalDate toDate(LocalDateTime time) {
        return time == null ? null : time.toLocalDate();
    }

    private boolean isAscii(String value) {
        if (value == null) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 127) {
                return false;
            }
        }
        return true;
    }

    private String toSprintDisplayName(int sprintId, String sprintName) {
        String normalized = normalize(sprintName);
        if (normalized.isEmpty() || !isAscii(normalized)) {
            return "Sprint #" + sprintId;
        }
        return normalized;
    }

    private Map<Integer, String> buildSprintDisplayNames(List<Sprint> sprints) {
        Map<Integer, String> names = new HashMap<>();
        if (sprints == null) {
            return names;
        }
        for (Sprint sprint : sprints) {
            if (sprint == null) {
                continue;
            }
            int sprintId = sprint.getSprintId();
            names.put(sprintId, toSprintDisplayName(sprintId, sprint.getSprintName()));
        }
        return names;
    }

    private boolean validateLeaderProject(Group group,
            Student student,
            Project project,
            RedirectAttributes redirectAttributes) {
        if (!isLeader(group, student)) {
            redirectAttributes.addFlashAttribute("error", "Only the group leader can perform this action.");
            return false;
        }
        if (project == null) {
            redirectAttributes.addFlashAttribute("error", "Your group does not have a project yet.");
            return false;
        }
        return true;
    }

    private boolean validateNoOpenChangeRequest(Project project, RedirectAttributes redirectAttributes) {
        if (hasOpenChangeRequest(project)) {
            redirectAttributes.addFlashAttribute("error",
                    "The project has a pending topic change request, so planning and task actions are temporarily locked.");
            return false;
        }
        return true;
    }

    private Double parseEstimatedPoints(String estimatedPoints, RedirectAttributes redirectAttributes) {
        try {
            double points = Double.parseDouble(normalize(estimatedPoints).replace(',', '.'));
            if (points <= 0) {
                redirectAttributes.addFlashAttribute("error", "Estimated points must be greater than 0.");
                return null;
            }
            return points;
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Invalid estimated points.");
            return null;
        }
    }


    private Integer parseIntegerOrNull(String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(normalized);
        } catch (Exception ex) {
            return null;
        }
    }

    @GetMapping
    public String index(Model model, HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return "redirect:/acc/log";
        }

        bindLayout(model, session, student);
        model.addAttribute("noGroup", false);
        model.addAttribute("noProject", false);
        Group group = resolveCurrentGroup(student);
        if (group == null) {
            model.addAttribute("noGroup", true);
            return "student/project/home";
        }

        boolean leader = isLeader(group, student);
        List<Student> members = groupMemberRepository.findMemberDetailsOfGroup(group.getGroupId());
        Project project = projectRepository.findByGroupId(group.getGroupId());

        int memberCount = groupMemberRepository.countMembers(group.getGroupId());
        model.addAttribute("group", group);
        model.addAttribute("isLeader", leader);
        model.addAttribute("members", members);
        model.addAttribute("memberCount", memberCount);
        model.addAttribute("singleMemberGroup", memberCount <= 1);
        model.addAttribute("studentId", student.getStudentId());

        if (project == null) {
            model.addAttribute("noProject", true);
            return "student/project/home";
        }

        refreshSprintState(project);
        ProjectEditRequest latestRequest = projectEditRequestRepository.findLatestByProject(project.getProjectId());
        ProjectChangeRequest latestChangeRequest = projectChangeRequestRepository.findLatestByProject(project.getProjectId());
        boolean projectChangeOpen = hasOpenChangeRequest(project);
        boolean hasDoneTasks = projectTaskRepository.hasDoneTasksByProject(project.getProjectId());
        boolean canRequestEdit = isStudentSourceProject(project)
                && !project.isStudentCanEdit()
                && !projectEditRequestRepository.existsPendingByProjectAndStudent(project.getProjectId(), student.getStudentId())
                && !projectChangeOpen;
        List<Sprint> sprints = sprintRepository.findByProject(project.getProjectId());
        Sprint openSprint = sprintRepository.findOpenByProject(project.getProjectId());
        List<ProjectTask> tasks = projectTaskRepository.findByProject(project.getProjectId());
        List<ProjectTask> failedTasks = projectTaskRepository.findFailedByProject(project.getProjectId());
        Map<Integer, String> sprintDisplayNames = buildSprintDisplayNames(sprints);
        Map<Integer, List<TaskAttachment>> taskFiles = new HashMap<>();
        Map<Integer, Boolean> taskHasCode = new HashMap<>();
        for (ProjectTask task : tasks) {
            List<TaskAttachment> attachments = parseAttachments(task.getSubmissionFiles());
            taskFiles.put(task.getTaskId(), attachments);
            boolean hasCode = !isBlank(task.getSubmissionCode());
            if (!hasCode) {
                for (TaskAttachment attachment : attachments) {
                    if (attachment != null && attachment.isViewable()) {
                        hasCode = true;
                        break;
                    }
                }
            }
            taskHasCode.put(task.getTaskId(), hasCode);
        }

        boolean projectNotStarted = isProjectNotStarted(project);
        boolean projectLockedForWork = isProjectLockedForWork(project);
        boolean withinFinalLinkGrace = isWithinFinalLinkGrace(project);
        boolean canTaskOps = canOperateTask(project, projectChangeOpen);
        boolean canManageSprint = leader && isProjectApproved(project) && !projectLockedForWork && !projectChangeOpen;
        boolean canManageTaskPlan = leader && isProjectApproved(project) && !projectLockedForWork && !projectChangeOpen;

        model.addAttribute("project", project);
        model.addAttribute("latestEditRequest", latestRequest);
        model.addAttribute("latestChangeRequest", latestChangeRequest);
        model.addAttribute("projectChangeOpen", projectChangeOpen);
        model.addAttribute("hasDoneTasks", hasDoneTasks);
        model.addAttribute("canRequestEdit", canRequestEdit);
        model.addAttribute("canEditContent",
                isStudentSourceProject(project) && project.isStudentCanEdit() && !projectLockedForWork && !projectChangeOpen);
        model.addAttribute("canSubmitReview",
                isStudentSourceProject(project)
                        && project.isStudentCanEdit()
                        && !normalize(project.getProjectName()).isEmpty()
                        && !projectLockedForWork
                        && !projectChangeOpen);
        model.addAttribute("canRequestProjectChange",
                leader && isProjectApproved(project) && !projectLockedForWork && !projectChangeOpen && !hasDoneTasks);
        model.addAttribute("projectNotStarted", projectNotStarted);
        model.addAttribute("projectLockedForWork", projectLockedForWork);
        model.addAttribute("withinFinalLinkGrace", withinFinalLinkGrace);
        model.addAttribute("canUpdateFinalLinks", leader && withinFinalLinkGrace);
        model.addAttribute("canCreateSprint", canManageSprint);
        model.addAttribute("canManageSprint", canManageSprint);
        model.addAttribute("canCreateTask", canManageTaskPlan && canTaskOps && openSprint != null);
        model.addAttribute("canManageTaskPlan", canManageTaskPlan);
        model.addAttribute("canReplanFailed", leader && canTaskOps && openSprint != null && failedTasks != null && !failedTasks.isEmpty());
        model.addAttribute("sprints", sprints);
        model.addAttribute("openSprint", openSprint);
        model.addAttribute("sprintDisplayNames", sprintDisplayNames);
        model.addAttribute("tasks", tasks);
        model.addAttribute("failedTasks", failedTasks);
        model.addAttribute("taskFiles", taskFiles);
        model.addAttribute("taskHasCode", taskHasCode);
        model.addAttribute("lecturerComments", projectCommentRepository.findByProject(project.getProjectId()));
        return "student/project/home";
    }

    @GetMapping("/tasks")
    public String taskBoard(@RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "assigneeId", required = false) String assigneeId,
            Model model,
            HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return "redirect:/acc/log";
        }

        bindLayout(model, session, student);
        Group group = resolveCurrentGroup(student);
        if (group == null) {
            return "redirect:/student/project";
        }

        boolean leader = isLeader(group, student);
        List<Student> members = groupMemberRepository.findMemberDetailsOfGroup(group.getGroupId());
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            return "redirect:/student/project";
        }

        Integer statusFilter = parseIntegerOrNull(status);
        Integer assigneeFilter = parseIntegerOrNull(assigneeId);

        Map<Integer, String> statusOptions = new LinkedHashMap<>();
        statusOptions.put(ProjectTask.STATUS_TODO, "Pending");
        statusOptions.put(ProjectTask.STATUS_IN_PROGRESS, "In progress");
        statusOptions.put(ProjectTask.STATUS_SUBMITTED, "Awaiting review");
        statusOptions.put(ProjectTask.STATUS_DONE, "Done");
        statusOptions.put(ProjectTask.STATUS_REJECTED, "Returned");
        statusOptions.put(ProjectTask.STATUS_FAILED_SPRINT, "Failed (sprint)");
        statusOptions.put(ProjectTask.STATUS_CANCELLED, "Cancelled");
        if (statusFilter != null && !statusOptions.containsKey(statusFilter)) {
            statusFilter = null;
        }

        int memberCount = groupMemberRepository.countMembers(group.getGroupId());
        model.addAttribute("group", group);
        model.addAttribute("isLeader", leader);
        model.addAttribute("members", members);
        model.addAttribute("memberCount", memberCount);
        model.addAttribute("singleMemberGroup", memberCount <= 1);
        model.addAttribute("studentId", student.getStudentId());
        model.addAttribute("project", project);
        model.addAttribute("statusOptions", statusOptions);
        model.addAttribute("selectedStatus", statusFilter);
        model.addAttribute("selectedAssigneeId", assigneeFilter);

        refreshSprintState(project);
        boolean projectChangeOpen = hasOpenChangeRequest(project);
        boolean projectNotStarted = isProjectNotStarted(project);
        boolean projectLockedForWork = isProjectLockedForWork(project);
        boolean canTaskOps = canOperateTask(project, projectChangeOpen);
        boolean canManageSprint = leader && isProjectApproved(project) && !projectLockedForWork && !projectChangeOpen;
        boolean canManageTaskPlan = leader && isProjectApproved(project) && !projectLockedForWork && !projectChangeOpen;

        List<Sprint> sprints = sprintRepository.findByProject(project.getProjectId());
        Sprint openSprint = sprintRepository.findOpenByProject(project.getProjectId());
        List<ProjectTask> tasks = projectTaskRepository.findByProject(project.getProjectId());
        List<ProjectTask> failedTasks = projectTaskRepository.findFailedByProject(project.getProjectId());
        Map<Integer, String> sprintDisplayNames = buildSprintDisplayNames(sprints);
        Map<Integer, List<TaskAttachment>> taskFiles = new HashMap<>();
        Map<Integer, Boolean> taskHasCode = new HashMap<>();
        for (ProjectTask task : tasks) {
            List<TaskAttachment> attachments = parseAttachments(task.getSubmissionFiles());
            taskFiles.put(task.getTaskId(), attachments);
            boolean hasCode = !isBlank(task.getSubmissionCode());
            if (!hasCode) {
                for (TaskAttachment attachment : attachments) {
                    if (attachment != null && attachment.isViewable()) {
                        hasCode = true;
                        break;
                    }
                }
            }
            taskHasCode.put(task.getTaskId(), hasCode);
        }

        List<ProjectTask> filteredTasks = new ArrayList<>();
        for (ProjectTask task : tasks) {
            if (statusFilter != null && task.getStatus() != statusFilter) {
                continue;
            }
            if (assigneeFilter != null && task.getAssigneeId() != assigneeFilter) {
                continue;
            }
            filteredTasks.add(task);
        }

        model.addAttribute("projectNotStarted", projectNotStarted);
        model.addAttribute("projectLockedForWork", projectLockedForWork);
        model.addAttribute("projectChangeOpen", projectChangeOpen);
        model.addAttribute("canCreateSprint", canManageSprint);
        model.addAttribute("canManageSprint", canManageSprint);
        model.addAttribute("canCreateTask", canManageTaskPlan && canTaskOps && openSprint != null);
        model.addAttribute("canManageTaskPlan", canManageTaskPlan);
        model.addAttribute("canReplanFailed", leader && canTaskOps && openSprint != null && failedTasks != null && !failedTasks.isEmpty());
        model.addAttribute("sprints", sprints);
        model.addAttribute("openSprint", openSprint);
        model.addAttribute("sprintDisplayNames", sprintDisplayNames);
        model.addAttribute("tasks", filteredTasks);
        model.addAttribute("taskTotalCount", tasks.size());
        model.addAttribute("taskFilteredCount", filteredTasks.size());
        model.addAttribute("failedTasks", failedTasks);
        model.addAttribute("taskFiles", taskFiles);
        model.addAttribute("taskHasCode", taskHasCode);
        return "student/project/tasks";
    }

    @PostMapping("/request-edit")
    public String requestEditPermission(@RequestParam(name = "requestNote", required = false) String requestNote,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return "redirect:/acc/log";
        }
        Group group = resolveCurrentGroup(student);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "You don't have a group in the current term.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            redirectAttributes.addFlashAttribute("error", "Your group does not have a project yet.");
            return "redirect:/student/project";
        }
        if (!validateNoOpenChangeRequest(project, redirectAttributes)) {
            return "redirect:/student/project";
        }
        if (!isStudentSourceProject(project)) {
            redirectAttributes.addFlashAttribute("error", "This project does not allow student self-updates.");
            return "redirect:/student/project";
        }
        if (project.isStudentCanEdit()) {
            redirectAttributes.addFlashAttribute("success", "You already have permission to update the project content.");
            return "redirect:/student/project";
        }
        if (projectEditRequestRepository.existsPendingByProjectAndStudent(project.getProjectId(), student.getStudentId())) {
            redirectAttributes.addFlashAttribute("error", "You have already requested permission and are waiting for staff to review.");
            return "redirect:/student/project";
        }
        String note = normalize(requestNote);
        if (note.length() > 2000) {
            redirectAttributes.addFlashAttribute("error", "Request note is too long.");
            return "redirect:/student/project";
        }
        int requestId = projectEditRequestRepository.createRequest(project.getProjectId(), student.getStudentId(), note);
        if (requestId <= 0) {
            redirectAttributes.addFlashAttribute("error", "Unable to submit permission request. Please try again.");
            return "redirect:/student/project";
        }
        redirectAttributes.addFlashAttribute("success", "Permission request to update project content has been sent.");
        return "redirect:/student/project";
    }

    @PostMapping("/update-content")
    public String updateContent(@RequestParam("projectName") String projectName,
            @RequestParam(name = "description", required = false) String description,
            @RequestParam(name = "sourceCodeUrl", required = false) String sourceCodeUrl,
            @RequestParam(name = "documentUrl", required = false) String documentUrl,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return "redirect:/acc/log";
        }
        Group group = resolveCurrentGroup(student);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "You don't have a group in the current term.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            redirectAttributes.addFlashAttribute("error", "Your group does not have a project yet.");
            return "redirect:/student/project";
        }
        if (!validateNoOpenChangeRequest(project, redirectAttributes)) {
            return "redirect:/student/project";
        }
        if (!isStudentSourceProject(project)) {
            redirectAttributes.addFlashAttribute("error", "This project does not allow student self-updates.");
            return "redirect:/student/project";
        }
        if (!project.isStudentCanEdit()) {
            redirectAttributes.addFlashAttribute("error", "You are not authorized to update the project content.");
            return "redirect:/student/project";
        }
        if (isProjectLockedForWork(project)) {
            redirectAttributes.addFlashAttribute("error", "The project has ended, so you cannot update its content.");
            return "redirect:/student/project";
        }
        String normalizedName = normalize(projectName);
        if (normalizedName.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Project name cannot be empty.");
            return "redirect:/student/project";
        }
        if (normalizedName.length() > 200) {
            redirectAttributes.addFlashAttribute("error", "Project name can be at most 200 characters.");
            return "redirect:/student/project";
        }
        int updated = projectRepository.updateStudentContent(
                project.getProjectId(),
                normalizedName,
                normalize(description),
                normalize(sourceCodeUrl),
                normalize(documentUrl));
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Unable to update project content.");
            return "redirect:/student/project";
        }
        redirectAttributes.addFlashAttribute("success", "Project content updated.");
        return "redirect:/student/project";
    }

    @PostMapping("/submit-review")
    public String submitForReview(HttpSession session, RedirectAttributes redirectAttributes) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return "redirect:/acc/log";
        }
        Group group = resolveCurrentGroup(student);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "You don't have a group in the current term.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            redirectAttributes.addFlashAttribute("error", "Your group does not have a project yet.");
            return "redirect:/student/project";
        }
        if (!validateNoOpenChangeRequest(project, redirectAttributes)) {
            return "redirect:/student/project";
        }
        if (!isStudentSourceProject(project)) {
            redirectAttributes.addFlashAttribute("error", "This project does not allow student self-updates.");
            return "redirect:/student/project";
        }
        if (!project.isStudentCanEdit()) {
            redirectAttributes.addFlashAttribute("error", "You are not authorized to update the project content.");
            return "redirect:/student/project";
        }
        if (isProjectLockedForWork(project)) {
            redirectAttributes.addFlashAttribute("error", "The project has ended, so it cannot be submitted for approval.");
            return "redirect:/student/project";
        }
        if (normalize(project.getProjectName()).isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please update the project name before submitting for approval.");
            return "redirect:/student/project";
        }
        int updated = projectRepository.submitForLecturerReview(project.getProjectId());
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Unable to send approval request to lecturers.");
            return "redirect:/student/project";
        }
        Project refreshedProject = projectRepository.findById(project.getProjectId());
        studentNotificationService.notifyProjectSubmittedForReview(refreshedProject != null ? refreshedProject : project);

        Set<String> uniqueEmails = new LinkedHashSet<>();
        for (String email : projectRepository.findLecturerEmailsForProject(project.getProjectId())) {
            String normalized = normalize(email).toLowerCase();
            if (!normalized.isEmpty()) {
                uniqueEmails.add(normalized);
            }
        }
        int sent = 0;
        for (String email : uniqueEmails) {
            try {
                mailService.sendProjectReviewRequest(email, project.getProjectName(), group.getGroupName());
                sent++;
            } catch (Exception ex) {
                // Keep workflow successful even if one email fails.
            }
        }
        if (uniqueEmails.isEmpty()) {
            redirectAttributes.addFlashAttribute("success",
                    "Approval request sent. There are no lecturers assigned to receive the email yet.");
        } else {
            redirectAttributes.addFlashAttribute("success",
                    "Approval request sent to lecturers. Emails sent: " + sent + "/" + uniqueEmails.size() + ".");
        }
        return "redirect:/student/project";
    }

    @PostMapping("/change-requests/create")
    public String createProjectChangeRequest(@RequestParam("proposedProjectName") String proposedProjectName,
            @RequestParam(name = "proposedDescription", required = false) String proposedDescription,
            @RequestParam("changeReason") String changeReason,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return "redirect:/acc/log";
        }
        Group group = resolveCurrentGroup(student);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "You don't have a group in the current term.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (!validateLeaderProject(group, student, project, redirectAttributes)) {
            return "redirect:/student/project";
        }
        if (!isProjectApproved(project)) {
            redirectAttributes.addFlashAttribute("error", "Only approved projects can request a topic change.");
            return "redirect:/student/project";
        }
        if (isProjectLockedForWork(project)) {
            redirectAttributes.addFlashAttribute("error", "The project has ended, so a topic change request cannot be sent.");
            return "redirect:/student/project";
        }
        if (hasOpenChangeRequest(project)) {
            redirectAttributes.addFlashAttribute("error", "A topic change request is already pending for this project.");
            return "redirect:/student/project";
        }
        if (projectTaskRepository.hasDoneTasksByProject(project.getProjectId())) {
            redirectAttributes.addFlashAttribute("error", "This project already has completed tasks, so the topic cannot be changed.");
            return "redirect:/student/project";
        }

        String normalizedName = normalize(proposedProjectName);
        String normalizedDescription = normalize(proposedDescription);
        String normalizedReason = normalize(changeReason);
        if (normalizedName.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Proposed project name cannot be empty.");
            return "redirect:/student/project";
        }
        if (normalizedName.length() > 200) {
            redirectAttributes.addFlashAttribute("error", "Proposed project name can be at most 200 characters.");
            return "redirect:/student/project";
        }
        if (normalizedReason.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please provide a reason for changing the project.");
            return "redirect:/student/project";
        }
        if (normalizedReason.length() > 2000) {
            redirectAttributes.addFlashAttribute("error", "Reason for changing the project is too long.");
            return "redirect:/student/project";
        }

        int requestId = projectChangeRequestRepository.createRequest(
                project.getProjectId(),
                student.getStudentId(),
                normalizedName,
                normalizedDescription,
                normalizedReason);
        if (requestId <= 0) {
            redirectAttributes.addFlashAttribute("error", "Unable to submit project change request. Please try again.");
            return "redirect:/student/project";
        }

        studentNotificationService.notifyProjectChangeRequested(project, student.getFullName());
        redirectAttributes.addFlashAttribute("success", "Project change request sent to training staff.");
        return "redirect:/student/project";
    }

    @PostMapping("/sprints/create")
    public String createSprint(@RequestParam(name = "sprintName", required = false) String sprintName,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return "redirect:/acc/log";
        }
        Group group = resolveCurrentGroup(student);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "You don't have a group in the current term.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (!validateLeaderProject(group, student, project, redirectAttributes)) {
            return "redirect:/student/project";
        }
        if (!isProjectApproved(project)) {
            redirectAttributes.addFlashAttribute("error", "The project must be approved before creating a sprint.");
            return "redirect:/student/project";
        }
        if (project.getStartDate() == null || project.getEndDate() == null) {
            redirectAttributes.addFlashAttribute("error", "The project does not have start/end dates.");
            return "redirect:/student/project";
        }
        if (isProjectLockedForWork(project)) {
            redirectAttributes.addFlashAttribute("error", "The project has ended, so a new sprint cannot be created.");
            return "redirect:/student/project";
        }
        if (!validateNoOpenChangeRequest(project, redirectAttributes)) {
            return "redirect:/student/project";
        }

        refreshSprintState(project);
        int sprintId = sprintRepository.createNextSprint(
                project.getProjectId(),
                normalize(sprintName),
                toDate(project.getStartDate()),
                toDate(project.getEndDate()));
        if (sprintId == -2) {
            redirectAttributes.addFlashAttribute("error", "There is already an open sprint. Please wait for the current sprint to end.");
            return "redirect:/student/project";
        }
        if (sprintId == -3) {
            redirectAttributes.addFlashAttribute("error", "Cannot create a sprint because it exceeds the project timeline.");
            return "redirect:/student/project";
        }
        if (sprintId <= 0) {
            redirectAttributes.addFlashAttribute("error", "Unable to create a new sprint.");
            return "redirect:/student/project";
        }

        Sprint createdSprint = sprintRepository.findById(sprintId);
        if (createdSprint != null) {
            studentNotificationService.notifySprintCreated(project, createdSprint);
        }
        redirectAttributes.addFlashAttribute("success", "New sprint created successfully.");
        return "redirect:/student/project";
    }

    @PostMapping("/sprints/{sprintId}/update")
    public String updateSprint(@PathVariable("sprintId") int sprintId,
            @RequestParam("sprintName") String sprintName,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return "redirect:/acc/log";
        }
        Group group = resolveCurrentGroup(student);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "You don't have a group in the current term.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (!validateLeaderProject(group, student, project, redirectAttributes)) {
            return "redirect:/student/project";
        }
        if (!isProjectApproved(project) || isProjectLockedForWork(project)) {
            redirectAttributes.addFlashAttribute("error", "The project currently does not allow sprint updates.");
            return "redirect:/student/project";
        }
        if (!validateNoOpenChangeRequest(project, redirectAttributes)) {
            return "redirect:/student/project";
        }

        Sprint sprint = findSprintInProject(project, sprintId);
        if (sprint == null) {
            redirectAttributes.addFlashAttribute("error", "The sprint does not exist in the group's project.");
            return "redirect:/student/project";
        }
        if (sprint.isClosed() || sprint.isCancelled()) {
            redirectAttributes.addFlashAttribute("error", "Only the open sprint can be renamed.");
            return "redirect:/student/project";
        }

        String normalizedName = normalize(sprintName);
        if (normalizedName.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Sprint name cannot be empty.");
            return "redirect:/student/project";
        }
        if (normalizedName.length() > 50) {
            redirectAttributes.addFlashAttribute("error", "Sprint name can be at most 50 characters.");
            return "redirect:/student/project";
        }

        int updated = sprintRepository.updateSprintName(sprintId, project.getProjectId(), normalizedName);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Unable to update sprint name.");
            return "redirect:/student/project";
        }
        redirectAttributes.addFlashAttribute("success", "Sprint name updated.");
        return "redirect:/student/project";
    }

    @PostMapping("/sprints/{sprintId}/delete")
    public String deleteSprint(@PathVariable("sprintId") int sprintId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return "redirect:/acc/log";
        }
        Group group = resolveCurrentGroup(student);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "You don't have a group in the current term.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (!validateLeaderProject(group, student, project, redirectAttributes)) {
            return "redirect:/student/project";
        }
        if (!isProjectApproved(project) || isProjectLockedForWork(project)) {
            redirectAttributes.addFlashAttribute("error", "The project currently does not allow sprint deletion.");
            return "redirect:/student/project";
        }
        if (!validateNoOpenChangeRequest(project, redirectAttributes)) {
            return "redirect:/student/project";
        }

        Sprint sprint = findSprintInProject(project, sprintId);
        if (sprint == null) {
            redirectAttributes.addFlashAttribute("error", "The sprint does not exist in the group's project.");
            return "redirect:/student/project";
        }
        if (sprint.isClosed() || sprint.isCancelled()) {
            redirectAttributes.addFlashAttribute("error", "Only the open sprint can be deleted.");
            return "redirect:/student/project";
        }
        if (sprintRepository.hasAnyTasks(sprintId)) {
            redirectAttributes.addFlashAttribute("error", "This sprint has tasks, so it cannot be hard-deleted. Cancel the sprint instead if needed.");
            return "redirect:/student/project";
        }

        int deleted = sprintRepository.deleteEmptyOpenSprint(sprintId, project.getProjectId());
        if (deleted <= 0) {
            redirectAttributes.addFlashAttribute("error", "Unable to delete sprint.");
            return "redirect:/student/project";
        }
        redirectAttributes.addFlashAttribute("success", "Empty sprint deleted.");
        return "redirect:/student/project";
    }

    @Transactional
    @PostMapping("/sprints/{sprintId}/cancel")
    public String cancelSprint(@PathVariable("sprintId") int sprintId,
            @RequestParam("cancelReason") String cancelReason,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return "redirect:/acc/log";
        }
        Group group = resolveCurrentGroup(student);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "You don't have a group in the current term.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (!validateLeaderProject(group, student, project, redirectAttributes)) {
            return "redirect:/student/project";
        }
        if (!isProjectApproved(project) || isProjectLockedForWork(project)) {
            redirectAttributes.addFlashAttribute("error", "The project currently does not allow sprint cancellation.");
            return "redirect:/student/project";
        }
        if (!validateNoOpenChangeRequest(project, redirectAttributes)) {
            return "redirect:/student/project";
        }

        Sprint sprint = findSprintInProject(project, sprintId);
        if (sprint == null) {
            redirectAttributes.addFlashAttribute("error", "The sprint does not exist in the group's project.");
            return "redirect:/student/project";
        }
        if (sprint.isClosed() || sprint.isCancelled()) {
            redirectAttributes.addFlashAttribute("error", "Only the open sprint can be cancelled.");
            return "redirect:/student/project";
        }
        if (projectTaskRepository.hasDoneTasksInSprint(sprintId)) {
            redirectAttributes.addFlashAttribute("error", "This sprint has completed tasks, so it cannot be cancelled.");
            return "redirect:/student/project";
        }

        String normalizedReason = normalize(cancelReason);
        if (normalizedReason.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please enter a reason for cancelling the sprint.");
            return "redirect:/student/project";
        }
        if (normalizedReason.length() > 2000) {
            redirectAttributes.addFlashAttribute("error", "Sprint cancellation reason is too long.");
            return "redirect:/student/project";
        }

        List<ProjectTask> tasksCancelledBySprint = new ArrayList<>();
        for (ProjectTask existingTask : projectTaskRepository.findByProject(project.getProjectId())) {
            if (existingTask != null
                    && existingTask.getSprintId() == sprintId
                    && existingTask.getStatus() != ProjectTask.STATUS_DONE
                    && existingTask.getStatus() != ProjectTask.STATUS_CANCELLED) {
                tasksCancelledBySprint.add(existingTask);
            }
        }

        int updated = sprintRepository.cancelSprint(sprintId, project.getProjectId(), student.getStudentId(), normalizedReason);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Unable to cancel sprint.");
            return "redirect:/student/project";
        }
        projectTaskRepository.cancelTasksBySprint(sprintId, student.getStudentId(), "Task cancelled because the sprint was cancelled.");
        studentNotificationService.notifySprintCancelled(project, sprint, normalizedReason);
        String sprintLabel = normalize(sprint.getSprintName());
        if (sprintLabel.isEmpty()) {
            sprintLabel = "Sprint #" + sprint.getSprintId();
        }
        for (ProjectTask cancelledTask : tasksCancelledBySprint) {
            studentNotificationService.notifyTaskCancelled(project, cancelledTask,
                    "Sprint \"" + sprintLabel + "\" was cancelled. Reason: " + normalizedReason);
        }

        redirectAttributes.addFlashAttribute("success", "Sprint cancelled and all unfinished tasks in the sprint were cancelled.");
        return "redirect:/student/project";
    }

    @PostMapping("/tasks/create")
    public String createTask(@RequestParam("sprintId") int sprintId,
            @RequestParam("taskName") String taskName,
            @RequestParam(name = "description", required = false) String description,
            @RequestParam(name = "taskImage", required = false) String taskImage,
            @RequestParam("estimatedPoints") String estimatedPoints,
            @RequestParam("assigneeId") int assigneeId,
            @RequestParam("reviewerId") int reviewerId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return "redirect:/acc/log";
        }
        Group group = resolveCurrentGroup(student);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "You don't have a group in the current term.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (!validateLeaderProject(group, student, project, redirectAttributes)) {
            return "redirect:/student/project";
        }
        if (!isProjectApproved(project)) {
            redirectAttributes.addFlashAttribute("error", "Tasks can be created only after the project is approved.");
            return "redirect:/student/project";
        }
        if (isProjectNotStarted(project)) {
            redirectAttributes.addFlashAttribute("error", "The project build period has not started yet.");
            return "redirect:/student/project";
        }
        if (isProjectLockedForWork(project)) {
            redirectAttributes.addFlashAttribute("error", "The project has ended, so tasks cannot be created.");
            return "redirect:/student/project";
        }
        if (!validateNoOpenChangeRequest(project, redirectAttributes)) {
            return "redirect:/student/project";
        }

        refreshSprintState(project);
        Sprint openSprint = sprintRepository.findOpenByProject(project.getProjectId());
        if (openSprint == null) {
            redirectAttributes.addFlashAttribute("error", "There is no open sprint. Please create a sprint first.");
            return "redirect:/student/project";
        }
        if (sprintId != openSprint.getSprintId()) {
            redirectAttributes.addFlashAttribute("error", "Tasks can only be added to the open sprint.");
            return "redirect:/student/project";
        }

        String normalizedTaskName = normalize(taskName);
        if (normalizedTaskName.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Task name cannot be empty.");
            return "redirect:/student/project";
        }
        if (normalizedTaskName.length() > 200) {
            redirectAttributes.addFlashAttribute("error", "Task name can be at most 200 characters.");
            return "redirect:/student/project";
        }

        Double points = parseEstimatedPoints(estimatedPoints, redirectAttributes);
        if (points == null) {
            return "redirect:/student/project";
        }

        if (!groupMemberRepository.isMember(group.getGroupId(), assigneeId)) {
            redirectAttributes.addFlashAttribute("error", "Assignee must be a member of the group.");
            return "redirect:/student/project";
        }
        if (!groupMemberRepository.isMember(group.getGroupId(), reviewerId)) {
            redirectAttributes.addFlashAttribute("error", "Reviewer must be a member of the group.");
            return "redirect:/student/project";
        }
        int memberCount = groupMemberRepository.countMembers(group.getGroupId());
        if (assigneeId == reviewerId && memberCount > 1) {
            redirectAttributes.addFlashAttribute("error",
                    "Assignee and reviewer must be different people (unless the group has only 1 member).");
            return "redirect:/student/project";
        }

        int taskId = projectTaskRepository.createTaskInSprint(
                openSprint.getSprintId(),
                normalizedTaskName,
                normalize(description),
                normalize(taskImage),
                points,
                assigneeId,
                reviewerId);
        if (taskId <= 0) {
            redirectAttributes.addFlashAttribute("error", "Unable to create task. Please check the data.");
            return "redirect:/student/project";
        }

        ProjectTask createdTask = projectTaskRepository.findById(taskId);
        if (createdTask != null) {
            studentNotificationService.notifyTaskAssigned(project, createdTask);
            studentNotificationService.notifyTaskReviewerAssigned(project, createdTask);
        }
        redirectAttributes.addFlashAttribute("success",
                "Task created. Points: " + points + " equals " + (points * 4.0d) + " hours.");
        return "redirect:/student/project";
    }

    @PostMapping("/tasks/{taskId}/update")
    public String updateTask(@PathVariable("taskId") int taskId,
            @RequestParam("taskName") String taskName,
            @RequestParam(name = "description", required = false) String description,
            @RequestParam(name = "taskImage", required = false) String taskImage,
            @RequestParam("estimatedPoints") String estimatedPoints,
            @RequestParam("assigneeId") int assigneeId,
            @RequestParam("reviewerId") int reviewerId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return "redirect:/acc/log";
        }
        Group group = resolveCurrentGroup(student);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "You don't have a group in the current term.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (!validateLeaderProject(group, student, project, redirectAttributes)) {
            return "redirect:/student/project";
        }
        if (!isProjectApproved(project) || isProjectLockedForWork(project)) {
            redirectAttributes.addFlashAttribute("error", "The project currently does not allow task updates.");
            return "redirect:/student/project";
        }
        if (!validateNoOpenChangeRequest(project, redirectAttributes)) {
            return "redirect:/student/project";
        }

        ProjectTask task = findTaskInProject(project, taskId);
        if (task == null) {
            redirectAttributes.addFlashAttribute("error", "Task does not exist in the group's project.");
            return "redirect:/student/project";
        }
        if (task.getStatus() != ProjectTask.STATUS_TODO && task.getStatus() != ProjectTask.STATUS_REJECTED) {
            redirectAttributes.addFlashAttribute("error", "Only tasks in Pending or Returned status can be edited.");
            return "redirect:/student/project";
        }
        int previousAssigneeId = task.getAssigneeId();
        Integer previousReviewerId = task.getReviewerId();

        String normalizedTaskName = normalize(taskName);
        if (normalizedTaskName.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Task name cannot be empty.");
            return "redirect:/student/project";
        }
        if (normalizedTaskName.length() > 200) {
            redirectAttributes.addFlashAttribute("error", "Task name can be at most 200 characters.");
            return "redirect:/student/project";
        }
        Double points = parseEstimatedPoints(estimatedPoints, redirectAttributes);
        if (points == null) {
            return "redirect:/student/project";
        }
        if (!groupMemberRepository.isMember(group.getGroupId(), assigneeId)) {
            redirectAttributes.addFlashAttribute("error", "Assignee must be a member of the group.");
            return "redirect:/student/project";
        }
        if (!groupMemberRepository.isMember(group.getGroupId(), reviewerId)) {
            redirectAttributes.addFlashAttribute("error", "Reviewer must be a member of the group.");
            return "redirect:/student/project";
        }
        int memberCount = groupMemberRepository.countMembers(group.getGroupId());
        if (assigneeId == reviewerId && memberCount > 1) {
            redirectAttributes.addFlashAttribute("error",
                    "Assignee and reviewer must be different people (unless the group has only 1 member).");
            return "redirect:/student/project";
        }

        int updated = projectTaskRepository.updateTaskDetails(
                taskId,
                normalizedTaskName,
                normalize(description),
                normalize(taskImage),
                points,
                assigneeId,
                reviewerId);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Unable to update task.");
            return "redirect:/student/project";
        }
        ProjectTask updatedTask = projectTaskRepository.findById(taskId);
        if (updatedTask != null) {
            if (updatedTask.getAssigneeId() != previousAssigneeId) {
                studentNotificationService.notifyTaskAssigned(project, updatedTask);
            }
            Integer currentReviewerId = updatedTask.getReviewerId();
            boolean reviewerChanged = previousReviewerId == null
                    ? currentReviewerId != null
                    : !previousReviewerId.equals(currentReviewerId);
            if (reviewerChanged) {
                studentNotificationService.notifyTaskReviewerAssigned(project, updatedTask);
            }
        }
        redirectAttributes.addFlashAttribute("success", "Task updated.");
        return "redirect:/student/project";
    }

    @PostMapping("/tasks/{taskId}/delete")
    public String deleteTask(@PathVariable("taskId") int taskId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return "redirect:/acc/log";
        }
        Group group = resolveCurrentGroup(student);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "You don't have a group in the current term.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (!validateLeaderProject(group, student, project, redirectAttributes)) {
            return "redirect:/student/project";
        }
        if (!isProjectApproved(project) || isProjectLockedForWork(project)) {
            redirectAttributes.addFlashAttribute("error", "The project currently does not allow task deletion.");
            return "redirect:/student/project";
        }
        if (!validateNoOpenChangeRequest(project, redirectAttributes)) {
            return "redirect:/student/project";
        }

        ProjectTask task = findTaskInProject(project, taskId);
        if (task == null) {
            redirectAttributes.addFlashAttribute("error", "Task does not exist in the group's project.");
            return "redirect:/student/project";
        }
        if (task.getStatus() != ProjectTask.STATUS_TODO) {
            redirectAttributes.addFlashAttribute("error", "Only untouched tasks in Pending status can be hard-deleted.");
            return "redirect:/student/project";
        }

        int deleted = projectTaskRepository.deleteTaskIfPristine(taskId);
        if (deleted <= 0) {
            redirectAttributes.addFlashAttribute("error", "Unable to delete task. If the task has history, please cancel it instead.");
            return "redirect:/student/project";
        }
        redirectAttributes.addFlashAttribute("success", "Untouched task deleted.");
        return "redirect:/student/project";
    }

    @PostMapping("/tasks/{taskId}/cancel")
    public String cancelTask(@PathVariable("taskId") int taskId,
            @RequestParam("cancelReason") String cancelReason,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return "redirect:/acc/log";
        }
        Group group = resolveCurrentGroup(student);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "You don't have a group in the current term.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (!validateLeaderProject(group, student, project, redirectAttributes)) {
            return "redirect:/student/project";
        }
        if (!isProjectApproved(project) || isProjectLockedForWork(project)) {
            redirectAttributes.addFlashAttribute("error", "The project currently does not allow task cancellation.");
            return "redirect:/student/project";
        }
        if (!validateNoOpenChangeRequest(project, redirectAttributes)) {
            return "redirect:/student/project";
        }

        ProjectTask task = findTaskInProject(project, taskId);
        if (task == null) {
            redirectAttributes.addFlashAttribute("error", "Task does not exist in the group's project.");
            return "redirect:/student/project";
        }
        if (task.getStatus() == ProjectTask.STATUS_DONE) {
            redirectAttributes.addFlashAttribute("error", "Completed tasks cannot be cancelled.");
            return "redirect:/student/project";
        }
        if (task.getStatus() == ProjectTask.STATUS_CANCELLED) {
            redirectAttributes.addFlashAttribute("error", "This task has already been cancelled.");
            return "redirect:/student/project";
        }

        String normalizedReason = normalize(cancelReason);
        if (normalizedReason.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please enter a reason for cancelling the task.");
            return "redirect:/student/project";
        }
        if (normalizedReason.length() > 2000) {
            redirectAttributes.addFlashAttribute("error", "Task cancellation reason is too long.");
            return "redirect:/student/project";
        }

        int updated = projectTaskRepository.cancelTask(taskId, student.getStudentId(), normalizedReason);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Unable to cancel task.");
            return "redirect:/student/project";
        }
        studentNotificationService.notifyTaskCancelled(project, task, normalizedReason);
        redirectAttributes.addFlashAttribute("success", "Task cancelled.");
        return "redirect:/student/project";
    }

    @PostMapping("/tasks/{taskId}/start")
    public String startTask(@PathVariable("taskId") int taskId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return "redirect:/acc/log";
        }
        Group group = resolveCurrentGroup(student);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "You don't have a group in the current term.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            redirectAttributes.addFlashAttribute("error", "Your group does not have a project yet.");
            return "redirect:/student/project";
        }
        boolean projectChangeOpen = hasOpenChangeRequest(project);
        if (!canOperateTask(project, projectChangeOpen)) {
            redirectAttributes.addFlashAttribute("error",
                    projectChangeOpen
                            ? "The project has a pending topic change request, so task actions are temporarily locked."
                            : "The project currently does not allow task operations.");
            return "redirect:/student/project";
        }

        refreshSprintState(project);
        ProjectTask task = findTaskInProject(project, taskId);
        if (task == null) {
            redirectAttributes.addFlashAttribute("error", "Task does not exist in the group's project.");
            return "redirect:/student/project";
        }
        if (task.getAssigneeId() != student.getStudentId()) {
            redirectAttributes.addFlashAttribute("error", "Only the assignee can start this task.");
            return "redirect:/student/project";
        }
        if (task.getStatus() == ProjectTask.STATUS_FAILED_SPRINT) {
            redirectAttributes.addFlashAttribute("error", "This task failed in a previous sprint. Ask the group leader to move it to a new sprint.");
            return "redirect:/student/project";
        }
        if (task.getStatus() == ProjectTask.STATUS_CANCELLED) {
            redirectAttributes.addFlashAttribute("error", "This task was cancelled, so it cannot be started again.");
            return "redirect:/student/project";
        }

        int updated = projectTaskRepository.markInProgress(taskId, student.getStudentId());
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Unable to move task to In Progress.");
            return "redirect:/student/project";
        }

        redirectAttributes.addFlashAttribute("success", "Task started.");
        return "redirect:/student/project";
    }

    @PostMapping("/tasks/{taskId}/submit")
    public String submitTask(@PathVariable("taskId") int taskId,
            @RequestParam(name = "submissionNote", required = false) String submissionNote,
            @RequestParam(name = "submissionUrl", required = false) String submissionUrl,
            @RequestParam(name = "submissionCode", required = false) String submissionCode,
            @RequestParam(name = "submissionFiles", required = false) MultipartFile[] submissionFiles,
            @RequestParam(name = "clearFiles", required = false) String clearFiles,
            @RequestParam(name = "clearCode", required = false) String clearCode,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return "redirect:/acc/log";
        }
        Group group = resolveCurrentGroup(student);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "You don't have a group in the current term.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            redirectAttributes.addFlashAttribute("error", "Your group does not have a project yet.");
            return "redirect:/student/project";
        }
        boolean projectChangeOpen = hasOpenChangeRequest(project);
        if (!canOperateTask(project, projectChangeOpen)) {
            redirectAttributes.addFlashAttribute("error",
                    projectChangeOpen
                            ? "The project has a pending topic change request, so task actions are temporarily locked."
                            : "The project currently does not allow task operations.");
            return "redirect:/student/project";
        }

        refreshSprintState(project);
        ProjectTask task = findTaskInProject(project, taskId);
        if (task == null) {
            redirectAttributes.addFlashAttribute("error", "Task does not exist in the group's project.");
            return "redirect:/student/project";
        }
        if (task.getAssigneeId() != student.getStudentId()) {
            redirectAttributes.addFlashAttribute("error", "Only the assignee can submit this task.");
            return "redirect:/student/project";
        }
        if (task.getStatus() == ProjectTask.STATUS_CANCELLED) {
            redirectAttributes.addFlashAttribute("error", "This task was cancelled, so it cannot be submitted.");
            return "redirect:/student/project";
        }

        String note = normalize(submissionNote);
        String url = normalize(submissionUrl);
        String code = normalize(submissionCode);
        if (url.length() > 500) {
            redirectAttributes.addFlashAttribute("error", "Reference link is too long.");
            return "redirect:/student/project";
        }
        if (code.length() > 20000) {
            redirectAttributes.addFlashAttribute("error", "Code is too long, please shorten it.");
            return "redirect:/student/project";
        }

        List<TaskAttachment> existingAttachments = parseAttachments(task.getSubmissionFiles());
        boolean removeFiles = clearFiles != null;
        boolean removeCode = clearCode != null;

        List<TaskAttachment> newAttachments = storeAttachments(taskId, submissionFiles, redirectAttributes);
        if (newAttachments == null) {
            return "redirect:/student/project";
        }

        List<TaskAttachment> finalAttachments = existingAttachments;
        if (removeFiles) {
            deleteAttachments(taskId, existingAttachments);
            finalAttachments = new java.util.ArrayList<>();
        }
        if (!newAttachments.isEmpty()) {
            deleteAttachments(taskId, existingAttachments);
            finalAttachments = newAttachments;
        }

        String finalCode = task.getSubmissionCode();
        if (removeCode) {
            finalCode = "";
        } else if (!isBlank(code)) {
            finalCode = code;
        }

        boolean hasAny = !isBlank(note) || !isBlank(url) || !finalAttachments.isEmpty() || !isBlank(finalCode);
        if (!hasAny) {
            redirectAttributes.addFlashAttribute("error",
                    "Submission must include a description, link, file, or code snippet.");
            return "redirect:/student/project";
        }

        int updated = projectTaskRepository.submitTask(
                taskId,
                student.getStudentId(),
                note,
                url,
                serializeAttachments(finalAttachments),
                finalCode);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Unable to submit the task. Make sure the task is In Progress.");
            return "redirect:/student/project";
        }

        ProjectTask submittedTask = projectTaskRepository.findById(taskId);
        studentNotificationService.notifyTaskSubmitted(project, submittedTask != null ? submittedTask : task);
        redirectAttributes.addFlashAttribute("success", "Task submitted and is awaiting review by the reviewer or group leader.");
        return "redirect:/student/project";
    }

    @PostMapping("/tasks/{taskId}/unsubmit")
    public String unsubmitTask(@PathVariable("taskId") int taskId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return "redirect:/acc/log";
        }
        Group group = resolveCurrentGroup(student);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "You don't have a group in the current term.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            redirectAttributes.addFlashAttribute("error", "Your group does not have a project yet.");
            return "redirect:/student/project";
        }
        boolean projectChangeOpen = hasOpenChangeRequest(project);
        if (!canOperateTask(project, projectChangeOpen)) {
            redirectAttributes.addFlashAttribute("error",
                    projectChangeOpen
                            ? "The project has a pending topic change request, so task actions are temporarily locked."
                            : "The project currently does not allow task operations.");
            return "redirect:/student/project";
        }

        refreshSprintState(project);
        ProjectTask task = findTaskInProject(project, taskId);
        if (task == null) {
            redirectAttributes.addFlashAttribute("error", "Task does not exist in the group's project.");
            return "redirect:/student/project";
        }
        if (task.getAssigneeId() != student.getStudentId()) {
            redirectAttributes.addFlashAttribute("error", "Only the assignee can cancel the submission.");
            return "redirect:/student/project";
        }
        if (task.getStatus() != ProjectTask.STATUS_SUBMITTED) {
            redirectAttributes.addFlashAttribute("error", "Task is not in Awaiting Review status.");
            return "redirect:/student/project";
        }

        int updated = projectTaskRepository.unsubmitTask(taskId, student.getStudentId());
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Unable to cancel submission. Please try again.");
            return "redirect:/student/project";
        }

        studentNotificationService.notifyTaskSubmissionCancelled(project, task);
        redirectAttributes.addFlashAttribute("success", "Submission cancelled. Task moved back to In Progress.");
        return "redirect:/student/project";
    }

    

    @GetMapping("/tasks/{taskId}/files/{fileId:.+}")
    public ResponseEntity<Resource> downloadTaskFile(@PathVariable("taskId") int taskId,
            @PathVariable("fileId") String fileId,
            HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "/acc/log")
                    .build();
        }
        Group group = resolveCurrentGroup(student);
        if (group == null) {
            return ResponseEntity.notFound().build();
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            return ResponseEntity.notFound().build();
        }
        ProjectTask task = findTaskInProject(project, taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }

        List<TaskAttachment> attachments = parseAttachments(task.getSubmissionFiles());
        TaskAttachment target = null;
        for (TaskAttachment attachment : attachments) {
            if (attachment != null && attachment.getStoredName().equals(fileId)) {
                target = attachment;
                break;
            }
        }
        if (target == null) {
            return ResponseEntity.notFound().build();
        }

        Path filePath = Paths.get(System.getProperty("user.dir"), TASK_UPLOAD_DIR,
                String.valueOf(taskId), target.getStoredName());
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        try {
            Resource resource = new UrlResource(filePath.toUri());
            String contentType = Files.probeContentType(filePath);
            if (contentType == null || contentType.isBlank()) {
                contentType = target.getContentType();
            }
            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
            try {
                if (contentType != null && !contentType.isBlank()) {
                    mediaType = MediaType.parseMediaType(contentType);
                }
            } catch (Exception ex) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + normalizeFileName(target.getDisplayName()) + "\"")
                    .contentType(mediaType)
                    .body(resource);
        } catch (Exception ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/tasks/{taskId}/code")
    public String viewTaskCode(@PathVariable("taskId") int taskId,
            @RequestParam(name = "file", required = false) String fileId,
            Model model,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return "redirect:/acc/log";
        }
        Group group = resolveCurrentGroup(student);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "B???n ch??a c?? nh??m trong h???c k??? hi???n t???i.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            redirectAttributes.addFlashAttribute("error", "Nh??m c???a b???n ch??a ???????c t???o project.");
            return "redirect:/student/project";
        }
        ProjectTask task = findTaskInProject(project, taskId);
        if (task == null) {
            redirectAttributes.addFlashAttribute("error", "C??ng vi???c kh??ng t???n t???i.");
            return "redirect:/student/project";
        }

        String codeContent;
        String codeTitle;
        String downloadUrl = null;

        if (isBlank(fileId)) {
            if (isBlank(task.getSubmissionCode())) {
                redirectAttributes.addFlashAttribute("error", "Ch??a c?? ??o???n code ???????c n??p.");
                return "redirect:/student/project";
            }
            codeContent = task.getSubmissionCode();
            codeTitle = "Code nh???p tay";
        } else {
            List<TaskAttachment> attachments = parseAttachments(task.getSubmissionFiles());
            TaskAttachment target = null;
            for (TaskAttachment attachment : attachments) {
                if (attachment != null && attachment.getStoredName().equals(fileId)) {
                    target = attachment;
                    break;
                }
            }
            if (target == null || !target.isViewable()) {
                redirectAttributes.addFlashAttribute("error", "Cannot view code from this file.");
                return "redirect:/student/project";
            }
            Path filePath = Paths.get(System.getProperty("user.dir"), TASK_UPLOAD_DIR,
                    String.valueOf(taskId), target.getStoredName());
            if (!Files.exists(filePath)) {
                redirectAttributes.addFlashAttribute("error", "File not found.");
                return "redirect:/student/project";
            }
            try {
                long size = Files.size(filePath);
                if (size > MAX_CODE_VIEW_BYTES) {
                    redirectAttributes.addFlashAttribute("error", "File is too large to preview.");
                    return "redirect:/student/project";
                }
                codeContent = Files.readString(filePath, StandardCharsets.UTF_8);
                codeTitle = target.getDisplayName();
                downloadUrl = "/student/project/tasks/" + taskId + "/files/" + target.getStoredName();
            } catch (IOException ex) {
                redirectAttributes.addFlashAttribute("error", "Unable to read file.");
                return "redirect:/student/project";
            }
        }

        bindLayout(model, session, student);
        model.addAttribute("task", task);
        model.addAttribute("codeTitle", codeTitle);
        model.addAttribute("downloadUrl", downloadUrl);
        model.addAttribute("codeLines", java.util.Arrays.asList(codeContent.split("\\r?\\n", -1)));
        return "student/project/code-view";
    }

@PostMapping("/tasks/{taskId}/review")
    public String reviewTask(@PathVariable("taskId") int taskId,
            @RequestParam("action") String action,
            @RequestParam(name = "reviewComment", required = false) String reviewComment,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return "redirect:/acc/log";
        }
        Group group = resolveCurrentGroup(student);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "You don't have a group in the current term.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            redirectAttributes.addFlashAttribute("error", "Your group does not have a project yet.");
            return "redirect:/student/project";
        }
        boolean projectChangeOpen = hasOpenChangeRequest(project);
        if (!canOperateTask(project, projectChangeOpen)) {
            redirectAttributes.addFlashAttribute("error",
                    projectChangeOpen
                            ? "The project has a pending topic change request, so task actions are temporarily locked."
                            : "The project currently does not allow task operations.");
            return "redirect:/student/project";
        }

        refreshSprintState(project);
        ProjectTask task = findTaskInProject(project, taskId);
        if (task == null) {
            redirectAttributes.addFlashAttribute("error", "Task does not exist in the group's project.");
            return "redirect:/student/project";
        }
        if (task.getStatus() == ProjectTask.STATUS_CANCELLED) {
            redirectAttributes.addFlashAttribute("error", "This task was cancelled, so it cannot be reviewed.");
            return "redirect:/student/project";
        }
        if (!canReviewTask(task, group, student)) {
            redirectAttributes.addFlashAttribute("error", "You are not allowed to review this task.");
            return "redirect:/student/project";
        }

        String normalizedAction = normalize(action).toLowerCase();
        String comment = normalize(reviewComment);

        if ("approve".equals(normalizedAction)) {
            int updated = projectTaskRepository.approveTask(taskId, comment);
            if (updated <= 0) {
                redirectAttributes.addFlashAttribute("error", "Unable to approve task. It must be in Awaiting Review status.");
                return "redirect:/student/project";
            }
            studentNotificationService.notifyTaskCompleted(project, task);
            redirectAttributes.addFlashAttribute("success", "Task approved as completed.");
            return "redirect:/student/project";
        }

        if ("reject".equals(normalizedAction)) {
            if (comment.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Please enter a reason when returning the task.");
                return "redirect:/student/project";
            }
            int updated = projectTaskRepository.rejectTask(taskId, comment);
            if (updated <= 0) {
                redirectAttributes.addFlashAttribute("error", "Unable to return task. It must be in Awaiting Review status.");
                return "redirect:/student/project";
            }
            studentNotificationService.notifyTaskReturned(project, task, comment);
            redirectAttributes.addFlashAttribute("success", "Task returned for the student to revise and resubmit.");
            return "redirect:/student/project";
        }

        redirectAttributes.addFlashAttribute("error", "Invalid task review action.");
        return "redirect:/student/project";
    }

    @PostMapping("/sprints/{sprintId}/replan")
    public String replanFailedTask(@PathVariable("sprintId") int sprintId,
            @RequestParam("taskId") int taskId,
            @RequestParam("assigneeId") int assigneeId,
            @RequestParam("reviewerId") int reviewerId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return "redirect:/acc/log";
        }
        Group group = resolveCurrentGroup(student);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "You don't have a group in the current term.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (!validateLeaderProject(group, student, project, redirectAttributes)) {
            return "redirect:/student/project";
        }
        boolean projectChangeOpen = hasOpenChangeRequest(project);
        if (!canOperateTask(project, projectChangeOpen)) {
            redirectAttributes.addFlashAttribute("error",
                    projectChangeOpen
                            ? "The project has a pending topic change request, so task actions are temporarily locked."
                            : "The project currently does not allow task operations.");
            return "redirect:/student/project";
        }

        refreshSprintState(project);
        Sprint openSprint = sprintRepository.findOpenByProject(project.getProjectId());
        if (openSprint == null) {
            redirectAttributes.addFlashAttribute("error", "There is no open sprint to receive failed tasks.");
            return "redirect:/student/project";
        }
        if (openSprint.getSprintId() != sprintId) {
            redirectAttributes.addFlashAttribute("error", "Failed tasks can only be moved to the open sprint.");
            return "redirect:/student/project";
        }

        ProjectTask task = findTaskInProject(project, taskId);
        if (task == null || task.getStatus() != ProjectTask.STATUS_FAILED_SPRINT) {
            redirectAttributes.addFlashAttribute("error", "Task is invalid or not in Failed (sprint) status.");
            return "redirect:/student/project";
        }
        if (!groupMemberRepository.isMember(group.getGroupId(), assigneeId)) {
            redirectAttributes.addFlashAttribute("error", "Assignee must be a member of the group.");
            return "redirect:/student/project";
        }
        if (!groupMemberRepository.isMember(group.getGroupId(), reviewerId)) {
            redirectAttributes.addFlashAttribute("error", "Reviewer must be a member of the group.");
            return "redirect:/student/project";
        }
        int memberCount = groupMemberRepository.countMembers(group.getGroupId());
        if (assigneeId == reviewerId && memberCount > 1) {
            redirectAttributes.addFlashAttribute("error",
                    "Assignee and reviewer must be different people (unless the group has only 1 member).");
            return "redirect:/student/project";
        }

        int updated = projectTaskRepository.replanFailedTask(taskId, sprintId, assigneeId, reviewerId);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Unable to move failed task to the new sprint.");
            return "redirect:/student/project";
        }

        ProjectTask replannedTask = projectTaskRepository.findById(taskId);
        if (replannedTask != null) {
            studentNotificationService.notifyTaskReplanned(project, replannedTask);
        }
        redirectAttributes.addFlashAttribute("success", "Failed task moved to the new sprint and reassigned.");
        return "redirect:/student/project";
    }

    @PostMapping("/finalize-links")
    public String finalizeProjectLinks(@RequestParam(name = "sourceCodeUrl", required = false) String sourceCodeUrl,
            @RequestParam(name = "documentUrl", required = false) String documentUrl,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return "redirect:/acc/log";
        }
        Group group = resolveCurrentGroup(student);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "You don't have a group in the current term.");
            return "redirect:/student/project";
        }
        if (!isLeader(group, student)) {
            redirectAttributes.addFlashAttribute("error", "Only the group leader can finalize the project links.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (!isProjectApproved(project)) {
            redirectAttributes.addFlashAttribute("error", "The project is not approved yet, so links cannot be finalized.");
            return "redirect:/student/project";
        }
        if (!isWithinFinalLinkGrace(project)) {
            redirectAttributes.addFlashAttribute("error", "Links can only be finalized within 1 day after the project ends.");
            return "redirect:/student/project";
        }

        String sourceUrl = normalize(sourceCodeUrl);
        String docUrl = normalize(documentUrl);
        if (sourceUrl.isEmpty() || docUrl.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Both source code and document links are required.");
            return "redirect:/student/project";
        }

        int updated = projectRepository.updateFinalLinks(project.getProjectId(), sourceUrl, docUrl);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Unable to update final delivery links.");
            return "redirect:/student/project";
        }
        redirectAttributes.addFlashAttribute("success", "Final source code and document links updated.");
        return "redirect:/student/project";
    }
}



package com.example.pms.controller.api;

import com.example.pms.model.Account;
import com.example.pms.model.Group;
import com.example.pms.model.MemberPerformance;
import com.example.pms.model.Project;
import com.example.pms.model.ProjectAttachment;
import com.example.pms.model.ProjectComment;
import com.example.pms.model.ProjectScoreRecord;
import com.example.pms.model.ProjectTask;
import com.example.pms.model.ProjectTemplateAttachment;
import com.example.pms.model.Semester;
import com.example.pms.model.Sprint;
import com.example.pms.model.Student;
import com.example.pms.repository.GroupMemberRepository;
import com.example.pms.repository.GroupRepository;
import com.example.pms.repository.ProjectChangeRequestRepository;
import com.example.pms.repository.ProjectCommentRepository;
import com.example.pms.repository.ProjectRepository;
import com.example.pms.repository.ProjectScoreRepository;
import com.example.pms.repository.ProjectTaskRepository;
import com.example.pms.repository.SemesterRepository;
import com.example.pms.repository.SprintRepository;
import com.example.pms.service.ProjectAttachmentService;
import com.example.pms.service.ProjectTemplateService;
import com.example.pms.service.StudentNotificationService;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/student/project")
public class ApiStudentProjectController {

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

    private static class TaskAttachment {
        private final String displayName;
        private final String storedName;
        private final String contentType;
        private final long size;
        private final boolean viewable;

        private TaskAttachment(String displayName, String storedName, String contentType, long size, boolean viewable) {
            this.displayName = displayName;
            this.storedName = storedName;
            this.contentType = contentType;
            this.size = size;
            this.viewable = viewable;
        }

        private String getDisplayName() {
            return displayName;
        }

        private String getStoredName() {
            return storedName;
        }

        private String getContentType() {
            return contentType;
        }

        private long getSize() {
            return size;
        }

        private boolean isViewable() {
            return viewable;
        }
    }

    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectScoreRepository projectScoreRepository;

    @Autowired
    private ProjectChangeRequestRepository projectChangeRequestRepository;

    @Autowired
    private ProjectCommentRepository projectCommentRepository;

    @Autowired
    private ProjectTaskRepository projectTaskRepository;

    @Autowired
    private SprintRepository sprintRepository;

    @Autowired
    private ProjectAttachmentService projectAttachmentService;

    @Autowired
    private ProjectTemplateService projectTemplateService;

    @Autowired
    private StudentNotificationService studentNotificationService;

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

    private List<TaskAttachment> parseStoredAttachments(String raw) {
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
            String contentType = "";
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
                        contentType = decoded;
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
                result.add(new TaskAttachment(displayName, storedName, contentType, size, isViewableCodeFile(displayName)));
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
                // best effort cleanup
            }
        }
    }

    private List<TaskAttachment> storeAttachments(int taskId, MultipartFile[] files) throws IOException {
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
            String originalName = file.getOriginalFilename();
            String displayName = normalizeDisplayPath(originalName);
            if (file.getSize() > MAX_UPLOAD_BYTES) {
                throw new IllegalArgumentException("File " + displayName + " is too large.");
            }
            if (!isAllowedFile(originalName)) {
                throw new IllegalArgumentException("File " + displayName + " is not in the allowed list.");
            }
        }

        if (fileCount == 0) {
            return saved;
        }
        if (fileCount > MAX_UPLOAD_FILES) {
            throw new IllegalArgumentException("You can upload at most " + MAX_UPLOAD_FILES + " files per submission.");
        }
        if (totalBytes > MAX_UPLOAD_BYTES * 2) {
            throw new IllegalArgumentException("Total file size is too large.");
        }

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }

            String originalName = file.getOriginalFilename();
            String displayName = normalizeDisplayPath(originalName);
            String storedName = UUID.randomUUID().toString().replace("-", "") + "_" + normalizeFileName(originalName);
            Path dir = resolveTaskUploadDir(taskId);
            Path target = dir.resolve(storedName);
            Files.copy(file.getInputStream(), target);
            saved.add(new TaskAttachment(
                    displayName,
                    storedName,
                    file.getContentType(),
                    file.getSize(),
                    isViewableCodeFile(originalName)));
        }

        return saved;
    }

    private boolean isLeader(Group group, Student student) {
        return group != null
                && student != null
                && group.getLeaderId() != null
                && group.getLeaderId() == student.getStudentId();
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
        if (!isProjectApproved(project) || project == null || project.getEndDate() == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return now.isAfter(project.getEndDate()) && !now.isAfter(project.getEndDate().plusDays(1));
    }

    private boolean isProjectLockedForWork(Project project) {
        return isProjectEnded(project);
    }

    private boolean isCurrentSemesterProject(Project project) {
        return project != null
                && project.getSemesterId() != null
                && Objects.equals(project.getSemesterId(), resolveCurrentSemesterId());
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

    private LocalDate toDate(LocalDateTime value) {
        return value == null ? null : value.toLocalDate();
    }

    private Integer intValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(normalize(String.valueOf(value)));
        } catch (Exception ex) {
            return null;
        }
    }

    private Double parseEstimatedPointsValue(Object value) {
        String raw = value == null ? "" : String.valueOf(value);
        try {
            double points = Double.parseDouble(normalize(raw).replace(',', '.'));
            if (points <= 0) {
                return null;
            }
            return points;
        } catch (Exception ex) {
            return null;
        }
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);
        return ResponseEntity.badRequest().body(body);
    }

    private Map<String, Object> successBody(String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("message", message);
        return body;
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
            names.put(sprint.getSprintId(), toSprintDisplayName(sprint.getSprintId(), sprint.getSprintName()));
        }
        return names;
    }

    private Map<Integer, String> buildTaskStatusLabels() {
        Map<Integer, String> labels = new LinkedHashMap<>();
        labels.put(ProjectTask.STATUS_TODO, "Pending");
        labels.put(ProjectTask.STATUS_IN_PROGRESS, "In progress");
        labels.put(ProjectTask.STATUS_SUBMITTED, "Awaiting review");
        labels.put(ProjectTask.STATUS_DONE, "Done");
        labels.put(ProjectTask.STATUS_REJECTED, "Returned");
        labels.put(ProjectTask.STATUS_FAILED_SPRINT, "Failed (sprint)");
        labels.put(ProjectTask.STATUS_CANCELLED, "Cancelled");
        return labels;
    }

    private int countTasksByStatus(List<ProjectTask> tasks, int status) {
        if (tasks == null || tasks.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ProjectTask task : tasks) {
            if (task != null && task.getStatus() == status) {
                count++;
            }
        }
        return count;
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

    private String buildApprovalStatusLabel(Project project) {
        if (project == null) {
            return "Unknown";
        }
        return switch (project.getApprovalStatus()) {
            case Project.STATUS_APPROVED -> "Approved";
            case Project.STATUS_PENDING_LECTURER -> "Pending lecturer approval";
            case Project.STATUS_REJECTED -> "Rejected";
            case Project.STATUS_WAITING_STUDENT_CONTENT -> "Waiting for student updates";
            default -> "Unknown";
        };
    }

    private String buildTaskFileUrl(int taskId, String storedName) {
        return "/api/student/project/tasks/" + taskId + "/files/" + storedName;
    }

    private String buildTaskCodeUrl(int taskId, String storedName) {
        String base = "/student/project/code-ui/" + taskId;
        if (isBlank(storedName)) {
            return base;
        }
        return base + "?file=" + URLEncoder.encode(storedName, StandardCharsets.UTF_8);
    }

    private List<Map<String, Object>> parseAttachments(int taskId, String raw) {
        List<Map<String, Object>> result = new ArrayList<>();
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
            String contentType = "";
            long size = 0L;

            String[] parts = line.split("&");
            for (String part : parts) {
                int eq = part.indexOf('=');
                if (eq < 0) {
                    continue;
                }

                String key = part.substring(0, eq);
                String value = part.substring(eq + 1);
                String decoded = java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
                switch (key) {
                    case "name":
                        displayName = decoded;
                        break;
                    case "stored":
                        storedName = decoded;
                        break;
                    case "type":
                        contentType = decoded;
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

            if (isBlank(storedName)) {
                continue;
            }

            String lowerName = displayName.toLowerCase(Locale.ROOT);
            boolean viewable = lowerName.endsWith(".txt")
                    || lowerName.endsWith(".md")
                    || lowerName.endsWith(".java")
                    || lowerName.endsWith(".js")
                    || lowerName.endsWith(".ts")
                    || lowerName.endsWith(".py")
                    || lowerName.endsWith(".html")
                    || lowerName.endsWith(".css")
                    || lowerName.endsWith(".json")
                    || lowerName.endsWith(".xml")
                    || lowerName.endsWith(".yml")
                    || lowerName.endsWith(".yaml")
                    || lowerName.endsWith(".sql");

            Map<String, Object> item = new HashMap<>();
            item.put("displayName", displayName);
            item.put("storedName", storedName);
            item.put("contentType", contentType);
            item.put("size", size);
            item.put("viewable", viewable);
            item.put("downloadUrl", buildTaskFileUrl(taskId, storedName));
            if (viewable) {
                item.put("codeViewUrl", buildTaskCodeUrl(taskId, storedName));
            }
            result.add(item);
        }

        return result;
    }

    private Map<String, Object> toMemberPayload(Student member, Group group) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("studentId", member.getStudentId());
        payload.put("studentCode", member.getStudentCode());
        payload.put("fullName", member.getFullName());
        payload.put("schoolEmail", member.getSchoolEmail());
        payload.put("isLeader", group != null
                && group.getLeaderId() != null
                && group.getLeaderId() == member.getStudentId());
        return payload;
    }

    private Map<String, Object> toSprintPayload(Sprint sprint, Map<Integer, String> sprintDisplayNames) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sprintId", sprint.getSprintId());
        payload.put("sprintName", sprint.getSprintName());
        payload.put("displayName", sprintDisplayNames.getOrDefault(sprint.getSprintId(), "Sprint #" + sprint.getSprintId()));
        payload.put("startDate", sprint.getStartDate());
        payload.put("endDate", sprint.getEndDate());
        payload.put("closed", sprint.isClosed());
        payload.put("cancelled", sprint.isCancelled());
        payload.put("cancelReason", sprint.getCancelReason());
        payload.put("cancelledAt", sprint.getCancelledAt());
        return payload;
    }

    private Map<String, Object> toTaskPayload(ProjectTask task, Map<Integer, String> taskStatusLabels) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", task.getTaskId());
        payload.put("projectId", task.getProjectId());
        payload.put("sprintId", task.getSprintId());
        payload.put("sprintName", task.getSprintName());
        payload.put("taskName", task.getTaskName());
        payload.put("description", task.getDescription());
        payload.put("taskImage", task.getTaskImage());
        payload.put("estimatedPoints", task.getEstimatedPoints());
        payload.put("estimatedHours", task.getEstimatedHours());
        payload.put("assigneeId", task.getAssigneeId());
        payload.put("assigneeName", task.getAssigneeName());
        payload.put("assigneeCode", task.getAssigneeCode());
        payload.put("reviewerId", task.getReviewerId());
        payload.put("reviewerName", task.getReviewerName());
        payload.put("status", task.getStatus());
        payload.put("statusLabel", taskStatusLabels.getOrDefault(task.getStatus(), "Unknown"));
        payload.put("actualStartTime", task.getActualStartTime());
        payload.put("expectedEndTime", task.getExpectedEndTime());
        payload.put("actualEndTime", task.getActualEndTime());
        payload.put("submissionNote", task.getSubmissionNote());
        payload.put("submissionUrl", task.getSubmissionUrl());
        payload.put("submissionCode", task.getSubmissionCode());
        payload.put("submittedAt", task.getSubmittedAt());
        payload.put("reviewComment", task.getReviewComment());
        payload.put("reviewedAt", task.getReviewedAt());
        payload.put("cancelledReason", task.getCancelledReason());
        payload.put("cancelledAt", task.getCancelledAt());

        List<Map<String, Object>> attachments = parseAttachments(task.getTaskId(), task.getSubmissionFiles());
        payload.put("attachments", attachments);

        boolean hasCode = !isBlank(task.getSubmissionCode());
        if (!hasCode) {
            for (Map<String, Object> attachment : attachments) {
                if (Boolean.TRUE.equals(attachment.get("viewable"))) {
                    hasCode = true;
                    break;
                }
            }
        }
        payload.put("hasCode", hasCode);
        if (hasCode) {
            payload.put("codeViewUrl", buildTaskCodeUrl(task.getTaskId(), null));
        }

        return payload;
    }

    private Map<String, Object> toProjectPayload(Project project) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("projectId", project.getProjectId());
        payload.put("groupId", project.getGroupId());
        payload.put("groupName", project.getGroupName());
        payload.put("classId", project.getClassId());
        payload.put("className", project.getClassName());
        payload.put("semesterId", project.getSemesterId());
        payload.put("semesterName", project.getSemesterName());
        payload.put("leaderId", project.getLeaderId());
        payload.put("leaderName", project.getLeaderName());
        payload.put("templateId", project.getTemplateId());
        payload.put("projectName", project.getProjectName());
        payload.put("description", project.getDescription());
        payload.put("topicSource", project.getTopicSource());
        payload.put("templateImageUrl", project.getTemplateImageUrl());
        payload.put("approvalStatus", project.getApprovalStatus());
        payload.put("approvalStatusLabel", buildApprovalStatusLabel(project));
        payload.put("rejectReason", project.getRejectReason());
        payload.put("requirementFileName", project.getRequirementFileName());
        payload.put("requirementDownloadUrl", isBlank(project.getRequirementFilePath())
                ? null
                : "/project-files/requirements/" + project.getProjectId());
        payload.put("sourceCodeUrl", project.getSourceCodeUrl());
        payload.put("documentUrl", project.getDocumentUrl());
        payload.put("submissionDate", project.getSubmissionDate());
        payload.put("startDate", project.getStartDate());
        payload.put("endDate", project.getEndDate());
        payload.put("studentCanEdit", project.isStudentCanEdit());
        return payload;
    }

    @GetMapping("/tasks")
    public ResponseEntity<Map<String, Object>> taskBoard(@RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "assigneeId", required = false) String assigneeId,
            HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Map<String, Object> result = new HashMap<>();
        Group group = resolveCurrentGroup(student);
        if (group == null) {
            result.put("noGroup", true);
            return ResponseEntity.ok(result);
        }

        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            result.put("noProject", true);
            Map<String, Object> groupPayload = new HashMap<>();
            groupPayload.put("groupId", group.getGroupId());
            groupPayload.put("groupName", group.getGroupName());
            groupPayload.put("leaderId", group.getLeaderId());
            result.put("group", groupPayload);
            return ResponseEntity.ok(result);
        }

        refreshSprintState(project);

        boolean leader = isLeader(group, student);
        List<Student> members = groupMemberRepository.findMemberDetailsOfGroup(group.getGroupId());
        int memberCount = groupMemberRepository.countMembers(group.getGroupId());
        Integer statusFilter = parseIntegerOrNull(status);
        Integer assigneeFilter = parseIntegerOrNull(assigneeId);

        Map<Integer, String> taskStatusLabels = buildTaskStatusLabels();
        if (statusFilter != null && !taskStatusLabels.containsKey(statusFilter)) {
            statusFilter = null;
        }

        boolean projectChangeOpen = hasOpenChangeRequest(project);
        boolean projectNotStarted = isProjectNotStarted(project);
        boolean projectLockedForWork = isProjectLockedForWork(project);
        boolean withinFinalLinkGrace = isWithinFinalLinkGrace(project);
        boolean canTaskOps = canOperateTask(project, projectChangeOpen);
        boolean canManageSprint = leader && isProjectApproved(project) && !projectLockedForWork && !projectChangeOpen;
        boolean canManageTaskPlan = leader && isProjectApproved(project) && !projectLockedForWork && !projectChangeOpen;

        List<Sprint> sprints = sprintRepository.findByProject(project.getProjectId());
        Sprint openSprint = sprintRepository.findOpenByProject(project.getProjectId());
        List<ProjectTask> allTasks = projectTaskRepository.findByProject(project.getProjectId());
        List<ProjectTask> failedTasks = projectTaskRepository.findFailedByProject(project.getProjectId());
        Map<Integer, String> sprintDisplayNames = buildSprintDisplayNames(sprints);

        List<Map<String, Object>> memberPayload = new ArrayList<>();
        for (Student member : members) {
            memberPayload.add(toMemberPayload(member, group));
        }

        List<Map<String, Object>> sprintPayload = new ArrayList<>();
        for (Sprint sprint : sprints) {
            Map<String, Object> sprintItem = toSprintPayload(sprint, sprintDisplayNames);
            int sprintTaskCount = 0;
            int sprintDoneTaskCount = 0;
            for (ProjectTask task : allTasks) {
                if (task == null || task.getSprintId() != sprint.getSprintId()) {
                    continue;
                }
                sprintTaskCount++;
                if (task.getStatus() == ProjectTask.STATUS_DONE) {
                    sprintDoneTaskCount++;
                }
            }
            boolean openSprintItem = !sprint.isClosed() && !sprint.isCancelled();
            sprintItem.put("taskCount", sprintTaskCount);
            sprintItem.put("doneTaskCount", sprintDoneTaskCount);
            sprintItem.put("canRename", canManageSprint && openSprintItem);
            sprintItem.put("canDelete", canManageSprint && openSprintItem && sprintTaskCount == 0);
            sprintItem.put("canCancel", canManageSprint && openSprintItem);
            sprintPayload.add(sprintItem);
        }

        List<Map<String, Object>> filteredTasks = new ArrayList<>();
        for (ProjectTask task : allTasks) {
            if (statusFilter != null && task.getStatus() != statusFilter) {
                continue;
            }
            if (assigneeFilter != null && task.getAssigneeId() != assigneeFilter) {
                continue;
            }
            Map<String, Object> taskPayload = toTaskPayload(task, taskStatusLabels);
            boolean canEditTask = canManageTaskPlan
                    && (task.getStatus() == ProjectTask.STATUS_TODO || task.getStatus() == ProjectTask.STATUS_REJECTED);
            boolean canDeleteTask = canManageTaskPlan && task.getStatus() == ProjectTask.STATUS_TODO;
            boolean canCancelTask = canManageTaskPlan
                    && task.getStatus() != ProjectTask.STATUS_DONE
                    && task.getStatus() != ProjectTask.STATUS_CANCELLED;
            boolean canStartTask = canTaskOps
                    && task.getAssigneeId() == student.getStudentId()
                    && task.getStatus() != ProjectTask.STATUS_FAILED_SPRINT
                    && task.getStatus() != ProjectTask.STATUS_CANCELLED
                    && (task.getStatus() == ProjectTask.STATUS_TODO || task.getStatus() == ProjectTask.STATUS_REJECTED);
            boolean canSubmitTask = canTaskOps
                    && task.getAssigneeId() == student.getStudentId()
                    && task.getStatus() == ProjectTask.STATUS_IN_PROGRESS;
            boolean canUnsubmitTask = canTaskOps
                    && task.getAssigneeId() == student.getStudentId()
                    && task.getStatus() == ProjectTask.STATUS_SUBMITTED;
            boolean canReview = canTaskOps
                    && task.getStatus() == ProjectTask.STATUS_SUBMITTED
                    && task.getStatus() != ProjectTask.STATUS_CANCELLED
                    && canReviewTask(task, group, student);
            taskPayload.put("canEdit", canEditTask);
            taskPayload.put("canDelete", canDeleteTask);
            taskPayload.put("canCancel", canCancelTask);
            taskPayload.put("canStart", canStartTask);
            taskPayload.put("canSubmit", canSubmitTask);
            taskPayload.put("canUnsubmit", canUnsubmitTask);
            taskPayload.put("canReview", canReview);
            taskPayload.put("canReplan", leader && canTaskOps && openSprint != null && task.getStatus() == ProjectTask.STATUS_FAILED_SPRINT);
            filteredTasks.add(taskPayload);
        }

        List<Map<String, Object>> failedTaskPayload = new ArrayList<>();
        for (ProjectTask task : failedTasks) {
            Map<String, Object> taskPayload = toTaskPayload(task, taskStatusLabels);
            taskPayload.put("canReplan", leader && canTaskOps && openSprint != null);
            failedTaskPayload.add(taskPayload);
        }

        Map<String, Object> groupPayload = new HashMap<>();
        groupPayload.put("groupId", group.getGroupId());
        groupPayload.put("groupName", group.getGroupName());
        groupPayload.put("leaderId", group.getLeaderId());
        groupPayload.put("leaderName", group.getLeaderName());
        result.put("group", groupPayload);
        result.put("project", toProjectPayload(project));
        result.put("members", memberPayload);
        result.put("memberCount", memberCount);
        result.put("singleMemberGroup", memberCount <= 1);
        result.put("studentId", student.getStudentId());
        result.put("isLeader", leader);
        result.put("projectNotStarted", projectNotStarted);
        result.put("projectLockedForWork", projectLockedForWork);
        result.put("projectChangeOpen", projectChangeOpen);
        result.put("withinFinalLinkGrace", withinFinalLinkGrace);
        result.put("canCreateSprint", canManageSprint);
        result.put("canManageSprint", canManageSprint);
        result.put("canCreateTask", canManageTaskPlan && canTaskOps && openSprint != null);
        result.put("canManageTaskPlan", canManageTaskPlan);
        result.put("canReplanFailed", leader && canTaskOps && openSprint != null && !failedTasks.isEmpty());
        result.put("canFinalizeLinks", leader && withinFinalLinkGrace);
        result.put("sprints", sprintPayload);
        result.put("openSprint", openSprint == null ? null : toSprintPayload(openSprint, sprintDisplayNames));
        result.put("taskStatusLabels", taskStatusLabels);
        result.put("statusOptions", taskStatusLabels.entrySet().stream().map(entry -> Map.of(
                "value", entry.getKey(),
                "label", entry.getValue())).toList());
        result.put("selectedStatus", statusFilter);
        result.put("selectedAssigneeId", assigneeFilter);
        result.put("tasks", filteredTasks);
        result.put("taskTotalCount", allTasks.size());
        result.put("taskFilteredCount", filteredTasks.size());
        result.put("failedTasks", failedTaskPayload);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/sprints")
    public ResponseEntity<Map<String, Object>> createSprint(@RequestBody(required = false) Map<String, Object> body,
            HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Group group = resolveCurrentGroup(student);
        if (group == null) {
            return badRequest("You don't have a group in the current term.");
        }
        if (!isLeader(group, student)) {
            return badRequest("Only the group leader can perform this action.");
        }

        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            return badRequest("Your group does not have a project yet.");
        }
        if (!isProjectApproved(project)) {
            return badRequest("The project must be approved before creating a sprint.");
        }
        if (project.getStartDate() == null || project.getEndDate() == null) {
            return badRequest("The project does not have start/end dates.");
        }
        if (isProjectLockedForWork(project)) {
            return badRequest("The project has ended, so a new sprint cannot be created.");
        }
        if (hasOpenChangeRequest(project)) {
            return badRequest("The project has a pending topic change request, so planning and task actions are temporarily locked.");
        }

        refreshSprintState(project);
        String sprintName = normalize(body == null ? null : String.valueOf(body.getOrDefault("sprintName", "")));
        int sprintId = sprintRepository.createNextSprint(
                project.getProjectId(),
                sprintName,
                toDate(project.getStartDate()),
                toDate(project.getEndDate()));
        if (sprintId == -2) {
            return badRequest("There is already an open sprint. Please wait for the current sprint to end.");
        }
        if (sprintId == -3) {
            return badRequest("Cannot create a sprint because it exceeds the project timeline.");
        }
        if (sprintId <= 0) {
            return badRequest("Unable to create a new sprint.");
        }

        Sprint createdSprint = sprintRepository.findById(sprintId);
        if (createdSprint != null) {
            studentNotificationService.notifySprintCreated(project, createdSprint);
        }

        Map<String, Object> response = successBody("New sprint created successfully.");
        response.put("sprintId", sprintId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sprints/{sprintId}/rename")
    public ResponseEntity<Map<String, Object>> renameSprint(@PathVariable("sprintId") int sprintId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Group group = resolveCurrentGroup(student);
        if (group == null) {
            return badRequest("You don't have a group in the current term.");
        }
        if (!isLeader(group, student)) {
            return badRequest("Only the group leader can perform this action.");
        }

        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            return badRequest("Your group does not have a project yet.");
        }
        if (!isProjectApproved(project) || isProjectLockedForWork(project)) {
            return badRequest("The project currently does not allow sprint updates.");
        }
        if (hasOpenChangeRequest(project)) {
            return badRequest("The project has a pending topic change request, so planning and task actions are temporarily locked.");
        }

        Sprint sprint = findSprintInProject(project, sprintId);
        if (sprint == null) {
            return badRequest("The sprint does not exist in the group's project.");
        }
        if (sprint.isClosed() || sprint.isCancelled()) {
            return badRequest("Only the open sprint can be renamed.");
        }

        String sprintName = normalize(body == null ? null : String.valueOf(body.getOrDefault("sprintName", "")));
        if (sprintName.isEmpty()) {
            return badRequest("Sprint name cannot be empty.");
        }
        if (sprintName.length() > 50) {
            return badRequest("Sprint name can be at most 50 characters.");
        }

        int updated = sprintRepository.updateSprintName(sprintId, project.getProjectId(), sprintName);
        if (updated <= 0) {
            return badRequest("Unable to update sprint name.");
        }
        return ResponseEntity.ok(successBody("Sprint name updated."));
    }

    @PostMapping("/sprints/{sprintId}/delete")
    public ResponseEntity<Map<String, Object>> deleteSprint(@PathVariable("sprintId") int sprintId,
            HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Group group = resolveCurrentGroup(student);
        if (group == null) {
            return badRequest("You don't have a group in the current term.");
        }
        if (!isLeader(group, student)) {
            return badRequest("Only the group leader can perform this action.");
        }

        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            return badRequest("Your group does not have a project yet.");
        }
        if (!isProjectApproved(project) || isProjectLockedForWork(project)) {
            return badRequest("The project currently does not allow sprint deletion.");
        }
        if (hasOpenChangeRequest(project)) {
            return badRequest("The project has a pending topic change request, so planning and task actions are temporarily locked.");
        }

        Sprint sprint = findSprintInProject(project, sprintId);
        if (sprint == null) {
            return badRequest("The sprint does not exist in the group's project.");
        }
        if (sprint.isClosed() || sprint.isCancelled()) {
            return badRequest("Only the open sprint can be deleted.");
        }
        if (sprintRepository.hasAnyTasks(sprintId)) {
            return badRequest("This sprint has tasks, so it cannot be hard-deleted. Cancel the sprint instead if needed.");
        }

        int deleted = sprintRepository.deleteEmptyOpenSprint(sprintId, project.getProjectId());
        if (deleted <= 0) {
            return badRequest("Unable to delete sprint.");
        }
        return ResponseEntity.ok(successBody("Empty sprint deleted."));
    }

    @Transactional
    @PostMapping("/sprints/{sprintId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelSprint(@PathVariable("sprintId") int sprintId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Group group = resolveCurrentGroup(student);
        if (group == null) {
            return badRequest("You don't have a group in the current term.");
        }
        if (!isLeader(group, student)) {
            return badRequest("Only the group leader can perform this action.");
        }

        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            return badRequest("Your group does not have a project yet.");
        }
        if (!isProjectApproved(project) || isProjectLockedForWork(project)) {
            return badRequest("The project currently does not allow sprint cancellation.");
        }
        if (hasOpenChangeRequest(project)) {
            return badRequest("The project has a pending topic change request, so planning and task actions are temporarily locked.");
        }

        Sprint sprint = findSprintInProject(project, sprintId);
        if (sprint == null) {
            return badRequest("The sprint does not exist in the group's project.");
        }
        if (sprint.isClosed() || sprint.isCancelled()) {
            return badRequest("Only the open sprint can be cancelled.");
        }
        if (projectTaskRepository.hasDoneTasksInSprint(sprintId)) {
            return badRequest("This sprint has completed tasks, so it cannot be cancelled.");
        }

        String cancelReason = normalize(body == null ? null : String.valueOf(body.getOrDefault("cancelReason", "")));
        if (cancelReason.isEmpty()) {
            return badRequest("Please enter a reason for cancelling the sprint.");
        }
        if (cancelReason.length() > 2000) {
            return badRequest("Sprint cancellation reason is too long.");
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

        int updated = sprintRepository.cancelSprint(sprintId, project.getProjectId(), student.getStudentId(), cancelReason);
        if (updated <= 0) {
            return badRequest("Unable to cancel sprint.");
        }
        projectTaskRepository.cancelTasksBySprint(sprintId, student.getStudentId(), "Task cancelled because the sprint was cancelled.");
        studentNotificationService.notifySprintCancelled(project, sprint, cancelReason);

        String sprintLabel = normalize(sprint.getSprintName());
        if (sprintLabel.isEmpty()) {
            sprintLabel = "Sprint #" + sprint.getSprintId();
        }
        for (ProjectTask cancelledTask : tasksCancelledBySprint) {
            studentNotificationService.notifyTaskCancelled(project, cancelledTask,
                    "Sprint \"" + sprintLabel + "\" was cancelled. Reason: " + cancelReason);
        }

        return ResponseEntity.ok(successBody("Sprint cancelled and all unfinished tasks in the sprint were cancelled."));
    }

    @PostMapping("/finalize-links")
    public ResponseEntity<Map<String, Object>> finalizeProjectLinks(@RequestBody(required = false) Map<String, Object> body,
            HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Group group = resolveCurrentGroup(student);
        if (group == null) {
            return badRequest("You don't have a group in the current term.");
        }
        if (!isLeader(group, student)) {
            return badRequest("Only the group leader can finalize the project links.");
        }

        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            return badRequest("Your group does not have a project yet.");
        }
        if (!isProjectApproved(project)) {
            return badRequest("The project is not approved yet, so links cannot be finalized.");
        }
        if (!isWithinFinalLinkGrace(project)) {
            return badRequest("Links can only be finalized within 1 day after the project ends.");
        }

        String sourceCodeUrl = normalize(body == null ? null : String.valueOf(body.getOrDefault("sourceCodeUrl", "")));
        String documentUrl = normalize(body == null ? null : String.valueOf(body.getOrDefault("documentUrl", "")));
        if (sourceCodeUrl.isEmpty() || documentUrl.isEmpty()) {
            return badRequest("Both source code and document links are required.");
        }

        int updated = projectRepository.updateFinalLinks(project.getProjectId(), sourceCodeUrl, documentUrl);
        if (updated <= 0) {
            return badRequest("Unable to update final delivery links.");
        }
        return ResponseEntity.ok(successBody("Final source code and document links updated."));
    }

    @PostMapping("/tasks")
    public ResponseEntity<Map<String, Object>> createTask(@RequestBody(required = false) Map<String, Object> body,
            HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Group group = resolveCurrentGroup(student);
        if (group == null) {
            return badRequest("You don't have a group in the current term.");
        }
        if (!isLeader(group, student)) {
            return badRequest("Only the group leader can perform this action.");
        }

        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            return badRequest("Your group does not have a project yet.");
        }
        if (!isProjectApproved(project)) {
            return badRequest("Tasks can be created only after the project is approved.");
        }
        if (isProjectNotStarted(project)) {
            return badRequest("The project build period has not started yet.");
        }
        if (isProjectLockedForWork(project)) {
            return badRequest("The project has ended, so tasks cannot be created.");
        }
        if (hasOpenChangeRequest(project)) {
            return badRequest("The project has a pending topic change request, so planning and task actions are temporarily locked.");
        }

        refreshSprintState(project);
        Sprint openSprint = sprintRepository.findOpenByProject(project.getProjectId());
        if (openSprint == null) {
            return badRequest("There is no open sprint. Please create a sprint first.");
        }

        Integer sprintId = intValue(body == null ? null : body.get("sprintId"));
        if (sprintId == null || sprintId != openSprint.getSprintId()) {
            return badRequest("Tasks can only be added to the open sprint.");
        }

        String taskName = normalize(body == null ? null : String.valueOf(body.getOrDefault("taskName", "")));
        if (taskName.isEmpty()) {
            return badRequest("Task name cannot be empty.");
        }
        if (taskName.length() > 200) {
            return badRequest("Task name can be at most 200 characters.");
        }

        Double estimatedPoints = parseEstimatedPointsValue(body == null ? null : body.get("estimatedPoints"));
        if (estimatedPoints == null) {
            return badRequest("Estimated points must be greater than 0.");
        }

        Integer assigneeId = intValue(body == null ? null : body.get("assigneeId"));
        if (assigneeId == null || !groupMemberRepository.isMember(group.getGroupId(), assigneeId)) {
            return badRequest("Assignee must be a member of the group.");
        }

        Integer reviewerId = intValue(body == null ? null : body.get("reviewerId"));
        if (reviewerId == null || !groupMemberRepository.isMember(group.getGroupId(), reviewerId)) {
            return badRequest("Reviewer must be a member of the group.");
        }

        int memberCount = groupMemberRepository.countMembers(group.getGroupId());
        if (assigneeId.equals(reviewerId) && memberCount > 1) {
            return badRequest("Assignee and reviewer must be different people (unless the group has only 1 member).");
        }

        int taskId = projectTaskRepository.createTaskInSprint(
                openSprint.getSprintId(),
                taskName,
                normalize(body == null ? null : String.valueOf(body.getOrDefault("description", ""))),
                normalize(body == null ? null : String.valueOf(body.getOrDefault("taskImage", ""))),
                estimatedPoints,
                assigneeId,
                reviewerId);
        if (taskId <= 0) {
            return badRequest("Unable to create task. Please check the data.");
        }

        ProjectTask createdTask = projectTaskRepository.findById(taskId);
        if (createdTask != null) {
            studentNotificationService.notifyTaskAssigned(project, createdTask);
            studentNotificationService.notifyTaskReviewerAssigned(project, createdTask);
        }

        Map<String, Object> response = successBody(
                "Task created. Points: " + estimatedPoints + " equals " + (estimatedPoints * 4.0d) + " hours.");
        response.put("taskId", taskId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/tasks/{taskId}/update")
    public ResponseEntity<Map<String, Object>> updateTask(@PathVariable("taskId") int taskId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Group group = resolveCurrentGroup(student);
        if (group == null) {
            return badRequest("You don't have a group in the current term.");
        }
        if (!isLeader(group, student)) {
            return badRequest("Only the group leader can perform this action.");
        }

        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            return badRequest("Your group does not have a project yet.");
        }
        if (!isProjectApproved(project) || isProjectLockedForWork(project)) {
            return badRequest("The project currently does not allow task updates.");
        }
        if (hasOpenChangeRequest(project)) {
            return badRequest("The project has a pending topic change request, so planning and task actions are temporarily locked.");
        }

        ProjectTask task = findTaskInProject(project, taskId);
        if (task == null) {
            return badRequest("Task does not exist in the group's project.");
        }
        if (task.getStatus() != ProjectTask.STATUS_TODO && task.getStatus() != ProjectTask.STATUS_REJECTED) {
            return badRequest("Only tasks in Pending or Returned status can be edited.");
        }

        String taskName = normalize(body == null ? null : String.valueOf(body.getOrDefault("taskName", "")));
        if (taskName.isEmpty()) {
            return badRequest("Task name cannot be empty.");
        }
        if (taskName.length() > 200) {
            return badRequest("Task name can be at most 200 characters.");
        }

        Double estimatedPoints = parseEstimatedPointsValue(body == null ? null : body.get("estimatedPoints"));
        if (estimatedPoints == null) {
            return badRequest("Estimated points must be greater than 0.");
        }

        Integer assigneeId = intValue(body == null ? null : body.get("assigneeId"));
        if (assigneeId == null || !groupMemberRepository.isMember(group.getGroupId(), assigneeId)) {
            return badRequest("Assignee must be a member of the group.");
        }

        Integer reviewerId = intValue(body == null ? null : body.get("reviewerId"));
        if (reviewerId == null || !groupMemberRepository.isMember(group.getGroupId(), reviewerId)) {
            return badRequest("Reviewer must be a member of the group.");
        }

        int memberCount = groupMemberRepository.countMembers(group.getGroupId());
        if (assigneeId.equals(reviewerId) && memberCount > 1) {
            return badRequest("Assignee and reviewer must be different people (unless the group has only 1 member).");
        }

        int previousAssigneeId = task.getAssigneeId();
        Integer previousReviewerId = task.getReviewerId();

        int updated = projectTaskRepository.updateTaskDetails(
                taskId,
                taskName,
                normalize(body == null ? null : String.valueOf(body.getOrDefault("description", ""))),
                normalize(body == null ? null : String.valueOf(body.getOrDefault("taskImage", ""))),
                estimatedPoints,
                assigneeId,
                reviewerId);
        if (updated <= 0) {
            return badRequest("Unable to update task.");
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

        return ResponseEntity.ok(successBody("Task updated."));
    }

    @PostMapping("/tasks/{taskId}/delete")
    public ResponseEntity<Map<String, Object>> deleteTask(@PathVariable("taskId") int taskId,
            HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Group group = resolveCurrentGroup(student);
        if (group == null) {
            return badRequest("You don't have a group in the current term.");
        }
        if (!isLeader(group, student)) {
            return badRequest("Only the group leader can perform this action.");
        }

        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            return badRequest("Your group does not have a project yet.");
        }
        if (!isProjectApproved(project) || isProjectLockedForWork(project)) {
            return badRequest("The project currently does not allow task deletion.");
        }
        if (hasOpenChangeRequest(project)) {
            return badRequest("The project has a pending topic change request, so planning and task actions are temporarily locked.");
        }

        ProjectTask task = findTaskInProject(project, taskId);
        if (task == null) {
            return badRequest("Task does not exist in the group's project.");
        }
        if (task.getStatus() != ProjectTask.STATUS_TODO) {
            return badRequest("Only untouched tasks in Pending status can be hard-deleted.");
        }

        int deleted = projectTaskRepository.deleteTaskIfPristine(taskId);
        if (deleted <= 0) {
            return badRequest("Unable to delete task. If the task has history, please cancel it instead.");
        }
        return ResponseEntity.ok(successBody("Untouched task deleted."));
    }

    @PostMapping("/tasks/{taskId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelTask(@PathVariable("taskId") int taskId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Group group = resolveCurrentGroup(student);
        if (group == null) {
            return badRequest("You don't have a group in the current term.");
        }
        if (!isLeader(group, student)) {
            return badRequest("Only the group leader can perform this action.");
        }

        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            return badRequest("Your group does not have a project yet.");
        }
        if (!isProjectApproved(project) || isProjectLockedForWork(project)) {
            return badRequest("The project currently does not allow task cancellation.");
        }
        if (hasOpenChangeRequest(project)) {
            return badRequest("The project has a pending topic change request, so planning and task actions are temporarily locked.");
        }

        ProjectTask task = findTaskInProject(project, taskId);
        if (task == null) {
            return badRequest("Task does not exist in the group's project.");
        }
        if (task.getStatus() == ProjectTask.STATUS_DONE) {
            return badRequest("Completed tasks cannot be cancelled.");
        }
        if (task.getStatus() == ProjectTask.STATUS_CANCELLED) {
            return badRequest("This task has already been cancelled.");
        }

        String cancelReason = normalize(body == null ? null : String.valueOf(body.getOrDefault("cancelReason", "")));
        if (cancelReason.isEmpty()) {
            return badRequest("Please enter a reason for cancelling the task.");
        }
        if (cancelReason.length() > 2000) {
            return badRequest("Task cancellation reason is too long.");
        }

        int updated = projectTaskRepository.cancelTask(taskId, student.getStudentId(), cancelReason);
        if (updated <= 0) {
            return badRequest("Unable to cancel task.");
        }
        studentNotificationService.notifyTaskCancelled(project, task, cancelReason);
        return ResponseEntity.ok(successBody("Task cancelled."));
    }

    @PostMapping("/tasks/{taskId}/start")
    public ResponseEntity<Map<String, Object>> startTask(@PathVariable("taskId") int taskId,
            HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Group group = resolveCurrentGroup(student);
        if (group == null) {
            return badRequest("You don't have a group in the current term.");
        }

        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            return badRequest("Your group does not have a project yet.");
        }

        boolean projectChangeOpen = hasOpenChangeRequest(project);
        if (!canOperateTask(project, projectChangeOpen)) {
            return badRequest(projectChangeOpen
                    ? "The project has a pending topic change request, so task actions are temporarily locked."
                    : "The project currently does not allow task operations.");
        }

        refreshSprintState(project);
        ProjectTask task = findTaskInProject(project, taskId);
        if (task == null) {
            return badRequest("Task does not exist in the group's project.");
        }
        if (task.getAssigneeId() != student.getStudentId()) {
            return badRequest("Only the assignee can start this task.");
        }
        if (task.getStatus() == ProjectTask.STATUS_FAILED_SPRINT) {
            return badRequest("This task failed in a previous sprint. Ask the group leader to move it to a new sprint.");
        }
        if (task.getStatus() == ProjectTask.STATUS_CANCELLED) {
            return badRequest("This task was cancelled, so it cannot be started again.");
        }

        int updated = projectTaskRepository.markInProgress(taskId, student.getStudentId());
        if (updated <= 0) {
            return badRequest("Unable to move task to In Progress.");
        }
        return ResponseEntity.ok(successBody("Task started."));
    }

    @PostMapping(value = "/tasks/{taskId}/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> submitTask(@PathVariable("taskId") int taskId,
            @RequestParam(name = "submissionNote", required = false) String submissionNote,
            @RequestParam(name = "submissionUrl", required = false) String submissionUrl,
            @RequestParam(name = "submissionCode", required = false) String submissionCode,
            @RequestParam(name = "submissionFiles", required = false) MultipartFile[] submissionFiles,
            @RequestParam(name = "clearFiles", required = false) String clearFiles,
            @RequestParam(name = "clearCode", required = false) String clearCode,
            HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Group group = resolveCurrentGroup(student);
        if (group == null) {
            return badRequest("You don't have a group in the current term.");
        }

        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            return badRequest("Your group does not have a project yet.");
        }

        boolean projectChangeOpen = hasOpenChangeRequest(project);
        if (!canOperateTask(project, projectChangeOpen)) {
            return badRequest(projectChangeOpen
                    ? "The project has a pending topic change request, so task actions are temporarily locked."
                    : "The project currently does not allow task operations.");
        }

        refreshSprintState(project);
        ProjectTask task = findTaskInProject(project, taskId);
        if (task == null) {
            return badRequest("Task does not exist in the group's project.");
        }
        if (task.getAssigneeId() != student.getStudentId()) {
            return badRequest("Only the assignee can submit this task.");
        }
        if (task.getStatus() == ProjectTask.STATUS_CANCELLED) {
            return badRequest("This task was cancelled, so it cannot be submitted.");
        }

        String note = normalize(submissionNote);
        String url = normalize(submissionUrl);
        String code = normalize(submissionCode);
        if (url.length() > 500) {
            return badRequest("Reference link is too long.");
        }
        if (code.length() > 20000) {
            return badRequest("Code is too long, please shorten it.");
        }

        List<TaskAttachment> existingAttachments = parseStoredAttachments(task.getSubmissionFiles());
        boolean removeFiles = clearFiles != null;
        boolean removeCode = clearCode != null;

        List<TaskAttachment> newAttachments;
        try {
            newAttachments = storeAttachments(taskId, submissionFiles);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        } catch (IOException ex) {
            return badRequest("Unable to save uploaded files.");
        }

        List<TaskAttachment> finalAttachments = existingAttachments;
        if (removeFiles) {
            deleteAttachments(taskId, existingAttachments);
            finalAttachments = new ArrayList<>();
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
            return badRequest("Submission must include a description, link, file, or code snippet.");
        }

        int updated = projectTaskRepository.submitTask(
                taskId,
                student.getStudentId(),
                note,
                url,
                serializeAttachments(finalAttachments),
                finalCode);
        if (updated <= 0) {
            return badRequest("Unable to submit the task. Make sure the task is In Progress.");
        }

        ProjectTask submittedTask = projectTaskRepository.findById(taskId);
        studentNotificationService.notifyTaskSubmitted(project, submittedTask != null ? submittedTask : task);
        return ResponseEntity.ok(successBody("Task submitted and is awaiting review by the reviewer or group leader."));
    }

    @PostMapping("/tasks/{taskId}/unsubmit")
    public ResponseEntity<Map<String, Object>> unsubmitTask(@PathVariable("taskId") int taskId,
            HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Group group = resolveCurrentGroup(student);
        if (group == null) {
            return badRequest("You don't have a group in the current term.");
        }

        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            return badRequest("Your group does not have a project yet.");
        }

        boolean projectChangeOpen = hasOpenChangeRequest(project);
        if (!canOperateTask(project, projectChangeOpen)) {
            return badRequest(projectChangeOpen
                    ? "The project has a pending topic change request, so task actions are temporarily locked."
                    : "The project currently does not allow task operations.");
        }

        refreshSprintState(project);
        ProjectTask task = findTaskInProject(project, taskId);
        if (task == null) {
            return badRequest("Task does not exist in the group's project.");
        }
        if (task.getAssigneeId() != student.getStudentId()) {
            return badRequest("Only the assignee can cancel the submission.");
        }
        if (task.getStatus() != ProjectTask.STATUS_SUBMITTED) {
            return badRequest("Task is not in Awaiting Review status.");
        }

        int updated = projectTaskRepository.unsubmitTask(taskId, student.getStudentId());
        if (updated <= 0) {
            return badRequest("Unable to cancel submission. Please try again.");
        }

        studentNotificationService.notifyTaskSubmissionCancelled(project, task);
        return ResponseEntity.ok(successBody("Submission cancelled. Task moved back to In Progress."));
    }

    @PostMapping("/tasks/{taskId}/review")
    public ResponseEntity<Map<String, Object>> reviewTask(@PathVariable("taskId") int taskId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Group group = resolveCurrentGroup(student);
        if (group == null) {
            return badRequest("You don't have a group in the current term.");
        }

        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            return badRequest("Your group does not have a project yet.");
        }

        boolean projectChangeOpen = hasOpenChangeRequest(project);
        if (!canOperateTask(project, projectChangeOpen)) {
            return badRequest(projectChangeOpen
                    ? "The project has a pending topic change request, so task actions are temporarily locked."
                    : "The project currently does not allow task operations.");
        }

        refreshSprintState(project);
        ProjectTask task = findTaskInProject(project, taskId);
        if (task == null) {
            return badRequest("Task does not exist in the group's project.");
        }
        if (task.getStatus() == ProjectTask.STATUS_CANCELLED) {
            return badRequest("This task was cancelled, so it cannot be reviewed.");
        }
        if (!canReviewTask(task, group, student)) {
            return badRequest("You are not allowed to review this task.");
        }

        String action = normalize(body == null ? null : String.valueOf(body.getOrDefault("action", ""))).toLowerCase(Locale.ROOT);
        String comment = normalize(body == null ? null : String.valueOf(body.getOrDefault("reviewComment", "")));

        if ("approve".equals(action)) {
            int updated = projectTaskRepository.approveTask(taskId, comment);
            if (updated <= 0) {
                return badRequest("Unable to approve task. It must be in Awaiting Review status.");
            }
            studentNotificationService.notifyTaskCompleted(project, task);
            return ResponseEntity.ok(successBody("Task approved as completed."));
        }

        if ("reject".equals(action)) {
            if (comment.isEmpty()) {
                return badRequest("Please enter a reason when returning the task.");
            }
            int updated = projectTaskRepository.rejectTask(taskId, comment);
            if (updated <= 0) {
                return badRequest("Unable to return task. It must be in Awaiting Review status.");
            }
            studentNotificationService.notifyTaskReturned(project, task, comment);
            return ResponseEntity.ok(successBody("Task returned for the student to revise and resubmit."));
        }

        return badRequest("Invalid task review action.");
    }

    @GetMapping("/tasks/{taskId}/files/{fileId:.+}")
    public ResponseEntity<Resource> downloadTaskFile(@PathVariable("taskId") int taskId,
            @PathVariable("fileId") String fileId,
            HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        ProjectTask task = projectTaskRepository.findById(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }

        Project project = projectRepository.findHistoryProjectForStudent(task.getProjectId(), student.getStudentId());
        if (project == null || project.getProjectId() <= 0) {
            return ResponseEntity.notFound().build();
        }

        List<TaskAttachment> attachments = parseStoredAttachments(task.getSubmissionFiles());
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

    @GetMapping("/tasks/{taskId}/code-preview")
    public ResponseEntity<Map<String, Object>> previewTaskCode(@PathVariable("taskId") int taskId,
            @RequestParam(name = "file", required = false) String fileId,
            HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        ProjectTask task = projectTaskRepository.findById(taskId);
        if (task == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "message", "Task does not exist."));
        }

        Project project = projectRepository.findHistoryProjectForStudent(task.getProjectId(), student.getStudentId());
        if (project == null || project.getProjectId() <= 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "message", "You do not have access to this task."));
        }

        String codeContent;
        String codeTitle;
        String downloadUrl = null;
        boolean inlineCode = isBlank(fileId);

        if (inlineCode) {
            if (isBlank(task.getSubmissionCode())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "No inline code has been submitted for this task."));
            }
            codeContent = task.getSubmissionCode();
            codeTitle = "Inline code";
        } else {
            List<TaskAttachment> attachments = parseStoredAttachments(task.getSubmissionFiles());
            TaskAttachment target = null;
            for (TaskAttachment attachment : attachments) {
                if (attachment != null && attachment.getStoredName().equals(fileId)) {
                    target = attachment;
                    break;
                }
            }
            if (target == null || !target.isViewable()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Cannot view code from this file."));
            }

            Path filePath = Paths.get(System.getProperty("user.dir"), TASK_UPLOAD_DIR,
                    String.valueOf(taskId), target.getStoredName());
            if (!Files.exists(filePath)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "success", false,
                        "message", "File not found."));
            }
            try {
                long size = Files.size(filePath);
                if (size > MAX_CODE_VIEW_BYTES) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "success", false,
                            "message", "File is too large to preview."));
                }
                codeContent = Files.readString(filePath, StandardCharsets.UTF_8);
                codeTitle = target.getDisplayName();
                downloadUrl = buildTaskFileUrl(taskId, target.getStoredName());
            } catch (IOException ex) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Unable to read file."));
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("taskId", task.getTaskId());
        response.put("taskName", task.getTaskName());
        response.put("projectId", project.getProjectId());
        response.put("projectName", project.getProjectName());
        response.put("codeTitle", codeTitle);
        response.put("downloadUrl", downloadUrl);
        response.put("inlineCode", inlineCode);
        response.put("fileId", isBlank(fileId) ? null : fileId);
        response.put("content", codeContent);
        response.put("lineCount", codeContent.split("\\r?\\n", -1).length);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sprints/{sprintId}/replan")
    public ResponseEntity<Map<String, Object>> replanFailedTask(@PathVariable("sprintId") int sprintId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Group group = resolveCurrentGroup(student);
        if (group == null) {
            return badRequest("You don't have a group in the current term.");
        }
        if (!isLeader(group, student)) {
            return badRequest("Only the group leader can perform this action.");
        }

        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            return badRequest("Your group does not have a project yet.");
        }

        boolean projectChangeOpen = hasOpenChangeRequest(project);
        if (!canOperateTask(project, projectChangeOpen)) {
            return badRequest(projectChangeOpen
                    ? "The project has a pending topic change request, so task actions are temporarily locked."
                    : "The project currently does not allow task operations.");
        }

        refreshSprintState(project);
        Sprint openSprint = sprintRepository.findOpenByProject(project.getProjectId());
        if (openSprint == null) {
            return badRequest("There is no open sprint to receive failed tasks.");
        }
        if (openSprint.getSprintId() != sprintId) {
            return badRequest("Failed tasks can only be moved to the open sprint.");
        }

        Integer taskId = intValue(body == null ? null : body.get("taskId"));
        if (taskId == null) {
            return badRequest("Task is invalid or not in Failed (sprint) status.");
        }

        ProjectTask task = findTaskInProject(project, taskId);
        if (task == null || task.getStatus() != ProjectTask.STATUS_FAILED_SPRINT) {
            return badRequest("Task is invalid or not in Failed (sprint) status.");
        }

        Integer assigneeId = intValue(body == null ? null : body.get("assigneeId"));
        if (assigneeId == null || !groupMemberRepository.isMember(group.getGroupId(), assigneeId)) {
            return badRequest("Assignee must be a member of the group.");
        }

        Integer reviewerId = intValue(body == null ? null : body.get("reviewerId"));
        if (reviewerId == null || !groupMemberRepository.isMember(group.getGroupId(), reviewerId)) {
            return badRequest("Reviewer must be a member of the group.");
        }

        int memberCount = groupMemberRepository.countMembers(group.getGroupId());
        if (assigneeId.equals(reviewerId) && memberCount > 1) {
            return badRequest("Assignee and reviewer must be different people (unless the group has only 1 member).");
        }

        int updated = projectTaskRepository.replanFailedTask(taskId, sprintId, assigneeId, reviewerId);
        if (updated <= 0) {
            return badRequest("Unable to move failed task to the new sprint.");
        }

        ProjectTask replannedTask = projectTaskRepository.findById(taskId);
        if (replannedTask != null) {
            studentNotificationService.notifyTaskReplanned(project, replannedTask);
        }
        return ResponseEntity.ok(successBody("Failed task moved to the new sprint and reassigned."));
    }

    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> history(HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        int currentSemesterId = resolveCurrentSemesterId();
        List<Project> historyProjects = projectRepository.findHistoryByStudent(student.getStudentId());
        List<Map<String, Object>> entries = new ArrayList<>();

        for (Project project : historyProjects) {
            if (project == null || project.getProjectId() <= 0) {
                continue;
            }

            List<Sprint> sprints = sprintRepository.findByProject(project.getProjectId());
            List<ProjectTask> tasks = projectTaskRepository.findByProject(project.getProjectId());
            ProjectScoreRecord score = projectScoreRepository.findLatestByProjectAndStudent(project.getProjectId(), student.getStudentId());

            Map<String, Object> entry = new HashMap<>();
            entry.put("project", toProjectPayload(project));
            entry.put("currentSemester", Objects.equals(project.getSemesterId(), currentSemesterId));
            entry.put("sprintCount", sprints != null ? sprints.size() : 0);
            entry.put("totalTasks", tasks != null ? tasks.size() : 0);
            entry.put("doneTasks", countTasksByStatus(tasks, ProjectTask.STATUS_DONE));
            entry.put("failedTasks", countTasksByStatus(tasks, ProjectTask.STATUS_FAILED_SPRINT));
            entry.put("finalScore", score != null ? score.getFinalScore() : null);
            entry.put("scorePublished", score != null && score.isPublished());
            entries.add(entry);
        }

        return ResponseEntity.ok(Map.of(
                "entries", entries,
                "currentSemesterId", currentSemesterId));
    }

    @GetMapping("/history/{projectId}")
    public ResponseEntity<Map<String, Object>> historyDetail(@PathVariable("projectId") int projectId, HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Project project = projectRepository.findHistoryProjectForStudent(projectId, student.getStudentId());
        if (project == null || project.getProjectId() <= 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "message", "This project history item does not exist or is no longer available."));
        }

        Group group = project.getGroupId() > 0 ? groupRepository.findById(project.getGroupId()) : null;
        List<Student> members = groupMemberRepository.findMemberDetailsOfGroup(project.getGroupId());
        List<ProjectTemplateAttachment> templateAttachments = project.getTemplateId() > 0
                ? projectTemplateService.findAttachments(project.getTemplateId())
                : List.of();
        List<ProjectAttachment> projectFiles = projectAttachmentService.findByProjectId(project.getProjectId());
        List<Sprint> sprints = sprintRepository.findByProject(project.getProjectId());
        List<ProjectTask> tasks = projectTaskRepository.findByProject(project.getProjectId());
        List<MemberPerformance> overallPerformance = projectTaskRepository.findMemberPerformanceOverallByProject(project.getProjectId());
        List<MemberPerformance> sprintPerformance = projectTaskRepository.findMemberPerformanceBySprint(project.getProjectId());
        List<ProjectComment> lecturerComments = projectCommentRepository.findByProject(project.getProjectId());
        ProjectScoreRecord score = projectScoreRepository.findLatestByProjectAndStudent(project.getProjectId(), student.getStudentId());

        Map<Integer, String> sprintDisplayNames = buildSprintDisplayNames(sprints);
        Map<Integer, String> taskStatusLabels = buildTaskStatusLabels();

        List<Map<String, Object>> memberPayload = new ArrayList<>();
        for (Student member : members) {
            memberPayload.add(toMemberPayload(member, group));
        }

        List<Map<String, Object>> templateAttachmentPayload = new ArrayList<>();
        for (ProjectTemplateAttachment attachment : templateAttachments) {
            Map<String, Object> item = new HashMap<>();
            item.put("attachmentId", attachment.getAttachmentId());
            item.put("fileName", attachment.getFileName());
            item.put("fileUrl", attachment.getFileUrl());
            item.put("contentType", attachment.getContentType());
            item.put("fileSize", attachment.getFileSize());
            item.put("createdAt", attachment.getCreatedAt());
            templateAttachmentPayload.add(item);
        }

        List<Map<String, Object>> projectFilePayload = new ArrayList<>();
        for (ProjectAttachment attachment : projectFiles) {
            Map<String, Object> item = new HashMap<>();
            item.put("attachmentId", attachment.getAttachmentId());
            item.put("fileName", attachment.getFileName());
            item.put("fileUrl", attachment.getFileUrl());
            item.put("contentType", attachment.getContentType());
            item.put("fileSize", attachment.getFileSize());
            item.put("createdAt", attachment.getCreatedAt());
            projectFilePayload.add(item);
        }

        List<Map<String, Object>> sprintPayload = new ArrayList<>();
        for (Sprint sprint : sprints) {
            sprintPayload.add(toSprintPayload(sprint, sprintDisplayNames));
        }

        List<Map<String, Object>> taskPayload = new ArrayList<>();
        for (ProjectTask task : tasks) {
            taskPayload.add(toTaskPayload(task, taskStatusLabels));
        }

        List<Map<String, Object>> overallPerformancePayload = new ArrayList<>();
        for (MemberPerformance item : overallPerformance) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("studentId", item.getStudentId());
            payload.put("studentCode", item.getStudentCode());
            payload.put("studentName", item.getStudentName());
            payload.put("totalTasks", item.getTotalTasks());
            payload.put("doneTasks", item.getDoneTasks());
            payload.put("failedTasks", item.getFailedTasks());
            payload.put("submittedTasks", item.getSubmittedTasks());
            payload.put("inProgressTasks", item.getInProgressTasks());
            payload.put("todoTasks", item.getTodoTasks());
            payload.put("doneRatePercent", item.getDoneRatePercent());
            overallPerformancePayload.add(payload);
        }

        List<Map<String, Object>> sprintPerformancePayload = new ArrayList<>();
        for (MemberPerformance item : sprintPerformance) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("sprintId", item.getSprintId());
            payload.put("sprintName", item.getSprintName());
            payload.put("studentId", item.getStudentId());
            payload.put("studentCode", item.getStudentCode());
            payload.put("studentName", item.getStudentName());
            payload.put("totalTasks", item.getTotalTasks());
            payload.put("doneTasks", item.getDoneTasks());
            payload.put("failedTasks", item.getFailedTasks());
            payload.put("submittedTasks", item.getSubmittedTasks());
            payload.put("inProgressTasks", item.getInProgressTasks());
            payload.put("todoTasks", item.getTodoTasks());
            sprintPerformancePayload.add(payload);
        }

        List<Map<String, Object>> commentPayload = new ArrayList<>();
        for (ProjectComment comment : lecturerComments) {
            Map<String, Object> item = new HashMap<>();
            item.put("commentId", comment.getCommentId());
            item.put("lecturerId", comment.getLecturerId());
            item.put("lecturerName", comment.getLecturerName());
            item.put("lecturerCode", comment.getLecturerCode());
            item.put("commentContent", comment.getCommentContent());
            item.put("createdAt", comment.getCreatedAt());
            commentPayload.add(item);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("project", toProjectPayload(project));
        response.put("currentSemester", isCurrentSemesterProject(project));
        response.put("members", memberPayload);
        response.put("templateAttachments", templateAttachmentPayload);
        response.put("projectFiles", projectFilePayload);
        response.put("sprints", sprintPayload);
        response.put("tasks", taskPayload);
        response.put("overallPerformance", overallPerformancePayload);
        response.put("sprintPerformance", sprintPerformancePayload);
        response.put("lecturerComments", commentPayload);
        response.put("taskStatusLabels", taskStatusLabels);
        response.put("sprintCount", sprints.size());
        response.put("doneTaskCount", countTasksByStatus(tasks, ProjectTask.STATUS_DONE));
        response.put("failedTaskCount", countTasksByStatus(tasks, ProjectTask.STATUS_FAILED_SPRINT));

        if (score != null) {
            Map<String, Object> scorePayload = new HashMap<>();
            scorePayload.put("scoreId", score.getScoreId());
            scorePayload.put("lecturerId", score.getLecturerId());
            scorePayload.put("lecturerScore", score.getLecturerScore());
            scorePayload.put("lecturerComment", score.getLecturerComment());
            scorePayload.put("staffAdjustedScore", score.getStaffAdjustedScore());
            scorePayload.put("staffNote", score.getStaffNote());
            scorePayload.put("finalScore", score.getFinalScore());
            scorePayload.put("published", score.isPublished());
            response.put("score", scorePayload);
        } else {
            response.put("score", null);
        }

        return ResponseEntity.ok(response);
    }
}

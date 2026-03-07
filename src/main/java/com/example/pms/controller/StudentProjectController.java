package com.example.pms.controller;

import com.example.pms.model.Account;
import com.example.pms.model.Classes;
import com.example.pms.model.Group;
import com.example.pms.model.Project;
import com.example.pms.model.ProjectEditRequest;
import com.example.pms.model.ProjectTask;
import com.example.pms.model.Semester;
import com.example.pms.model.Sprint;
import com.example.pms.model.Student;
import com.example.pms.repository.ClassRepository;
import com.example.pms.repository.GroupInvitationRepository;
import com.example.pms.repository.GroupMemberRepository;
import com.example.pms.repository.GroupRepository;
import com.example.pms.repository.ProjectEditRequestRepository;
import com.example.pms.repository.ProjectRepository;
import com.example.pms.repository.ProjectTaskRepository;
import com.example.pms.repository.SemesterRepository;
import com.example.pms.repository.SprintRepository;
import com.example.pms.service.MailService;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
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
    private ProjectTaskRepository projectTaskRepository;

    @Autowired
    private SprintRepository sprintRepository;

    @Autowired
    private MailService mailService;

    private String normalize(String value) {
        return value == null ? "" : value.trim();
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
        model.addAttribute("studentName", fullName != null ? fullName : (student != null ? student.getFullName() : "Học sinh"));
        model.addAttribute("userRole", "Học sinh");
        model.addAttribute("className", resolveClassName(student));

        boolean invitationEnabled = groupInvitationRepository.isInvitationTableAvailable();
        model.addAttribute("invitationFeatureEnabled", invitationEnabled);
        if (student != null && invitationEnabled) {
            int incomingFromLeader = groupInvitationRepository.countPendingByStudentFromLeader(student.getStudentId());
            int needLeaderApprove = groupInvitationRepository.countPendingForLeader(student.getStudentId());
            model.addAttribute("notificationCount", incomingFromLeader + needLeaderApprove);
        } else {
            model.addAttribute("notificationCount", 0);
        }
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

    private boolean canReviewTask(ProjectTask task, Group group, Student student) {
        if (task == null || group == null || student == null) {
            return false;
        }
        if (task.getAssigneeId() == student.getStudentId()) {
            return false;
        }
        if (task.getReviewerId() != null && task.getReviewerId() == student.getStudentId()) {
            return true;
        }
        return isLeader(group, student);
    }

    private void refreshSprintState(Project project) {
        if (project != null) {
            sprintRepository.closeExpiredSprintsAndFailTasks(project.getProjectId());
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

    private boolean canOperateTask(Project project) {
        return isProjectApproved(project) && !isProjectNotStarted(project) && !isProjectLockedForWork(project);
    }

    private LocalDate toDate(LocalDateTime time) {
        return time == null ? null : time.toLocalDate();
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

        model.addAttribute("group", group);
        model.addAttribute("isLeader", leader);
        model.addAttribute("members", members);
        model.addAttribute("studentId", student.getStudentId());

        if (project == null) {
            model.addAttribute("noProject", true);
            return "student/project/home";
        }

        refreshSprintState(project);
        ProjectEditRequest latestRequest = projectEditRequestRepository.findLatestByProject(project.getProjectId());
        boolean canRequestEdit = isStudentSourceProject(project)
                && !project.isStudentCanEdit()
                && !projectEditRequestRepository.existsPendingByProjectAndStudent(project.getProjectId(), student.getStudentId());
        List<Sprint> sprints = sprintRepository.findByProject(project.getProjectId());
        Sprint openSprint = sprintRepository.findOpenByProject(project.getProjectId());
        List<ProjectTask> tasks = projectTaskRepository.findByProject(project.getProjectId());
        List<ProjectTask> failedTasks = projectTaskRepository.findFailedByProject(project.getProjectId());

        boolean projectNotStarted = isProjectNotStarted(project);
        boolean projectLockedForWork = isProjectLockedForWork(project);
        boolean withinFinalLinkGrace = isWithinFinalLinkGrace(project);
        boolean canTaskOps = canOperateTask(project);

        model.addAttribute("project", project);
        model.addAttribute("latestEditRequest", latestRequest);
        model.addAttribute("canRequestEdit", canRequestEdit);
        model.addAttribute("canEditContent", isStudentSourceProject(project) && project.isStudentCanEdit() && !projectLockedForWork);
        model.addAttribute("canSubmitReview",
                isStudentSourceProject(project)
                        && project.isStudentCanEdit()
                        && !normalize(project.getProjectName()).isEmpty()
                        && !projectLockedForWork);
        model.addAttribute("projectNotStarted", projectNotStarted);
        model.addAttribute("projectLockedForWork", projectLockedForWork);
        model.addAttribute("withinFinalLinkGrace", withinFinalLinkGrace);
        model.addAttribute("canUpdateFinalLinks", leader && withinFinalLinkGrace);
        model.addAttribute("canCreateSprint", leader && isProjectApproved(project) && !projectLockedForWork);
        model.addAttribute("canCreateTask", leader && canTaskOps && openSprint != null);
        model.addAttribute("canReplanFailed", leader && canTaskOps && openSprint != null && failedTasks != null && !failedTasks.isEmpty());
        model.addAttribute("sprints", sprints);
        model.addAttribute("openSprint", openSprint);
        model.addAttribute("tasks", tasks);
        model.addAttribute("failedTasks", failedTasks);
        return "student/project/home";
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
            redirectAttributes.addFlashAttribute("error", "Bạn chưa có nhóm trong học kỳ hiện tại.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            redirectAttributes.addFlashAttribute("error", "Nhóm của bạn chưa được tạo project.");
            return "redirect:/student/project";
        }
        if (!isStudentSourceProject(project)) {
            redirectAttributes.addFlashAttribute("error", "Project này không thuộc luồng học viên tự cập nhật nội dung.");
            return "redirect:/student/project";
        }
        if (project.isStudentCanEdit()) {
            redirectAttributes.addFlashAttribute("success", "Bạn đã có quyền cập nhật nội dung project.");
            return "redirect:/student/project";
        }
        if (projectEditRequestRepository.existsPendingByProjectAndStudent(project.getProjectId(), student.getStudentId())) {
            redirectAttributes.addFlashAttribute("error", "Bạn đã gửi yêu cầu cấp quyền và đang chờ nhân viên xử lý.");
            return "redirect:/student/project";
        }
        String note = normalize(requestNote);
        if (note.length() > 2000) {
            redirectAttributes.addFlashAttribute("error", "Ghi chú yêu cầu quá dài.");
            return "redirect:/student/project";
        }
        int requestId = projectEditRequestRepository.createRequest(project.getProjectId(), student.getStudentId(), note);
        if (requestId <= 0) {
            redirectAttributes.addFlashAttribute("error", "Không thể gửi yêu cầu cấp quyền. Vui lòng thử lại.");
            return "redirect:/student/project";
        }
        redirectAttributes.addFlashAttribute("success", "Đã gửi yêu cầu cấp quyền cập nhật nội dung project.");
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
            redirectAttributes.addFlashAttribute("error", "Bạn chưa có nhóm trong học kỳ hiện tại.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            redirectAttributes.addFlashAttribute("error", "Nhóm của bạn chưa được tạo project.");
            return "redirect:/student/project";
        }
        if (!isStudentSourceProject(project)) {
            redirectAttributes.addFlashAttribute("error", "Project này không thuộc luồng học viên tự cập nhật nội dung.");
            return "redirect:/student/project";
        }
        if (!project.isStudentCanEdit()) {
            redirectAttributes.addFlashAttribute("error", "Bạn chưa được cấp quyền cập nhật nội dung project.");
            return "redirect:/student/project";
        }
        if (isProjectLockedForWork(project)) {
            redirectAttributes.addFlashAttribute("error", "Project đã hết hạn nên không thể cập nhật thêm nội dung.");
            return "redirect:/student/project";
        }
        String normalizedName = normalize(projectName);
        if (normalizedName.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Tên project không được để trống.");
            return "redirect:/student/project";
        }
        if (normalizedName.length() > 200) {
            redirectAttributes.addFlashAttribute("error", "Tên project tối đa 200 ký tự.");
            return "redirect:/student/project";
        }
        int updated = projectRepository.updateStudentContent(
                project.getProjectId(),
                normalizedName,
                normalize(description),
                normalize(sourceCodeUrl),
                normalize(documentUrl));
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Không thể cập nhật nội dung project.");
            return "redirect:/student/project";
        }
        redirectAttributes.addFlashAttribute("success", "Đã cập nhật nội dung project.");
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
            redirectAttributes.addFlashAttribute("error", "Bạn chưa có nhóm trong học kỳ hiện tại.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            redirectAttributes.addFlashAttribute("error", "Nhóm của bạn chưa được tạo project.");
            return "redirect:/student/project";
        }
        if (!isStudentSourceProject(project)) {
            redirectAttributes.addFlashAttribute("error", "Project này không thuộc luồng học viên tự cập nhật nội dung.");
            return "redirect:/student/project";
        }
        if (!project.isStudentCanEdit()) {
            redirectAttributes.addFlashAttribute("error", "Bạn chưa được cấp quyền cập nhật nội dung project.");
            return "redirect:/student/project";
        }
        if (isProjectLockedForWork(project)) {
            redirectAttributes.addFlashAttribute("error", "Project đã hết hạn nên không thể gửi duyệt.");
            return "redirect:/student/project";
        }
        if (normalize(project.getProjectName()).isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng cập nhật tên project trước khi gửi duyệt.");
            return "redirect:/student/project";
        }
        int updated = projectRepository.submitForLecturerReview(project.getProjectId());
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Không thể gửi yêu cầu duyệt tới giảng viên.");
            return "redirect:/student/project";
        }

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
                    "Đã gửi yêu cầu duyệt. Hiện chưa có giảng viên nào được phân lớp để nhận email.");
        } else {
            redirectAttributes.addFlashAttribute("success",
                    "Đã gửi yêu cầu duyệt tới giảng viên. Email đã gửi: " + sent + "/" + uniqueEmails.size() + ".");
        }
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
            redirectAttributes.addFlashAttribute("error", "Bạn chưa có nhóm trong học kỳ hiện tại.");
            return "redirect:/student/project";
        }
        if (!isLeader(group, student)) {
            redirectAttributes.addFlashAttribute("error", "Chỉ trưởng nhóm mới được tạo sprint.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (!isProjectApproved(project)) {
            redirectAttributes.addFlashAttribute("error", "Project phải ở trạng thái đã duyệt mới tạo được sprint.");
            return "redirect:/student/project";
        }
        if (project.getStartDate() == null || project.getEndDate() == null) {
            redirectAttributes.addFlashAttribute("error", "Project chưa có mốc thời gian bắt đầu/kết thúc.");
            return "redirect:/student/project";
        }
        if (isProjectLockedForWork(project)) {
            redirectAttributes.addFlashAttribute("error", "Project đã hết hạn nên không thể tạo sprint mới.");
            return "redirect:/student/project";
        }

        refreshSprintState(project);
        int sprintId = sprintRepository.createNextSprint(
                project.getProjectId(),
                normalize(sprintName),
                toDate(project.getStartDate()),
                toDate(project.getEndDate()));
        if (sprintId == -2) {
            redirectAttributes.addFlashAttribute("error", "Đã có sprint đang mở. Hãy chờ sprint hiện tại kết thúc.");
            return "redirect:/student/project";
        }
        if (sprintId == -3) {
            redirectAttributes.addFlashAttribute("error", "Không thể tạo sprint vì vượt quá thời gian của project.");
            return "redirect:/student/project";
        }
        if (sprintId <= 0) {
            redirectAttributes.addFlashAttribute("error", "Không thể tạo sprint mới.");
            return "redirect:/student/project";
        }

        redirectAttributes.addFlashAttribute("success", "Đã tạo sprint mới thành công.");
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
            redirectAttributes.addFlashAttribute("error", "Bạn chưa có nhóm trong học kỳ hiện tại.");
            return "redirect:/student/project";
        }
        if (!isLeader(group, student)) {
            redirectAttributes.addFlashAttribute("error", "Chỉ trưởng nhóm mới được tạo task.");
            return "redirect:/student/project";
        }

        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            redirectAttributes.addFlashAttribute("error", "Nhóm của bạn chưa được tạo project.");
            return "redirect:/student/project";
        }
        if (!isProjectApproved(project)) {
            redirectAttributes.addFlashAttribute("error", "Project chỉ được tạo task sau khi đã duyệt.");
            return "redirect:/student/project";
        }
        if (isProjectNotStarted(project)) {
            redirectAttributes.addFlashAttribute("error", "Project chưa tới thời gian bắt đầu xây dựng.");
            return "redirect:/student/project";
        }
        if (isProjectLockedForWork(project)) {
            redirectAttributes.addFlashAttribute("error", "Project đã hết hạn nên không thể tạo task.");
            return "redirect:/student/project";
        }

        refreshSprintState(project);
        Sprint openSprint = sprintRepository.findOpenByProject(project.getProjectId());
        if (openSprint == null) {
            redirectAttributes.addFlashAttribute("error", "Chưa có sprint đang mở. Hãy tạo sprint trước.");
            return "redirect:/student/project";
        }
        if (sprintId != openSprint.getSprintId()) {
            redirectAttributes.addFlashAttribute("error", "Chỉ được thêm task vào sprint đang mở.");
            return "redirect:/student/project";
        }

        String normalizedTaskName = normalize(taskName);
        if (normalizedTaskName.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Tên task không được để trống.");
            return "redirect:/student/project";
        }
        if (normalizedTaskName.length() > 200) {
            redirectAttributes.addFlashAttribute("error", "Tên task tối đa 200 ký tự.");
            return "redirect:/student/project";
        }

        double points;
        try {
            points = Double.parseDouble(normalize(estimatedPoints).replace(',', '.'));
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Point ước lượng không hợp lệ.");
            return "redirect:/student/project";
        }
        if (points <= 0) {
            redirectAttributes.addFlashAttribute("error", "Point ước lượng phải lớn hơn 0.");
            return "redirect:/student/project";
        }

        if (!groupMemberRepository.isMember(group.getGroupId(), assigneeId)) {
            redirectAttributes.addFlashAttribute("error", "Người được giao task phải là thành viên trong nhóm.");
            return "redirect:/student/project";
        }
        if (!groupMemberRepository.isMember(group.getGroupId(), reviewerId)) {
            redirectAttributes.addFlashAttribute("error", "Người kiểm tra phải là thành viên trong nhóm.");
            return "redirect:/student/project";
        }
        if (assigneeId == reviewerId) {
            redirectAttributes.addFlashAttribute("error", "Người thực hiện và người kiểm tra phải là hai người khác nhau.");
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
            redirectAttributes.addFlashAttribute("error", "Không thể tạo task. Vui lòng kiểm tra dữ liệu.");
            return "redirect:/student/project";
        }

        redirectAttributes.addFlashAttribute("success",
                "Đã tạo task. Point: " + points + " tương đương " + (points * 4.0d) + " giờ.");
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
            redirectAttributes.addFlashAttribute("error", "Bạn chưa có nhóm trong học kỳ hiện tại.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            redirectAttributes.addFlashAttribute("error", "Nhóm của bạn chưa được tạo project.");
            return "redirect:/student/project";
        }
        if (!canOperateTask(project)) {
            redirectAttributes.addFlashAttribute("error", "Project hiện không cho phép thao tác task.");
            return "redirect:/student/project";
        }

        refreshSprintState(project);
        ProjectTask task = findTaskInProject(project, taskId);
        if (task == null) {
            redirectAttributes.addFlashAttribute("error", "Task không tồn tại trong project của nhóm.");
            return "redirect:/student/project";
        }
        if (task.getAssigneeId() != student.getStudentId()) {
            redirectAttributes.addFlashAttribute("error", "Chỉ người được giao mới được bắt đầu task này.");
            return "redirect:/student/project";
        }
        if (task.getStatus() == ProjectTask.STATUS_FAILED_SPRINT) {
            redirectAttributes.addFlashAttribute("error", "Task đã thất bại ở sprint cũ. Hãy được trưởng nhóm đưa vào sprint mới.");
            return "redirect:/student/project";
        }

        int updated = projectTaskRepository.markInProgress(taskId, student.getStudentId());
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Không thể chuyển task sang trạng thái đang làm.");
            return "redirect:/student/project";
        }

        redirectAttributes.addFlashAttribute("success", "Đã bắt đầu task.");
        return "redirect:/student/project";
    }

    @PostMapping("/tasks/{taskId}/submit")
    public String submitTask(@PathVariable("taskId") int taskId,
            @RequestParam(name = "submissionNote", required = false) String submissionNote,
            @RequestParam(name = "submissionUrl", required = false) String submissionUrl,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student student = getSessionStudent(session);
        if (student == null) {
            return "redirect:/acc/log";
        }
        Group group = resolveCurrentGroup(student);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "Bạn chưa có nhóm trong học kỳ hiện tại.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            redirectAttributes.addFlashAttribute("error", "Nhóm của bạn chưa được tạo project.");
            return "redirect:/student/project";
        }
        if (!canOperateTask(project)) {
            redirectAttributes.addFlashAttribute("error", "Project hiện không cho phép thao tác task.");
            return "redirect:/student/project";
        }

        refreshSprintState(project);
        ProjectTask task = findTaskInProject(project, taskId);
        if (task == null) {
            redirectAttributes.addFlashAttribute("error", "Task không tồn tại trong project của nhóm.");
            return "redirect:/student/project";
        }
        if (task.getAssigneeId() != student.getStudentId()) {
            redirectAttributes.addFlashAttribute("error", "Chỉ người được giao mới được nộp task này.");
            return "redirect:/student/project";
        }

        String note = normalize(submissionNote);
        String url = normalize(submissionUrl);
        if (note.isEmpty() && url.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Nội dung nộp task cần có mô tả hoặc link minh chứng.");
            return "redirect:/student/project";
        }

        int updated = projectTaskRepository.submitTask(taskId, student.getStudentId(), note, url);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Không thể nộp task. Hãy chắc chắn task đang ở trạng thái đang làm.");
            return "redirect:/student/project";
        }

        redirectAttributes.addFlashAttribute("success", "Đã nộp task để reviewer/trưởng nhóm xét duyệt.");
        return "redirect:/student/project";
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
            redirectAttributes.addFlashAttribute("error", "Bạn chưa có nhóm trong học kỳ hiện tại.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            redirectAttributes.addFlashAttribute("error", "Nhóm của bạn chưa được tạo project.");
            return "redirect:/student/project";
        }
        if (!canOperateTask(project)) {
            redirectAttributes.addFlashAttribute("error", "Project hiện không cho phép thao tác task.");
            return "redirect:/student/project";
        }

        refreshSprintState(project);
        ProjectTask task = findTaskInProject(project, taskId);
        if (task == null) {
            redirectAttributes.addFlashAttribute("error", "Task không tồn tại trong project của nhóm.");
            return "redirect:/student/project";
        }
        if (!canReviewTask(task, group, student)) {
            redirectAttributes.addFlashAttribute("error", "Bạn không có quyền duyệt task này.");
            return "redirect:/student/project";
        }

        String normalizedAction = normalize(action).toLowerCase();
        String comment = normalize(reviewComment);

        if ("approve".equals(normalizedAction)) {
            int updated = projectTaskRepository.approveTask(taskId, comment);
            if (updated <= 0) {
                redirectAttributes.addFlashAttribute("error", "Không thể duyệt task. Task cần ở trạng thái chờ duyệt.");
                return "redirect:/student/project";
            }
            redirectAttributes.addFlashAttribute("success", "Đã duyệt hoàn thành task.");
            return "redirect:/student/project";
        }

        if ("reject".equals(normalizedAction)) {
            if (comment.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Vui lòng nhập lý do khi trả lại task.");
                return "redirect:/student/project";
            }
            int updated = projectTaskRepository.rejectTask(taskId, comment);
            if (updated <= 0) {
                redirectAttributes.addFlashAttribute("error", "Không thể trả lại task. Task cần ở trạng thái chờ duyệt.");
                return "redirect:/student/project";
            }
            redirectAttributes.addFlashAttribute("success", "Đã trả lại task để học viên chỉnh sửa và nộp lại.");
            return "redirect:/student/project";
        }

        redirectAttributes.addFlashAttribute("error", "Hành động duyệt task không hợp lệ.");
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
            redirectAttributes.addFlashAttribute("error", "Bạn chưa có nhóm trong học kỳ hiện tại.");
            return "redirect:/student/project";
        }
        if (!isLeader(group, student)) {
            redirectAttributes.addFlashAttribute("error", "Chỉ trưởng nhóm mới được chuyển task thất bại.");
            return "redirect:/student/project";
        }

        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (!canOperateTask(project)) {
            redirectAttributes.addFlashAttribute("error", "Project hiện không cho phép thao tác task.");
            return "redirect:/student/project";
        }

        refreshSprintState(project);
        Sprint openSprint = sprintRepository.findOpenByProject(project.getProjectId());
        if (openSprint == null) {
            redirectAttributes.addFlashAttribute("error", "Không có sprint đang mở để nhận task thất bại.");
            return "redirect:/student/project";
        }
        if (openSprint.getSprintId() != sprintId) {
            redirectAttributes.addFlashAttribute("error", "Task thất bại chỉ được chuyển vào sprint đang mở.");
            return "redirect:/student/project";
        }

        ProjectTask task = findTaskInProject(project, taskId);
        if (task == null || task.getStatus() != ProjectTask.STATUS_FAILED_SPRINT) {
            redirectAttributes.addFlashAttribute("error", "Task không hợp lệ hoặc chưa ở trạng thái thất bại sprint.");
            return "redirect:/student/project";
        }
        if (!groupMemberRepository.isMember(group.getGroupId(), assigneeId)) {
            redirectAttributes.addFlashAttribute("error", "Người thực hiện phải là thành viên trong nhóm.");
            return "redirect:/student/project";
        }
        if (!groupMemberRepository.isMember(group.getGroupId(), reviewerId)) {
            redirectAttributes.addFlashAttribute("error", "Người kiểm tra phải là thành viên trong nhóm.");
            return "redirect:/student/project";
        }
        if (assigneeId == reviewerId) {
            redirectAttributes.addFlashAttribute("error", "Người thực hiện và người kiểm tra phải là hai người khác nhau.");
            return "redirect:/student/project";
        }

        int updated = projectTaskRepository.replanFailedTask(taskId, sprintId, assigneeId, reviewerId);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Không thể chuyển task thất bại vào sprint mới.");
            return "redirect:/student/project";
        }

        redirectAttributes.addFlashAttribute("success", "Đã chuyển task thất bại vào sprint mới và gán lại thành viên.");
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
            redirectAttributes.addFlashAttribute("error", "Bạn chưa có nhóm trong học kỳ hiện tại.");
            return "redirect:/student/project";
        }
        if (!isLeader(group, student)) {
            redirectAttributes.addFlashAttribute("error", "Chỉ trưởng nhóm mới được chốt link project.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (!isProjectApproved(project)) {
            redirectAttributes.addFlashAttribute("error", "Project chưa duyệt nên chưa thể chốt link.");
            return "redirect:/student/project";
        }
        if (!isWithinFinalLinkGrace(project)) {
            redirectAttributes.addFlashAttribute("error", "Chỉ được chốt link trong 1 ngày sau khi project kết thúc.");
            return "redirect:/student/project";
        }

        String sourceUrl = normalize(sourceCodeUrl);
        String docUrl = normalize(documentUrl);
        if (sourceUrl.isEmpty() || docUrl.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Cần nhập cả link source code và link tài liệu.");
            return "redirect:/student/project";
        }

        int updated = projectRepository.updateFinalLinks(project.getProjectId(), sourceUrl, docUrl);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Không thể cập nhật link bàn giao cuối project.");
            return "redirect:/student/project";
        }
        redirectAttributes.addFlashAttribute("success", "Đã cập nhật link source code và tài liệu bàn giao.");
        return "redirect:/student/project";
    }
}

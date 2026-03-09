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
import com.example.pms.util.RoleDisplayUtil;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
        Object role = session.getAttribute("role");
        model.addAttribute("studentName", fullName != null ? fullName : (student != null ? student.getFullName() : "H\u1ecdc vi\u00ean"));
        model.addAttribute("userRole", RoleDisplayUtil.toDisplayRole(role != null ? role : "Student"));
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

    private boolean validateLeaderProject(Group group,
            Student student,
            Project project,
            RedirectAttributes redirectAttributes) {
        if (!isLeader(group, student)) {
            redirectAttributes.addFlashAttribute("error", "Chá»‰ trÆ°á»Ÿng nhÃ³m má»›i Ä‘Æ°á»£c thá»±c hiá»‡n thao tÃ¡c nÃ y.");
            return false;
        }
        if (project == null) {
            redirectAttributes.addFlashAttribute("error", "NhÃ³m cá»§a báº¡n chÆ°a Ä‘Æ°á»£c táº¡o project.");
            return false;
        }
        return true;
    }

    private boolean validateNoOpenChangeRequest(Project project, RedirectAttributes redirectAttributes) {
        if (hasOpenChangeRequest(project)) {
            redirectAttributes.addFlashAttribute("error",
                    "Project Ä‘ang cÃ³ yÃªu cáº§u Ä‘á»•i Ä‘á» tÃ i chá» xá»­ lÃ½ nÃªn táº¡m khÃ³a thao tÃ¡c káº¿ hoáº¡ch vÃ  cÃ´ng viá»‡c.");
            return false;
        }
        return true;
    }

    private Double parseEstimatedPoints(String estimatedPoints, RedirectAttributes redirectAttributes) {
        try {
            double points = Double.parseDouble(normalize(estimatedPoints).replace(',', '.'));
            if (points <= 0) {
                redirectAttributes.addFlashAttribute("error", "Point Æ°á»›c lÆ°á»£ng pháº£i lá»›n hÆ¡n 0.");
                return null;
            }
            return points;
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Point Æ°á»›c lÆ°á»£ng khÃ´ng há»£p lá»‡.");
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
        model.addAttribute("tasks", tasks);
        model.addAttribute("failedTasks", failedTasks);
        model.addAttribute("lecturerComments", projectCommentRepository.findByProject(project.getProjectId()));
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
            redirectAttributes.addFlashAttribute("error", "Báº¡n chÆ°a cÃ³ nhÃ³m trong há»c ká»³ hiá»‡n táº¡i.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            redirectAttributes.addFlashAttribute("error", "NhÃ³m cá»§a báº¡n chÆ°a Ä‘Æ°á»£c táº¡o project.");
            return "redirect:/student/project";
        }
        if (!validateNoOpenChangeRequest(project, redirectAttributes)) {
            return "redirect:/student/project";
        }
        if (!isStudentSourceProject(project)) {
            redirectAttributes.addFlashAttribute("error", "Project nÃ y khÃ´ng thuá»™c luá»“ng há»c viÃªn tá»± cáº­p nháº­t ná»™i dung.");
            return "redirect:/student/project";
        }
        if (project.isStudentCanEdit()) {
            redirectAttributes.addFlashAttribute("success", "Báº¡n Ä‘Ã£ cÃ³ quyá»n cáº­p nháº­t ná»™i dung project.");
            return "redirect:/student/project";
        }
        if (projectEditRequestRepository.existsPendingByProjectAndStudent(project.getProjectId(), student.getStudentId())) {
            redirectAttributes.addFlashAttribute("error", "Báº¡n Ä‘Ã£ gá»­i yÃªu cáº§u cáº¥p quyá»n vÃ  Ä‘ang chá» nhÃ¢n viÃªn xá»­ lÃ½.");
            return "redirect:/student/project";
        }
        String note = normalize(requestNote);
        if (note.length() > 2000) {
            redirectAttributes.addFlashAttribute("error", "Ghi chÃº yÃªu cáº§u quÃ¡ dÃ i.");
            return "redirect:/student/project";
        }
        int requestId = projectEditRequestRepository.createRequest(project.getProjectId(), student.getStudentId(), note);
        if (requestId <= 0) {
            redirectAttributes.addFlashAttribute("error", "KhÃ´ng thá»ƒ gá»­i yÃªu cáº§u cáº¥p quyá»n. Vui lÃ²ng thá»­ láº¡i.");
            return "redirect:/student/project";
        }
        redirectAttributes.addFlashAttribute("success", "ÄÃ£ gá»­i yÃªu cáº§u cáº¥p quyá»n cáº­p nháº­t ná»™i dung project.");
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
            redirectAttributes.addFlashAttribute("error", "Báº¡n chÆ°a cÃ³ nhÃ³m trong há»c ká»³ hiá»‡n táº¡i.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            redirectAttributes.addFlashAttribute("error", "NhÃ³m cá»§a báº¡n chÆ°a Ä‘Æ°á»£c táº¡o project.");
            return "redirect:/student/project";
        }
        if (!validateNoOpenChangeRequest(project, redirectAttributes)) {
            return "redirect:/student/project";
        }
        if (!isStudentSourceProject(project)) {
            redirectAttributes.addFlashAttribute("error", "Project nÃ y khÃ´ng thuá»™c luá»“ng há»c viÃªn tá»± cáº­p nháº­t ná»™i dung.");
            return "redirect:/student/project";
        }
        if (!project.isStudentCanEdit()) {
            redirectAttributes.addFlashAttribute("error", "Báº¡n chÆ°a Ä‘Æ°á»£c cáº¥p quyá»n cáº­p nháº­t ná»™i dung project.");
            return "redirect:/student/project";
        }
        if (isProjectLockedForWork(project)) {
            redirectAttributes.addFlashAttribute("error", "Project Ä‘Ã£ háº¿t háº¡n nÃªn khÃ´ng thá»ƒ cáº­p nháº­t thÃªm ná»™i dung.");
            return "redirect:/student/project";
        }
        String normalizedName = normalize(projectName);
        if (normalizedName.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "TÃªn project khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng.");
            return "redirect:/student/project";
        }
        if (normalizedName.length() > 200) {
            redirectAttributes.addFlashAttribute("error", "TÃªn project tá»‘i Ä‘a 200 kÃ½ tá»±.");
            return "redirect:/student/project";
        }
        int updated = projectRepository.updateStudentContent(
                project.getProjectId(),
                normalizedName,
                normalize(description),
                normalize(sourceCodeUrl),
                normalize(documentUrl));
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "KhÃ´ng thá»ƒ cáº­p nháº­t ná»™i dung project.");
            return "redirect:/student/project";
        }
        redirectAttributes.addFlashAttribute("success", "ÄÃ£ cáº­p nháº­t ná»™i dung project.");
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
            redirectAttributes.addFlashAttribute("error", "Báº¡n chÆ°a cÃ³ nhÃ³m trong há»c ká»³ hiá»‡n táº¡i.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            redirectAttributes.addFlashAttribute("error", "NhÃ³m cá»§a báº¡n chÆ°a Ä‘Æ°á»£c táº¡o project.");
            return "redirect:/student/project";
        }
        if (!validateNoOpenChangeRequest(project, redirectAttributes)) {
            return "redirect:/student/project";
        }
        if (!isStudentSourceProject(project)) {
            redirectAttributes.addFlashAttribute("error", "Project nÃ y khÃ´ng thuá»™c luá»“ng há»c viÃªn tá»± cáº­p nháº­t ná»™i dung.");
            return "redirect:/student/project";
        }
        if (!project.isStudentCanEdit()) {
            redirectAttributes.addFlashAttribute("error", "Báº¡n chÆ°a Ä‘Æ°á»£c cáº¥p quyá»n cáº­p nháº­t ná»™i dung project.");
            return "redirect:/student/project";
        }
        if (isProjectLockedForWork(project)) {
            redirectAttributes.addFlashAttribute("error", "Project Ä‘Ã£ háº¿t háº¡n nÃªn khÃ´ng thá»ƒ gá»­i duyá»‡t.");
            return "redirect:/student/project";
        }
        if (normalize(project.getProjectName()).isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Vui lÃ²ng cáº­p nháº­t tÃªn project trÆ°á»›c khi gá»­i duyá»‡t.");
            return "redirect:/student/project";
        }
        int updated = projectRepository.submitForLecturerReview(project.getProjectId());
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "KhÃ´ng thá»ƒ gá»­i yÃªu cáº§u duyá»‡t tá»›i giáº£ng viÃªn.");
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
                    "ÄÃ£ gá»­i yÃªu cáº§u duyá»‡t. Hiá»‡n chÆ°a cÃ³ giáº£ng viÃªn nÃ o Ä‘Æ°á»£c phÃ¢n lá»›p Ä‘á»ƒ nháº­n email.");
        } else {
            redirectAttributes.addFlashAttribute("success",
                    "ÄÃ£ gá»­i yÃªu cáº§u duyá»‡t tá»›i giáº£ng viÃªn. Email Ä‘Ã£ gá»­i: " + sent + "/" + uniqueEmails.size() + ".");
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
            redirectAttributes.addFlashAttribute("error", "Bạn chưa có nhóm trong học kỳ hiện tại.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (!validateLeaderProject(group, student, project, redirectAttributes)) {
            return "redirect:/student/project";
        }
        if (!isProjectApproved(project)) {
            redirectAttributes.addFlashAttribute("error", "Chỉ project đã duyệt mới được gửi yêu cầu đổi đề tài.");
            return "redirect:/student/project";
        }
        if (isProjectLockedForWork(project)) {
            redirectAttributes.addFlashAttribute("error", "Project đã hết hạn nên không thể gửi yêu cầu đổi đề tài.");
            return "redirect:/student/project";
        }
        if (hasOpenChangeRequest(project)) {
            redirectAttributes.addFlashAttribute("error", "Project đã có yêu cầu đổi đề tài đang chờ xử lý.");
            return "redirect:/student/project";
        }
        if (projectTaskRepository.hasDoneTasksByProject(project.getProjectId())) {
            redirectAttributes.addFlashAttribute("error", "Project đã có công việc hoàn thành nên không thể đổi đề tài nữa.");
            return "redirect:/student/project";
        }

        String normalizedName = normalize(proposedProjectName);
        String normalizedDescription = normalize(proposedDescription);
        String normalizedReason = normalize(changeReason);
        if (normalizedName.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Tên project đề xuất mới không được để trống.");
            return "redirect:/student/project";
        }
        if (normalizedName.length() > 200) {
            redirectAttributes.addFlashAttribute("error", "Tên project đề xuất tối đa 200 ký tự.");
            return "redirect:/student/project";
        }
        if (normalizedReason.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng nhập lý do đổi project.");
            return "redirect:/student/project";
        }
        if (normalizedReason.length() > 2000) {
            redirectAttributes.addFlashAttribute("error", "Lý do đổi project quá dài.");
            return "redirect:/student/project";
        }

        int requestId = projectChangeRequestRepository.createRequest(
                project.getProjectId(),
                student.getStudentId(),
                normalizedName,
                normalizedDescription,
                normalizedReason);
        if (requestId <= 0) {
            redirectAttributes.addFlashAttribute("error", "Không thể gửi yêu cầu đổi project. Vui lòng thử lại.");
            return "redirect:/student/project";
        }

        redirectAttributes.addFlashAttribute("success", "Đã gửi yêu cầu đổi project tới nhân viên đào tạo.");
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
            redirectAttributes.addFlashAttribute("error", "Báº¡n chÆ°a cÃ³ nhÃ³m trong há»c ká»³ hiá»‡n táº¡i.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (!validateLeaderProject(group, student, project, redirectAttributes)) {
            return "redirect:/student/project";
        }
        if (!isProjectApproved(project)) {
            redirectAttributes.addFlashAttribute("error", "Project pháº£i á»Ÿ tráº¡ng thÃ¡i Ä‘Ã£ duyá»‡t má»›i táº¡o Ä‘Æ°á»£c Ä‘á»£t lÃ m viá»‡c.");
            return "redirect:/student/project";
        }
        if (project.getStartDate() == null || project.getEndDate() == null) {
            redirectAttributes.addFlashAttribute("error", "Project chÆ°a cÃ³ má»‘c thá»i gian báº¯t Ä‘áº§u/káº¿t thÃºc.");
            return "redirect:/student/project";
        }
        if (isProjectLockedForWork(project)) {
            redirectAttributes.addFlashAttribute("error", "Project Ä‘Ã£ háº¿t háº¡n nÃªn khÃ´ng thá»ƒ táº¡o Ä‘á»£t lÃ m viá»‡c má»›i.");
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
            redirectAttributes.addFlashAttribute("error", "ÄÃ£ cÃ³ Ä‘á»£t lÃ m viá»‡c Ä‘ang má»Ÿ. HÃ£y chá» Ä‘á»£t hiá»‡n táº¡i káº¿t thÃºc.");
            return "redirect:/student/project";
        }
        if (sprintId == -3) {
            redirectAttributes.addFlashAttribute("error", "KhÃ´ng thá»ƒ táº¡o Ä‘á»£t lÃ m viá»‡c vÃ¬ vÆ°á»£t quÃ¡ thá»i gian cá»§a project.");
            return "redirect:/student/project";
        }
        if (sprintId <= 0) {
            redirectAttributes.addFlashAttribute("error", "KhÃ´ng thá»ƒ táº¡o Ä‘á»£t lÃ m viá»‡c má»›i.");
            return "redirect:/student/project";
        }

        redirectAttributes.addFlashAttribute("success", "ÄÃ£ táº¡o Ä‘á»£t lÃ m viá»‡c má»›i thÃ nh cÃ´ng.");
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
            redirectAttributes.addFlashAttribute("error", "Bạn chưa có nhóm trong học kỳ hiện tại.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (!validateLeaderProject(group, student, project, redirectAttributes)) {
            return "redirect:/student/project";
        }
        if (!isProjectApproved(project) || isProjectLockedForWork(project)) {
            redirectAttributes.addFlashAttribute("error", "Project hiện không cho phép cập nhật đợt làm việc.");
            return "redirect:/student/project";
        }
        if (!validateNoOpenChangeRequest(project, redirectAttributes)) {
            return "redirect:/student/project";
        }

        Sprint sprint = findSprintInProject(project, sprintId);
        if (sprint == null) {
            redirectAttributes.addFlashAttribute("error", "Đợt làm việc không tồn tại trong project của nhóm.");
            return "redirect:/student/project";
        }
        if (sprint.isClosed() || sprint.isCancelled()) {
            redirectAttributes.addFlashAttribute("error", "Chỉ đợt làm việc đang mở mới được sửa tên.");
            return "redirect:/student/project";
        }

        String normalizedName = normalize(sprintName);
        if (normalizedName.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Tên đợt làm việc không được để trống.");
            return "redirect:/student/project";
        }
        if (normalizedName.length() > 50) {
            redirectAttributes.addFlashAttribute("error", "Tên đợt làm việc tối đa 50 ký tự.");
            return "redirect:/student/project";
        }

        int updated = sprintRepository.updateSprintName(sprintId, project.getProjectId(), normalizedName);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Không thể cập nhật tên đợt làm việc.");
            return "redirect:/student/project";
        }
        redirectAttributes.addFlashAttribute("success", "Đã cập nhật tên đợt làm việc.");
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
            redirectAttributes.addFlashAttribute("error", "Bạn chưa có nhóm trong học kỳ hiện tại.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (!validateLeaderProject(group, student, project, redirectAttributes)) {
            return "redirect:/student/project";
        }
        if (!isProjectApproved(project) || isProjectLockedForWork(project)) {
            redirectAttributes.addFlashAttribute("error", "Project hiện không cho phép xóa đợt làm việc.");
            return "redirect:/student/project";
        }
        if (!validateNoOpenChangeRequest(project, redirectAttributes)) {
            return "redirect:/student/project";
        }

        Sprint sprint = findSprintInProject(project, sprintId);
        if (sprint == null) {
            redirectAttributes.addFlashAttribute("error", "Đợt làm việc không tồn tại trong project của nhóm.");
            return "redirect:/student/project";
        }
        if (sprint.isClosed() || sprint.isCancelled()) {
            redirectAttributes.addFlashAttribute("error", "Chỉ đợt làm việc đang mở mới được xóa.");
            return "redirect:/student/project";
        }
        if (sprintRepository.hasAnyTasks(sprintId)) {
            redirectAttributes.addFlashAttribute("error", "Đợt làm việc đã có công việc nên không thể xóa cứng. Hãy dùng hủy đợt làm việc nếu cần.");
            return "redirect:/student/project";
        }

        int deleted = sprintRepository.deleteEmptyOpenSprint(sprintId, project.getProjectId());
        if (deleted <= 0) {
            redirectAttributes.addFlashAttribute("error", "Không thể xóa đợt làm việc.");
            return "redirect:/student/project";
        }
        redirectAttributes.addFlashAttribute("success", "Đã xóa đợt làm việc trống.");
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
            redirectAttributes.addFlashAttribute("error", "Bạn chưa có nhóm trong học kỳ hiện tại.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (!validateLeaderProject(group, student, project, redirectAttributes)) {
            return "redirect:/student/project";
        }
        if (!isProjectApproved(project) || isProjectLockedForWork(project)) {
            redirectAttributes.addFlashAttribute("error", "Project hiện không cho phép hủy đợt làm việc.");
            return "redirect:/student/project";
        }
        if (!validateNoOpenChangeRequest(project, redirectAttributes)) {
            return "redirect:/student/project";
        }

        Sprint sprint = findSprintInProject(project, sprintId);
        if (sprint == null) {
            redirectAttributes.addFlashAttribute("error", "Đợt làm việc không tồn tại trong project của nhóm.");
            return "redirect:/student/project";
        }
        if (sprint.isClosed() || sprint.isCancelled()) {
            redirectAttributes.addFlashAttribute("error", "Chỉ đợt làm việc đang mở mới được hủy.");
            return "redirect:/student/project";
        }
        if (projectTaskRepository.hasDoneTasksInSprint(sprintId)) {
            redirectAttributes.addFlashAttribute("error", "Đợt làm việc đã có công việc hoàn thành nên không thể hủy.");
            return "redirect:/student/project";
        }

        String normalizedReason = normalize(cancelReason);
        if (normalizedReason.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng nhập lý do hủy đợt làm việc.");
            return "redirect:/student/project";
        }
        if (normalizedReason.length() > 2000) {
            redirectAttributes.addFlashAttribute("error", "Lý do hủy đợt làm việc quá dài.");
            return "redirect:/student/project";
        }

        int updated = sprintRepository.cancelSprint(sprintId, project.getProjectId(), student.getStudentId(), normalizedReason);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Không thể hủy đợt làm việc.");
            return "redirect:/student/project";
        }
        projectTaskRepository.cancelTasksBySprint(sprintId, student.getStudentId(), "Công việc bị hủy do đợt làm việc đã bị hủy.");

        redirectAttributes.addFlashAttribute("success", "Đã hủy đợt làm việc và toàn bộ công việc chưa hoàn thành trong đợt.");
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
            redirectAttributes.addFlashAttribute("error", "Báº¡n chÆ°a cÃ³ nhÃ³m trong há»c ká»³ hiá»‡n táº¡i.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (!validateLeaderProject(group, student, project, redirectAttributes)) {
            return "redirect:/student/project";
        }
        if (!isProjectApproved(project)) {
            redirectAttributes.addFlashAttribute("error", "Project chá»‰ Ä‘Æ°á»£c táº¡o cÃ´ng viá»‡c sau khi Ä‘Ã£ duyá»‡t.");
            return "redirect:/student/project";
        }
        if (isProjectNotStarted(project)) {
            redirectAttributes.addFlashAttribute("error", "Project chÆ°a tá»›i thá»i gian báº¯t Ä‘áº§u xÃ¢y dá»±ng.");
            return "redirect:/student/project";
        }
        if (isProjectLockedForWork(project)) {
            redirectAttributes.addFlashAttribute("error", "Project Ä‘Ã£ háº¿t háº¡n nÃªn khÃ´ng thá»ƒ táº¡o cÃ´ng viá»‡c.");
            return "redirect:/student/project";
        }
        if (!validateNoOpenChangeRequest(project, redirectAttributes)) {
            return "redirect:/student/project";
        }

        refreshSprintState(project);
        Sprint openSprint = sprintRepository.findOpenByProject(project.getProjectId());
        if (openSprint == null) {
            redirectAttributes.addFlashAttribute("error", "ChÆ°a cÃ³ Ä‘á»£t lÃ m viá»‡c Ä‘ang má»Ÿ. HÃ£y táº¡o Ä‘á»£t lÃ m viá»‡c trÆ°á»›c.");
            return "redirect:/student/project";
        }
        if (sprintId != openSprint.getSprintId()) {
            redirectAttributes.addFlashAttribute("error", "Chá»‰ Ä‘Æ°á»£c thÃªm cÃ´ng viá»‡c vÃ o Ä‘á»£t lÃ m viá»‡c Ä‘ang má»Ÿ.");
            return "redirect:/student/project";
        }

        String normalizedTaskName = normalize(taskName);
        if (normalizedTaskName.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "TÃªn cÃ´ng viá»‡c khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng.");
            return "redirect:/student/project";
        }
        if (normalizedTaskName.length() > 200) {
            redirectAttributes.addFlashAttribute("error", "TÃªn cÃ´ng viá»‡c tá»‘i Ä‘a 200 kÃ½ tá»±.");
            return "redirect:/student/project";
        }

        Double points = parseEstimatedPoints(estimatedPoints, redirectAttributes);
        if (points == null) {
            return "redirect:/student/project";
        }

        if (!groupMemberRepository.isMember(group.getGroupId(), assigneeId)) {
            redirectAttributes.addFlashAttribute("error", "NgÆ°á»i Ä‘Æ°á»£c giao cÃ´ng viá»‡c pháº£i lÃ  thÃ nh viÃªn trong nhÃ³m.");
            return "redirect:/student/project";
        }
        if (!groupMemberRepository.isMember(group.getGroupId(), reviewerId)) {
            redirectAttributes.addFlashAttribute("error", "NgÆ°á»i kiá»ƒm tra pháº£i lÃ  thÃ nh viÃªn trong nhÃ³m.");
            return "redirect:/student/project";
        }
        if (assigneeId == reviewerId) {
            redirectAttributes.addFlashAttribute("error", "NgÆ°á»i thá»±c hiá»‡n vÃ  ngÆ°á»i kiá»ƒm tra pháº£i lÃ  hai ngÆ°á»i khÃ¡c nhau.");
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
            redirectAttributes.addFlashAttribute("error", "KhÃ´ng thá»ƒ táº¡o cÃ´ng viá»‡c. Vui lÃ²ng kiá»ƒm tra dá»¯ liá»‡u.");
            return "redirect:/student/project";
        }

        redirectAttributes.addFlashAttribute("success",
                "ÄÃ£ táº¡o cÃ´ng viá»‡c. Point: " + points + " tÆ°Æ¡ng Ä‘Æ°Æ¡ng " + (points * 4.0d) + " giá».");
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
            redirectAttributes.addFlashAttribute("error", "Bạn chưa có nhóm trong học kỳ hiện tại.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (!validateLeaderProject(group, student, project, redirectAttributes)) {
            return "redirect:/student/project";
        }
        if (!isProjectApproved(project) || isProjectLockedForWork(project)) {
            redirectAttributes.addFlashAttribute("error", "Project hiện không cho phép cập nhật công việc.");
            return "redirect:/student/project";
        }
        if (!validateNoOpenChangeRequest(project, redirectAttributes)) {
            return "redirect:/student/project";
        }

        ProjectTask task = findTaskInProject(project, taskId);
        if (task == null) {
            redirectAttributes.addFlashAttribute("error", "Công việc không tồn tại trong project của nhóm.");
            return "redirect:/student/project";
        }
        if (task.getStatus() != ProjectTask.STATUS_TODO && task.getStatus() != ProjectTask.STATUS_REJECTED) {
            redirectAttributes.addFlashAttribute("error", "Chỉ công việc ở trạng thái chờ xử lý hoặc trả lại mới được chỉnh sửa.");
            return "redirect:/student/project";
        }

        String normalizedTaskName = normalize(taskName);
        if (normalizedTaskName.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Tên công việc không được để trống.");
            return "redirect:/student/project";
        }
        if (normalizedTaskName.length() > 200) {
            redirectAttributes.addFlashAttribute("error", "Tên công việc tối đa 200 ký tự.");
            return "redirect:/student/project";
        }
        Double points = parseEstimatedPoints(estimatedPoints, redirectAttributes);
        if (points == null) {
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

        int updated = projectTaskRepository.updateTaskDetails(
                taskId,
                normalizedTaskName,
                normalize(description),
                normalize(taskImage),
                points,
                assigneeId,
                reviewerId);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Không thể cập nhật công việc.");
            return "redirect:/student/project";
        }
        redirectAttributes.addFlashAttribute("success", "Đã cập nhật công việc.");
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
            redirectAttributes.addFlashAttribute("error", "Bạn chưa có nhóm trong học kỳ hiện tại.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (!validateLeaderProject(group, student, project, redirectAttributes)) {
            return "redirect:/student/project";
        }
        if (!isProjectApproved(project) || isProjectLockedForWork(project)) {
            redirectAttributes.addFlashAttribute("error", "Project hiện không cho phép xóa công việc.");
            return "redirect:/student/project";
        }
        if (!validateNoOpenChangeRequest(project, redirectAttributes)) {
            return "redirect:/student/project";
        }

        ProjectTask task = findTaskInProject(project, taskId);
        if (task == null) {
            redirectAttributes.addFlashAttribute("error", "Công việc không tồn tại trong project của nhóm.");
            return "redirect:/student/project";
        }
        if (task.getStatus() != ProjectTask.STATUS_TODO) {
            redirectAttributes.addFlashAttribute("error", "Chỉ công việc còn nguyên ở trạng thái chờ xử lý mới được xóa cứng.");
            return "redirect:/student/project";
        }

        int deleted = projectTaskRepository.deleteTaskIfPristine(taskId);
        if (deleted <= 0) {
            redirectAttributes.addFlashAttribute("error", "Không thể xóa công việc. Nếu công việc đã phát sinh lịch sử, hãy dùng hủy công việc.");
            return "redirect:/student/project";
        }
        redirectAttributes.addFlashAttribute("success", "Đã xóa công việc còn nguyên.");
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
            redirectAttributes.addFlashAttribute("error", "Bạn chưa có nhóm trong học kỳ hiện tại.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (!validateLeaderProject(group, student, project, redirectAttributes)) {
            return "redirect:/student/project";
        }
        if (!isProjectApproved(project) || isProjectLockedForWork(project)) {
            redirectAttributes.addFlashAttribute("error", "Project hiện không cho phép hủy công việc.");
            return "redirect:/student/project";
        }
        if (!validateNoOpenChangeRequest(project, redirectAttributes)) {
            return "redirect:/student/project";
        }

        ProjectTask task = findTaskInProject(project, taskId);
        if (task == null) {
            redirectAttributes.addFlashAttribute("error", "Công việc không tồn tại trong project của nhóm.");
            return "redirect:/student/project";
        }
        if (task.getStatus() == ProjectTask.STATUS_DONE) {
            redirectAttributes.addFlashAttribute("error", "Công việc đã hoàn thành nên không thể hủy.");
            return "redirect:/student/project";
        }
        if (task.getStatus() == ProjectTask.STATUS_CANCELLED) {
            redirectAttributes.addFlashAttribute("error", "Công việc này đã bị hủy trước đó.");
            return "redirect:/student/project";
        }

        String normalizedReason = normalize(cancelReason);
        if (normalizedReason.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng nhập lý do hủy công việc.");
            return "redirect:/student/project";
        }
        if (normalizedReason.length() > 2000) {
            redirectAttributes.addFlashAttribute("error", "Lý do hủy công việc quá dài.");
            return "redirect:/student/project";
        }

        int updated = projectTaskRepository.cancelTask(taskId, student.getStudentId(), normalizedReason);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Không thể hủy công việc.");
            return "redirect:/student/project";
        }
        redirectAttributes.addFlashAttribute("success", "Đã hủy công việc.");
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
            redirectAttributes.addFlashAttribute("error", "Báº¡n chÆ°a cÃ³ nhÃ³m trong há»c ká»³ hiá»‡n táº¡i.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            redirectAttributes.addFlashAttribute("error", "NhÃ³m cá»§a báº¡n chÆ°a Ä‘Æ°á»£c táº¡o project.");
            return "redirect:/student/project";
        }
        boolean projectChangeOpen = hasOpenChangeRequest(project);
        if (!canOperateTask(project, projectChangeOpen)) {
            redirectAttributes.addFlashAttribute("error",
                    projectChangeOpen
                            ? "Project đang có yêu cầu đổi đề tài chờ xử lý nên tạm khóa thao tác công việc."
                            : "Project hiá»‡n khÃ´ng cho phÃ©p thao tÃ¡c cÃ´ng viá»‡c.");
            return "redirect:/student/project";
        }

        refreshSprintState(project);
        ProjectTask task = findTaskInProject(project, taskId);
        if (task == null) {
            redirectAttributes.addFlashAttribute("error", "CÃ´ng viá»‡c khÃ´ng tá»“n táº¡i trong project cá»§a nhÃ³m.");
            return "redirect:/student/project";
        }
        if (task.getAssigneeId() != student.getStudentId()) {
            redirectAttributes.addFlashAttribute("error", "Chá»‰ ngÆ°á»i Ä‘Æ°á»£c giao má»›i Ä‘Æ°á»£c báº¯t Ä‘áº§u cÃ´ng viá»‡c nÃ y.");
            return "redirect:/student/project";
        }
        if (task.getStatus() == ProjectTask.STATUS_FAILED_SPRINT) {
            redirectAttributes.addFlashAttribute("error", "CÃ´ng viá»‡c Ä‘Ã£ tháº¥t báº¡i á»Ÿ Ä‘á»£t lÃ m viá»‡c cÅ©. HÃ£y Ä‘á»ƒ trÆ°á»Ÿng nhÃ³m Ä‘Æ°a vÃ o Ä‘á»£t má»›i.");
            return "redirect:/student/project";
        }
        if (task.getStatus() == ProjectTask.STATUS_CANCELLED) {
            redirectAttributes.addFlashAttribute("error", "Công việc này đã bị hủy nên không thể bắt đầu lại.");
            return "redirect:/student/project";
        }

        int updated = projectTaskRepository.markInProgress(taskId, student.getStudentId());
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "KhÃ´ng thá»ƒ chuyá»ƒn cÃ´ng viá»‡c sang tráº¡ng thÃ¡i Ä‘ang lÃ m.");
            return "redirect:/student/project";
        }

        redirectAttributes.addFlashAttribute("success", "ÄÃ£ báº¯t Ä‘áº§u cÃ´ng viá»‡c.");
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
            redirectAttributes.addFlashAttribute("error", "Báº¡n chÆ°a cÃ³ nhÃ³m trong há»c ká»³ hiá»‡n táº¡i.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            redirectAttributes.addFlashAttribute("error", "NhÃ³m cá»§a báº¡n chÆ°a Ä‘Æ°á»£c táº¡o project.");
            return "redirect:/student/project";
        }
        boolean projectChangeOpen = hasOpenChangeRequest(project);
        if (!canOperateTask(project, projectChangeOpen)) {
            redirectAttributes.addFlashAttribute("error",
                    projectChangeOpen
                            ? "Project đang có yêu cầu đổi đề tài chờ xử lý nên tạm khóa thao tác công việc."
                            : "Project hiá»‡n khÃ´ng cho phÃ©p thao tÃ¡c cÃ´ng viá»‡c.");
            return "redirect:/student/project";
        }

        refreshSprintState(project);
        ProjectTask task = findTaskInProject(project, taskId);
        if (task == null) {
            redirectAttributes.addFlashAttribute("error", "CÃ´ng viá»‡c khÃ´ng tá»“n táº¡i trong project cá»§a nhÃ³m.");
            return "redirect:/student/project";
        }
        if (task.getAssigneeId() != student.getStudentId()) {
            redirectAttributes.addFlashAttribute("error", "Chá»‰ ngÆ°á»i Ä‘Æ°á»£c giao má»›i Ä‘Æ°á»£c ná»™p cÃ´ng viá»‡c nÃ y.");
            return "redirect:/student/project";
        }
        if (task.getStatus() == ProjectTask.STATUS_CANCELLED) {
            redirectAttributes.addFlashAttribute("error", "Công việc này đã bị hủy nên không thể nộp.");
            return "redirect:/student/project";
        }

        String note = normalize(submissionNote);
        String url = normalize(submissionUrl);
        if (note.isEmpty() && url.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Ná»™i dung ná»™p cÃ´ng viá»‡c cáº§n cÃ³ mÃ´ táº£ hoáº·c link minh chá»©ng.");
            return "redirect:/student/project";
        }

        int updated = projectTaskRepository.submitTask(taskId, student.getStudentId(), note, url);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "KhÃ´ng thá»ƒ ná»™p cÃ´ng viá»‡c. HÃ£y cháº¯c cháº¯n cÃ´ng viá»‡c Ä‘ang á»Ÿ tráº¡ng thÃ¡i Ä‘ang lÃ m.");
            return "redirect:/student/project";
        }

        redirectAttributes.addFlashAttribute("success", "ÄÃ£ ná»™p cÃ´ng viá»‡c Ä‘á»ƒ ngÆ°á»i kiá»ƒm tra hoáº·c trÆ°á»Ÿng nhÃ³m xÃ©t duyá»‡t.");
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
            redirectAttributes.addFlashAttribute("error", "Báº¡n chÆ°a cÃ³ nhÃ³m trong há»c ká»³ hiá»‡n táº¡i.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            redirectAttributes.addFlashAttribute("error", "NhÃ³m cá»§a báº¡n chÆ°a Ä‘Æ°á»£c táº¡o project.");
            return "redirect:/student/project";
        }
        boolean projectChangeOpen = hasOpenChangeRequest(project);
        if (!canOperateTask(project, projectChangeOpen)) {
            redirectAttributes.addFlashAttribute("error",
                    projectChangeOpen
                            ? "Project đang có yêu cầu đổi đề tài chờ xử lý nên tạm khóa thao tác công việc."
                            : "Project hiá»‡n khÃ´ng cho phÃ©p thao tÃ¡c cÃ´ng viá»‡c.");
            return "redirect:/student/project";
        }

        refreshSprintState(project);
        ProjectTask task = findTaskInProject(project, taskId);
        if (task == null) {
            redirectAttributes.addFlashAttribute("error", "CÃ´ng viá»‡c khÃ´ng tá»“n táº¡i trong project cá»§a nhÃ³m.");
            return "redirect:/student/project";
        }
        if (task.getStatus() == ProjectTask.STATUS_CANCELLED) {
            redirectAttributes.addFlashAttribute("error", "Công việc này đã bị hủy nên không thể review.");
            return "redirect:/student/project";
        }
        if (!canReviewTask(task, group, student)) {
            redirectAttributes.addFlashAttribute("error", "Báº¡n khÃ´ng cÃ³ quyá»n duyá»‡t cÃ´ng viá»‡c nÃ y.");
            return "redirect:/student/project";
        }

        String normalizedAction = normalize(action).toLowerCase();
        String comment = normalize(reviewComment);

        if ("approve".equals(normalizedAction)) {
            int updated = projectTaskRepository.approveTask(taskId, comment);
            if (updated <= 0) {
                redirectAttributes.addFlashAttribute("error", "KhÃ´ng thá»ƒ duyá»‡t cÃ´ng viá»‡c. CÃ´ng viá»‡c cáº§n á»Ÿ tráº¡ng thÃ¡i chá» duyá»‡t.");
                return "redirect:/student/project";
            }
            redirectAttributes.addFlashAttribute("success", "ÄÃ£ duyá»‡t hoÃ n thÃ nh cÃ´ng viá»‡c.");
            return "redirect:/student/project";
        }

        if ("reject".equals(normalizedAction)) {
            if (comment.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Vui lÃ²ng nháº­p lÃ½ do khi tráº£ láº¡i cÃ´ng viá»‡c.");
                return "redirect:/student/project";
            }
            int updated = projectTaskRepository.rejectTask(taskId, comment);
            if (updated <= 0) {
                redirectAttributes.addFlashAttribute("error", "KhÃ´ng thá»ƒ tráº£ láº¡i cÃ´ng viá»‡c. CÃ´ng viá»‡c cáº§n á»Ÿ tráº¡ng thÃ¡i chá» duyá»‡t.");
                return "redirect:/student/project";
            }
            redirectAttributes.addFlashAttribute("success", "ÄÃ£ tráº£ láº¡i cÃ´ng viá»‡c Ä‘á»ƒ há»c viÃªn chá»‰nh sá»­a vÃ  ná»™p láº¡i.");
            return "redirect:/student/project";
        }

        redirectAttributes.addFlashAttribute("error", "HÃ nh Ä‘á»™ng duyá»‡t cÃ´ng viá»‡c khÃ´ng há»£p lá»‡.");
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
            redirectAttributes.addFlashAttribute("error", "Báº¡n chÆ°a cÃ³ nhÃ³m trong há»c ká»³ hiá»‡n táº¡i.");
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
                            ? "Project đang có yêu cầu đổi đề tài chờ xử lý nên tạm khóa thao tác công việc."
                            : "Project hiá»‡n khÃ´ng cho phÃ©p thao tÃ¡c cÃ´ng viá»‡c.");
            return "redirect:/student/project";
        }

        refreshSprintState(project);
        Sprint openSprint = sprintRepository.findOpenByProject(project.getProjectId());
        if (openSprint == null) {
            redirectAttributes.addFlashAttribute("error", "KhÃ´ng cÃ³ Ä‘á»£t lÃ m viá»‡c Ä‘ang má»Ÿ Ä‘á»ƒ nháº­n cÃ´ng viá»‡c tháº¥t báº¡i.");
            return "redirect:/student/project";
        }
        if (openSprint.getSprintId() != sprintId) {
            redirectAttributes.addFlashAttribute("error", "CÃ´ng viá»‡c tháº¥t báº¡i chá»‰ Ä‘Æ°á»£c chuyá»ƒn vÃ o Ä‘á»£t lÃ m viá»‡c Ä‘ang má»Ÿ.");
            return "redirect:/student/project";
        }

        ProjectTask task = findTaskInProject(project, taskId);
        if (task == null || task.getStatus() != ProjectTask.STATUS_FAILED_SPRINT) {
            redirectAttributes.addFlashAttribute("error", "CÃ´ng viá»‡c khÃ´ng há»£p lá»‡ hoáº·c chÆ°a á»Ÿ tráº¡ng thÃ¡i tháº¥t báº¡i Ä‘á»£t.");
            return "redirect:/student/project";
        }
        if (!groupMemberRepository.isMember(group.getGroupId(), assigneeId)) {
            redirectAttributes.addFlashAttribute("error", "NgÆ°á»i thá»±c hiá»‡n pháº£i lÃ  thÃ nh viÃªn trong nhÃ³m.");
            return "redirect:/student/project";
        }
        if (!groupMemberRepository.isMember(group.getGroupId(), reviewerId)) {
            redirectAttributes.addFlashAttribute("error", "NgÆ°á»i kiá»ƒm tra pháº£i lÃ  thÃ nh viÃªn trong nhÃ³m.");
            return "redirect:/student/project";
        }
        if (assigneeId == reviewerId) {
            redirectAttributes.addFlashAttribute("error", "NgÆ°á»i thá»±c hiá»‡n vÃ  ngÆ°á»i kiá»ƒm tra pháº£i lÃ  hai ngÆ°á»i khÃ¡c nhau.");
            return "redirect:/student/project";
        }

        int updated = projectTaskRepository.replanFailedTask(taskId, sprintId, assigneeId, reviewerId);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "KhÃ´ng thá»ƒ chuyá»ƒn cÃ´ng viá»‡c tháº¥t báº¡i vÃ o Ä‘á»£t lÃ m viá»‡c má»›i.");
            return "redirect:/student/project";
        }

        redirectAttributes.addFlashAttribute("success", "ÄÃ£ chuyá»ƒn cÃ´ng viá»‡c tháº¥t báº¡i vÃ o Ä‘á»£t lÃ m viá»‡c má»›i vÃ  gÃ¡n láº¡i thÃ nh viÃªn.");
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
            redirectAttributes.addFlashAttribute("error", "Báº¡n chÆ°a cÃ³ nhÃ³m trong há»c ká»³ hiá»‡n táº¡i.");
            return "redirect:/student/project";
        }
        if (!isLeader(group, student)) {
            redirectAttributes.addFlashAttribute("error", "Chá»‰ trÆ°á»Ÿng nhÃ³m má»›i Ä‘Æ°á»£c chá»‘t link project.");
            return "redirect:/student/project";
        }
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (!isProjectApproved(project)) {
            redirectAttributes.addFlashAttribute("error", "Project chÆ°a duyá»‡t nÃªn chÆ°a thá»ƒ chá»‘t link.");
            return "redirect:/student/project";
        }
        if (!isWithinFinalLinkGrace(project)) {
            redirectAttributes.addFlashAttribute("error", "Chá»‰ Ä‘Æ°á»£c chá»‘t link trong 1 ngÃ y sau khi project káº¿t thÃºc.");
            return "redirect:/student/project";
        }

        String sourceUrl = normalize(sourceCodeUrl);
        String docUrl = normalize(documentUrl);
        if (sourceUrl.isEmpty() || docUrl.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Cáº§n nháº­p cáº£ link source code vÃ  link tÃ i liá»‡u.");
            return "redirect:/student/project";
        }

        int updated = projectRepository.updateFinalLinks(project.getProjectId(), sourceUrl, docUrl);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "KhÃ´ng thá»ƒ cáº­p nháº­t link bÃ n giao cuá»‘i project.");
            return "redirect:/student/project";
        }
        redirectAttributes.addFlashAttribute("success", "ÄÃ£ cáº­p nháº­t link source code vÃ  tÃ i liá»‡u bÃ n giao.");
        return "redirect:/student/project";
    }
}



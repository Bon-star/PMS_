package com.example.pms.service;

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
import com.example.pms.repository.ProjectEditRequestRepository;
import com.example.pms.repository.ProjectRepository;
import com.example.pms.repository.ProjectTaskRepository;
import com.example.pms.repository.SemesterRepository;
import com.example.pms.repository.SprintRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class StudentAiContextService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int MAX_ACTIONABLE_TASKS = 5;

    private final ClassRepository classRepository;
    private final SemesterRepository semesterRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupInvitationRepository groupInvitationRepository;
    private final ProjectRepository projectRepository;
    private final ProjectTaskRepository projectTaskRepository;
    private final SprintRepository sprintRepository;
    private final ProjectEditRequestRepository projectEditRequestRepository;
    private final ProjectChangeRequestRepository projectChangeRequestRepository;

    public StudentAiContextService(ClassRepository classRepository,
            SemesterRepository semesterRepository,
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            GroupInvitationRepository groupInvitationRepository,
            ProjectRepository projectRepository,
            ProjectTaskRepository projectTaskRepository,
            SprintRepository sprintRepository,
            ProjectEditRequestRepository projectEditRequestRepository,
            ProjectChangeRequestRepository projectChangeRequestRepository) {
        this.classRepository = classRepository;
        this.semesterRepository = semesterRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupInvitationRepository = groupInvitationRepository;
        this.projectRepository = projectRepository;
        this.projectTaskRepository = projectTaskRepository;
        this.sprintRepository = sprintRepository;
        this.projectEditRequestRepository = projectEditRequestRepository;
        this.projectChangeRequestRepository = projectChangeRequestRepository;
    }

    public String buildStudentContext(Student student) {
        StringBuilder context = new StringBuilder(1024);
        context.append("LIVE STUDENT CONTEXT\n");
        context.append("- Current date: ").append(formatDate(LocalDate.now())).append('\n');
        context.append("- User role: Student\n");

        if (student == null) {
            context.append("- Logged in student context is unavailable.\n");
            return context.toString();
        }

        Semester semester = resolveCurrentSemester();
        Classes classObj = resolveClass(student);

        context.append("- Student: ").append(safe(student.getFullName()));
        if (hasText(student.getStudentCode())) {
            context.append(" (").append(student.getStudentCode().trim()).append(')');
        }
        context.append('\n');
        context.append("- Class: ").append(classObj != null ? safe(classObj.getClassName()) : "Unknown").append('\n');
        context.append("- Semester: ").append(semester != null ? safe(semester.getSemesterName()) : "Unknown");
        if (semester != null) {
            context.append(" [")
                    .append(formatDate(semester.getStartDate()))
                    .append(" -> ")
                    .append(formatDate(semester.getEndDate()))
                    .append(']');
        }
        context.append('\n');

        if (groupInvitationRepository.isInvitationTableAvailable()) {
            context.append("- Pending group invitations from leaders: ")
                    .append(groupInvitationRepository.countPendingByStudentFromLeader(student.getStudentId()))
                    .append('\n');
        }

        int semesterId = semester != null ? semester.getSemesterId() : 1;
        Group group = resolveActiveGroup(student, semesterId);
        if (group == null) {
            context.append("\nGROUP SUMMARY\n");
            context.append("- Active group: none in the current semester.\n");
            if (student.getClassId() != null) {
                int openGroupCount = groupRepository.findOpenByClassAndSemester(student.getClassId(), semesterId).size();
                context.append("- Open groups available in the current class: ").append(openGroupCount).append('\n');
            }
            context.append("- Recommended next step: create a group or request to join an open group.\n");
            return context.toString();
        }

        boolean isLeader = group.getLeaderId() != null && group.getLeaderId() == student.getStudentId();
        List<Student> members = groupMemberRepository.findMemberDetailsOfGroup(group.getGroupId());
        context.append("\nGROUP SUMMARY\n");
        context.append("- Active group: ").append(safe(group.getGroupName())).append(" (ID ").append(group.getGroupId()).append(")\n");
        context.append("- Your role in group: ").append(isLeader ? "Leader" : "Member").append('\n');
        context.append("- Leader: ").append(safe(group.getLeaderName())).append('\n');
        context.append("- Member count: ").append(groupMemberRepository.countMembers(group.getGroupId())).append("/6\n");
        if (!members.isEmpty()) {
            context.append("- Members: ")
                    .append(members.stream()
                            .map(member -> member.getStudentId() == student.getStudentId()
                                    ? safe(member.getFullName()) + " (current user)"
                                    : safe(member.getFullName()))
                            .collect(Collectors.joining(", ")))
                    .append('\n');
        }
        if (isLeader && groupInvitationRepository.isInvitationTableAvailable()) {
            context.append("- Pending join/invitation approvals for leader: ")
                    .append(groupInvitationRepository.countPendingForLeader(student.getStudentId()))
                    .append('\n');
        }

        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) {
            context.append("\nPROJECT SUMMARY\n");
            context.append("- Project: none assigned or created yet for this group.\n");
            context.append("- Recommended next step: prepare group members and wait for or coordinate project assignment.\n");
            return context.toString();
        }

        context.append("\nPROJECT SUMMARY\n");
        context.append("- Project name: ").append(safe(project.getProjectName())).append('\n');
        context.append("- Project status: ").append(projectStatusLabel(project)).append('\n');
        context.append("- Topic source: ").append(safe(project.getTopicSource())).append('\n');
        context.append("- Student can edit project content now: ").append(yesNo(project.isStudentCanEdit())).append('\n');
        context.append("- Requirement file available: ").append(yesNo(hasText(project.getRequirementFileName()))).append('\n');
        context.append("- Source code link set: ").append(yesNo(hasText(project.getSourceCodeUrl()))).append('\n');
        context.append("- Document link set: ").append(yesNo(hasText(project.getDocumentUrl()))).append('\n');
        if (project.getStartDate() != null || project.getEndDate() != null) {
            context.append("- Project timeline: ")
                    .append(formatDateTime(project.getStartDate()))
                    .append(" -> ")
                    .append(formatDateTime(project.getEndDate()))
                    .append('\n');
        }
        if (hasText(project.getRejectReason())) {
            context.append("- Latest lecturer reject reason: ").append(project.getRejectReason().trim()).append('\n');
        }

        ProjectEditRequest latestEditRequest = projectEditRequestRepository.findLatestByProject(project.getProjectId());
        if (latestEditRequest != null) {
            context.append("- Latest project edit request: ")
                    .append(projectEditRequestLabel(latestEditRequest))
                    .append('\n');
        }

        ProjectChangeRequest latestChangeRequest = projectChangeRequestRepository.findLatestByProject(project.getProjectId());
        if (latestChangeRequest != null) {
            context.append("- Latest project change request: ")
                    .append(projectChangeRequestLabel(latestChangeRequest))
                    .append('\n');
        }

        List<Sprint> sprints = sprintRepository.findByProject(project.getProjectId());
        Sprint openSprint = sprintRepository.findOpenByProject(project.getProjectId());
        context.append("\nSPRINT SUMMARY\n");
        context.append("- Total sprints: ").append(sprints.size()).append('\n');
        if (openSprint != null) {
            context.append("- Open sprint: ").append(safe(openSprint.getSprintName()))
                    .append(" [")
                    .append(formatDate(openSprint.getStartDate()))
                    .append(" -> ")
                    .append(formatDate(openSprint.getEndDate()))
                    .append("]\n");
        } else if (!sprints.isEmpty()) {
            Sprint latestSprint = sprints.get(0);
            context.append("- No open sprint right now. Latest sprint: ")
                    .append(safe(latestSprint.getSprintName()))
                    .append(" [")
                    .append(formatDate(latestSprint.getStartDate()))
                    .append(" -> ")
                    .append(formatDate(latestSprint.getEndDate()))
                    .append("]\n");
        } else {
            context.append("- No sprints created yet.\n");
        }

        List<ProjectTask> tasks = projectTaskRepository.findByProject(project.getProjectId());
        context.append("\nTASK SUMMARY\n");
        if (tasks.isEmpty()) {
            context.append("- No tasks created yet for this project.\n");
        } else {
            context.append("- Total tasks: ").append(tasks.size()).append('\n');
            context.append("- Task counts by status: ").append(taskStatusSummary(tasks)).append('\n');

            List<ProjectTask> myActionableTasks = tasks.stream()
                    .filter(task -> task.getAssigneeId() == student.getStudentId())
                    .filter(task -> isActionableTaskStatus(task.getStatus()))
                    .sorted(Comparator.comparing(this::taskSortTime))
                    .limit(MAX_ACTIONABLE_TASKS)
                    .toList();
            if (!myActionableTasks.isEmpty()) {
                context.append("- Current user's actionable tasks:\n");
                for (ProjectTask task : myActionableTasks) {
                    context.append("  * ")
                            .append(task.getTaskName())
                            .append(" [").append(taskStatusLabel(task.getStatus())).append("]");
                    if (hasText(task.getSprintName())) {
                        context.append(" in ").append(task.getSprintName());
                    }
                    if (task.getExpectedEndTime() != null) {
                        context.append(", expected by ").append(formatDateTime(task.getExpectedEndTime()));
                    } else if (task.getSprintEndDate() != null) {
                        context.append(", sprint ends ").append(formatDate(task.getSprintEndDate()));
                    }
                    context.append('\n');
                }
            }

            List<ProjectTask> reviewTasks = tasks.stream()
                    .filter(task -> task.getReviewerId() != null && task.getReviewerId() == student.getStudentId())
                    .filter(task -> task.getStatus() == ProjectTask.STATUS_SUBMITTED)
                    .limit(MAX_ACTIONABLE_TASKS)
                    .toList();
            if (!reviewTasks.isEmpty()) {
                context.append("- Tasks waiting for current user's review:\n");
                for (ProjectTask task : reviewTasks) {
                    context.append("  * ")
                            .append(task.getTaskName())
                            .append(" from ").append(safe(task.getAssigneeName()));
                    if (task.getSubmittedAt() != null) {
                        context.append(", submitted at ").append(formatDateTime(task.getSubmittedAt()));
                    }
                    context.append('\n');
                }
            }
        }

        List<String> nextActions = buildNextActions(student, group, project, isLeader, openSprint, tasks,
                latestEditRequest, latestChangeRequest);
        if (!nextActions.isEmpty()) {
            context.append("\nRECOMMENDED NEXT ACTIONS\n");
            for (String action : nextActions) {
                context.append("- ").append(action).append('\n');
            }
        }

        return context.toString();
    }

    private Semester resolveCurrentSemester() {
        Semester semester = semesterRepository.findCurrentSemester();
        if (semester == null) {
            semester = semesterRepository.findById(1);
        }
        return semester;
    }

    private Classes resolveClass(Student student) {
        if (student == null || student.getClassId() == null) {
            return null;
        }
        return classRepository.findById(student.getClassId());
    }

    private Group resolveActiveGroup(Student student, int semesterId) {
        if (student == null) {
            return null;
        }
        List<Group> groups = groupRepository.findByStudentAndSemester(student.getStudentId(), semesterId);
        return groups.isEmpty() ? null : groups.get(0);
    }

    private String projectStatusLabel(Project project) {
        if (project == null) {
            return "Unknown";
        }
        return switch (project.getApprovalStatus()) {
            case Project.STATUS_WAITING_STUDENT_CONTENT -> "Waiting student content";
            case Project.STATUS_PENDING_LECTURER -> "Pending lecturer review";
            case Project.STATUS_APPROVED -> "Approved";
            case Project.STATUS_REJECTED -> "Rejected";
            default -> "Draft or not yet submitted";
        };
    }

    private String projectEditRequestLabel(ProjectEditRequest request) {
        String status = request.getStatus() == null ? "UNKNOWN" : request.getStatus().trim().toUpperCase(Locale.ROOT);
        StringBuilder label = new StringBuilder(status);
        if (request.getRequestedDate() != null) {
            label.append(" at ").append(formatDateTime(request.getRequestedDate()));
        }
        if (hasText(request.getResponseReason())) {
            label.append(" (reason: ").append(request.getResponseReason().trim()).append(')');
        }
        return label.toString();
    }

    private String projectChangeRequestLabel(ProjectChangeRequest request) {
        String status = request.getStatus() == null ? "UNKNOWN" : request.getStatus().trim().toUpperCase(Locale.ROOT);
        StringBuilder label = new StringBuilder(status);
        if (hasText(request.getProposedProjectName())) {
            label.append(" for proposed name '").append(request.getProposedProjectName().trim()).append('\'');
        }
        if (request.getRequestedDate() != null) {
            label.append(" at ").append(formatDateTime(request.getRequestedDate()));
        }
        if (hasText(request.getStaffRejectReason())) {
            label.append(" (staff reason: ").append(request.getStaffRejectReason().trim()).append(')');
        } else if (hasText(request.getLecturerRejectReason())) {
            label.append(" (lecturer reason: ").append(request.getLecturerRejectReason().trim()).append(')');
        }
        return label.toString();
    }

    private String taskStatusSummary(List<ProjectTask> tasks) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ProjectTask task : tasks) {
            String label = taskStatusLabel(task.getStatus());
            counts.put(label, counts.getOrDefault(label, 0) + 1);
        }
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    private boolean isActionableTaskStatus(int status) {
        return status == ProjectTask.STATUS_TODO
                || status == ProjectTask.STATUS_IN_PROGRESS
                || status == ProjectTask.STATUS_SUBMITTED
                || status == ProjectTask.STATUS_REJECTED
                || status == ProjectTask.STATUS_FAILED_SPRINT;
    }

    private LocalDateTime taskSortTime(ProjectTask task) {
        if (task.getExpectedEndTime() != null) {
            return task.getExpectedEndTime();
        }
        if (task.getSprintEndDate() != null) {
            return task.getSprintEndDate().atTime(23, 59);
        }
        if (task.getSubmittedAt() != null) {
            return task.getSubmittedAt();
        }
        return LocalDateTime.MAX.minusDays(task.getTaskId());
    }

    private List<String> buildNextActions(Student student,
            Group group,
            Project project,
            boolean isLeader,
            Sprint openSprint,
            List<ProjectTask> tasks,
            ProjectEditRequest latestEditRequest,
            ProjectChangeRequest latestChangeRequest) {
        List<String> actions = new ArrayList<>();
        if (group == null) {
            actions.add("Create a group or request to join an open group in your class.");
            return actions;
        }

        if (project == null) {
            if (isLeader) {
                actions.add("Invite or confirm members so the group is ready before project work starts.");
            } else {
                actions.add("Coordinate with the leader and wait for a project to be assigned to the group.");
            }
            return actions;
        }

        if (project.getApprovalStatus() == Project.STATUS_WAITING_STUDENT_CONTENT || project.isStudentCanEdit()) {
            actions.add("Complete or revise the project content, then submit it for lecturer review when ready.");
        }
        if (project.getApprovalStatus() == Project.STATUS_PENDING_LECTURER) {
            actions.add("Wait for lecturer review and monitor notifications for feedback.");
        }
        if (project.getApprovalStatus() == Project.STATUS_REJECTED) {
            actions.add("Read the reject reason carefully and revise the project before submitting again.");
        }
        if (latestEditRequest != null && "PENDING".equalsIgnoreCase(latestEditRequest.getStatus())) {
            actions.add("A project edit request is pending, so wait for staff response before expecting more edits.");
        }
        if (latestChangeRequest != null && latestChangeRequest.isOpen()) {
            actions.add("A topic change request is in review, so avoid planning conflicting changes until it is resolved.");
        }
        if (project.getApprovalStatus() == Project.STATUS_APPROVED && openSprint == null) {
            actions.add(isLeader
                    ? "Create or plan the next sprint and tasks so the team can continue working."
                    : "Ask the group leader about the next sprint or the next planned tasks.");
        }

        long myTasksToStart = tasks.stream()
                .filter(task -> task.getAssigneeId() == student.getStudentId())
                .filter(task -> task.getStatus() == ProjectTask.STATUS_TODO || task.getStatus() == ProjectTask.STATUS_REJECTED)
                .count();
        if (myTasksToStart > 0) {
            actions.add("You have " + myTasksToStart + " task(s) ready to start or revise.");
        }

        long myTasksToSubmit = tasks.stream()
                .filter(task -> task.getAssigneeId() == student.getStudentId())
                .filter(task -> task.getStatus() == ProjectTask.STATUS_IN_PROGRESS)
                .count();
        if (myTasksToSubmit > 0) {
            actions.add("You have " + myTasksToSubmit + " in-progress task(s) that may need submission soon.");
        }

        long reviewTasks = tasks.stream()
                .filter(task -> task.getReviewerId() != null && task.getReviewerId() == student.getStudentId())
                .filter(task -> task.getStatus() == ProjectTask.STATUS_SUBMITTED)
                .count();
        if (reviewTasks > 0) {
            actions.add("You have " + reviewTasks + " submitted task(s) waiting for your review.");
        }

        if (isLeader && groupInvitationRepository.isInvitationTableAvailable()) {
            int pendingLeaderActions = groupInvitationRepository.countPendingForLeader(student.getStudentId());
            if (pendingLeaderActions > 0) {
                actions.add("You have " + pendingLeaderActions + " pending group invitation/request action(s) to review.");
            }
        }

        return actions.size() > 5 ? actions.subList(0, 5) : actions;
    }

    private String taskStatusLabel(int status) {
        return switch (status) {
            case ProjectTask.STATUS_TODO -> "To Do";
            case ProjectTask.STATUS_IN_PROGRESS -> "In Progress";
            case ProjectTask.STATUS_SUBMITTED -> "Submitted";
            case ProjectTask.STATUS_DONE -> "Done";
            case ProjectTask.STATUS_REJECTED -> "Rejected";
            case ProjectTask.STATUS_FAILED_SPRINT -> "Failed Sprint";
            case ProjectTask.STATUS_CANCELLED -> "Cancelled";
            default -> "Unknown";
        };
    }

    private String formatDate(LocalDate date) {
        return date == null ? "N/A" : DATE_FORMATTER.format(date);
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? "N/A" : DATETIME_FORMATTER.format(dateTime);
    }

    private String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "N/A" : value.trim();
    }
}

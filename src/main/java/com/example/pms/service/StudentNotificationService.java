package com.example.pms.service;

import com.example.pms.model.Group;
import com.example.pms.model.Project;
import com.example.pms.model.ProjectTask;
import com.example.pms.model.Semester;
import com.example.pms.model.Sprint;
import com.example.pms.model.Student;
import com.example.pms.model.StudentNotification;
import com.example.pms.repository.GroupInvitationRepository;
import com.example.pms.repository.GroupMemberRepository;
import com.example.pms.repository.GroupRepository;
import com.example.pms.repository.ProjectRepository;
import com.example.pms.repository.SemesterRepository;
import com.example.pms.repository.StudentNotificationRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class StudentNotificationService {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Autowired
    private StudentNotificationRepository studentNotificationRepository;

    @Autowired
    private GroupInvitationRepository groupInvitationRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private SemesterRepository semesterRepository;

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String projectName(Project project) {
        if (project == null) {
            return "Untitled project";
        }
        String name = normalize(project.getProjectName());
        return name.isEmpty() ? "Untitled project" : name;
    }

    private String groupName(Group group) {
        if (group == null) {
            return "Untitled group";
        }
        String name = normalize(group.getGroupName());
        return name.isEmpty() ? "Untitled group" : name;
    }

    private String sprintName(Sprint sprint) {
        if (sprint == null) {
            return "Sprint";
        }
        String name = normalize(sprint.getSprintName());
        return name.isEmpty() ? ("Sprint #" + sprint.getSprintId()) : name;
    }

    private String taskName(ProjectTask task) {
        if (task == null) {
            return "Untitled task";
        }
        String name = normalize(task.getTaskName());
        return name.isEmpty() ? ("Task #" + task.getTaskId()) : name;
    }

    private String taskSprintName(ProjectTask task) {
        if (task == null) {
            return "Sprint";
        }
        String name = normalize(task.getSprintName());
        return name.isEmpty() ? ("Sprint #" + task.getSprintId()) : name;
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "-" : DATE_TIME_FORMAT.format(value);
    }

    private String truncate(String value, int maxLength) {
        String normalized = normalize(value);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private List<Student> groupMembers(Project project) {
        if (project == null || project.getGroupId() <= 0) {
            return List.of();
        }
        return groupMemberRepository.findMemberDetailsOfGroup(project.getGroupId());
    }

    private void createForStudents(Set<Integer> studentIds,
            String notificationType,
            Integer projectId,
            Integer taskId,
            Integer groupId,
            String title,
            String message,
            String targetUrl,
            String eventKey) {
        String finalTitle = truncate(title, 200);
        String finalMessage = truncate(message, 2000);
        String finalUrl = truncate(targetUrl, 500);
        String finalEventKey = truncate(eventKey, 200);
        for (Integer studentId : studentIds) {
            if (studentId == null || studentId <= 0) {
                continue;
            }
            studentNotificationRepository.createNotification(
                    studentId,
                    notificationType,
                    projectId,
                    taskId,
                    groupId,
                    finalTitle,
                    finalMessage,
                    finalUrl,
                    finalEventKey);
        }
    }

    private void createForGroup(Project project,
            String notificationType,
            String title,
            String message,
            String targetUrl,
            String eventKey) {
        Set<Integer> studentIds = new LinkedHashSet<>();
        for (Student member : groupMembers(project)) {
            if (member != null) {
                studentIds.add(member.getStudentId());
            }
        }
        createForStudents(studentIds,
                notificationType,
                project != null ? project.getProjectId() : null,
                null,
                project != null ? project.getGroupId() : null,
                title,
                message,
                targetUrl,
                eventKey);
    }

    private void createForStudent(Integer studentId,
            Project project,
            ProjectTask task,
            String notificationType,
            String title,
            String message,
            String targetUrl,
            String eventKey) {
        Set<Integer> studentIds = new LinkedHashSet<>();
        if (studentId != null && studentId > 0) {
            studentIds.add(studentId);
        }
        createForStudents(studentIds,
                notificationType,
                project != null ? project.getProjectId() : null,
                task != null ? task.getTaskId() : null,
                project != null ? project.getGroupId() : null,
                title,
                message,
                targetUrl,
                eventKey);
    }

    private Project resolveCurrentProject(Student student) {
        if (student == null) {
            return null;
        }
        Semester currentSemester = semesterRepository.findCurrentSemester();
        if (currentSemester == null) {
            currentSemester = semesterRepository.findById(1);
        }
        if (currentSemester == null) {
            return null;
        }
        List<Group> groups = groupRepository.findByStudentAndSemester(student.getStudentId(), currentSemester.getSemesterId());
        if (groups.isEmpty()) {
            return null;
        }
        return projectRepository.findByGroupId(groups.get(0).getGroupId());
    }

    public void prepareStudentContext(Student student) {
        if (student == null) {
            return;
        }
        Project project = resolveCurrentProject(student);
        if (project == null
                || project.getApprovalStatus() != Project.STATUS_APPROVED
                || project.getStartDate() == null
                || project.getStartDate().isAfter(LocalDateTime.now())) {
            return;
        }
        notifyProjectStarted(project);
    }

    public int countHeaderNotifications(Student student, boolean invitationEnabled) {
        if (student == null) {
            return 0;
        }
        prepareStudentContext(student);
        int total = studentNotificationRepository.countUnreadByStudent(student.getStudentId());
        if (invitationEnabled) {
            total += groupInvitationRepository.countPendingByStudentFromLeader(student.getStudentId());
            total += groupInvitationRepository.countPendingForLeader(student.getStudentId());
        }
        return total;
    }

    public List<StudentNotification> findRecentForStudent(Student student, int limit) {
        if (student == null) {
            return List.of();
        }
        prepareStudentContext(student);
        return studentNotificationRepository.findRecentByStudent(student.getStudentId(), limit);
    }

    public void markAllAsRead(Student student) {
        if (student == null) {
            return;
        }
        studentNotificationRepository.markAllAsRead(student.getStudentId());
    }

    public void notifyProjectAssigned(Project project) {
        if (project == null) {
            return;
        }
        String title = "New project assigned";
        String message;
        if (project.getApprovalStatus() == Project.STATUS_WAITING_STUDENT_CONTENT) {
            message = "Your group received a student-sourced project slot for \"" + projectName(project)
                    + "\". Please prepare the content and submit it for lecturer review.";
        } else {
            message = "Your group received project \"" + projectName(project) + "\".";
        }
        createForGroup(project,
                "PROJECT_ASSIGNED",
                title,
                message,
                "/student/project",
                "PROJECT_ASSIGNED:" + project.getProjectId());
        if (project.getApprovalStatus() == Project.STATUS_APPROVED
                && project.getStartDate() != null
                && !project.getStartDate().isAfter(LocalDateTime.now())) {
            notifyProjectStarted(project);
        }
    }

    public void notifyProjectSubmittedForReview(Project project) {
        if (project == null) {
            return;
        }
        createForGroup(project,
                "PROJECT_SUBMITTED",
                "Project submitted for review",
                "Project \"" + projectName(project) + "\" was submitted to the lecturer for review.",
                "/student/project",
                null);
    }

    public void notifyProjectApproved(Project project) {
        if (project == null) {
            return;
        }
        String message = "Project \"" + projectName(project) + "\" was approved."
                + " Start: " + formatDateTime(project.getStartDate())
                + " | End: " + formatDateTime(project.getEndDate()) + ".";
        createForGroup(project,
                "PROJECT_APPROVED",
                "Project approved",
                message,
                "/student/project",
                null);
        if (project.getStartDate() != null && !project.getStartDate().isAfter(LocalDateTime.now())) {
            notifyProjectStarted(project);
        }
    }

    public void notifyProjectRejected(Project project, String reason) {
        if (project == null) {
            return;
        }
        String message = "Project \"" + projectName(project) + "\" was rejected."
                + (normalize(reason).isEmpty() ? "" : " Reason: " + normalize(reason));
        createForGroup(project,
                "PROJECT_REJECTED",
                "Project rejected",
                message,
                "/student/project",
                null);
    }

    public void notifyProjectStarted(Project project) {
        if (project == null || project.getProjectId() <= 0) {
            return;
        }
        createForGroup(project,
                "PROJECT_STARTED",
                "Project started",
                "Project \"" + projectName(project) + "\" has started. Your group can now work on sprints and tasks.",
                "/student/project/tasks",
                "PROJECT_STARTED:" + project.getProjectId());
    }

    public void notifyEditAccessGranted(Project project) {
        if (project == null) {
            return;
        }
        createForGroup(project,
                "PROJECT_EDIT_GRANTED",
                "Project edit access granted",
                "Students can now update the content of project \"" + projectName(project) + "\".",
                "/student/project",
                null);
    }

    public void notifyEditAccessRejected(Project project, String reason) {
        if (project == null) {
            return;
        }
        String message = "The request to edit project \"" + projectName(project) + "\" was rejected."
                + (normalize(reason).isEmpty() ? "" : " Reason: " + normalize(reason));
        createForGroup(project,
                "PROJECT_EDIT_REJECTED",
                "Project edit request rejected",
                message,
                "/student/project",
                null);
    }

    public void notifyProjectChangeRequested(Project project, String requesterName) {
        if (project == null) {
            return;
        }
        String actor = normalize(requesterName).isEmpty() ? "A group member" : requesterName;
        createForGroup(project,
                "PROJECT_CHANGE_REQUESTED",
                "Project change requested",
                actor + " submitted a project change request for \"" + projectName(project) + "\".",
                "/student/project",
                null);
    }

    public void notifyProjectChangeForwarded(Project project) {
        if (project == null) {
            return;
        }
        createForGroup(project,
                "PROJECT_CHANGE_FORWARDED",
                "Project change sent to lecturer",
                "The project change request for \"" + projectName(project) + "\" was forwarded to the lecturer.",
                "/student/project",
                null);
    }

    public void notifyProjectChangeApproved(Project project) {
        if (project == null) {
            return;
        }
        createForGroup(project,
                "PROJECT_CHANGE_APPROVED",
                "Project change approved",
                "The project change for \"" + projectName(project) + "\" was approved. Old unfinished plans were canceled.",
                "/student/project",
                null);
        if (project.getStartDate() != null && !project.getStartDate().isAfter(LocalDateTime.now())) {
            notifyProjectStarted(project);
        }
    }

    public void notifyProjectChangeRejected(Project project, String reason) {
        if (project == null) {
            return;
        }
        String message = "The project change request for \"" + projectName(project) + "\" was rejected."
                + (normalize(reason).isEmpty() ? "" : " Reason: " + normalize(reason));
        createForGroup(project,
                "PROJECT_CHANGE_REJECTED",
                "Project change rejected",
                message,
                "/student/project",
                null);
    }

    public void notifyLecturerComment(Project project, String comment) {
        if (project == null) {
            return;
        }
        String message = "A lecturer added feedback to project \"" + projectName(project) + "\"."
                + (normalize(comment).isEmpty() ? "" : " Comment: " + truncate(comment, 500));
        createForGroup(project,
                "LECTURER_COMMENT",
                "New lecturer feedback",
                message,
                "/student/project",
                null);
    }

    public void notifySprintCreated(Project project, Sprint sprint) {
        if (project == null || sprint == null) {
            return;
        }
        createForGroup(project,
                "SPRINT_CREATED",
                "New sprint created",
                "Sprint \"" + sprintName(sprint) + "\" was created for project \"" + projectName(project) + "\".",
                "/student/project/tasks",
                null);
    }

    public void notifySprintCancelled(Project project, Sprint sprint, String reason) {
        if (project == null || sprint == null) {
            return;
        }
        String message = "Sprint \"" + sprintName(sprint) + "\" was cancelled."
                + (normalize(reason).isEmpty() ? "" : " Reason: " + normalize(reason));
        createForGroup(project,
                "SPRINT_CANCELLED",
                "Sprint cancelled",
                message,
                "/student/project/tasks",
                null);
    }

    public void notifyTaskAssigned(Project project, ProjectTask task) {
        if (project == null || task == null) {
            return;
        }
        createForStudent(task.getAssigneeId(),
                project,
                task,
                "TASK_ASSIGNED",
                "Task assigned to you",
                "You were assigned task \"" + taskName(task) + "\" in project \"" + projectName(project) + "\".",
                "/student/project/tasks",
                null);
    }

    public void notifyTaskSubmitted(Project project, ProjectTask task) {
        if (project == null || task == null || task.getReviewerId() == null) {
            return;
        }
        createForStudent(task.getReviewerId(),
                project,
                task,
                "TASK_SUBMITTED",
                "Task waiting for review",
                "Task \"" + taskName(task) + "\" was submitted and is waiting for your review.",
                "/student/project/tasks",
                null);
    }

    public void notifyTaskReviewerAssigned(Project project, ProjectTask task) {
        if (project == null || task == null || task.getReviewerId() == null) {
            return;
        }
        createForStudent(task.getReviewerId(),
                project,
                task,
                "TASK_REVIEWER_ASSIGNED",
                "Task review assigned to you",
                "You were assigned to review task \"" + taskName(task) + "\" in project \"" + projectName(project) + "\".",
                "/student/project/tasks",
                null);
    }

    public void notifyTaskSubmissionCancelled(Project project, ProjectTask task) {
        if (project == null || task == null || task.getReviewerId() == null) {
            return;
        }
        createForStudent(task.getReviewerId(),
                project,
                task,
                "TASK_UNSUBMITTED",
                "Task submission cancelled",
                "Task \"" + taskName(task) + "\" is no longer waiting for your review.",
                "/student/project/tasks",
                null);
    }

    public void notifyTaskReturned(Project project, ProjectTask task, String reviewComment) {
        if (project == null || task == null) {
            return;
        }
        String message = "Task \"" + taskName(task) + "\" was returned for revision."
                + (normalize(reviewComment).isEmpty() ? "" : " Reason: " + normalize(reviewComment));
        Set<Integer> recipients = new LinkedHashSet<>();
        recipients.add(task.getAssigneeId());
        if (project.getLeaderId() != null) {
            recipients.add(project.getLeaderId());
        }
        createForStudents(recipients,
                "TASK_RETURNED",
                project.getProjectId(),
                task.getTaskId(),
                project.getGroupId(),
                "Task returned",
                message,
                "/student/project/tasks",
                null);
    }

    public void notifyTaskCompleted(Project project, ProjectTask task) {
        if (project == null || task == null) {
            return;
        }
        Set<Integer> recipients = new LinkedHashSet<>();
        recipients.add(task.getAssigneeId());
        if (project.getLeaderId() != null) {
            recipients.add(project.getLeaderId());
        }
        createForStudents(recipients,
                "TASK_COMPLETED",
                project.getProjectId(),
                task.getTaskId(),
                project.getGroupId(),
                "Task completed",
                "Task \"" + taskName(task) + "\" was approved as completed.",
                "/student/project/tasks",
                null);
    }

    public void notifyTaskCancelled(Project project, ProjectTask task, String reason) {
        if (project == null || task == null) {
            return;
        }
        Set<Integer> recipients = new LinkedHashSet<>();
        recipients.add(task.getAssigneeId());
        if (task.getReviewerId() != null) {
            recipients.add(task.getReviewerId());
        }
        if (project.getLeaderId() != null) {
            recipients.add(project.getLeaderId());
        }
        String message = "Task \"" + taskName(task) + "\" was cancelled."
                + (normalize(reason).isEmpty() ? "" : " Reason: " + normalize(reason));
        createForStudents(recipients,
                "TASK_CANCELLED",
                project.getProjectId(),
                task.getTaskId(),
                project.getGroupId(),
                "Task cancelled",
                message,
                "/student/project/tasks",
                null);
    }

    public void notifyTaskFailedSprint(Project project, ProjectTask task) {
        if (project == null || task == null) {
            return;
        }
        Set<Integer> recipients = new LinkedHashSet<>();
        recipients.add(task.getAssigneeId());
        if (task.getReviewerId() != null) {
            recipients.add(task.getReviewerId());
        }
        if (project.getLeaderId() != null) {
            recipients.add(project.getLeaderId());
        }
        String message = "Task \"" + taskName(task) + "\" was marked as failed because "
                + "\"" + taskSprintName(task) + "\" ended before the task was completed.";
        createForStudents(recipients,
                "TASK_FAILED_SPRINT",
                project.getProjectId(),
                task.getTaskId(),
                project.getGroupId(),
                "Task failed in sprint",
                message,
                "/student/project/tasks",
                "TASK_FAILED_SPRINT:" + task.getTaskId() + ":" + task.getSprintId());
    }

    public void notifyTaskReplanned(Project project, ProjectTask task) {
        if (project == null || task == null) {
            return;
        }
        Set<Integer> recipients = new LinkedHashSet<>();
        recipients.add(task.getAssigneeId());
        if (task.getReviewerId() != null) {
            recipients.add(task.getReviewerId());
        }
        if (project.getLeaderId() != null) {
            recipients.add(project.getLeaderId());
        }
        String message = "Task \"" + taskName(task) + "\" was moved to \"" + taskSprintName(task)
                + "\" and assigned again for execution.";
        createForStudents(recipients,
                "TASK_REPLANNED",
                project.getProjectId(),
                task.getTaskId(),
                project.getGroupId(),
                "Task moved to a new sprint",
                message,
                "/student/project/tasks",
                "TASK_REPLANNED:" + task.getTaskId() + ":" + task.getSprintId());
    }

    public void notifyRemovedFromGroup(Group group, Student removedStudent, Student actor) {
        if (group == null || removedStudent == null || removedStudent.getStudentId() <= 0) {
            return;
        }
        String actorName = normalize(actor != null ? actor.getFullName() : null);
        String message = "You were removed from group \"" + groupName(group) + "\" by "
                + (actorName.isEmpty() ? "the group leader." : actorName + ".");
        Set<Integer> recipients = new LinkedHashSet<>();
        recipients.add(removedStudent.getStudentId());
        createForStudents(recipients,
                "GROUP_MEMBER_REMOVED",
                null,
                null,
                group.getGroupId(),
                "Removed from group",
                message,
                "/student/group/list",
                null);
    }

    public void notifyMemberLeftGroup(Group group, Student member) {
        if (group == null || member == null || member.getStudentId() <= 0 || group.getLeaderId() == null || group.getLeaderId() <= 0) {
            return;
        }
        if (group.getLeaderId().intValue() == member.getStudentId()) {
            return;
        }
        String memberName = normalize(member.getFullName());
        String memberCode = normalize(member.getStudentCode());
        StringBuilder message = new StringBuilder();
        if (memberName.isEmpty()) {
            message.append("A member left group \"").append(groupName(group)).append("\".");
        } else {
            message.append(memberName);
            if (!memberCode.isEmpty()) {
                message.append(" (").append(memberCode).append(")");
            }
            message.append(" left group \"").append(groupName(group)).append("\".");
        }
        Set<Integer> recipients = new LinkedHashSet<>();
        recipients.add(group.getLeaderId());
        createForStudents(recipients,
                "GROUP_MEMBER_LEFT",
                null,
                null,
                group.getGroupId(),
                "Member left the group",
                message.toString(),
                "/student/group/" + group.getGroupId(),
                null);
    }
}

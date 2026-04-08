package com.example.pms.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

@Service
public class StudentAiKnowledgeBaseService {

    private static final String FALLBACK_KNOWLEDGE_BASE = """
            PMS STUDENT KNOWLEDGE BASE

            Navigation:
            - Home shows overview cards and quick links.
            - My group lets students create a group, browse open groups, view group detail, and manage invitations.
            - Group project lets students track project content, sprints, tasks, submissions, comments, and requests.
            - Notifications show project and group activity.

            Authentication and account access:
            - The student topbar avatar menu now contains Profile and Log out.
            - The Profile page currently shows basic account information only.
            - Local accounts can change password from the Profile page by entering the current password, new password, and confirmation.
            - After a successful in-profile password change, PMS signs the user out and asks them to sign in again.
            - The login page has a "Forgot password?" link.
            - The forgot-password flow is still available from the login page.
            - Reset flow: open the login page, click "Forgot password?", enter the registered email, receive OTP, verify OTP, then set a new password.
            - Social login accounts cannot change password through the forgot-password flow.

            Group rules:
            - A student can only have one active group in the current semester.
            - Group names must be unique inside the same class and semester.
            - Maximum group size is 6 members.
            - Only the leader can rename the group or transfer leadership.
            - The leader can delete the group only when the leader is the only member.
            - Students can invite classmates from the same class who are not already in another active group.
            - Students can request to join open groups only when the group has space and does not already have a project.
            - Members cannot be removed, cannot leave, and the group cannot be deleted once an approved project has started.

            Project lifecycle:
            - A group may not have a project yet.
            - Project statuses include waiting student content, pending lecturer review, approved, and rejected.
            - If studentCanEdit is true, students may update project content.
            - Students can submit project content for lecturer review.
            - Rejected projects should be revised based on the reject reason.
            - Requirement files may be available for download.
            - Final source code and document links can only be finalized after approval and within 1 day after the project end date.

            Requests:
            - Project edit requests ask staff to reopen project editing.
            - Project change requests ask to change project topic, name, or description.
            - Change requests are reviewed by staff first and lecturer after that.

            Sprints and tasks:
            - Tasks belong to sprints.
            - Common task statuses are To Do, In Progress, Submitted, Done, Rejected, Failed Sprint, and Cancelled.
            - The assignee can start a To Do or Rejected task.
            - The assignee can submit an In Progress task with note, URL, files, and code.
            - Submitted tasks are reviewed by the reviewer or group leader.
            - Approving a submitted task marks it Done.
            - Rejecting a submitted task returns it for revision.
            - Expired sprints may fail unfinished tasks.
            - Failed tasks can be replanned into a new sprint.
            """;

    private final String studentKnowledgeBase;

    public StudentAiKnowledgeBaseService() {
        this.studentKnowledgeBase = loadKnowledgeBase();
    }

    public String getStudentKnowledgeBase() {
        return studentKnowledgeBase;
    }

    private String loadKnowledgeBase() {
        ClassPathResource resource = new ClassPathResource("ai/student-assistant-guide.md");
        if (!resource.exists()) {
            return FALLBACK_KNOWLEDGE_BASE;
        }
        try {
            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8).trim();
            return content.isBlank() ? FALLBACK_KNOWLEDGE_BASE : content;
        } catch (IOException ex) {
            return FALLBACK_KNOWLEDGE_BASE;
        }
    }
}

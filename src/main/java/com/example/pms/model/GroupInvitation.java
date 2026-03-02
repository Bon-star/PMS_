package com.example.pms.model;

import java.time.LocalDateTime;

public class GroupInvitation {
    private int invitationId;
    private int groupId;
    private int studentId;
    private int invitedByStudentId;
    private String groupName;
    private String studentName;
    private String studentCode;
    private String invitedByName;
    private String invitedByCode;
    private String status; // PENDING, ACCEPTED, REJECTED
    private LocalDateTime invitedDate;
    private LocalDateTime respondedDate;

    public int getInvitationId() { return invitationId; }
    public void setInvitationId(int invitationId) { this.invitationId = invitationId; }

    public int getGroupId() { return groupId; }
    public void setGroupId(int groupId) { this.groupId = groupId; }

    public int getStudentId() { return studentId; }
    public void setStudentId(int studentId) { this.studentId = studentId; }

    public int getInvitedByStudentId() { return invitedByStudentId; }
    public void setInvitedByStudentId(int invitedByStudentId) { this.invitedByStudentId = invitedByStudentId; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getStudentCode() { return studentCode; }
    public void setStudentCode(String studentCode) { this.studentCode = studentCode; }

    public String getInvitedByName() { return invitedByName; }
    public void setInvitedByName(String invitedByName) { this.invitedByName = invitedByName; }

    public String getInvitedByCode() { return invitedByCode; }
    public void setInvitedByCode(String invitedByCode) { this.invitedByCode = invitedByCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getInvitedDate() { return invitedDate; }
    public void setInvitedDate(LocalDateTime invitedDate) { this.invitedDate = invitedDate; }

    public LocalDateTime getRespondedDate() { return respondedDate; }
    public void setRespondedDate(LocalDateTime respondedDate) { this.respondedDate = respondedDate; }
}

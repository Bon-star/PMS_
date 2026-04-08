package com.example.pms.model;

import java.time.LocalDateTime;

public class ProjectEditRequest {
    private int requestId;
    private int projectId;
    private int groupId;
    private String groupName;
    private int requestedByStudentId;
    private String requestedByName;
    private String requestedByCode;
    private String requestNote;
    private String status;
    private String responseReason;
    private Integer respondedByStaffId;
    private LocalDateTime requestedDate;
    private LocalDateTime respondedDate;

    public int getRequestId() {
        return requestId;
    }

    public void setRequestId(int requestId) {
        this.requestId = requestId;
    }

    public int getProjectId() {
        return projectId;
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }

    public int getGroupId() {
        return groupId;
    }

    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public int getRequestedByStudentId() {
        return requestedByStudentId;
    }

    public void setRequestedByStudentId(int requestedByStudentId) {
        this.requestedByStudentId = requestedByStudentId;
    }

    public String getRequestedByName() {
        return requestedByName;
    }

    public void setRequestedByName(String requestedByName) {
        this.requestedByName = requestedByName;
    }

    public String getRequestedByCode() {
        return requestedByCode;
    }

    public void setRequestedByCode(String requestedByCode) {
        this.requestedByCode = requestedByCode;
    }

    public String getRequestNote() {
        return requestNote;
    }

    public void setRequestNote(String requestNote) {
        this.requestNote = requestNote;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResponseReason() {
        return responseReason;
    }

    public void setResponseReason(String responseReason) {
        this.responseReason = responseReason;
    }

    public Integer getRespondedByStaffId() {
        return respondedByStaffId;
    }

    public void setRespondedByStaffId(Integer respondedByStaffId) {
        this.respondedByStaffId = respondedByStaffId;
    }

    public LocalDateTime getRequestedDate() {
        return requestedDate;
    }

    public void setRequestedDate(LocalDateTime requestedDate) {
        this.requestedDate = requestedDate;
    }

    public LocalDateTime getRespondedDate() {
        return respondedDate;
    }

    public void setRespondedDate(LocalDateTime respondedDate) {
        this.respondedDate = respondedDate;
    }
}

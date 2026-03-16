package com.example.pms.model;

import java.time.LocalDateTime;

public class ProjectChangeRequest {
    public static final String STATUS_PENDING_STAFF = "PENDING_STAFF";
    public static final String STATUS_PENDING_LECTURER = "PENDING_LECTURER";
    public static final String STATUS_REJECTED_BY_STAFF = "REJECTED_BY_STAFF";
    public static final String STATUS_REJECTED_BY_LECTURER = "REJECTED_BY_LECTURER";
    public static final String STATUS_APPROVED = "APPROVED";

    private int requestId;
    private int projectId;
    private int groupId;
    private String groupName;
    private String className;
    private String currentProjectName;
    private int requestedByStudentId;
    private String requestedByName;
    private String requestedByCode;
    private String proposedProjectName;
    private String proposedDescription;
    private String changeReason;
    private String status;
    private Integer staffReviewedByStaffId;
    private String staffRejectReason;
    private LocalDateTime staffReviewedAt;
    private Integer lecturerReviewedByLecturerId;
    private String lecturerRejectReason;
    private LocalDateTime lecturerReviewedAt;
    private LocalDateTime requestedDate;

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

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getCurrentProjectName() {
        return currentProjectName;
    }

    public void setCurrentProjectName(String currentProjectName) {
        this.currentProjectName = currentProjectName;
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

    public String getProposedProjectName() {
        return proposedProjectName;
    }

    public void setProposedProjectName(String proposedProjectName) {
        this.proposedProjectName = proposedProjectName;
    }

    public String getProposedDescription() {
        return proposedDescription;
    }

    public void setProposedDescription(String proposedDescription) {
        this.proposedDescription = proposedDescription;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public void setChangeReason(String changeReason) {
        this.changeReason = changeReason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getStaffReviewedByStaffId() {
        return staffReviewedByStaffId;
    }

    public void setStaffReviewedByStaffId(Integer staffReviewedByStaffId) {
        this.staffReviewedByStaffId = staffReviewedByStaffId;
    }

    public String getStaffRejectReason() {
        return staffRejectReason;
    }

    public void setStaffRejectReason(String staffRejectReason) {
        this.staffRejectReason = staffRejectReason;
    }

    public LocalDateTime getStaffReviewedAt() {
        return staffReviewedAt;
    }

    public void setStaffReviewedAt(LocalDateTime staffReviewedAt) {
        this.staffReviewedAt = staffReviewedAt;
    }

    public Integer getLecturerReviewedByLecturerId() {
        return lecturerReviewedByLecturerId;
    }

    public void setLecturerReviewedByLecturerId(Integer lecturerReviewedByLecturerId) {
        this.lecturerReviewedByLecturerId = lecturerReviewedByLecturerId;
    }

    public String getLecturerRejectReason() {
        return lecturerRejectReason;
    }

    public void setLecturerRejectReason(String lecturerRejectReason) {
        this.lecturerRejectReason = lecturerRejectReason;
    }

    public LocalDateTime getLecturerReviewedAt() {
        return lecturerReviewedAt;
    }

    public void setLecturerReviewedAt(LocalDateTime lecturerReviewedAt) {
        this.lecturerReviewedAt = lecturerReviewedAt;
    }

    public LocalDateTime getRequestedDate() {
        return requestedDate;
    }

    public void setRequestedDate(LocalDateTime requestedDate) {
        this.requestedDate = requestedDate;
    }

    public boolean isOpen() {
        return STATUS_PENDING_STAFF.equalsIgnoreCase(status) || STATUS_PENDING_LECTURER.equalsIgnoreCase(status);
    }
}

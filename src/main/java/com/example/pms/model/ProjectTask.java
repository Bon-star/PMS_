package com.example.pms.model;

import java.time.LocalDateTime;

public class ProjectTask {
    public static final int STATUS_TODO = 0;
    public static final int STATUS_IN_PROGRESS = 1;
    public static final int STATUS_SUBMITTED = 2;
    public static final int STATUS_DONE = 3;
    public static final int STATUS_REJECTED = 4;
    public static final int STATUS_FAILED_SPRINT = 5;
    public static final int STATUS_CANCELLED = 6;

    private int taskId;
    private int projectId;
    private int sprintId;
    private String sprintName;
    private java.time.LocalDate sprintStartDate;
    private java.time.LocalDate sprintEndDate;
    private String taskName;
    private String description;
    private String taskImage;
    private double estimatedPoints;
    private double estimatedHours;
    private int assigneeId;
    private String assigneeName;
    private String assigneeCode;
    private Integer reviewerId;
    private String reviewerName;
    private int status;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
    private LocalDateTime expectedEndTime;
    private String submissionNote;
    private String submissionUrl;
    private LocalDateTime submittedAt;
    private String reviewComment;
    private LocalDateTime reviewedAt;
    private String cancelledReason;
    private Integer cancelledByStudentId;
    private LocalDateTime cancelledAt;

    public int getTaskId() {
        return taskId;
    }

    public void setTaskId(int taskId) {
        this.taskId = taskId;
    }

    public int getProjectId() {
        return projectId;
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }

    public int getSprintId() {
        return sprintId;
    }

    public void setSprintId(int sprintId) {
        this.sprintId = sprintId;
    }

    public String getSprintName() {
        return sprintName;
    }

    public void setSprintName(String sprintName) {
        this.sprintName = sprintName;
    }

    public java.time.LocalDate getSprintStartDate() {
        return sprintStartDate;
    }

    public void setSprintStartDate(java.time.LocalDate sprintStartDate) {
        this.sprintStartDate = sprintStartDate;
    }

    public java.time.LocalDate getSprintEndDate() {
        return sprintEndDate;
    }

    public void setSprintEndDate(java.time.LocalDate sprintEndDate) {
        this.sprintEndDate = sprintEndDate;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTaskImage() {
        return taskImage;
    }

    public void setTaskImage(String taskImage) {
        this.taskImage = taskImage;
    }

    public double getEstimatedPoints() {
        return estimatedPoints;
    }

    public void setEstimatedPoints(double estimatedPoints) {
        this.estimatedPoints = estimatedPoints;
    }

    public double getEstimatedHours() {
        return estimatedHours;
    }

    public void setEstimatedHours(double estimatedHours) {
        this.estimatedHours = estimatedHours;
    }

    public int getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(int assigneeId) {
        this.assigneeId = assigneeId;
    }

    public String getAssigneeName() {
        return assigneeName;
    }

    public void setAssigneeName(String assigneeName) {
        this.assigneeName = assigneeName;
    }

    public String getAssigneeCode() {
        return assigneeCode;
    }

    public void setAssigneeCode(String assigneeCode) {
        this.assigneeCode = assigneeCode;
    }

    public Integer getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(Integer reviewerId) {
        this.reviewerId = reviewerId;
    }

    public String getReviewerName() {
        return reviewerName;
    }

    public void setReviewerName(String reviewerName) {
        this.reviewerName = reviewerName;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public LocalDateTime getActualStartTime() {
        return actualStartTime;
    }

    public void setActualStartTime(LocalDateTime actualStartTime) {
        this.actualStartTime = actualStartTime;
    }

    public LocalDateTime getActualEndTime() {
        return actualEndTime;
    }

    public void setActualEndTime(LocalDateTime actualEndTime) {
        this.actualEndTime = actualEndTime;
    }

    public LocalDateTime getExpectedEndTime() {
        return expectedEndTime;
    }

    public void setExpectedEndTime(LocalDateTime expectedEndTime) {
        this.expectedEndTime = expectedEndTime;
    }

    public String getSubmissionNote() {
        return submissionNote;
    }

    public void setSubmissionNote(String submissionNote) {
        this.submissionNote = submissionNote;
    }

    public String getSubmissionUrl() {
        return submissionUrl;
    }

    public void setSubmissionUrl(String submissionUrl) {
        this.submissionUrl = submissionUrl;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public void setReviewComment(String reviewComment) {
        this.reviewComment = reviewComment;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getCancelledReason() {
        return cancelledReason;
    }

    public void setCancelledReason(String cancelledReason) {
        this.cancelledReason = cancelledReason;
    }

    public Integer getCancelledByStudentId() {
        return cancelledByStudentId;
    }

    public void setCancelledByStudentId(Integer cancelledByStudentId) {
        this.cancelledByStudentId = cancelledByStudentId;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }
}

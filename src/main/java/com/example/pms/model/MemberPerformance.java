package com.example.pms.model;

public class MemberPerformance {
    private Integer sprintId;
    private String sprintName;
    private int studentId;
    private String studentCode;
    private String studentName;
    private int totalTasks;
    private int doneTasks;
    private int failedTasks;
    private int submittedTasks;
    private int inProgressTasks;
    private int todoTasks;

    public Integer getSprintId() {
        return sprintId;
    }

    public void setSprintId(Integer sprintId) {
        this.sprintId = sprintId;
    }

    public String getSprintName() {
        return sprintName;
    }

    public void setSprintName(String sprintName) {
        this.sprintName = sprintName;
    }

    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(int studentId) {
        this.studentId = studentId;
    }

    public String getStudentCode() {
        return studentCode;
    }

    public void setStudentCode(String studentCode) {
        this.studentCode = studentCode;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public int getTotalTasks() {
        return totalTasks;
    }

    public void setTotalTasks(int totalTasks) {
        this.totalTasks = totalTasks;
    }

    public int getDoneTasks() {
        return doneTasks;
    }

    public void setDoneTasks(int doneTasks) {
        this.doneTasks = doneTasks;
    }

    public int getFailedTasks() {
        return failedTasks;
    }

    public void setFailedTasks(int failedTasks) {
        this.failedTasks = failedTasks;
    }

    public int getSubmittedTasks() {
        return submittedTasks;
    }

    public void setSubmittedTasks(int submittedTasks) {
        this.submittedTasks = submittedTasks;
    }

    public int getInProgressTasks() {
        return inProgressTasks;
    }

    public void setInProgressTasks(int inProgressTasks) {
        this.inProgressTasks = inProgressTasks;
    }

    public int getTodoTasks() {
        return todoTasks;
    }

    public void setTodoTasks(int todoTasks) {
        this.todoTasks = todoTasks;
    }

    public int getResolvedTasks() {
        return doneTasks + failedTasks;
    }

    public int getUnresolvedTasks() {
        return totalTasks - getResolvedTasks();
    }

    public double getDoneRatePercent() {
        if (totalTasks <= 0) {
            return 0;
        }
        return doneTasks * 100.0d / totalTasks;
    }
}

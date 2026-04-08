package com.example.pms.model;

public class ProjectScoreRecord {
    private int scoreId;
    private int projectId;
    private int studentId;
    private int lecturerId;
    private Double lecturerScore;
    private String lecturerComment;
    private Double staffAdjustedScore;
    private String staffNote;
    private boolean published;

    public int getScoreId() {
        return scoreId;
    }

    public void setScoreId(int scoreId) {
        this.scoreId = scoreId;
    }

    public int getProjectId() {
        return projectId;
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }

    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(int studentId) {
        this.studentId = studentId;
    }

    public int getLecturerId() {
        return lecturerId;
    }

    public void setLecturerId(int lecturerId) {
        this.lecturerId = lecturerId;
    }

    public Double getLecturerScore() {
        return lecturerScore;
    }

    public void setLecturerScore(Double lecturerScore) {
        this.lecturerScore = lecturerScore;
    }

    public String getLecturerComment() {
        return lecturerComment;
    }

    public void setLecturerComment(String lecturerComment) {
        this.lecturerComment = lecturerComment;
    }

    public Double getStaffAdjustedScore() {
        return staffAdjustedScore;
    }

    public void setStaffAdjustedScore(Double staffAdjustedScore) {
        this.staffAdjustedScore = staffAdjustedScore;
    }

    public String getStaffNote() {
        return staffNote;
    }

    public void setStaffNote(String staffNote) {
        this.staffNote = staffNote;
    }

    public boolean isPublished() {
        return published;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public Double getFinalScore() {
        return staffAdjustedScore != null ? staffAdjustedScore : lecturerScore;
    }
}

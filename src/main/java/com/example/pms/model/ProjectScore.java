package com.example.pms.model;

public class ProjectScore {
    private int scoreId;
    private int projectId;
    private int studentId;
    private String studentName;
    private String studentCode;
    private int lecturerId;
    private String lecturerName;
    private Double lecturerScore;
    private String lecturerComment;
    private Double staffAdjustedScore;
    private String staffNote;
    private boolean isPublished;

    // constructors
    public ProjectScore() {}

    // getters and setters
    public int getScoreId() { return scoreId; }
    public void setScoreId(int scoreId) { this.scoreId = scoreId; }

    public int getProjectId() { return projectId; }
    public void setProjectId(int projectId) { this.projectId = projectId; }

    public int getStudentId() { return studentId; }
    public void setStudentId(int studentId) { this.studentId = studentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getStudentCode() { return studentCode; }
    public void setStudentCode(String studentCode) { this.studentCode = studentCode; }

    public int getLecturerId() { return lecturerId; }
    public void setLecturerId(int lecturerId) { this.lecturerId = lecturerId; }

    public String getLecturerName() { return lecturerName; }
    public void setLecturerName(String lecturerName) { this.lecturerName = lecturerName; }

    public Double getLecturerScore() { return lecturerScore; }
    public void setLecturerScore(Double lecturerScore) { this.lecturerScore = lecturerScore; }

    public String getLecturerComment() { return lecturerComment; }
    public void setLecturerComment(String lecturerComment) { this.lecturerComment = lecturerComment; }

    public Double getStaffAdjustedScore() { return staffAdjustedScore; }
    public void setStaffAdjustedScore(Double staffAdjustedScore) { this.staffAdjustedScore = staffAdjustedScore; }

    public String getStaffNote() { return staffNote; }
    public void setStaffNote(String staffNote) { this.staffNote = staffNote; }

    public boolean isPublished() { return isPublished; }
    public void setPublished(boolean published) { isPublished = published; }
}

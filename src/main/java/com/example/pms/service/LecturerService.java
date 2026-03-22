package com.example.pms.service;

import com.example.pms.model.MemberPerformance;
import com.example.pms.model.Project;
import com.example.pms.model.ProjectComment;
import com.example.pms.model.ProjectScore;
import com.example.pms.model.ProjectTask;

import java.time.LocalDateTime;
import java.util.List;

public interface LecturerService {
    boolean approveProjectContent(int lecturerId, int projectId, boolean approve, String reason, LocalDateTime startDate, LocalDateTime endDate);
    List<Project> getGuidedProjects(int lecturerId);
    boolean canAccessProject(int lecturerId, int projectId);
    List<ProjectTask> getProjectTasks(int projectId);
    List<ProjectComment> getProjectComments(int projectId);
    boolean addTaskComment(int lecturerId, int projectId, int taskId, String comment);
    List<Project> getProjectsToGrade(int lecturerId);
    List<MemberPerformance> getStudentPerformanceStats(int projectId);
    boolean submitStudentGrades(int lecturerId, int projectId, List<ProjectScore> grades);
    List<ProjectScore> getProjectScores(int projectId, int lecturerId);
}


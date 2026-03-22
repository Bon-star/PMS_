package com.example.pms.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pms.model.MemberPerformance;
import com.example.pms.model.Project;
import com.example.pms.model.ProjectComment;
import com.example.pms.model.ProjectScore;
import com.example.pms.model.ProjectTask;
import com.example.pms.repository.ProjectCommentRepository;
import com.example.pms.repository.ProjectRepository;
import com.example.pms.repository.ProjectScoreRepository;
import com.example.pms.repository.ProjectTaskRepository;

@Service
@Transactional
public class LecturerServiceImpl implements LecturerService {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectTaskRepository projectTaskRepository;

    @Autowired
    private ProjectCommentRepository projectCommentRepository;

    @Autowired
    private ProjectScoreRepository projectScoreRepository;

    @Override
    public boolean approveProjectContent(int lecturerId, int projectId, boolean approve, String reason, LocalDateTime startDate, LocalDateTime endDate) {
        if (!projectRepository.canLecturerAccessProject(lecturerId, projectId)) {
            throw new IllegalArgumentException("Lecturer does not have access to this project");
        }
        Project project = projectRepository.findById(projectId);
        if (project == null || project.getApprovalStatus() != Project.STATUS_PENDING_LECTURER) {
            throw new IllegalStateException("Project not pending lecturer approval");
        }
        int updated;
        if (approve) {
            updated = projectRepository.approveByLecturer(projectId, startDate, endDate);
        } else {
            if (reason == null || reason.trim().isEmpty()) {
                throw new IllegalArgumentException("Reason required for rejection");
            }
            updated = projectRepository.rejectByLecturer(projectId, reason.trim());
        }
        return updated > 0;
    }

    @Override
    public List<Project> getGuidedProjects(int lecturerId) {
        return projectRepository.findApprovedForLecturer(lecturerId);
    }

    @Override
    public boolean canAccessProject(int lecturerId, int projectId) {
        return projectRepository.canLecturerAccessProject(lecturerId, projectId);
    }

    @Override
    public List<ProjectTask> getProjectTasks(int projectId) {
        return projectTaskRepository.findByProject(projectId);
    }

    @Override
    public List<ProjectComment> getProjectComments(int projectId) {
        return projectCommentRepository.findByProject(projectId);
    }

    @Override
    public boolean addTaskComment(int lecturerId, int projectId, int taskId, String comment) {
        if (!canAccessProject(lecturerId, projectId)) {
            throw new IllegalArgumentException("No access to project");
        }
        if (comment == null || comment.trim().isEmpty()) {
            throw new IllegalArgumentException("Comment cannot be empty");
        }
        String fullComment = "[Task #" + taskId + "]: " + comment.trim();
        int created = projectCommentRepository.create(projectId, lecturerId, fullComment);
        return created > 0;
    }

    @Override
    public List<Project> getProjectsToGrade(int lecturerId) {
        List<Project> guided = getGuidedProjects(lecturerId);
        return guided.stream()
                .filter(p -> p.getSubmissionDate() != null)
                .collect(Collectors.toList());
    }

    @Override
    public List<MemberPerformance> getStudentPerformanceStats(int projectId) {
        return projectTaskRepository.findMemberPerformanceBySprint(projectId);
    }

    @Override
    public boolean submitStudentGrades(int lecturerId, int projectId, List<ProjectScore> grades) {
        if (!canAccessProject(lecturerId, projectId)) {
            throw new IllegalArgumentException("No access to project");
        }
        if (grades == null || grades.isEmpty()) {
            return false;
        }
        int successCount = 0;
        for (ProjectScore grade : grades) {
            if (grade.getStudentId() > 0 && grade.getLecturerScore() != null) {
                int updated = projectScoreRepository.upsertLecturerScore(
                        projectId, grade.getStudentId(), lecturerId,
                        grade.getLecturerScore(), grade.getLecturerComment()
                );
                if (updated > 0) successCount++;
            }
        }
        return successCount == grades.size();
    }

    @Override
    public List<ProjectScore> getProjectScores(int projectId, int lecturerId) {
        if (!canAccessProject(lecturerId, projectId)) {
            return List.of();
        }
        return projectScoreRepository.findByProjectId(projectId);
    }
}


package com.example.pms.repository;

import com.example.pms.model.MemberPerformance;
import com.example.pms.model.ProjectTask;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectTaskRepository {

    @Autowired
    private JdbcTemplate db;

    private volatile boolean schemaEnsured;

    private void ensureSchema() {
        if (schemaEnsured) {
            return;
        }
        synchronized (this) {
            if (schemaEnsured) {
                return;
            }
            db.execute("IF COL_LENGTH('Tasks', 'SubmissionNote') IS NULL ALTER TABLE Tasks ADD SubmissionNote NVARCHAR(MAX) NULL;");
            db.execute("IF COL_LENGTH('Tasks', 'SubmissionUrl') IS NULL ALTER TABLE Tasks ADD SubmissionUrl NVARCHAR(500) NULL;");
            db.execute("IF COL_LENGTH('Tasks', 'SubmittedAt') IS NULL ALTER TABLE Tasks ADD SubmittedAt DATETIME NULL;");
            db.execute("IF COL_LENGTH('Tasks', 'ReviewComment') IS NULL ALTER TABLE Tasks ADD ReviewComment NVARCHAR(MAX) NULL;");
            db.execute("IF COL_LENGTH('Tasks', 'ReviewedAt') IS NULL ALTER TABLE Tasks ADD ReviewedAt DATETIME NULL;");
            schemaEnsured = true;
        }
    }

    private int findMainSprintId(int projectId) {
        ensureSchema();
        try {
            String sql = "SELECT TOP 1 SprintID FROM Sprints WHERE ProjectID = ? ORDER BY SprintID ASC";
            Integer sprintId = db.queryForObject(sql, Integer.class, projectId);
            return sprintId != null ? sprintId : -1;
        } catch (Exception ex) {
            return -1;
        }
    }

    private int createMainSprint(int projectId, LocalDate startDate, LocalDate endDate) {
        ensureSchema();
        String sql = "INSERT INTO Sprints (ProjectID, SprintName, StartDate, EndDate, IsClosed) " +
                "OUTPUT INSERTED.SprintID VALUES (?, N'Sprint chinh', ?, ?, 0)";
        Integer sprintId = db.queryForObject(sql, Integer.class, projectId, startDate, endDate);
        return sprintId != null ? sprintId : -1;
    }

    private int ensureMainSprint(int projectId, LocalDate startDate, LocalDate endDate) {
        int sprintId = findMainSprintId(projectId);
        if (sprintId > 0) {
            return sprintId;
        }
        return createMainSprint(projectId, startDate, endDate);
    }

    public int createTask(int projectId,
            String taskName,
            String description,
            String taskImage,
            double estimatedPoints,
            int assigneeId,
            Integer reviewerId,
            LocalDateTime projectStart,
            LocalDateTime projectEnd) {
        ensureSchema();
        try {
            LocalDate fallbackStart = projectStart != null ? projectStart.toLocalDate() : LocalDate.now();
            LocalDate fallbackEnd = projectEnd != null ? projectEnd.toLocalDate() : fallbackStart.plusWeeks(2);
            int sprintId = ensureMainSprint(projectId, fallbackStart, fallbackEnd);
            if (sprintId <= 0) {
                return -1;
            }
            return createTaskInSprint(sprintId, taskName, description, taskImage, estimatedPoints, assigneeId, reviewerId);
        } catch (Exception ex) {
            return -1;
        }
    }

    public int createTaskInSprint(int sprintId,
            String taskName,
            String description,
            String taskImage,
            double estimatedPoints,
            int assigneeId,
            Integer reviewerId) {
        ensureSchema();
        try {
            String sql = "INSERT INTO Tasks (SprintID, TaskName, Description, TaskImage, EstimatedPoints, AssigneeID, ReviewerID, Status, " +
                    "SubmissionNote, SubmissionUrl, SubmittedAt, ReviewComment, ReviewedAt, ActualStartTime, ActualEndTime) " +
                    "OUTPUT INSERTED.TaskID VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, NULL, NULL, NULL, NULL, NULL)";
            Integer taskId = db.queryForObject(sql, Integer.class,
                    sprintId,
                    taskName,
                    description,
                    taskImage,
                    estimatedPoints,
                    assigneeId,
                    reviewerId,
                    ProjectTask.STATUS_TODO);
            return taskId != null ? taskId : -1;
        } catch (Exception ex) {
            return -1;
        }
    }

    public ProjectTask findById(int taskId) {
        ensureSchema();
        try {
            String sql = "SELECT t.TaskID, sp.ProjectID, t.SprintID, sp.SprintName, sp.StartDate AS SprintStartDate, sp.EndDate AS SprintEndDate, " +
                    "t.TaskName, t.Description, t.TaskImage, t.EstimatedPoints, " +
                    "t.AssigneeID, ass.FullName AS AssigneeName, ass.StudentCode AS AssigneeCode, " +
                    "t.ReviewerID, rev.FullName AS ReviewerName, " +
                    "t.Status, t.ActualStartTime, t.ActualEndTime, " +
                    "t.SubmissionNote, t.SubmissionUrl, t.SubmittedAt, t.ReviewComment, t.ReviewedAt " +
                    "FROM Tasks t " +
                    "INNER JOIN Sprints sp ON sp.SprintID = t.SprintID " +
                    "INNER JOIN Students ass ON ass.StudentID = t.AssigneeID " +
                    "LEFT JOIN Students rev ON rev.StudentID = t.ReviewerID " +
                    "WHERE t.TaskID = ?";
            return db.queryForObject(sql, (rs, rn) -> mapTask(rs), taskId);
        } catch (Exception ex) {
            return null;
        }
    }

    public int markInProgress(int taskId, int assigneeId) {
        ensureSchema();
        String sql = "UPDATE Tasks " +
                "SET Status = ?, ActualStartTime = ISNULL(ActualStartTime, GETDATE()), " +
                "ReviewComment = CASE WHEN Status = ? THEN NULL ELSE ReviewComment END " +
                "WHERE TaskID = ? AND AssigneeID = ? AND Status IN (?, ?)";
        return db.update(sql,
                ProjectTask.STATUS_IN_PROGRESS,
                ProjectTask.STATUS_REJECTED,
                taskId,
                assigneeId,
                ProjectTask.STATUS_TODO,
                ProjectTask.STATUS_REJECTED);
    }

    public int submitTask(int taskId, int assigneeId, String submissionNote, String submissionUrl) {
        ensureSchema();
        String sql = "UPDATE Tasks " +
                "SET Status = ?, SubmissionNote = ?, SubmissionUrl = ?, SubmittedAt = GETDATE(), " +
                "ReviewComment = NULL, ReviewedAt = NULL " +
                "WHERE TaskID = ? AND AssigneeID = ? AND Status = ?";
        return db.update(sql,
                ProjectTask.STATUS_SUBMITTED,
                submissionNote,
                submissionUrl,
                taskId,
                assigneeId,
                ProjectTask.STATUS_IN_PROGRESS);
    }

    public int approveTask(int taskId, String reviewComment) {
        ensureSchema();
        String sql = "UPDATE Tasks " +
                "SET Status = ?, ReviewComment = ?, ReviewedAt = GETDATE(), ActualEndTime = GETDATE() " +
                "WHERE TaskID = ? AND Status = ?";
        return db.update(sql,
                ProjectTask.STATUS_DONE,
                reviewComment,
                taskId,
                ProjectTask.STATUS_SUBMITTED);
    }

    public int rejectTask(int taskId, String reviewComment) {
        ensureSchema();
        String sql = "UPDATE Tasks " +
                "SET Status = ?, ReviewComment = ?, ReviewedAt = GETDATE(), ActualEndTime = NULL " +
                "WHERE TaskID = ? AND Status = ?";
        return db.update(sql,
                ProjectTask.STATUS_REJECTED,
                reviewComment,
                taskId,
                ProjectTask.STATUS_SUBMITTED);
    }

    public int markFailedForSprint(int sprintId, String reason) {
        ensureSchema();
        String sql = "UPDATE Tasks " +
                "SET Status = ?, " +
                "ReviewComment = CASE WHEN NULLIF(LTRIM(RTRIM(ISNULL(ReviewComment, ''))), '') IS NULL THEN ? ELSE ReviewComment END, " +
                "ReviewedAt = GETDATE(), " +
                "ActualEndTime = ISNULL(ActualEndTime, GETDATE()) " +
                "WHERE SprintID = ? AND Status <> ? AND Status <> ?";
        return db.update(sql,
                ProjectTask.STATUS_FAILED_SPRINT,
                reason,
                sprintId,
                ProjectTask.STATUS_DONE,
                ProjectTask.STATUS_FAILED_SPRINT);
    }

    public List<ProjectTask> findFailedByProject(int projectId) {
        ensureSchema();
        String sql = "SELECT t.TaskID, sp.ProjectID, t.SprintID, sp.SprintName, sp.StartDate AS SprintStartDate, sp.EndDate AS SprintEndDate, " +
                "t.TaskName, t.Description, t.TaskImage, t.EstimatedPoints, " +
                "t.AssigneeID, ass.FullName AS AssigneeName, ass.StudentCode AS AssigneeCode, " +
                "t.ReviewerID, rev.FullName AS ReviewerName, " +
                "t.Status, t.ActualStartTime, t.ActualEndTime, " +
                "t.SubmissionNote, t.SubmissionUrl, t.SubmittedAt, t.ReviewComment, t.ReviewedAt " +
                "FROM Tasks t " +
                "INNER JOIN Sprints sp ON sp.SprintID = t.SprintID " +
                "INNER JOIN Students ass ON ass.StudentID = t.AssigneeID " +
                "LEFT JOIN Students rev ON rev.StudentID = t.ReviewerID " +
                "WHERE sp.ProjectID = ? AND t.Status = ? " +
                "ORDER BY sp.EndDate DESC, t.TaskID DESC";
        return db.query(sql, (rs, rn) -> mapTask(rs), projectId, ProjectTask.STATUS_FAILED_SPRINT);
    }

    public int replanFailedTask(int taskId, int newSprintId, int assigneeId, Integer reviewerId) {
        ensureSchema();
        String sql = "UPDATE Tasks " +
                "SET SprintID = ?, AssigneeID = ?, ReviewerID = ?, Status = ?, " +
                "SubmissionNote = NULL, SubmissionUrl = NULL, SubmittedAt = NULL, " +
                "ReviewComment = NULL, ReviewedAt = NULL, ActualStartTime = NULL, ActualEndTime = NULL " +
                "WHERE TaskID = ? AND Status = ?";
        return db.update(sql,
                newSprintId,
                assigneeId,
                reviewerId,
                ProjectTask.STATUS_TODO,
                taskId,
                ProjectTask.STATUS_FAILED_SPRINT);
    }

    public List<ProjectTask> findByProject(int projectId) {
        ensureSchema();
        String sql = "SELECT t.TaskID, sp.ProjectID, t.SprintID, sp.SprintName, sp.StartDate AS SprintStartDate, sp.EndDate AS SprintEndDate, " +
                "t.TaskName, t.Description, t.TaskImage, t.EstimatedPoints, " +
                "t.AssigneeID, ass.FullName AS AssigneeName, ass.StudentCode AS AssigneeCode, " +
                "t.ReviewerID, rev.FullName AS ReviewerName, " +
                "t.Status, t.ActualStartTime, t.ActualEndTime, " +
                "t.SubmissionNote, t.SubmissionUrl, t.SubmittedAt, t.ReviewComment, t.ReviewedAt " +
                "FROM Tasks t " +
                "INNER JOIN Sprints sp ON sp.SprintID = t.SprintID " +
                "INNER JOIN Students ass ON ass.StudentID = t.AssigneeID " +
                "LEFT JOIN Students rev ON rev.StudentID = t.ReviewerID " +
                "WHERE sp.ProjectID = ? " +
                "ORDER BY sp.StartDate DESC, t.TaskID DESC";
        return db.query(sql, (rs, rn) -> mapTask(rs), projectId);
    }

    public List<MemberPerformance> findMemberPerformanceOverallByProject(int projectId) {
        ensureSchema();
        String sql = "SELECT " +
                "ass.StudentID, ass.StudentCode, ass.FullName AS StudentName, " +
                "COUNT(*) AS TotalTasks, " +
                "SUM(CASE WHEN t.Status = ? THEN 1 ELSE 0 END) AS DoneTasks, " +
                "SUM(CASE WHEN t.Status = ? THEN 1 ELSE 0 END) AS FailedTasks, " +
                "SUM(CASE WHEN t.Status = ? THEN 1 ELSE 0 END) AS SubmittedTasks, " +
                "SUM(CASE WHEN t.Status = ? THEN 1 ELSE 0 END) AS InProgressTasks, " +
                "SUM(CASE WHEN t.Status = ? THEN 1 ELSE 0 END) AS TodoTasks " +
                "FROM Tasks t " +
                "INNER JOIN Sprints sp ON sp.SprintID = t.SprintID " +
                "INNER JOIN Students ass ON ass.StudentID = t.AssigneeID " +
                "WHERE sp.ProjectID = ? " +
                "GROUP BY ass.StudentID, ass.StudentCode, ass.FullName " +
                "ORDER BY ass.FullName ASC";
        return db.query(sql, (rs, rn) -> mapPerformance(rs),
                ProjectTask.STATUS_DONE,
                ProjectTask.STATUS_FAILED_SPRINT,
                ProjectTask.STATUS_SUBMITTED,
                ProjectTask.STATUS_IN_PROGRESS,
                ProjectTask.STATUS_TODO,
                projectId);
    }

    public List<MemberPerformance> findMemberPerformanceBySprint(int projectId) {
        ensureSchema();
        String sql = "SELECT " +
                "sp.SprintID, sp.SprintName, ass.StudentID, ass.StudentCode, ass.FullName AS StudentName, " +
                "COUNT(*) AS TotalTasks, " +
                "SUM(CASE WHEN t.Status = ? THEN 1 ELSE 0 END) AS DoneTasks, " +
                "SUM(CASE WHEN t.Status = ? THEN 1 ELSE 0 END) AS FailedTasks, " +
                "SUM(CASE WHEN t.Status = ? THEN 1 ELSE 0 END) AS SubmittedTasks, " +
                "SUM(CASE WHEN t.Status = ? THEN 1 ELSE 0 END) AS InProgressTasks, " +
                "SUM(CASE WHEN t.Status = ? THEN 1 ELSE 0 END) AS TodoTasks " +
                "FROM Tasks t " +
                "INNER JOIN Sprints sp ON sp.SprintID = t.SprintID " +
                "INNER JOIN Students ass ON ass.StudentID = t.AssigneeID " +
                "WHERE sp.ProjectID = ? " +
                "GROUP BY sp.SprintID, sp.SprintName, ass.StudentID, ass.StudentCode, ass.FullName " +
                "ORDER BY sp.SprintID DESC, ass.FullName ASC";
        return db.query(sql, (rs, rn) -> mapPerformance(rs),
                ProjectTask.STATUS_DONE,
                ProjectTask.STATUS_FAILED_SPRINT,
                ProjectTask.STATUS_SUBMITTED,
                ProjectTask.STATUS_IN_PROGRESS,
                ProjectTask.STATUS_TODO,
                projectId);
    }

    private MemberPerformance mapPerformance(java.sql.ResultSet rs) throws java.sql.SQLException {
        MemberPerformance item = new MemberPerformance();
        int sprintId = rs.getInt("SprintID");
        if (!rs.wasNull()) {
            item.setSprintId(sprintId);
        }
        item.setSprintName(rs.getString("SprintName"));
        item.setStudentId(rs.getInt("StudentID"));
        item.setStudentCode(rs.getString("StudentCode"));
        item.setStudentName(rs.getString("StudentName"));
        item.setTotalTasks(rs.getInt("TotalTasks"));
        item.setDoneTasks(rs.getInt("DoneTasks"));
        item.setFailedTasks(rs.getInt("FailedTasks"));
        item.setSubmittedTasks(rs.getInt("SubmittedTasks"));
        item.setInProgressTasks(rs.getInt("InProgressTasks"));
        item.setTodoTasks(rs.getInt("TodoTasks"));
        return item;
    }

    private ProjectTask mapTask(java.sql.ResultSet rs) throws java.sql.SQLException {
        ProjectTask task = new ProjectTask();
        task.setTaskId(rs.getInt("TaskID"));
        task.setProjectId(rs.getInt("ProjectID"));
        task.setSprintId(rs.getInt("SprintID"));
        task.setSprintName(rs.getString("SprintName"));

        java.sql.Date sprintStart = rs.getDate("SprintStartDate");
        if (sprintStart != null) {
            task.setSprintStartDate(sprintStart.toLocalDate());
        }
        java.sql.Date sprintEnd = rs.getDate("SprintEndDate");
        if (sprintEnd != null) {
            task.setSprintEndDate(sprintEnd.toLocalDate());
        }

        task.setTaskName(rs.getString("TaskName"));
        task.setDescription(rs.getString("Description"));
        task.setTaskImage(rs.getString("TaskImage"));
        task.setEstimatedPoints(rs.getDouble("EstimatedPoints"));
        task.setEstimatedHours(rs.getDouble("EstimatedPoints") * 4.0d);
        task.setAssigneeId(rs.getInt("AssigneeID"));
        task.setAssigneeName(rs.getString("AssigneeName"));
        task.setAssigneeCode(rs.getString("AssigneeCode"));

        int reviewerId = rs.getInt("ReviewerID");
        if (!rs.wasNull()) {
            task.setReviewerId(reviewerId);
        }
        task.setReviewerName(rs.getString("ReviewerName"));
        task.setStatus(rs.getInt("Status"));
        task.setSubmissionNote(rs.getString("SubmissionNote"));
        task.setSubmissionUrl(rs.getString("SubmissionUrl"));
        task.setReviewComment(rs.getString("ReviewComment"));

        java.sql.Timestamp actualStartTs = rs.getTimestamp("ActualStartTime");
        if (actualStartTs != null) {
            task.setActualStartTime(actualStartTs.toLocalDateTime());
            long estimatedMinutes = Math.round(task.getEstimatedHours() * 60.0d);
            task.setExpectedEndTime(task.getActualStartTime().plusMinutes(estimatedMinutes));
        }

        java.sql.Timestamp actualEndTs = rs.getTimestamp("ActualEndTime");
        if (actualEndTs != null) {
            task.setActualEndTime(actualEndTs.toLocalDateTime());
        }

        java.sql.Timestamp submittedTs = rs.getTimestamp("SubmittedAt");
        if (submittedTs != null) {
            task.setSubmittedAt(submittedTs.toLocalDateTime());
        }

        java.sql.Timestamp reviewedTs = rs.getTimestamp("ReviewedAt");
        if (reviewedTs != null) {
            task.setReviewedAt(reviewedTs.toLocalDateTime());
        }
        return task;
    }
}
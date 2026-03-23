package com.example.pms.repository;

import com.example.pms.model.Project;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectRepository {

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
            db.execute(
                    "IF COL_LENGTH('Projects', 'StudentCanEdit') IS NULL " +
                            "BEGIN " +
                            "   ALTER TABLE Projects ADD StudentCanEdit BIT NOT NULL CONSTRAINT DF_Projects_StudentCanEdit DEFAULT 0; " +
                            "END");
            db.execute(
                    "IF COL_LENGTH('ProjectTemplates', 'ImageUrl') IS NULL " +
                            "BEGIN " +
                            "   ALTER TABLE ProjectTemplates ADD ImageUrl NVARCHAR(500) NULL; " +
                            "END");
            schemaEnsured = true;
        }
    }

    private Project mapProject(java.sql.ResultSet rs) throws java.sql.SQLException {
        Project p = new Project();
        p.setProjectId(rs.getInt("ProjectID"));
        p.setGroupId(rs.getInt("GroupID"));
        p.setGroupName(rs.getString("GroupName"));

        int classId = rs.getInt("ClassID");
        p.setClassId(rs.wasNull() ? null : classId);
        p.setClassName(rs.getString("ClassName"));

        int semesterId = rs.getInt("SemesterID");
        p.setSemesterId(rs.wasNull() ? null : semesterId);
        p.setSemesterName(rs.getString("SemesterName"));

        int leaderId = rs.getInt("LeaderID");
        p.setLeaderId(rs.wasNull() ? null : leaderId);
        p.setLeaderName(rs.getString("LeaderName"));
        p.setTemplateId(rs.getInt("TemplateID"));

        p.setProjectName(rs.getString("ProjectName"));
        p.setDescription(rs.getString("Description"));
        p.setTopicSource(rs.getString("TopicSource"));
        p.setTemplateImageUrl(rs.getString("TemplateImageUrl"));

        int approvalStatus = rs.getInt("ApprovalStatus");
        p.setApprovalStatus(rs.wasNull() ? 0 : approvalStatus);
        p.setRejectReason(rs.getString("RejectReason"));
        p.setSourceCodeUrl(rs.getString("SourceCodeUrl"));
        p.setDocumentUrl(rs.getString("DocumentUrl"));

        Timestamp submission = rs.getTimestamp("SubmissionDate");
        if (submission != null) {
            p.setSubmissionDate(submission.toLocalDateTime());
        }
        Timestamp startDate = rs.getTimestamp("StartDate");
        if (startDate != null) {
            p.setStartDate(startDate.toLocalDateTime());
        }
        Timestamp endDate = rs.getTimestamp("EndDate");
        if (endDate != null) {
            p.setEndDate(endDate.toLocalDateTime());
        }
        p.setStudentCanEdit(rs.getBoolean("StudentCanEdit"));
        return p;
    }

    private Timestamp toTimestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private String overviewSelect() {
        return "SELECT " +
                "ISNULL(proj.ProjectID, 0) AS ProjectID, " +
                "g.GroupID, g.GroupName, " +
                "g.ClassID, c.ClassName, " +
                "g.SemesterID, sem.SemesterName, " +
                "g.LeaderID, leader.FullName AS LeaderName, " +
                "proj.TemplateID, proj.ProjectName, proj.Description, proj.TopicSource, proj.ApprovalStatus, proj.RejectReason, " +
                "proj.TemplateImageUrl, proj.SourceCodeUrl, proj.DocumentUrl, proj.SubmissionDate, proj.StartDate, proj.EndDate, " +
                "ISNULL(proj.StudentCanEdit, 0) AS StudentCanEdit " +
                "FROM Groups g " +
                "INNER JOIN Classes c ON c.ClassID = g.ClassID " +
                "LEFT JOIN Semesters sem ON sem.SemesterID = g.SemesterID " +
                "LEFT JOIN Students leader ON leader.StudentID = g.LeaderID " +
                "OUTER APPLY ( " +
                "   SELECT TOP 1 p.ProjectID, pt.TemplateID, p.ProjectName, p.Description, pt.Source AS TopicSource, pt.ImageUrl AS TemplateImageUrl, " +
                "          p.ApprovalStatus, p.RejectReason, p.SourceCodeUrl, p.DocumentUrl, p.SubmissionDate, " +
                "          pa.StartDate, pa.EndDate, p.StudentCanEdit " +
                "   FROM ProjectAssignments pa " +
                "   LEFT JOIN Projects p ON p.AssignmentID = pa.AssignmentID " +
                "   LEFT JOIN ProjectTemplates pt ON pt.TemplateID = pa.TemplateID " +
                "   WHERE pa.GroupID = g.GroupID " +
                "   ORDER BY CASE WHEN p.ProjectID IS NULL THEN 1 ELSE 0 END, p.ProjectID DESC, pa.AssignmentID DESC " +
                ") proj ";
    }

    private String projectSelect() {
        return "SELECT " +
                "p.ProjectID, g.GroupID, g.GroupName, " +
                "g.ClassID, c.ClassName, " +
                "g.SemesterID, sem.SemesterName, " +
                "g.LeaderID, leader.FullName AS LeaderName, " +
                "pt.TemplateID, p.ProjectName, p.Description, pt.Source AS TopicSource, p.ApprovalStatus, p.RejectReason, " +
                "pt.ImageUrl AS TemplateImageUrl, p.SourceCodeUrl, p.DocumentUrl, p.SubmissionDate, pa.StartDate, pa.EndDate, " +
                "ISNULL(p.StudentCanEdit, 0) AS StudentCanEdit " +
                "FROM Projects p " +
                "INNER JOIN ProjectAssignments pa ON pa.AssignmentID = p.AssignmentID " +
                "INNER JOIN Groups g ON g.GroupID = pa.GroupID " +
                "INNER JOIN Classes c ON c.ClassID = g.ClassID " +
                "LEFT JOIN Semesters sem ON sem.SemesterID = g.SemesterID " +
                "LEFT JOIN Students leader ON leader.StudentID = g.LeaderID " +
                "LEFT JOIN ProjectTemplates pt ON pt.TemplateID = pa.TemplateID ";
    }

    private Integer findTemplateIdForSource(String topicSource) {
        try {
            String normalizedSource = topicSource == null ? null : topicSource.trim().toUpperCase();
            if (normalizedSource == null || normalizedSource.isEmpty()) {
                return db.queryForObject(
                        "SELECT TOP 1 TemplateID FROM ProjectTemplates WHERE IsActive = 1 ORDER BY Version DESC, TemplateID DESC",
                        Integer.class);
            }
            Integer templateId = db.queryForObject(
                    "SELECT TOP 1 TemplateID FROM ProjectTemplates WHERE IsActive = 1 AND UPPER(Source) = ? ORDER BY Version DESC, TemplateID DESC",
                    Integer.class,
                    normalizedSource);
            if (templateId != null) {
                return templateId;
            }
            return db.queryForObject(
                    "SELECT TOP 1 TemplateID FROM ProjectTemplates WHERE IsActive = 1 ORDER BY Version DESC, TemplateID DESC",
                    Integer.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private Timestamp resolveFallbackTimestamp(int groupId, boolean startDate) {
        try {
            String sql = startDate
                    ? "SELECT TOP 1 COALESCE(sem.StartDate, c.StartDate) FROM Groups g INNER JOIN Classes c ON c.ClassID = g.ClassID LEFT JOIN Semesters sem ON sem.SemesterID = g.SemesterID WHERE g.GroupID = ?"
                    : "SELECT TOP 1 COALESCE(sem.EndDate, c.EndDate) FROM Groups g INNER JOIN Classes c ON c.ClassID = g.ClassID LEFT JOIN Semesters sem ON sem.SemesterID = g.SemesterID WHERE g.GroupID = ?";
            Date date = db.queryForObject(sql, Date.class, groupId);
            return date == null ? Timestamp.valueOf(LocalDateTime.now()) : Timestamp.valueOf(date.toLocalDate().atStartOfDay());
        } catch (Exception ex) {
            return Timestamp.valueOf(LocalDateTime.now());
        }
    }

    private Integer findOpenAssignmentId(int groupId) {
        try {
            String sql = "SELECT TOP 1 pa.AssignmentID " +
                    "FROM ProjectAssignments pa " +
                    "LEFT JOIN Projects p ON p.AssignmentID = pa.AssignmentID " +
                    "WHERE pa.GroupID = ? AND p.ProjectID IS NULL " +
                    "ORDER BY pa.AssignmentID DESC";
            return db.queryForObject(sql, Integer.class, groupId);
        } catch (Exception ex) {
            return null;
        }
    }

    private Integer createAssignment(int groupId, String topicSource, LocalDateTime startDate, LocalDateTime endDate) {
        Integer templateId = findTemplateIdForSource(topicSource);
        if (templateId == null) {
            return null;
        }
        Timestamp start = startDate != null ? toTimestamp(startDate) : resolveFallbackTimestamp(groupId, true);
        Timestamp end = endDate != null ? toTimestamp(endDate) : resolveFallbackTimestamp(groupId, false);
        try {
            String sql = "INSERT INTO ProjectAssignments (TemplateID, GroupID, StartDate, EndDate, Status, AssignedAt) " +
                    "OUTPUT INSERTED.AssignmentID VALUES (?, ?, ?, ?, 'ASSIGNED', GETDATE())";
            return db.queryForObject(sql, Integer.class, templateId, groupId, start, end);
        } catch (Exception ex) {
            return null;
        }
    }

    public List<Project> findProjectOverviewBySemester(int semesterId) {
        ensureSchema();
        String sql = overviewSelect() +
                "WHERE g.SemesterID = ? AND g.IsLocked = 0 " +
                "ORDER BY c.ClassName ASC, g.GroupName ASC";
        return db.query(sql, (rs, rn) -> mapProject(rs), semesterId);
    }

    public Project findByGroupId(int groupId) {
        ensureSchema();
        try {
            String sql = projectSelect().replaceFirst("^SELECT ", "SELECT TOP 1 ") +
                    "WHERE g.GroupID = ? AND p.ProjectID IS NOT NULL " +
                    "ORDER BY pa.AssignmentID DESC";
            return db.queryForObject(sql, (rs, rn) -> mapProject(rs), groupId);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public Project findById(int projectId) {
        ensureSchema();
        try {
            String sql = projectSelect() +
                    "WHERE p.ProjectID = ?";
            return db.queryForObject(sql, (rs, rn) -> mapProject(rs), projectId);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public int createProjectByStaff(int groupId,
            String projectName,
            String description,
            String topicSource,
            int approvalStatus,
            LocalDateTime startDate,
            LocalDateTime endDate,
            boolean studentCanEdit) {
        ensureSchema();
        try {
            Integer assignmentId = findOpenAssignmentId(groupId);
            if (assignmentId == null) {
                assignmentId = createAssignment(groupId, topicSource, startDate, endDate);
            }
            if (assignmentId == null) {
                return -1;
            }
            String sql = "INSERT INTO Projects (AssignmentID, ProjectName, Description, ApprovalStatus, RejectReason, StudentCanEdit, SourceCodeUrl, DocumentUrl, SubmissionDate, CreatedAt) " +
                    "OUTPUT INSERTED.ProjectID VALUES (?, ?, ?, ?, NULL, ?, NULL, NULL, NULL, GETDATE())";
            Integer projectId = db.queryForObject(sql, Integer.class,
                    assignmentId,
                    projectName,
                    description,
                    approvalStatus,
                    studentCanEdit ? 1 : 0);
            return projectId != null ? projectId : -1;
        } catch (Exception ex) {
            return -1;
        }
    }

    public int createProjectFromAssignment(int assignmentId,
            String projectName,
            String description,
            int approvalStatus,
            boolean studentCanEdit) {
        ensureSchema();
        try {
            String sql = "INSERT INTO Projects (AssignmentID, ProjectName, Description, ApprovalStatus, RejectReason, StudentCanEdit, SourceCodeUrl, DocumentUrl, SubmissionDate, CreatedAt) " +
                    "OUTPUT INSERTED.ProjectID VALUES (?, ?, ?, ?, NULL, ?, NULL, NULL, NULL, GETDATE())";
            Integer projectId = db.queryForObject(sql, Integer.class,
                    assignmentId,
                    projectName,
                    description,
                    approvalStatus,
                    studentCanEdit ? 1 : 0);
            return projectId != null ? projectId : -1;
        } catch (Exception ex) {
            return -1;
        }
    }

    public int updateStudentContent(int projectId,
            String projectName,
            String description,
            String sourceCodeUrl,
            String documentUrl) {
        ensureSchema();
        try {
            String sql = "UPDATE Projects " +
                    "SET ProjectName = ?, Description = ?, SourceCodeUrl = ?, DocumentUrl = ? " +
                    "WHERE ProjectID = ?";
            return db.update(sql, projectName, description, sourceCodeUrl, documentUrl, projectId);
        } catch (Exception ex) {
            return 0;
        }
    }

    public int updateFinalLinks(int projectId, String sourceCodeUrl, String documentUrl) {
        ensureSchema();
        try {
            String sql = "UPDATE Projects SET SourceCodeUrl = ?, DocumentUrl = ? WHERE ProjectID = ?";
            return db.update(sql, sourceCodeUrl, documentUrl, projectId);
        } catch (Exception ex) {
            return 0;
        }
    }

    public int submitForLecturerReview(int projectId) {
        ensureSchema();
        try {
            String sql = "UPDATE Projects " +
                    "SET ApprovalStatus = ?, RejectReason = NULL, SubmissionDate = GETDATE(), StudentCanEdit = 0 " +
                    "WHERE ProjectID = ?";
            return db.update(sql, Project.STATUS_PENDING_LECTURER, projectId);
        } catch (Exception ex) {
            return 0;
        }
    }

    public int approveByLecturer(int projectId, LocalDateTime startDate, LocalDateTime endDate) {
        ensureSchema();
        try {
            int updatedProject = db.update(
                    "UPDATE Projects SET ApprovalStatus = ?, RejectReason = NULL, StudentCanEdit = 0 WHERE ProjectID = ?",
                    Project.STATUS_APPROVED,
                    projectId);
            db.update(
                    "UPDATE pa SET StartDate = ?, EndDate = ? " +
                            "FROM ProjectAssignments pa " +
                            "INNER JOIN Projects p ON p.AssignmentID = pa.AssignmentID " +
                            "WHERE p.ProjectID = ?",
                    toTimestamp(startDate),
                    toTimestamp(endDate),
                    projectId);
            return updatedProject;
        } catch (Exception ex) {
            return 0;
        }
    }

    public int rejectByLecturer(int projectId, String reason) {
        ensureSchema();
        try {
            String sql = "UPDATE p SET ApprovalStatus = ?, RejectReason = ?, StudentCanEdit = CASE WHEN UPPER(ISNULL(pt.Source, '')) = 'STUDENT' THEN 1 ELSE 0 END " +
                    "FROM Projects p " +
                    "INNER JOIN ProjectAssignments pa ON pa.AssignmentID = p.AssignmentID " +
                    "LEFT JOIN ProjectTemplates pt ON pt.TemplateID = pa.TemplateID " +
                    "WHERE p.ProjectID = ?";
            return db.update(sql, Project.STATUS_REJECTED, reason, projectId);
        } catch (Exception ex) {
            return 0;
        }
    }

    public int setStudentCanEdit(int projectId, boolean canEdit) {
        ensureSchema();
        try {
            String sql = "UPDATE Projects SET StudentCanEdit = ? WHERE ProjectID = ?";
            return db.update(sql, canEdit ? 1 : 0, projectId);
        } catch (Exception ex) {
            return 0;
        }
    }

    public int applyApprovedChange(int projectId,
            String projectName,
            String description,
            LocalDateTime startDate,
            LocalDateTime endDate) {
        ensureSchema();
        try {
            int updatedProject = db.update("UPDATE Projects SET " +
                            "ProjectName = ?, Description = ?, ApprovalStatus = ?, RejectReason = NULL, " +
                            "SourceCodeUrl = NULL, DocumentUrl = NULL, SubmissionDate = NULL, StudentCanEdit = 0 " +
                            "WHERE ProjectID = ?",
                    projectName,
                    description,
                    Project.STATUS_APPROVED,
                    projectId);
            db.update(
                    "UPDATE pa SET StartDate = ?, EndDate = ? " +
                            "FROM ProjectAssignments pa " +
                            "INNER JOIN Projects p ON p.AssignmentID = pa.AssignmentID " +
                            "WHERE p.ProjectID = ?",
                    toTimestamp(startDate),
                    toTimestamp(endDate),
                    projectId);
            return updatedProject;
        } catch (Exception ex) {
            return 0;
        }
    }

    public List<Project> findPendingForLecturer(int lecturerId) {
        ensureSchema();
        String sql = "SELECT " +
                "p.ProjectID, g.GroupID, g.GroupName, " +
                "g.ClassID, c.ClassName, " +
                "g.SemesterID, sem.SemesterName, " +
                "g.LeaderID, leader.FullName AS LeaderName, " +
                "p.ProjectName, p.Description, pt.Source AS TopicSource, p.ApprovalStatus, p.RejectReason, " +
                "p.SourceCodeUrl, p.DocumentUrl, p.SubmissionDate, pa.StartDate, pa.EndDate, " +
                "ISNULL(p.StudentCanEdit, 0) AS StudentCanEdit " +
                "FROM Projects p " +
                "INNER JOIN ProjectAssignments pa ON pa.AssignmentID = p.AssignmentID " +
                "INNER JOIN Groups g ON g.GroupID = pa.GroupID " +
                "INNER JOIN Classes c ON c.ClassID = g.ClassID " +
                "LEFT JOIN Semesters sem ON sem.SemesterID = g.SemesterID " +
                "LEFT JOIN Students leader ON leader.StudentID = g.LeaderID " +
                "LEFT JOIN ProjectTemplates pt ON pt.TemplateID = pa.TemplateID " +
                "INNER JOIN (SELECT DISTINCT ClassID, SemesterID FROM Class_Lecturers WHERE LecturerID = ?) cl " +
                "ON cl.ClassID = g.ClassID AND cl.SemesterID = g.SemesterID " +
                "WHERE p.ApprovalStatus = ? " +
                "ORDER BY p.SubmissionDate DESC";
        return db.query(sql, (rs, rn) -> mapProject(rs), lecturerId, Project.STATUS_PENDING_LECTURER);
    }

    public List<Project> findApprovedForLecturer(int lecturerId) {
        ensureSchema();
        String sql = "SELECT " +
                "p.ProjectID, g.GroupID, g.GroupName, " +
                "g.ClassID, c.ClassName, " +
                "g.SemesterID, sem.SemesterName, " +
                "g.LeaderID, leader.FullName AS LeaderName, " +
                "p.ProjectName, p.Description, pt.Source AS TopicSource, p.ApprovalStatus, p.RejectReason, " +
                "p.SourceCodeUrl, p.DocumentUrl, p.SubmissionDate, pa.StartDate, pa.EndDate, " +
                "ISNULL(p.StudentCanEdit, 0) AS StudentCanEdit " +
                "FROM Projects p " +
                "INNER JOIN ProjectAssignments pa ON pa.AssignmentID = p.AssignmentID " +
                "INNER JOIN Groups g ON g.GroupID = pa.GroupID " +
                "INNER JOIN Classes c ON c.ClassID = g.ClassID " +
                "LEFT JOIN Semesters sem ON sem.SemesterID = g.SemesterID " +
                "LEFT JOIN Students leader ON leader.StudentID = g.LeaderID " +
                "LEFT JOIN ProjectTemplates pt ON pt.TemplateID = pa.TemplateID " +
                "INNER JOIN (SELECT DISTINCT ClassID, SemesterID FROM Class_Lecturers WHERE LecturerID = ?) cl " +
                "ON cl.ClassID = g.ClassID AND cl.SemesterID = g.SemesterID " +
                "WHERE p.ApprovalStatus = ? " +
                "ORDER BY ISNULL(pa.EndDate, pa.StartDate) DESC, p.ProjectID DESC";
        return db.query(sql, (rs, rn) -> mapProject(rs), lecturerId, Project.STATUS_APPROVED);
    }

    public boolean canLecturerAccessProject(int lecturerId, int projectId) {
        ensureSchema();
        String sql = "SELECT COUNT(*) " +
                "FROM Projects p " +
                "INNER JOIN ProjectAssignments pa ON pa.AssignmentID = p.AssignmentID " +
                "INNER JOIN Groups g ON g.GroupID = pa.GroupID " +
                "INNER JOIN Class_Lecturers cl ON cl.ClassID = g.ClassID AND cl.SemesterID = g.SemesterID " +
                "WHERE cl.LecturerID = ? AND p.ProjectID = ?";
        Integer count = db.queryForObject(sql, Integer.class, lecturerId, projectId);
        return count != null && count > 0;
    }

    public List<String> findLecturerEmailsForProject(int projectId) {
        ensureSchema();
        String sql = "SELECT DISTINCT l.SchoolEmail " +
                "FROM Projects p " +
                "INNER JOIN ProjectAssignments pa ON pa.AssignmentID = p.AssignmentID " +
                "INNER JOIN Groups g ON g.GroupID = pa.GroupID " +
                "INNER JOIN Class_Lecturers cl ON cl.ClassID = g.ClassID AND cl.SemesterID = g.SemesterID " +
                "INNER JOIN Lecturers l ON l.LecturerID = cl.LecturerID " +
                "WHERE p.ProjectID = ? AND l.SchoolEmail IS NOT NULL";
        return db.query(sql, (rs, rn) -> rs.getString("SchoolEmail"), projectId);
    }
}

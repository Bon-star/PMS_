package com.example.pms.repository;

import com.example.pms.model.Project;
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

        p.setProjectName(rs.getString("ProjectName"));
        p.setDescription(rs.getString("Description"));
        p.setTopicSource(rs.getString("TopicSource"));

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

    public List<Project> findProjectOverviewBySemester(int semesterId) {
        ensureSchema();
        String sql = "SELECT " +
                "ISNULL(p.ProjectID, 0) AS ProjectID, " +
                "g.GroupID, g.GroupName, " +
                "g.ClassID, c.ClassName, " +
                "g.SemesterID, sem.SemesterName, " +
                "g.LeaderID, leader.FullName AS LeaderName, " +
                "p.ProjectName, p.Description, p.TopicSource, p.ApprovalStatus, p.RejectReason, " +
                "p.SourceCodeUrl, p.DocumentUrl, p.SubmissionDate, p.StartDate, p.EndDate, " +
                "ISNULL(p.StudentCanEdit, 0) AS StudentCanEdit " +
                "FROM Groups g " +
                "INNER JOIN Classes c ON c.ClassID = g.ClassID " +
                "LEFT JOIN Semesters sem ON sem.SemesterID = g.SemesterID " +
                "LEFT JOIN Students leader ON leader.StudentID = g.LeaderID " +
                "LEFT JOIN Projects p ON p.GroupID = g.GroupID " +
                "WHERE g.SemesterID = ? AND g.IsLocked = 0 " +
                "ORDER BY c.ClassName ASC, g.GroupName ASC";
        return db.query(sql, (rs, rn) -> mapProject(rs), semesterId);
    }

    public Project findByGroupId(int groupId) {
        ensureSchema();
        try {
            String sql = "SELECT " +
                    "p.ProjectID, g.GroupID, g.GroupName, " +
                    "g.ClassID, c.ClassName, " +
                    "g.SemesterID, sem.SemesterName, " +
                    "g.LeaderID, leader.FullName AS LeaderName, " +
                    "p.ProjectName, p.Description, p.TopicSource, p.ApprovalStatus, p.RejectReason, " +
                    "p.SourceCodeUrl, p.DocumentUrl, p.SubmissionDate, p.StartDate, p.EndDate, " +
                    "ISNULL(p.StudentCanEdit, 0) AS StudentCanEdit " +
                    "FROM Projects p " +
                    "INNER JOIN Groups g ON g.GroupID = p.GroupID " +
                    "INNER JOIN Classes c ON c.ClassID = g.ClassID " +
                    "LEFT JOIN Semesters sem ON sem.SemesterID = g.SemesterID " +
                    "LEFT JOIN Students leader ON leader.StudentID = g.LeaderID " +
                    "WHERE p.GroupID = ?";
            return db.queryForObject(sql, (rs, rn) -> mapProject(rs), groupId);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public Project findById(int projectId) {
        ensureSchema();
        try {
            String sql = "SELECT " +
                    "p.ProjectID, g.GroupID, g.GroupName, " +
                    "g.ClassID, c.ClassName, " +
                    "g.SemesterID, sem.SemesterName, " +
                    "g.LeaderID, leader.FullName AS LeaderName, " +
                    "p.ProjectName, p.Description, p.TopicSource, p.ApprovalStatus, p.RejectReason, " +
                    "p.SourceCodeUrl, p.DocumentUrl, p.SubmissionDate, p.StartDate, p.EndDate, " +
                    "ISNULL(p.StudentCanEdit, 0) AS StudentCanEdit " +
                    "FROM Projects p " +
                    "INNER JOIN Groups g ON g.GroupID = p.GroupID " +
                    "INNER JOIN Classes c ON c.ClassID = g.ClassID " +
                    "LEFT JOIN Semesters sem ON sem.SemesterID = g.SemesterID " +
                    "LEFT JOIN Students leader ON leader.StudentID = g.LeaderID " +
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
            String sql = "INSERT INTO Projects (GroupID, ProjectName, Description, TopicSource, ApprovalStatus, " +
                    "RejectReason, SourceCodeUrl, DocumentUrl, SubmissionDate, StartDate, EndDate, StudentCanEdit) " +
                    "OUTPUT INSERTED.ProjectID VALUES (?, ?, ?, ?, ?, NULL, NULL, NULL, NULL, ?, ?, ?)";
            Integer projectId = db.queryForObject(sql, Integer.class,
                    groupId,
                    projectName,
                    description,
                    topicSource,
                    approvalStatus,
                    toTimestamp(startDate),
                    toTimestamp(endDate),
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
            String sql = "UPDATE Projects " +
                    "SET ApprovalStatus = ?, RejectReason = NULL, StartDate = ?, EndDate = ?, StudentCanEdit = 0 " +
                    "WHERE ProjectID = ?";
            return db.update(sql, Project.STATUS_APPROVED, toTimestamp(startDate), toTimestamp(endDate), projectId);
        } catch (Exception ex) {
            return 0;
        }
    }

    public int rejectByLecturer(int projectId, String reason) {
        ensureSchema();
        try {
            String sql = "UPDATE Projects " +
                    "SET ApprovalStatus = ?, RejectReason = ?, StartDate = NULL, EndDate = NULL, " +
                    "StudentCanEdit = CASE WHEN UPPER(ISNULL(TopicSource, '')) = 'STUDENT' THEN 1 ELSE 0 END " +
                    "WHERE ProjectID = ?";
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
            String sql = "UPDATE Projects SET " +
                    "ProjectName = ?, Description = ?, ApprovalStatus = ?, RejectReason = NULL, " +
                    "SourceCodeUrl = NULL, DocumentUrl = NULL, SubmissionDate = NULL, " +
                    "StartDate = ?, EndDate = ?, StudentCanEdit = 0 " +
                    "WHERE ProjectID = ?";
            return db.update(sql,
                    projectName,
                    description,
                    Project.STATUS_APPROVED,
                    toTimestamp(startDate),
                    toTimestamp(endDate),
                    projectId);
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
                "p.ProjectName, p.Description, p.TopicSource, p.ApprovalStatus, p.RejectReason, " +
                "p.SourceCodeUrl, p.DocumentUrl, p.SubmissionDate, p.StartDate, p.EndDate, " +
                "ISNULL(p.StudentCanEdit, 0) AS StudentCanEdit " +
                "FROM Projects p " +
                "INNER JOIN Groups g ON g.GroupID = p.GroupID " +
                "INNER JOIN Classes c ON c.ClassID = g.ClassID " +
                "LEFT JOIN Semesters sem ON sem.SemesterID = g.SemesterID " +
                "LEFT JOIN Students leader ON leader.StudentID = g.LeaderID " +
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
                "p.ProjectName, p.Description, p.TopicSource, p.ApprovalStatus, p.RejectReason, " +
                "p.SourceCodeUrl, p.DocumentUrl, p.SubmissionDate, p.StartDate, p.EndDate, " +
                "ISNULL(p.StudentCanEdit, 0) AS StudentCanEdit " +
                "FROM Projects p " +
                "INNER JOIN Groups g ON g.GroupID = p.GroupID " +
                "INNER JOIN Classes c ON c.ClassID = g.ClassID " +
                "LEFT JOIN Semesters sem ON sem.SemesterID = g.SemesterID " +
                "LEFT JOIN Students leader ON leader.StudentID = g.LeaderID " +
                "INNER JOIN (SELECT DISTINCT ClassID, SemesterID FROM Class_Lecturers WHERE LecturerID = ?) cl " +
                "ON cl.ClassID = g.ClassID AND cl.SemesterID = g.SemesterID " +
                "WHERE p.ApprovalStatus = ? " +
                "ORDER BY ISNULL(p.EndDate, p.StartDate) DESC, p.ProjectID DESC";
        return db.query(sql, (rs, rn) -> mapProject(rs), lecturerId, Project.STATUS_APPROVED);
    }

    public boolean canLecturerAccessProject(int lecturerId, int projectId) {
        ensureSchema();
        String sql = "SELECT COUNT(*) " +
                "FROM Projects p " +
                "INNER JOIN Groups g ON g.GroupID = p.GroupID " +
                "INNER JOIN Class_Lecturers cl ON cl.ClassID = g.ClassID AND cl.SemesterID = g.SemesterID " +
                "WHERE cl.LecturerID = ? AND p.ProjectID = ?";
        Integer count = db.queryForObject(sql, Integer.class, lecturerId, projectId);
        return count != null && count > 0;
    }

    public List<String> findLecturerEmailsForProject(int projectId) {
        ensureSchema();
        String sql = "SELECT DISTINCT l.SchoolEmail " +
                "FROM Projects p " +
                "INNER JOIN Groups g ON g.GroupID = p.GroupID " +
                "INNER JOIN Class_Lecturers cl ON cl.ClassID = g.ClassID AND cl.SemesterID = g.SemesterID " +
                "INNER JOIN Lecturers l ON l.LecturerID = cl.LecturerID " +
                "WHERE p.ProjectID = ? AND l.SchoolEmail IS NOT NULL";
        return db.query(sql, (rs, rn) -> rs.getString("SchoolEmail"), projectId);
    }
}

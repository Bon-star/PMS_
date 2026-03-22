package com.example.pms.repository;

import com.example.pms.model.ProjectEditRequest;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectEditRequestRepository {

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
                    "IF OBJECT_ID('dbo.Project_Edit_Requests', 'U') IS NULL " +
                            "BEGIN " +
                            "   CREATE TABLE Project_Edit_Requests ( " +
                            "       RequestID INT IDENTITY(1,1) PRIMARY KEY, " +
                            "       ProjectID INT NOT NULL, " +
                            "       RequestedByStudentID INT NOT NULL, " +
                            "       RequestNote NVARCHAR(MAX) NULL, " +
                            "       Status VARCHAR(20) NOT NULL DEFAULT 'PENDING', " +
                            "       ResponseReason NVARCHAR(MAX) NULL, " +
                            "       RespondedByStaffID INT NULL, " +
                            "       RequestedDate DATETIME NOT NULL DEFAULT GETDATE(), " +
                            "       RespondedDate DATETIME NULL, " +
                            "       CONSTRAINT FK_ProjectEditReq_Project FOREIGN KEY (ProjectID) REFERENCES Projects(ProjectID), " +
                            "       CONSTRAINT FK_ProjectEditReq_Student FOREIGN KEY (RequestedByStudentID) REFERENCES Students(StudentID), " +
                            "       CONSTRAINT FK_ProjectEditReq_Staff FOREIGN KEY (RespondedByStaffID) REFERENCES Staff(StaffID) " +
                            "   ); " +
                            "   CREATE INDEX IX_ProjectEditReq_Status ON Project_Edit_Requests(Status, RequestedDate DESC); " +
                            "END");
            schemaEnsured = true;
        }
    }

    private ProjectEditRequest mapRequest(java.sql.ResultSet rs) throws java.sql.SQLException {
        ProjectEditRequest r = new ProjectEditRequest();
        r.setRequestId(rs.getInt("RequestID"));
        r.setProjectId(rs.getInt("ProjectID"));
        r.setGroupId(rs.getInt("GroupID"));
        r.setGroupName(rs.getString("GroupName"));
        r.setRequestedByStudentId(rs.getInt("RequestedByStudentID"));
        r.setRequestedByName(rs.getString("RequestedByName"));
        r.setRequestedByCode(rs.getString("RequestedByCode"));
        r.setRequestNote(rs.getString("RequestNote"));
        r.setStatus(rs.getString("Status"));
        r.setResponseReason(rs.getString("ResponseReason"));

        int respondedBy = rs.getInt("RespondedByStaffID");
        if (!rs.wasNull()) {
            r.setRespondedByStaffId(respondedBy);
        }

        Timestamp requested = rs.getTimestamp("RequestedDate");
        if (requested != null) {
            r.setRequestedDate(requested.toLocalDateTime());
        }

        Timestamp responded = rs.getTimestamp("RespondedDate");
        if (responded != null) {
            r.setRespondedDate(responded.toLocalDateTime());
        }
        return r;
    }

    public boolean existsPendingByProjectAndStudent(int projectId, int studentId) {
        ensureSchema();
        String sql = "SELECT COUNT(*) FROM Project_Edit_Requests " +
                "WHERE ProjectID = ? AND RequestedByStudentID = ? AND Status = 'PENDING'";
        Integer count = db.queryForObject(sql, Integer.class, projectId, studentId);
        return count != null && count > 0;
    }

    public int createRequest(int projectId, int studentId, String requestNote) {
        ensureSchema();
        try {
            String sql = "INSERT INTO Project_Edit_Requests (ProjectID, RequestedByStudentID, RequestNote, Status, RequestedDate) " +
                    "OUTPUT INSERTED.RequestID VALUES (?, ?, ?, 'PENDING', GETDATE())";
            Integer requestId = db.queryForObject(sql, Integer.class, projectId, studentId, requestNote);
            return requestId != null ? requestId : -1;
        } catch (Exception ex) {
            return -1;
        }
    }

    public ProjectEditRequest findLatestByProject(int projectId) {
        ensureSchema();
        try {
            String sql = "SELECT TOP 1 r.*, g.GroupID, g.GroupName, s.FullName AS RequestedByName, s.StudentCode AS RequestedByCode " +
                    "FROM Project_Edit_Requests r " +
                    "INNER JOIN Projects p ON p.ProjectID = r.ProjectID " +
                    "INNER JOIN ProjectAssignments pa ON pa.AssignmentID = p.AssignmentID " +
                    "INNER JOIN Groups g ON g.GroupID = pa.GroupID " +
                    "INNER JOIN Students s ON s.StudentID = r.RequestedByStudentID " +
                    "WHERE r.ProjectID = ? " +
                    "ORDER BY r.RequestedDate DESC";
            return db.queryForObject(sql, (rs, rn) -> mapRequest(rs), projectId);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public List<ProjectEditRequest> findPendingRequests() {
        ensureSchema();
        String sql = "SELECT r.*, g.GroupID, g.GroupName, s.FullName AS RequestedByName, s.StudentCode AS RequestedByCode " +
                "FROM Project_Edit_Requests r " +
                "INNER JOIN Projects p ON p.ProjectID = r.ProjectID " +
            "INNER JOIN ProjectAssignments pa ON pa.AssignmentID = p.AssignmentID " +
            "INNER JOIN Groups g ON g.GroupID = pa.GroupID " +
                "INNER JOIN Students s ON s.StudentID = r.RequestedByStudentID " +
                "WHERE r.Status = 'PENDING' " +
                "ORDER BY r.RequestedDate DESC";
        return db.query(sql, (rs, rn) -> mapRequest(rs));
    }

    public ProjectEditRequest findById(int requestId) {
        ensureSchema();
        try {
            String sql = "SELECT r.*, g.GroupID, g.GroupName, s.FullName AS RequestedByName, s.StudentCode AS RequestedByCode " +
                    "FROM Project_Edit_Requests r " +
                    "INNER JOIN Projects p ON p.ProjectID = r.ProjectID " +
                    "INNER JOIN ProjectAssignments pa ON pa.AssignmentID = p.AssignmentID " +
                    "INNER JOIN Groups g ON g.GroupID = pa.GroupID " +
                    "INNER JOIN Students s ON s.StudentID = r.RequestedByStudentID " +
                    "WHERE r.RequestID = ?";
            return db.queryForObject(sql, (rs, rn) -> mapRequest(rs), requestId);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public int approve(int requestId, int staffId) {
        ensureSchema();
        String sql = "UPDATE Project_Edit_Requests " +
                "SET Status = 'APPROVED', ResponseReason = NULL, RespondedByStaffID = ?, RespondedDate = GETDATE() " +
                "WHERE RequestID = ? AND Status = 'PENDING'";
        return db.update(sql, staffId, requestId);
    }

    public int reject(int requestId, int staffId, String reason) {
        ensureSchema();
        String sql = "UPDATE Project_Edit_Requests " +
                "SET Status = 'REJECTED', ResponseReason = ?, RespondedByStaffID = ?, RespondedDate = GETDATE() " +
                "WHERE RequestID = ? AND Status = 'PENDING'";
        return db.update(sql, reason, staffId, requestId);
    }
}

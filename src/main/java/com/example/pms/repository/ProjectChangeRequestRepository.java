package com.example.pms.repository;

import com.example.pms.model.ProjectChangeRequest;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectChangeRequestRepository {

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
                    "IF OBJECT_ID('dbo.Project_Change_Requests', 'U') IS NULL " +
                            "BEGIN " +
                            "   CREATE TABLE dbo.Project_Change_Requests ( " +
                            "       RequestID INT IDENTITY(1,1) PRIMARY KEY, " +
                            "       ProjectID INT NOT NULL, " +
                            "       RequestedByStudentID INT NOT NULL, " +
                            "       ProposedProjectName NVARCHAR(200) NOT NULL, " +
                            "       ProposedDescription NVARCHAR(MAX) NULL, " +
                            "       ChangeReason NVARCHAR(MAX) NOT NULL, " +
                            "       Status VARCHAR(30) NOT NULL DEFAULT 'PENDING_STAFF', " +
                            "       StaffReviewedByStaffID INT NULL, " +
                            "       StaffRejectReason NVARCHAR(MAX) NULL, " +
                            "       StaffReviewedAt DATETIME NULL, " +
                            "       LecturerReviewedByLecturerID INT NULL, " +
                            "       LecturerRejectReason NVARCHAR(MAX) NULL, " +
                            "       LecturerReviewedAt DATETIME NULL, " +
                            "       RequestedDate DATETIME NOT NULL DEFAULT GETDATE(), " +
                            "       CONSTRAINT FK_ProjectChangeReq_Project FOREIGN KEY (ProjectID) REFERENCES Projects(ProjectID), " +
                            "       CONSTRAINT FK_ProjectChangeReq_Student FOREIGN KEY (RequestedByStudentID) REFERENCES Students(StudentID), " +
                            "       CONSTRAINT FK_ProjectChangeReq_Staff FOREIGN KEY (StaffReviewedByStaffID) REFERENCES Staff(StaffID), " +
                            "       CONSTRAINT FK_ProjectChangeReq_Lecturer FOREIGN KEY (LecturerReviewedByLecturerID) REFERENCES Lecturers(LecturerID) " +
                            "   ); " +
                            "END");
            if (db.queryForObject(
                    "SELECT COUNT(*) FROM sys.indexes WHERE name = 'IX_ProjectChangeReq_Status' AND object_id = OBJECT_ID('dbo.Project_Change_Requests')",
                    Integer.class) == 0) {
                db.execute("CREATE INDEX IX_ProjectChangeReq_Status ON dbo.Project_Change_Requests(Status, RequestedDate DESC);");
            }
            if (db.queryForObject(
                    "SELECT COUNT(*) FROM sys.indexes WHERE name = 'IX_ProjectChangeReq_Project' AND object_id = OBJECT_ID('dbo.Project_Change_Requests')",
                    Integer.class) == 0) {
                db.execute("CREATE INDEX IX_ProjectChangeReq_Project ON dbo.Project_Change_Requests(ProjectID, RequestedDate DESC);");
            }
            schemaEnsured = true;
        }
    }

    private ProjectChangeRequest mapRequest(java.sql.ResultSet rs) throws java.sql.SQLException {
        ProjectChangeRequest item = new ProjectChangeRequest();
        item.setRequestId(rs.getInt("RequestID"));
        item.setProjectId(rs.getInt("ProjectID"));
        item.setGroupId(rs.getInt("GroupID"));
        item.setGroupName(rs.getString("GroupName"));
        item.setClassName(rs.getString("ClassName"));
        item.setCurrentProjectName(rs.getString("CurrentProjectName"));
        item.setRequestedByStudentId(rs.getInt("RequestedByStudentID"));
        item.setRequestedByName(rs.getString("RequestedByName"));
        item.setRequestedByCode(rs.getString("RequestedByCode"));
        item.setProposedProjectName(rs.getString("ProposedProjectName"));
        item.setProposedDescription(rs.getString("ProposedDescription"));
        item.setChangeReason(rs.getString("ChangeReason"));
        item.setStatus(rs.getString("Status"));
        item.setStaffRejectReason(rs.getString("StaffRejectReason"));
        item.setLecturerRejectReason(rs.getString("LecturerRejectReason"));

        int staffId = rs.getInt("StaffReviewedByStaffID");
        if (!rs.wasNull()) {
            item.setStaffReviewedByStaffId(staffId);
        }
        int lecturerId = rs.getInt("LecturerReviewedByLecturerID");
        if (!rs.wasNull()) {
            item.setLecturerReviewedByLecturerId(lecturerId);
        }

        Timestamp requestedAt = rs.getTimestamp("RequestedDate");
        if (requestedAt != null) {
            item.setRequestedDate(requestedAt.toLocalDateTime());
        }
        Timestamp staffReviewedAt = rs.getTimestamp("StaffReviewedAt");
        if (staffReviewedAt != null) {
            item.setStaffReviewedAt(staffReviewedAt.toLocalDateTime());
        }
        Timestamp lecturerReviewedAt = rs.getTimestamp("LecturerReviewedAt");
        if (lecturerReviewedAt != null) {
            item.setLecturerReviewedAt(lecturerReviewedAt.toLocalDateTime());
        }
        return item;
    }

    private String baseSelect() {
        return "SELECT r.*, g.GroupID, g.GroupName, c.ClassName, p.ProjectName AS CurrentProjectName, " +
                "s.FullName AS RequestedByName, s.StudentCode AS RequestedByCode " +
                "FROM Project_Change_Requests r " +
                "INNER JOIN Projects p ON p.ProjectID = r.ProjectID " +
                "INNER JOIN Groups g ON g.GroupID = p.GroupID " +
                "INNER JOIN Classes c ON c.ClassID = g.ClassID " +
                "INNER JOIN Students s ON s.StudentID = r.RequestedByStudentID ";
    }

    public boolean existsOpenByProject(int projectId) {
        ensureSchema();
        String sql = "SELECT COUNT(*) FROM Project_Change_Requests WHERE ProjectID = ? AND Status IN (?, ?)";
        Integer count = db.queryForObject(sql, Integer.class,
                projectId,
                ProjectChangeRequest.STATUS_PENDING_STAFF,
                ProjectChangeRequest.STATUS_PENDING_LECTURER);
        return count != null && count > 0;
    }

    public int createRequest(int projectId,
            int studentId,
            String proposedProjectName,
            String proposedDescription,
            String changeReason) {
        ensureSchema();
        try {
            String sql = "INSERT INTO Project_Change_Requests " +
                    "(ProjectID, RequestedByStudentID, ProposedProjectName, ProposedDescription, ChangeReason, Status, RequestedDate) " +
                    "OUTPUT INSERTED.RequestID VALUES (?, ?, ?, ?, ?, ?, GETDATE())";
            Integer requestId = db.queryForObject(sql, Integer.class,
                    projectId,
                    studentId,
                    proposedProjectName,
                    proposedDescription,
                    changeReason,
                    ProjectChangeRequest.STATUS_PENDING_STAFF);
            return requestId != null ? requestId : -1;
        } catch (Exception ex) {
            return -1;
        }
    }

    public ProjectChangeRequest findLatestByProject(int projectId) {
        ensureSchema();
        try {
            String sql = baseSelect() +
                    "WHERE r.ProjectID = ? ORDER BY r.RequestedDate DESC, r.RequestID DESC";
            return db.queryForObject(sql, (rs, rn) -> mapRequest(rs), projectId);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public ProjectChangeRequest findById(int requestId) {
        ensureSchema();
        try {
            String sql = baseSelect() + "WHERE r.RequestID = ?";
            return db.queryForObject(sql, (rs, rn) -> mapRequest(rs), requestId);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public List<ProjectChangeRequest> findPendingForStaff() {
        ensureSchema();
        String sql = baseSelect() +
                "WHERE r.Status = ? ORDER BY r.RequestedDate DESC, r.RequestID DESC";
        return db.query(sql, (rs, rn) -> mapRequest(rs), ProjectChangeRequest.STATUS_PENDING_STAFF);
    }

    public List<ProjectChangeRequest> findPendingForLecturer(int lecturerId) {
        ensureSchema();
        String sql = baseSelect() +
                "INNER JOIN Class_Lecturers cl ON cl.ClassID = g.ClassID AND cl.SemesterID = g.SemesterID " +
                "WHERE cl.LecturerID = ? AND r.Status = ? ORDER BY r.RequestedDate DESC, r.RequestID DESC";
        return db.query(sql, (rs, rn) -> mapRequest(rs), lecturerId, ProjectChangeRequest.STATUS_PENDING_LECTURER);
    }

    public int approveByStaff(int requestId, int staffId) {
        ensureSchema();
        String sql = "UPDATE Project_Change_Requests " +
                "SET Status = ?, StaffReviewedByStaffID = ?, StaffRejectReason = NULL, StaffReviewedAt = GETDATE() " +
                "WHERE RequestID = ? AND Status = ?";
        return db.update(sql,
                ProjectChangeRequest.STATUS_PENDING_LECTURER,
                staffId,
                requestId,
                ProjectChangeRequest.STATUS_PENDING_STAFF);
    }

    public int rejectByStaff(int requestId, int staffId, String reason) {
        ensureSchema();
        String sql = "UPDATE Project_Change_Requests " +
                "SET Status = ?, StaffReviewedByStaffID = ?, StaffRejectReason = ?, StaffReviewedAt = GETDATE() " +
                "WHERE RequestID = ? AND Status = ?";
        return db.update(sql,
                ProjectChangeRequest.STATUS_REJECTED_BY_STAFF,
                staffId,
                reason,
                requestId,
                ProjectChangeRequest.STATUS_PENDING_STAFF);
    }

    public int approveByLecturer(int requestId, int lecturerId) {
        ensureSchema();
        String sql = "UPDATE Project_Change_Requests " +
                "SET Status = ?, LecturerReviewedByLecturerID = ?, LecturerRejectReason = NULL, LecturerReviewedAt = GETDATE() " +
                "WHERE RequestID = ? AND Status = ?";
        return db.update(sql,
                ProjectChangeRequest.STATUS_APPROVED,
                lecturerId,
                requestId,
                ProjectChangeRequest.STATUS_PENDING_LECTURER);
    }

    public int rejectByLecturer(int requestId, int lecturerId, String reason) {
        ensureSchema();
        String sql = "UPDATE Project_Change_Requests " +
                "SET Status = ?, LecturerReviewedByLecturerID = ?, LecturerRejectReason = ?, LecturerReviewedAt = GETDATE() " +
                "WHERE RequestID = ? AND Status = ?";
        return db.update(sql,
                ProjectChangeRequest.STATUS_REJECTED_BY_LECTURER,
                lecturerId,
                reason,
                requestId,
                ProjectChangeRequest.STATUS_PENDING_LECTURER);
    }
}

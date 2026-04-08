package com.example.pms.repository;

import com.example.pms.model.ProjectComment;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectCommentRepository {

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
                    "IF OBJECT_ID('Project_Comments', 'U') IS NULL " +
                            "BEGIN " +
                            "   CREATE TABLE Project_Comments ( " +
                            "       CommentID INT IDENTITY(1,1) PRIMARY KEY, " +
                            "       ProjectID INT NOT NULL, " +
                            "       LecturerID INT NOT NULL, " +
                            "       CommentContent NVARCHAR(MAX) NOT NULL, " +
                            "       CreatedAt DATETIME NOT NULL DEFAULT GETDATE(), " +
                            "       CONSTRAINT FK_ProjectComments_Project FOREIGN KEY (ProjectID) REFERENCES Projects(ProjectID), " +
                            "       CONSTRAINT FK_ProjectComments_Lecturer FOREIGN KEY (LecturerID) REFERENCES Lecturers(LecturerID) " +
                            "   ); " +
                            "END");
            db.execute(
                    "IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_ProjectComments_Project' AND object_id = OBJECT_ID('Project_Comments')) " +
                            "BEGIN " +
                            "   CREATE INDEX IX_ProjectComments_Project ON Project_Comments(ProjectID, CreatedAt DESC); " +
                            "END");
            schemaEnsured = true;
        }
    }

    private ProjectComment mapComment(java.sql.ResultSet rs) throws java.sql.SQLException {
        ProjectComment item = new ProjectComment();
        item.setCommentId(rs.getInt("CommentID"));
        item.setProjectId(rs.getInt("ProjectID"));
        item.setLecturerId(rs.getInt("LecturerID"));
        item.setLecturerName(rs.getString("LecturerName"));
        item.setLecturerCode(rs.getString("LecturerCode"));
        item.setCommentContent(rs.getString("CommentContent"));
        Timestamp createdAt = rs.getTimestamp("CreatedAt");
        if (createdAt != null) {
            item.setCreatedAt(createdAt.toLocalDateTime());
        }
        return item;
    }

    public List<ProjectComment> findByProject(int projectId) {
        ensureSchema();
        String sql = "SELECT pc.CommentID, pc.ProjectID, pc.LecturerID, l.FullName AS LecturerName, l.LecturerCode AS LecturerCode, " +
                "pc.CommentContent, pc.CreatedAt " +
                "FROM Project_Comments pc " +
                "INNER JOIN Lecturers l ON l.LecturerID = pc.LecturerID " +
                "WHERE pc.ProjectID = ? " +
                "ORDER BY pc.CreatedAt DESC, pc.CommentID DESC";
        return db.query(sql, (rs, rn) -> mapComment(rs), projectId);
    }

    public int create(int projectId, int lecturerId, String commentContent) {
        ensureSchema();
        try {
            String sql = "INSERT INTO Project_Comments (ProjectID, LecturerID, CommentContent) VALUES (?, ?, ?)";
            return db.update(sql, projectId, lecturerId, commentContent);
        } catch (Exception ex) {
            return 0;
        }
    }
}

package com.example.pms.repository;

import com.example.pms.model.StudentNotification;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StudentNotificationRepository {

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
                    "IF OBJECT_ID('dbo.Student_Notifications', 'U') IS NULL " +
                            "BEGIN " +
                            "   CREATE TABLE dbo.Student_Notifications ( " +
                            "       NotificationID INT IDENTITY(1,1) PRIMARY KEY, " +
                            "       StudentID INT NOT NULL, " +
                            "       NotificationType VARCHAR(50) NOT NULL, " +
                            "       ProjectID INT NULL, " +
                            "       TaskID INT NULL, " +
                            "       GroupID INT NULL, " +
                            "       Title NVARCHAR(200) NOT NULL, " +
                            "       Message NVARCHAR(MAX) NOT NULL, " +
                            "       TargetUrl NVARCHAR(500) NULL, " +
                            "       IsRead BIT NOT NULL CONSTRAINT DF_StudentNotifications_IsRead DEFAULT 0, " +
                            "       EventKey VARCHAR(200) NULL, " +
                            "       CreatedAt DATETIME NOT NULL CONSTRAINT DF_StudentNotifications_CreatedAt DEFAULT GETDATE() " +
                            "   ); " +
                            "END");
            db.execute(
                    "IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_StudentNotifications_Student') " +
                            "ALTER TABLE dbo.Student_Notifications DROP CONSTRAINT FK_StudentNotifications_Student;");
            db.execute(
                    "IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_StudentNotifications_Project') " +
                            "ALTER TABLE dbo.Student_Notifications DROP CONSTRAINT FK_StudentNotifications_Project;");
            db.execute(
                    "IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_StudentNotifications_Task') " +
                            "ALTER TABLE dbo.Student_Notifications DROP CONSTRAINT FK_StudentNotifications_Task;");
            db.execute(
                    "IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_StudentNotifications_Group') " +
                            "ALTER TABLE dbo.Student_Notifications DROP CONSTRAINT FK_StudentNotifications_Group;");
            if (db.queryForObject(
                    "SELECT COUNT(*) FROM sys.indexes WHERE name = 'IX_StudentNotifications_Student' " +
                            "AND object_id = OBJECT_ID('dbo.Student_Notifications')",
                    Integer.class) == 0) {
                db.execute("CREATE INDEX IX_StudentNotifications_Student " +
                        "ON dbo.Student_Notifications(StudentID, CreatedAt DESC, NotificationID DESC);");
            }
            if (db.queryForObject(
                    "SELECT COUNT(*) FROM sys.indexes WHERE name = 'IX_StudentNotifications_Unread' " +
                            "AND object_id = OBJECT_ID('dbo.Student_Notifications')",
                    Integer.class) == 0) {
                db.execute("CREATE INDEX IX_StudentNotifications_Unread " +
                        "ON dbo.Student_Notifications(StudentID, IsRead, CreatedAt DESC);");
            }
            if (db.queryForObject(
                    "SELECT COUNT(*) FROM sys.indexes WHERE name = 'UX_StudentNotifications_EventKey' " +
                            "AND object_id = OBJECT_ID('dbo.Student_Notifications')",
                    Integer.class) == 0) {
                db.execute("CREATE UNIQUE INDEX UX_StudentNotifications_EventKey " +
                        "ON dbo.Student_Notifications(StudentID, EventKey) WHERE EventKey IS NOT NULL;");
            }
            schemaEnsured = true;
        }
    }

    private StudentNotification mapNotification(java.sql.ResultSet rs) throws java.sql.SQLException {
        StudentNotification item = new StudentNotification();
        item.setNotificationId(rs.getInt("NotificationID"));
        item.setStudentId(rs.getInt("StudentID"));
        item.setNotificationType(rs.getString("NotificationType"));

        int projectId = rs.getInt("ProjectID");
        item.setProjectId(rs.wasNull() ? null : projectId);

        int taskId = rs.getInt("TaskID");
        item.setTaskId(rs.wasNull() ? null : taskId);

        int groupId = rs.getInt("GroupID");
        item.setGroupId(rs.wasNull() ? null : groupId);

        item.setTitle(rs.getString("Title"));
        item.setMessage(rs.getString("Message"));
        item.setTargetUrl(rs.getString("TargetUrl"));
        item.setRead(rs.getBoolean("IsRead"));
        item.setEventKey(rs.getString("EventKey"));

        Timestamp createdAt = rs.getTimestamp("CreatedAt");
        if (createdAt != null) {
            item.setCreatedAt(createdAt.toLocalDateTime());
        }
        return item;
    }

    public int createNotification(int studentId,
            String notificationType,
            Integer projectId,
            Integer taskId,
            Integer groupId,
            String title,
            String message,
            String targetUrl,
            String eventKey) {
        ensureSchema();
        try {
            String normalizedEventKey = eventKey == null || eventKey.trim().isEmpty() ? null : eventKey.trim();
            if (normalizedEventKey == null) {
                String sql = "INSERT INTO Student_Notifications " +
                        "(StudentID, NotificationType, ProjectID, TaskID, GroupID, Title, Message, TargetUrl, IsRead, EventKey, CreatedAt) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, NULL, GETDATE())";
                return db.update(sql, studentId, notificationType, projectId, taskId, groupId, title, message, targetUrl);
            }

            String sql = "IF NOT EXISTS (SELECT 1 FROM Student_Notifications WHERE StudentID = ? AND EventKey = ?) " +
                    "BEGIN " +
                    "   INSERT INTO Student_Notifications " +
                    "   (StudentID, NotificationType, ProjectID, TaskID, GroupID, Title, Message, TargetUrl, IsRead, EventKey, CreatedAt) " +
                    "   VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, GETDATE()) " +
                    "END";
            return db.update(sql,
                    studentId,
                    normalizedEventKey,
                    studentId,
                    notificationType,
                    projectId,
                    taskId,
                    groupId,
                    title,
                    message,
                    targetUrl,
                    normalizedEventKey);
        } catch (Exception ex) {
            return 0;
        }
    }

    public List<StudentNotification> findRecentByStudent(int studentId, int limit) {
        ensureSchema();
        int safeLimit = limit <= 0 ? 30 : Math.min(limit, 200);
        String sql = "SELECT TOP " + safeLimit + " NotificationID, StudentID, NotificationType, ProjectID, TaskID, GroupID, " +
                "Title, Message, TargetUrl, IsRead, EventKey, CreatedAt " +
                "FROM Student_Notifications WHERE StudentID = ? ORDER BY CreatedAt DESC, NotificationID DESC";
        return db.query(sql, (rs, rn) -> mapNotification(rs), studentId);
    }

    public int countUnreadByStudent(int studentId) {
        ensureSchema();
        String sql = "SELECT COUNT(*) FROM Student_Notifications WHERE StudentID = ? AND IsRead = 0";
        Integer count = db.queryForObject(sql, Integer.class, studentId);
        return count != null ? count : 0;
    }

    public int markAllAsRead(int studentId) {
        ensureSchema();
        String sql = "UPDATE Student_Notifications SET IsRead = 1 WHERE StudentID = ? AND IsRead = 0";
        return db.update(sql, studentId);
    }
}

package com.example.pms.service;

import com.example.pms.model.ProjectAttachment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProjectAttachmentService {

    private static final long MAX_ATTACHMENT_BYTES = 10L * 1024L * 1024L;
    private static final String ATTACHMENT_UPLOAD_DIR = "uploads/project-attachments";

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
                    "IF OBJECT_ID('ProjectAttachments', 'U') IS NULL " +
                            "BEGIN " +
                            "   CREATE TABLE ProjectAttachments (" +
                            "       AttachmentID INT PRIMARY KEY IDENTITY(1,1)," +
                            "       ProjectID INT NOT NULL," +
                            "       FileName NVARCHAR(255) NOT NULL," +
                            "       StoredName NVARCHAR(255) NOT NULL," +
                            "       FileUrl NVARCHAR(500) NOT NULL," +
                            "       ContentType NVARCHAR(100) NULL," +
                            "       FileSize BIGINT NULL," +
                            "       CreatedAt DATETIME DEFAULT GETDATE()," +
                            "       CONSTRAINT FK_ProjectAttachments_Project FOREIGN KEY (ProjectID) REFERENCES Projects(ProjectID)" +
                            "   );" +
                            "END");
            schemaEnsured = true;
        }
    }

    private String normalizeFileName(String originalName) {
        String safe = originalName == null ? "file" : Paths.get(originalName).getFileName().toString();
        safe = safe.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.length() > 80) {
            safe = safe.substring(0, 80);
        }
        return safe.isEmpty() ? "file" : safe;
    }

    private ProjectAttachment mapAttachment(java.sql.ResultSet rs) throws java.sql.SQLException {
        ProjectAttachment attachment = new ProjectAttachment();
        attachment.setAttachmentId(rs.getInt("AttachmentID"));
        attachment.setProjectId(rs.getInt("ProjectID"));
        attachment.setFileName(rs.getString("FileName"));
        attachment.setStoredName(rs.getString("StoredName"));
        attachment.setFileUrl(rs.getString("FileUrl"));
        attachment.setContentType(rs.getString("ContentType"));
        long size = rs.getLong("FileSize");
        attachment.setFileSize(rs.wasNull() ? null : size);
        Timestamp createdAt = rs.getTimestamp("CreatedAt");
        if (createdAt != null) {
            attachment.setCreatedAt(createdAt.toLocalDateTime());
        }
        return attachment;
    }

    private Path resolveUploadDir() throws IOException {
        Path baseDir = Paths.get(System.getProperty("user.dir"), ATTACHMENT_UPLOAD_DIR);
        Files.createDirectories(baseDir);
        return baseDir;
    }

    public List<ProjectAttachment> findByProjectId(int projectId) {
        ensureSchema();
        String sql = "SELECT AttachmentID, ProjectID, FileName, StoredName, FileUrl, ContentType, FileSize, CreatedAt " +
                "FROM ProjectAttachments WHERE ProjectID = ? ORDER BY AttachmentID DESC";
        return db.query(sql, (rs, rn) -> mapAttachment(rs), projectId);
    }

    public int saveAttachments(int projectId, MultipartFile[] files) throws IOException {
        ensureSchema();
        if (files == null || files.length == 0) {
            return 0;
        }
        int saved = 0;
        Path uploadDir = resolveUploadDir();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            if (file.getSize() > MAX_ATTACHMENT_BYTES) {
                throw new IOException("File is too large: " + file.getOriginalFilename());
            }
            String storedName = UUID.randomUUID().toString().replace("-", "") + "_" + normalizeFileName(file.getOriginalFilename());
            Path target = uploadDir.resolve(storedName);
            Files.copy(file.getInputStream(), target);
            String fileUrl = "/uploads/project-attachments/" + storedName;
            db.update(
                    "INSERT INTO ProjectAttachments (ProjectID, FileName, StoredName, FileUrl, ContentType, FileSize, CreatedAt) VALUES (?, ?, ?, ?, ?, ?, GETDATE())",
                    projectId,
                    normalizeFileName(file.getOriginalFilename()),
                    storedName,
                    fileUrl,
                    file.getContentType(),
                    file.getSize());
            saved++;
        }
        return saved;
    }
}
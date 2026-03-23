package com.example.pms.service;

import com.example.pms.dto.AssignProjectDTO;
import com.example.pms.dto.CreateTemplateDTO;
import com.example.pms.model.Group;
import com.example.pms.model.Project;
import com.example.pms.model.ProjectTemplateAttachment;
import com.example.pms.model.ProjectTemplate;
import com.example.pms.model.Semester;
import com.example.pms.repository.GroupMemberRepository;
import com.example.pms.repository.ProjectRepository;
import com.example.pms.repository.SemesterRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProjectTemplateService {

    private static final long MAX_ATTACHMENT_BYTES = 10L * 1024L * 1024L;
    private static final String ATTACHMENT_UPLOAD_DIR = "uploads/project-template-attachments";
    private static final Set<String> ALLOWED_ATTACHMENT_EXTENSIONS = Set.of(
            "zip", "rar", "7z",
            "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx",
            "txt", "md", "java", "js", "ts", "py", "html", "css", "json", "xml", "yml", "yaml", "sql",
            "png", "jpg", "jpeg", "gif", "webp");

    @Autowired
    private JdbcTemplate db;

    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

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
                    "IF COL_LENGTH('ProjectTemplates', 'ImageUrl') IS NULL " +
                            "BEGIN " +
                            "   ALTER TABLE ProjectTemplates ADD ImageUrl NVARCHAR(500) NULL; " +
                            "END");
            db.execute(
                    "IF OBJECT_ID('ProjectTemplateAttachments', 'U') IS NULL " +
                            "BEGIN " +
                            "   CREATE TABLE ProjectTemplateAttachments (" +
                            "       AttachmentID INT PRIMARY KEY IDENTITY(1,1)," +
                            "       TemplateID INT NOT NULL," +
                            "       FileName NVARCHAR(255) NOT NULL," +
                            "       StoredName NVARCHAR(255) NOT NULL," +
                            "       FileUrl NVARCHAR(500) NOT NULL," +
                            "       ContentType NVARCHAR(100) NULL," +
                            "       FileSize BIGINT NULL," +
                            "       CreatedAt DATETIME DEFAULT GETDATE()," +
                            "       CONSTRAINT FK_ProjectTemplateAttachments_Template FOREIGN KEY (TemplateID) REFERENCES ProjectTemplates(TemplateID)" +
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
        if (safe.isEmpty()) {
            safe = "file";
        }
        return safe;
    }

    private String fileExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        String baseName = fileName;
        try {
            baseName = Paths.get(fileName).getFileName().toString();
        } catch (Exception ex) {
            baseName = fileName;
        }
        int idx = baseName.lastIndexOf('.');
        if (idx < 0 || idx == baseName.length() - 1) {
            return "";
        }
        return baseName.substring(idx + 1).toLowerCase();
    }

    private boolean isAllowedAttachment(String fileName) {
        String ext = fileExtension(fileName);
        return !ext.isEmpty() && ALLOWED_ATTACHMENT_EXTENSIONS.contains(ext);
    }

    private Path resolveAttachmentUploadDir() throws IOException {
        Path baseDir = Paths.get(System.getProperty("user.dir"), ATTACHMENT_UPLOAD_DIR);
        Files.createDirectories(baseDir);
        return baseDir;
    }

    private ProjectTemplate mapTemplate(java.sql.ResultSet rs) throws java.sql.SQLException {
        ProjectTemplate template = new ProjectTemplate();
        template.setTemplateId(rs.getInt("TemplateID"));
        template.setName(rs.getString("Name"));
        template.setDescription(rs.getString("Description"));
        template.setSource(rs.getString("Source"));
        template.setImageUrl(rs.getString("ImageUrl"));
        template.setVersion(rs.getInt("Version"));
        template.setActive(rs.getBoolean("IsActive"));

        int semesterId = rs.getInt("SemesterID");
        template.setSemesterId(rs.wasNull() ? null : semesterId);
        template.setSemesterName(rs.getString("SemesterName"));

        int year = rs.getInt("Year");
        template.setYear(rs.wasNull() ? null : year);

        int createdBy = rs.getInt("CreatedByStaffID");
        template.setCreatedByStaffId(rs.wasNull() ? null : createdBy);
        template.setCreatedByName(rs.getString("CreatedByName"));

        Timestamp createdAt = rs.getTimestamp("CreatedAt");
        if (createdAt != null) {
            template.setCreatedAt(createdAt.toLocalDateTime());
        }
        return template;
    }

    private ProjectTemplateAttachment mapAttachment(java.sql.ResultSet rs) throws java.sql.SQLException {
        ProjectTemplateAttachment attachment = new ProjectTemplateAttachment();
        attachment.setAttachmentId(rs.getInt("AttachmentID"));
        attachment.setTemplateId(rs.getInt("TemplateID"));
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

    private java.sql.Timestamp toTimestamp(LocalDate value) {
        return value == null ? null : java.sql.Timestamp.valueOf(value.atStartOfDay());
    }

    public List<ProjectTemplate> findTemplates(int semesterId) {
        ensureSchema();
        String sql = "SELECT pt.TemplateID, pt.Name, pt.Description, pt.Source, pt.ImageUrl, pt.Version, pt.IsActive, " +
                "pt.SemesterID, sem.SemesterName, pt.Year, pt.CreatedByStaffID, st.FullName AS CreatedByName, pt.CreatedAt " +
                "FROM ProjectTemplates pt " +
                "LEFT JOIN Semesters sem ON sem.SemesterID = pt.SemesterID " +
                "LEFT JOIN Staff st ON st.StaffID = pt.CreatedByStaffID " +
                "WHERE pt.IsActive = 1 AND (pt.SemesterID IS NULL OR pt.SemesterID = ?) " +
                "ORDER BY pt.Version DESC, pt.CreatedAt DESC, pt.TemplateID DESC";
        List<ProjectTemplate> templates = db.query(sql, (rs, rn) -> mapTemplate(rs), semesterId);
        for (ProjectTemplate template : templates) {
            template.setAttachments(findAttachments(template.getTemplateId()));
        }
        return templates;
    }

    public List<ProjectTemplate> findTemplatesForManagement(int semesterId) {
        return findTemplatesForManagement(semesterId, null, null, null, 1, Integer.MAX_VALUE);
    }

    public int countTemplatesForManagement(int semesterId, String keyword, Integer year, Boolean active) {
        ensureSchema();
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM ProjectTemplates pt WHERE (pt.SemesterID IS NULL OR pt.SemesterID = ?)");
        List<Object> params = new ArrayList<>();
        params.add(semesterId);
        appendTemplateManagementFilters(sql, params, keyword, year, active);
        Integer count = db.queryForObject(sql.toString(), Integer.class, params.toArray());
        return count != null ? count : 0;
    }

    public List<ProjectTemplate> findTemplatesForManagement(int semesterId,
            String keyword,
            Integer year,
            Boolean active,
            int page,
            int pageSize) {
        ensureSchema();
        int safePageSize = Math.max(1, pageSize);
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safePageSize;

        StringBuilder sql = new StringBuilder(
                "SELECT pt.TemplateID, pt.Name, pt.Description, pt.Source, pt.ImageUrl, pt.Version, pt.IsActive, " +
                        "pt.SemesterID, sem.SemesterName, pt.Year, pt.CreatedByStaffID, st.FullName AS CreatedByName, pt.CreatedAt " +
                        "FROM ProjectTemplates pt " +
                        "LEFT JOIN Semesters sem ON sem.SemesterID = pt.SemesterID " +
                        "LEFT JOIN Staff st ON st.StaffID = pt.CreatedByStaffID " +
                        "WHERE (pt.SemesterID IS NULL OR pt.SemesterID = ?)");
        List<Object> params = new ArrayList<>();
        params.add(semesterId);
        appendTemplateManagementFilters(sql, params, keyword, year, active);
        sql.append(" ORDER BY pt.IsActive DESC, pt.Version DESC, pt.CreatedAt DESC, pt.TemplateID DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        params.add(offset);
        params.add(safePageSize);

        List<ProjectTemplate> templates = db.query(sql.toString(), (rs, rn) -> mapTemplate(rs), params.toArray());
        for (ProjectTemplate template : templates) {
            template.setAttachments(findAttachments(template.getTemplateId()));
        }
        return templates;
    }

    private void appendTemplateManagementFilters(StringBuilder sql,
            List<Object> params,
            String keyword,
            Integer year,
            Boolean active) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (!normalizedKeyword.isEmpty()) {
            sql.append(" AND LOWER(pt.Name) LIKE ?");
            params.add("%" + normalizedKeyword.toLowerCase() + "%");
        }
        if (year != null) {
            sql.append(" AND pt.Year = ?");
            params.add(year);
        }
        if (active != null) {
            sql.append(" AND pt.IsActive = ?");
            params.add(active ? 1 : 0);
        }
    }

    public ProjectTemplate findById(int templateId) {
        try {
            ensureSchema();
            String sql = "SELECT pt.TemplateID, pt.Name, pt.Description, pt.Source, pt.ImageUrl, pt.Version, pt.IsActive, " +
                    "pt.SemesterID, sem.SemesterName, pt.Year, pt.CreatedByStaffID, st.FullName AS CreatedByName, pt.CreatedAt " +
                    "FROM ProjectTemplates pt " +
                    "LEFT JOIN Semesters sem ON sem.SemesterID = pt.SemesterID " +
                    "LEFT JOIN Staff st ON st.StaffID = pt.CreatedByStaffID " +
                    "WHERE pt.TemplateID = ?";
            return db.queryForObject(sql, (rs, rn) -> mapTemplate(rs), templateId);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public List<Group> findAvailableGroups(int semesterId) {
        String sql = "SELECT g.*, c.ClassName AS ClassName, leader.FullName AS LeaderName, mc.MemberCount AS MemberCount " +
                "FROM Groups g " +
                "INNER JOIN Classes c ON c.ClassID = g.ClassID " +
                "LEFT JOIN Students leader ON leader.StudentID = g.LeaderID " +
                "OUTER APPLY (SELECT COUNT(*) AS MemberCount FROM Group_Members gm WHERE gm.GroupID = g.GroupID AND gm.IsActive = 1) mc " +
                "WHERE g.SemesterID = ? AND g.IsLocked = 0 " +
                "AND ISNULL(mc.MemberCount, 0) BETWEEN 4 AND 6 " +
                "AND NOT EXISTS ( " +
                "    SELECT 1 FROM ProjectAssignments pa " +
                "    WHERE pa.GroupID = g.GroupID " +
                ") " +
                "ORDER BY g.CreatedDate DESC, g.GroupName ASC";
        return db.query(sql, (rs, rn) -> {
            Group group = new Group();
            group.setGroupId(rs.getInt("GroupID"));
            group.setGroupName(rs.getString("GroupName"));
            group.setClassId(rs.getInt("ClassID"));
            group.setClassName(rs.getString("ClassName"));
            group.setSemesterId(rs.getInt("SemesterID"));
            int leaderId = rs.getInt("LeaderID");
            if (!rs.wasNull()) {
                group.setLeaderId(leaderId);
            }
            group.setLeaderName(rs.getString("LeaderName"));
            group.setMemberCount(rs.getInt("MemberCount"));
            java.sql.Timestamp created = rs.getTimestamp("CreatedDate");
            if (created != null) {
                group.setCreatedDate(created.toLocalDateTime());
            }
            group.setLocked(rs.getBoolean("IsLocked"));
            return group;
        }, semesterId);
    }

    public int updateTemplate(int templateId, CreateTemplateDTO dto) {
        ensureSchema();
        try {
            String sql = "UPDATE ProjectTemplates SET Name = ?, Description = ?, Source = ?, ImageUrl = ?, SemesterID = ?, Year = ?, IsActive = 1 WHERE TemplateID = ?";
            return db.update(sql,
                    dto.getName(),
                    dto.getDescription(),
                    dto.getSource(),
                    dto.getImageUrl(),
                    dto.getSemesterId(),
                    dto.getYear(),
                    templateId);
        } catch (Exception ex) {
            return 0;
        }
    }

    public int deactivateTemplate(int templateId) {
        ensureSchema();
        try {
            return db.update("UPDATE ProjectTemplates SET IsActive = 0 WHERE TemplateID = ?", templateId);
        } catch (Exception ex) {
            return 0;
        }
    }

    public int createTemplate(CreateTemplateDTO dto) {
        try {
            ensureSchema();
            String sql = "INSERT INTO ProjectTemplates (Name, Description, Source, ImageUrl, Version, IsActive, SemesterID, Year, CreatedByStaffID, CreatedAt) " +
                "OUTPUT INSERTED.TemplateID VALUES (?, ?, ?, ?, 1, 1, ?, ?, ?, GETDATE())";
            Integer templateId = db.queryForObject(sql, Integer.class,
                    dto.getName(),
                    dto.getDescription(),
                    dto.getSource(),
                dto.getImageUrl(),
                    dto.getSemesterId(),
                    dto.getYear(),
                    dto.getStaffId());
            return templateId != null ? templateId : -1;
        } catch (Exception ex) {
            return -1;
        }
    }

    public List<ProjectTemplateAttachment> findAttachments(int templateId) {
        ensureSchema();
        String sql = "SELECT AttachmentID, TemplateID, FileName, StoredName, FileUrl, ContentType, FileSize, CreatedAt " +
                "FROM ProjectTemplateAttachments WHERE TemplateID = ? ORDER BY AttachmentID ASC";
        return db.query(sql, (rs, rn) -> mapAttachment(rs), templateId);
    }

    public int deleteAttachment(int attachmentId) {
        ensureSchema();
        List<ProjectTemplateAttachment> attachments = db.query(
                "SELECT AttachmentID, TemplateID, FileName, StoredName, FileUrl, ContentType, FileSize, CreatedAt FROM ProjectTemplateAttachments WHERE AttachmentID = ?",
                (rs, rn) -> mapAttachment(rs),
                attachmentId);
        if (attachments.isEmpty()) {
            return 0;
        }
        ProjectTemplateAttachment attachment = attachments.get(0);
        try {
            Path baseDir = Paths.get(System.getProperty("user.dir"), ATTACHMENT_UPLOAD_DIR);
            Files.deleteIfExists(baseDir.resolve(attachment.getStoredName()));
        } catch (IOException ex) {
            // Continue deleting DB row even if file cleanup fails.
        }
        return db.update("DELETE FROM ProjectTemplateAttachments WHERE AttachmentID = ?", attachmentId);
    }

    public int saveAttachments(int templateId, MultipartFile[] files) throws IOException {
        ensureSchema();
        if (files == null || files.length == 0) {
            return 0;
        }
        int savedCount = 0;
        Path uploadDir = resolveAttachmentUploadDir();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String originalName = file.getOriginalFilename();
            if (!isAllowedAttachment(originalName)) {
                throw new IOException("Unsupported file type: " + originalName);
            }
            if (file.getSize() > MAX_ATTACHMENT_BYTES) {
                throw new IOException("File is too large: " + originalName);
            }
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String originalName = file.getOriginalFilename();
            String storedName = UUID.randomUUID().toString().replace("-", "") + "_" + normalizeFileName(originalName);
            Path target = uploadDir.resolve(storedName);
            Files.copy(file.getInputStream(), target);
            String fileUrl = "/uploads/project-template-attachments/" + storedName;
            db.update(
                    "INSERT INTO ProjectTemplateAttachments (TemplateID, FileName, StoredName, FileUrl, ContentType, FileSize, CreatedAt) " +
                            "VALUES (?, ?, ?, ?, ?, ?, GETDATE())",
                    templateId,
                    normalizeFileName(originalName),
                    storedName,
                    fileUrl,
                    file.getContentType(),
                    file.getSize());
            savedCount++;
        }
        return savedCount;
    }

    private Timestamp resolveSemesterBoundary(int semesterId, boolean startDate) {
        Semester semester = semesterRepository.findById(semesterId);
        if (semester == null) {
            return Timestamp.valueOf(LocalDateTime.now());
        }
        LocalDate boundary = startDate ? semester.getStartDate() : semester.getEndDate();
        return boundary == null ? Timestamp.valueOf(LocalDateTime.now()) : toTimestamp(boundary);
    }

    private boolean hasAssignmentForGroup(int groupId) {
        try {
            Integer marker = db.queryForObject(
                    "SELECT TOP 1 1 FROM ProjectAssignments WHERE GroupID = ? ORDER BY AssignmentID DESC",
                    Integer.class,
                    groupId);
            return marker != null;
        } catch (Exception ex) {
            return false;
        }
    }

    private void lockGroupForAssignment(int groupId) {
        db.queryForObject(
                "SELECT GroupID FROM Groups WITH (UPDLOCK, HOLDLOCK) WHERE GroupID = ?",
                Integer.class,
                groupId);
    }

    private List<Integer> distinctGroupIds(List<Integer> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return List.of();
        }
        Set<Integer> distinct = new LinkedHashSet<>();
        for (Integer groupId : groupIds) {
            if (groupId != null) {
                distinct.add(groupId);
            }
        }
        return new ArrayList<>(distinct);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public List<Integer> assignProjects(AssignProjectDTO dto) {
        ProjectTemplate template = findById(dto.getTemplateId());
        if (template == null) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startValue = dto.getStartDate() != null ? dto.getStartDate() : now;
        if (startValue.isBefore(now)) {
            throw new IllegalArgumentException("Start time cannot be in the past.");
        }
        LocalDateTime endValue = dto.getEndDate() != null ? dto.getEndDate() : startValue.plusDays(7);
        if (endValue.isBefore(now)) {
            endValue = startValue.plusDays(7);
        }
        if (!endValue.isAfter(startValue)) {
            throw new IllegalArgumentException("End time must be after start time.");
        }
        Timestamp startDate = Timestamp.valueOf(startValue);
        Timestamp endDate = Timestamp.valueOf(endValue);
        List<Integer> createdProjectIds = new ArrayList<>();
        for (Integer groupId : distinctGroupIds(dto.getGroupIds())) {
            lockGroupForAssignment(groupId);
            int memberCount = groupMemberRepository.countMembers(groupId);
            if (memberCount < 4 || memberCount > 6) {
                continue;
            }
            if (projectRepository.findByGroupId(groupId) != null || hasAssignmentForGroup(groupId)) {
                continue;
            }
            Integer assignmentId = db.queryForObject(
                    "INSERT INTO ProjectAssignments (TemplateID, GroupID, StartDate, EndDate, Status, AssignedAt) " +
                            "OUTPUT INSERTED.AssignmentID VALUES (?, ?, ?, ?, 'ASSIGNED', GETDATE())",
                    Integer.class,
                    template.getTemplateId(),
                    groupId,
                    startDate,
                    endDate);
            if (assignmentId == null) {
                continue;
            }
            int projectId = projectRepository.createProjectFromAssignment(
                    assignmentId,
                    template.getName(),
                    template.getDescription(),
                    Project.STATUS_APPROVED,
                    false);
            if (projectId > 0) {
                createdProjectIds.add(projectId);
                continue;
            }
            throw new IllegalStateException("Unable to create project for group " + groupId);
        }
        return createdProjectIds;
    }

    public int reassignProject(int projectId, int templateId, LocalDateTime startDate, LocalDateTime endDate) {
        ensureSchema();
        ProjectTemplate template = findById(templateId);
        if (template == null || !template.isActive()) {
            return -1;
        }
        Project project = projectRepository.findById(projectId);
        if (project == null) {
            return -1;
        }
        try {
            Integer assignmentId = db.queryForObject("SELECT AssignmentID FROM Projects WHERE ProjectID = ?", Integer.class, projectId);
            if (assignmentId == null) {
                return -1;
            }
            int updatedAssignment = db.update(
                    "UPDATE ProjectAssignments SET TemplateID = ?, StartDate = ?, EndDate = ?, Status = 'ASSIGNED', AssignedAt = GETDATE() WHERE AssignmentID = ?",
                    templateId,
                    Timestamp.valueOf(startDate),
                    Timestamp.valueOf(endDate),
                    assignmentId);
            int updatedProject = db.update(
                    "UPDATE Projects SET ProjectName = ?, Description = ?, ApprovalStatus = ?, RejectReason = NULL, SourceCodeUrl = NULL, DocumentUrl = NULL, SubmissionDate = NULL, StudentCanEdit = 0 WHERE ProjectID = ?",
                    template.getName(),
                    template.getDescription(),
                    Project.STATUS_APPROVED,
                    projectId);
            return updatedAssignment > 0 && updatedProject > 0 ? updatedProject : -1;
        } catch (Exception ex) {
            return -1;
        }
    }
}
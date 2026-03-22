package com.example.pms.service;

import com.example.pms.dto.AssignProjectDTO;
import com.example.pms.dto.CreateTemplateDTO;
import com.example.pms.model.Group;
import com.example.pms.model.Project;
import com.example.pms.model.ProjectTemplate;
import com.example.pms.model.Semester;
import com.example.pms.repository.ProjectRepository;
import com.example.pms.repository.SemesterRepository;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectTemplateService {

    @Autowired
    private JdbcTemplate db;

    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired
    private ProjectRepository projectRepository;

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
            schemaEnsured = true;
        }
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
        return db.query(sql, (rs, rn) -> mapTemplate(rs), semesterId);
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
        Timestamp startDate = dto.getStartDate() != null ? Timestamp.valueOf(dto.getStartDate()) : resolveSemesterBoundary(dto.getSemesterId(), true);
        Timestamp endDate = dto.getEndDate() != null ? Timestamp.valueOf(dto.getEndDate()) : resolveSemesterBoundary(dto.getSemesterId(), false);
        List<Integer> createdProjectIds = new ArrayList<>();
        for (Integer groupId : distinctGroupIds(dto.getGroupIds())) {
            lockGroupForAssignment(groupId);
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
}
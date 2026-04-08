package com.example.pms.repository;

import com.example.pms.model.ProjectTask;
import com.example.pms.model.Sprint;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SprintRepository {

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
            db.execute("IF COL_LENGTH('Sprints', 'IsCancelled') IS NULL ALTER TABLE Sprints ADD IsCancelled BIT NOT NULL CONSTRAINT DF_Sprints_IsCancelled DEFAULT 0;");
            db.execute("IF COL_LENGTH('Sprints', 'CancelReason') IS NULL ALTER TABLE Sprints ADD CancelReason NVARCHAR(MAX) NULL;");
            db.execute("IF COL_LENGTH('Sprints', 'CancelledByStudentID') IS NULL ALTER TABLE Sprints ADD CancelledByStudentID INT NULL;");
            db.execute("IF COL_LENGTH('Sprints', 'CancelledAt') IS NULL ALTER TABLE Sprints ADD CancelledAt DATETIME NULL;");
            schemaEnsured = true;
        }
    }

    private Sprint mapSprint(java.sql.ResultSet rs) throws java.sql.SQLException {
        Sprint sprint = new Sprint();
        sprint.setSprintId(rs.getInt("SprintID"));
        sprint.setProjectId(rs.getInt("ProjectID"));
        sprint.setSprintName(rs.getString("SprintName"));
        Date start = rs.getDate("StartDate");
        Date end = rs.getDate("EndDate");
        if (start != null) {
            sprint.setStartDate(start.toLocalDate());
        }
        if (end != null) {
            sprint.setEndDate(end.toLocalDate());
        }
        sprint.setClosed(rs.getBoolean("IsClosed"));
        sprint.setCancelled(rs.getBoolean("IsCancelled"));
        sprint.setCancelReason(rs.getString("CancelReason"));

        int cancelledByStudentId = rs.getInt("CancelledByStudentID");
        if (!rs.wasNull()) {
            sprint.setCancelledByStudentId(cancelledByStudentId);
        }
        Timestamp cancelledAt = rs.getTimestamp("CancelledAt");
        if (cancelledAt != null) {
            sprint.setCancelledAt(cancelledAt.toLocalDateTime());
        }
        return sprint;
    }

    public List<Sprint> findByProject(int projectId) {
        ensureSchema();
        String sql = "SELECT SprintID, ProjectID, SprintName, StartDate, EndDate, IsClosed, " +
                "ISNULL(IsCancelled, 0) AS IsCancelled, CancelReason, CancelledByStudentID, CancelledAt " +
                "FROM Sprints WHERE ProjectID = ? ORDER BY StartDate DESC, SprintID DESC";
        return db.query(sql, (rs, rn) -> mapSprint(rs), projectId);
    }

    public Sprint findById(int sprintId) {
        ensureSchema();
        try {
            String sql = "SELECT SprintID, ProjectID, SprintName, StartDate, EndDate, IsClosed, " +
                    "ISNULL(IsCancelled, 0) AS IsCancelled, CancelReason, CancelledByStudentID, CancelledAt " +
                    "FROM Sprints WHERE SprintID = ?";
            return db.queryForObject(sql, (rs, rn) -> mapSprint(rs), sprintId);
        } catch (Exception ex) {
            return null;
        }
    }

    public Sprint findLatestByProject(int projectId) {
        ensureSchema();
        try {
            String sql = "SELECT TOP 1 SprintID, ProjectID, SprintName, StartDate, EndDate, IsClosed, " +
                    "ISNULL(IsCancelled, 0) AS IsCancelled, CancelReason, CancelledByStudentID, CancelledAt " +
                    "FROM Sprints WHERE ProjectID = ? ORDER BY StartDate DESC, SprintID DESC";
            return db.queryForObject(sql, (rs, rn) -> mapSprint(rs), projectId);
        } catch (Exception ex) {
            return null;
        }
    }

    public Sprint findOpenByProject(int projectId) {
        ensureSchema();
        try {
            String sql = "SELECT TOP 1 SprintID, ProjectID, SprintName, StartDate, EndDate, IsClosed, " +
                    "ISNULL(IsCancelled, 0) AS IsCancelled, CancelReason, CancelledByStudentID, CancelledAt " +
                    "FROM Sprints WHERE ProjectID = ? AND IsClosed = 0 AND ISNULL(IsCancelled, 0) = 0 " +
                    "ORDER BY StartDate ASC, SprintID ASC";
            return db.queryForObject(sql, (rs, rn) -> mapSprint(rs), projectId);
        } catch (Exception ex) {
            return null;
        }
    }

    private LocalDate nextMonday(LocalDate date) {
        LocalDate cursor = date;
        while (cursor.getDayOfWeek() != DayOfWeek.MONDAY) {
            cursor = cursor.plusDays(1);
        }
        return cursor;
    }

    private LocalDate normalizeSprintStart(LocalDate baseDate) {
        return nextMonday(baseDate);
    }

    private LocalDate calculateSprintEnd(LocalDate sprintStart) {
        return sprintStart.plusDays(12);
    }

    public int createNextSprint(int projectId, String sprintName, LocalDate projectStartDate, LocalDate projectEndDate) {
        ensureSchema();
        String normalizedName = sprintName == null ? "" : sprintName.trim();

        Sprint open = findOpenByProject(projectId);
        if (open != null) {
            return -2;
        }

        Sprint latest = findLatestByProject(projectId);
        LocalDate baseStartDate;
        if (latest != null && latest.getEndDate() != null) {
            baseStartDate = latest.getEndDate().plusDays(1);
        } else if (projectStartDate != null) {
            baseStartDate = projectStartDate;
        } else {
            baseStartDate = LocalDate.now();
        }

        LocalDate sprintStart = normalizeSprintStart(baseStartDate);
        LocalDate sprintEnd = calculateSprintEnd(sprintStart);
        if (projectEndDate != null && sprintStart.isAfter(projectEndDate)) {
            return -3;
        }
        if (projectEndDate != null && sprintEnd.isAfter(projectEndDate)) {
            sprintEnd = projectEndDate;
            if (sprintEnd.isBefore(sprintStart)) {
                return -3;
            }
        }

        if (normalizedName.isEmpty()) {
            int sprintNo = latest == null ? 1 : extractSprintNo(latest.getSprintName()) + 1;
            normalizedName = "Sprint " + sprintNo;
        }

        try {
            String sql = "INSERT INTO Sprints (ProjectID, SprintName, StartDate, EndDate, IsClosed) " +
                    "OUTPUT INSERTED.SprintID VALUES (?, ?, ?, ?, 0)";
            Integer sprintId = db.queryForObject(sql, Integer.class,
                    projectId,
                    normalizedName,
                    Date.valueOf(sprintStart),
                    Date.valueOf(sprintEnd));
            return sprintId != null ? sprintId : -1;
        } catch (Exception ex) {
            return -1;
        }
    }

    private int extractSprintNo(String name) {
        if (name == null) {
            return 0;
        }
        String digits = name.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (Exception ex) {
            return 0;
        }
    }

    public boolean hasAnyTasks(int sprintId) {
        ensureSchema();
        String sql = "SELECT COUNT(*) FROM Tasks WHERE SprintID = ?";
        Integer count = db.queryForObject(sql, Integer.class, sprintId);
        return count != null && count > 0;
    }

    public boolean hasDoneTasks(int sprintId) {
        ensureSchema();
        String sql = "SELECT COUNT(*) FROM Tasks WHERE SprintID = ? AND Status = ?";
        Integer count = db.queryForObject(sql, Integer.class, sprintId, ProjectTask.STATUS_DONE);
        return count != null && count > 0;
    }

    public int updateSprintName(int sprintId, int projectId, String sprintName) {
        ensureSchema();
        try {
            String sql = "UPDATE Sprints SET SprintName = ? " +
                    "WHERE SprintID = ? AND ProjectID = ? AND IsClosed = 0 AND ISNULL(IsCancelled, 0) = 0";
            return db.update(sql, sprintName, sprintId, projectId);
        } catch (Exception ex) {
            return 0;
        }
    }

    public int deleteEmptyOpenSprint(int sprintId, int projectId) {
        ensureSchema();
        try {
            String sql = "DELETE sp FROM Sprints sp " +
                    "WHERE sp.SprintID = ? AND sp.ProjectID = ? AND sp.IsClosed = 0 AND ISNULL(sp.IsCancelled, 0) = 0 " +
                    "AND NOT EXISTS (SELECT 1 FROM Tasks t WHERE t.SprintID = sp.SprintID)";
            return db.update(sql, sprintId, projectId);
        } catch (Exception ex) {
            return 0;
        }
    }

    public int cancelSprint(int sprintId, int projectId, Integer studentId, String reason) {
        ensureSchema();
        try {
            String sql = "UPDATE Sprints SET IsClosed = 1, IsCancelled = 1, CancelReason = ?, " +
                    "CancelledByStudentID = ?, CancelledAt = GETDATE() " +
                    "WHERE SprintID = ? AND ProjectID = ? AND ISNULL(IsCancelled, 0) = 0 " +
                    "AND NOT EXISTS (SELECT 1 FROM Tasks t WHERE t.SprintID = ? AND t.Status = ?)";
            return db.update(sql, reason, studentId, sprintId, projectId, sprintId, ProjectTask.STATUS_DONE);
        } catch (Exception ex) {
            return 0;
        }
    }

    public int cancelAllByProject(int projectId, String reason) {
        ensureSchema();
        try {
            String sql = "UPDATE Sprints SET IsClosed = 1, IsCancelled = 1, CancelReason = ?, " +
                    "CancelledByStudentID = NULL, CancelledAt = GETDATE() " +
                    "WHERE ProjectID = ? AND ISNULL(IsCancelled, 0) = 0";
            return db.update(sql, reason, projectId);
        } catch (Exception ex) {
            return 0;
        }
    }

    public void closeExpiredSprintsAndFailTasks(int projectId) {
        ensureSchema();
        try {
            String failTasksSql = "UPDATE t " +
                    "SET t.Status = ?, " +
                    "    t.ReviewComment = CASE WHEN NULLIF(LTRIM(RTRIM(ISNULL(t.ReviewComment, ''))), '') IS NULL " +
                    "           THEN N'Task failed because it was not completed before the sprint ended.' " +
                    "           ELSE t.ReviewComment END, " +
                    "    t.ReviewedAt = GETDATE(), " +
                    "    t.ActualEndTime = ISNULL(t.ActualEndTime, GETDATE()) " +
                    "FROM Tasks t " +
                    "INNER JOIN Sprints sp ON sp.SprintID = t.SprintID " +
                    "WHERE sp.ProjectID = ? " +
                    "  AND sp.IsClosed = 0 " +
                    "  AND ISNULL(sp.IsCancelled, 0) = 0 " +
                    "  AND sp.EndDate < CAST(GETDATE() AS DATE) " +
                    "  AND t.Status <> ? " +
                    "  AND t.Status <> ? " +
                    "  AND t.Status <> ?";
            db.update(failTasksSql,
                    ProjectTask.STATUS_FAILED_SPRINT,
                    projectId,
                    ProjectTask.STATUS_DONE,
                    ProjectTask.STATUS_FAILED_SPRINT,
                    ProjectTask.STATUS_CANCELLED);

            String closeSprintSql = "UPDATE Sprints SET IsClosed = 1 " +
                    "WHERE ProjectID = ? AND IsClosed = 0 AND ISNULL(IsCancelled, 0) = 0 AND EndDate < CAST(GETDATE() AS DATE)";
            db.update(closeSprintSql, projectId);
        } catch (Exception ex) {
            // Non-blocking by design.
        }
    }

    public int closeSprint(int sprintId) {
        ensureSchema();
        try {
            String sql = "UPDATE Sprints SET IsClosed = 1 WHERE SprintID = ? AND IsClosed = 0 AND ISNULL(IsCancelled, 0) = 0";
            return db.update(sql, sprintId);
        } catch (Exception ex) {
            return 0;
        }
    }
}

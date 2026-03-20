package com.example.pms.repository;

import com.example.pms.model.Group;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class GroupRepository {
    @Autowired
    private JdbcTemplate db;

    private static final int MAX_GROUP_MEMBERS = 4;

    public List<Group> findByStudentAndSemester(int studentId, int semesterId) {
        try {
            String sql = "SELECT DISTINCT g.*, " +
                    "leader.FullName AS LeaderName, " +
                    "mc.MemberCount AS MemberCount " +
                    "FROM Groups g " +
                    "INNER JOIN Group_Members gm ON g.GroupID = gm.GroupID " +
                    "LEFT JOIN Students leader ON leader.StudentID = g.LeaderID " +
                    "OUTER APPLY ( " +
                    "    SELECT COUNT(*) AS MemberCount " +
                    "    FROM Group_Members gm2 " +
                    "    WHERE gm2.GroupID = g.GroupID AND gm2.IsActive = 1 " +
                    ") mc " +
                    "WHERE gm.StudentID = ? AND g.SemesterID = ? AND gm.IsActive = 1 AND g.IsLocked = 0 " +
                    "ORDER BY g.CreatedDate DESC";
            return db.query(sql, (rs, rn) -> mapGroup(rs), studentId, semesterId);
        } catch (Exception ex) {
            ex.printStackTrace();
            return List.of();
        }
    }

    public List<Group> findOpenByClassAndSemester(int classId, int semesterId) {
        try {
            String sql = "SELECT g.*, " +
                    "leader.FullName AS LeaderName, " +
                    "mc.MemberCount AS MemberCount " +
                    "FROM Groups g " +
                    "LEFT JOIN Students leader ON leader.StudentID = g.LeaderID " +
                    "OUTER APPLY ( " +
                    "    SELECT COUNT(*) AS MemberCount " +
                    "    FROM Group_Members gm " +
                    "    WHERE gm.GroupID = g.GroupID AND gm.IsActive = 1 " +
                    ") mc " +
                    "WHERE g.ClassID = ? AND g.SemesterID = ? AND g.IsLocked = 0 " +
                    "AND ISNULL(mc.MemberCount, 0) < ? " +
                    "ORDER BY g.CreatedDate DESC";
            return db.query(sql, (rs, rn) -> mapGroup(rs), classId, semesterId, MAX_GROUP_MEMBERS);
        } catch (Exception ex) {
            return List.of();
        }
    }

    public Group findById(int groupId) {
        try {
            String sql = "SELECT g.*, " +
                    "leader.FullName AS LeaderName, " +
                    "mc.MemberCount AS MemberCount " +
                    "FROM Groups g " +
                    "LEFT JOIN Students leader ON leader.StudentID = g.LeaderID " +
                    "OUTER APPLY ( " +
                    "    SELECT COUNT(*) AS MemberCount " +
                    "    FROM Group_Members gm " +
                    "    WHERE gm.GroupID = g.GroupID AND gm.IsActive = 1 " +
                    ") mc " +
                    "WHERE g.GroupID = ?";
            return db.queryForObject(sql, (rs, rn) -> mapGroup(rs), groupId);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public int create(String groupName, int classId, int semesterId, int leaderId) {
        try {
            String sql = "INSERT INTO Groups (GroupName, ClassID, SemesterID, LeaderID, CreatedDate, IsLocked) " +
                    "OUTPUT INSERTED.GroupID VALUES (?, ?, ?, ?, GETDATE(), 0)";
            Integer groupId = db.queryForObject(sql, Integer.class, groupName, classId, semesterId, leaderId);
            return groupId != null ? groupId : -1;
        } catch (Exception ex) {
            ex.printStackTrace();
            return -1;
        }
    }

    public boolean hasActiveGroup(int studentId, int semesterId) {
        try {
            String sql = "SELECT COUNT(*) FROM Group_Members gm " +
                    "INNER JOIN Groups g ON gm.GroupID = g.GroupID " +
                    "WHERE gm.StudentID = ? AND g.SemesterID = ? AND gm.IsActive = 1 AND g.IsLocked = 0";
            Integer count = db.queryForObject(sql, Integer.class, studentId, semesterId);
            return count != null && count > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    public int deleteGroup(int groupId) {
        try {
            String sql = "DELETE FROM Groups WHERE GroupID = ?";
            return db.update(sql, groupId);
        } catch (Exception ex) {
            ex.printStackTrace();
            return 0;
        }
    }

    public int updateLeader(int groupId, int newLeaderId) {
        try {
            String sql = "UPDATE g SET LeaderID = ? " +
                    "FROM Groups g " +
                    "WHERE g.GroupID = ? " +
                    "AND EXISTS ( " +
                    "    SELECT 1 FROM Group_Members gm " +
                    "    WHERE gm.GroupID = g.GroupID AND gm.StudentID = ? AND gm.IsActive = 1" +
                    ")";
            return db.update(sql, newLeaderId, groupId, newLeaderId);
        } catch (Exception ex) {
            ex.printStackTrace();
            return 0;
        }
    }

    private Group mapGroup(java.sql.ResultSet rs) throws java.sql.SQLException {
        Group g = new Group();
        g.setGroupId(rs.getInt("GroupID"));
        g.setGroupName(rs.getString("GroupName"));
        g.setClassId(rs.getInt("ClassID"));
        g.setSemesterId(rs.getInt("SemesterID"));

        int leaderId = rs.getInt("LeaderID");
        if (!rs.wasNull()) {
            g.setLeaderId(leaderId);
        }

        g.setLeaderName(rs.getString("LeaderName"));

        int memberCount = rs.getInt("MemberCount");
        g.setMemberCount(rs.wasNull() ? 0 : memberCount);

        java.sql.Timestamp ts = rs.getTimestamp("CreatedDate");
        if (ts != null) {
            g.setCreatedDate(ts.toLocalDateTime());
        }

        g.setLocked(rs.getBoolean("IsLocked"));
        return g;
    }
}

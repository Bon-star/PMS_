package com.example.pms.repository;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.pms.model.GroupInvitation;

@Repository
public class GroupInvitationRepository {

    @Autowired
    private JdbcTemplate db;

    private volatile Boolean invitationTableAvailable;

    private static final String BASE_SELECT =
            "SELECT gi.*, " +
                    "g.GroupName AS GroupName, " +
                    "s.FullName AS StudentName, " +
                    "s.StudentCode AS StudentCode, " +
                    "ib.FullName AS InvitedByName, " +
                    "ib.StudentCode AS InvitedByCode " +
                    "FROM Group_Invitations gi " +
                    "INNER JOIN Groups g ON g.GroupID = gi.GroupID " +
                    "INNER JOIN Students s ON s.StudentID = gi.StudentID " +
                    "INNER JOIN Students ib ON ib.StudentID = gi.InvitedByStudentID ";

    public boolean isInvitationTableAvailable() {
        if (invitationTableAvailable != null) {
            return invitationTableAvailable;
        }
        synchronized (this) {
            if (invitationTableAvailable != null) {
                return invitationTableAvailable;
            }
            try {
                String sql = "SELECT COUNT(*) FROM sys.tables WHERE name = 'Group_Invitations'";
                Integer count = db.queryForObject(sql, Integer.class);
                invitationTableAvailable = count != null && count > 0;
            } catch (Exception ex) {
                invitationTableAvailable = false;
            }
            return invitationTableAvailable;
        }
    }

    public List<GroupInvitation> findPendingByGroup(int groupId) {
        if (!isInvitationTableAvailable()) {
            return List.of();
        }
        try {
            String sql = BASE_SELECT +
                    "WHERE gi.GroupID = ? AND gi.Status = 'PENDING' " +
                    "ORDER BY gi.InvitedDate DESC";
            return db.query(sql, (rs, rn) -> mapInvitation(rs), groupId);
        } catch (Exception ex) {
            return List.of();
        }
    }

    public List<GroupInvitation> findPendingByGroupForLeaderApproval(int groupId, int leaderId) {
        if (!isInvitationTableAvailable()) {
            return List.of();
        }
        try {
            String sql = BASE_SELECT +
                    "WHERE gi.GroupID = ? AND gi.Status = 'PENDING' AND gi.InvitedByStudentID <> ? " +
                    "ORDER BY gi.InvitedDate DESC";
            return db.query(sql, (rs, rn) -> mapInvitation(rs), groupId, leaderId);
        } catch (Exception ex) {
            return List.of();
        }
    }

    public List<GroupInvitation> findPendingByGroupWaitingStudentConfirm(int groupId, int leaderId) {
        if (!isInvitationTableAvailable()) {
            return List.of();
        }
        try {
            String sql = BASE_SELECT +
                    "WHERE gi.GroupID = ? AND gi.Status = 'PENDING' AND gi.InvitedByStudentID = ? " +
                    "ORDER BY gi.InvitedDate DESC";
            return db.query(sql, (rs, rn) -> mapInvitation(rs), groupId, leaderId);
        } catch (Exception ex) {
            return List.of();
        }
    }

    public List<GroupInvitation> findPendingByStudent(int studentId) {
        if (!isInvitationTableAvailable()) {
            return List.of();
        }
        try {
            String sql = BASE_SELECT +
                    "WHERE gi.StudentID = ? AND gi.Status = 'PENDING' " +
                    "ORDER BY gi.InvitedDate DESC";
            return db.query(sql, (rs, rn) -> mapInvitation(rs), studentId);
        } catch (Exception ex) {
            return List.of();
        }
    }

    public List<GroupInvitation> findPendingByStudentFromLeader(int studentId) {
        if (!isInvitationTableAvailable()) {
            return List.of();
        }
        try {
            String sql = BASE_SELECT +
                    "WHERE gi.StudentID = ? AND gi.Status = 'PENDING' AND gi.InvitedByStudentID = g.LeaderID " +
                    "ORDER BY gi.InvitedDate DESC";
            return db.query(sql, (rs, rn) -> mapInvitation(rs), studentId);
        } catch (Exception ex) {
            return List.of();
        }
    }

    public List<GroupInvitation> findByStudentFromLeader(int studentId) {
        if (!isInvitationTableAvailable()) {
            return List.of();
        }
        try {
            String sql = BASE_SELECT +
                    "WHERE gi.StudentID = ? AND gi.InvitedByStudentID = g.LeaderID " +
                    "ORDER BY CASE WHEN gi.Status = 'PENDING' THEN 0 ELSE 1 END, " +
                    "COALESCE(gi.RespondedDate, gi.InvitedDate) DESC";
            return db.query(sql, (rs, rn) -> mapInvitation(rs), studentId);
        } catch (Exception ex) {
            return List.of();
        }
    }

    public List<GroupInvitation> findPendingForLeader(int leaderId) {
        if (!isInvitationTableAvailable()) {
            return List.of();
        }
        try {
            String sql = BASE_SELECT +
                    "WHERE g.LeaderID = ? AND gi.Status = 'PENDING' AND gi.InvitedByStudentID <> g.LeaderID " +
                    "ORDER BY gi.InvitedDate DESC";
            return db.query(sql, (rs, rn) -> mapInvitation(rs), leaderId);
        } catch (Exception ex) {
            return List.of();
        }
    }

    public List<GroupInvitation> findForLeader(int leaderId) {
        if (!isInvitationTableAvailable()) {
            return List.of();
        }
        try {
            String sql = BASE_SELECT +
                    "WHERE g.LeaderID = ? AND gi.InvitedByStudentID <> g.LeaderID " +
                    "ORDER BY CASE WHEN gi.Status = 'PENDING' THEN 0 ELSE 1 END, " +
                    "COALESCE(gi.RespondedDate, gi.InvitedDate) DESC";
            return db.query(sql, (rs, rn) -> mapInvitation(rs), leaderId);
        } catch (Exception ex) {
            return List.of();
        }
    }

    public List<GroupInvitation> findSentByStudent(int studentId) {
        if (!isInvitationTableAvailable()) {
            return List.of();
        }
        try {
            String sql = BASE_SELECT +
                    "WHERE gi.InvitedByStudentID = ? " +
                    "ORDER BY CASE WHEN gi.Status = 'PENDING' THEN 0 ELSE 1 END, " +
                    "COALESCE(gi.RespondedDate, gi.InvitedDate) DESC";
            return db.query(sql, (rs, rn) -> mapInvitation(rs), studentId);
        } catch (Exception ex) {
            return List.of();
        }
    }

    public int countPendingByStudentFromLeader(int studentId) {
        if (!isInvitationTableAvailable()) {
            return 0;
        }
        try {
            String sql = "SELECT COUNT(*) FROM Group_Invitations gi " +
                    "INNER JOIN Groups g ON g.GroupID = gi.GroupID " +
                    "WHERE gi.StudentID = ? AND gi.Status = 'PENDING' AND gi.InvitedByStudentID = g.LeaderID";
            Integer count = db.queryForObject(sql, Integer.class, studentId);
            return count != null ? count : 0;
        } catch (Exception ex) {
            return 0;
        }
    }

    public int countPendingForLeader(int leaderId) {
        if (!isInvitationTableAvailable()) {
            return 0;
        }
        try {
            String sql = "SELECT COUNT(*) FROM Group_Invitations gi " +
                    "INNER JOIN Groups g ON g.GroupID = gi.GroupID " +
                    "WHERE g.LeaderID = ? AND gi.Status = 'PENDING' AND gi.InvitedByStudentID <> g.LeaderID";
            Integer count = db.queryForObject(sql, Integer.class, leaderId);
            return count != null ? count : 0;
        } catch (Exception ex) {
            return 0;
        }
    }

    public int create(int groupId, int studentId, int invitedByStudentId) {
        if (!isInvitationTableAvailable()) {
            return -2;
        }
        try {
            String sql = "INSERT INTO Group_Invitations (GroupID, StudentID, InvitedByStudentID, Status, InvitedDate) " +
                    "OUTPUT INSERTED.InvitationID VALUES (?, ?, ?, 'PENDING', GETDATE())";
            Integer invId = db.queryForObject(sql, Integer.class, groupId, studentId, invitedByStudentId);
            return invId != null ? invId : -1;
        } catch (Exception ex) {
            return -1;
        }
    }

    public boolean existsPendingByGroupAndStudent(int groupId, int studentId) {
        if (!isInvitationTableAvailable()) {
            return false;
        }
        try {
            String sql = "SELECT COUNT(*) FROM Group_Invitations " +
                    "WHERE GroupID = ? AND StudentID = ? AND Status = 'PENDING'";
            Integer count = db.queryForObject(sql, Integer.class, groupId, studentId);
            return count != null && count > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    public int updateStatus(int invitationId, String status) {
        if (!isInvitationTableAvailable()) {
            return 0;
        }
        try {
            String sql = "UPDATE Group_Invitations SET Status = ?, RespondedDate = GETDATE() WHERE InvitationID = ?";
            return db.update(sql, status, invitationId);
        } catch (Exception ex) {
            return 0;
        }
    }

    public GroupInvitation findById(int invitationId) {
        if (!isInvitationTableAvailable()) {
            return null;
        }
        try {
            String sql = BASE_SELECT + "WHERE gi.InvitationID = ?";
            return db.queryForObject(sql, (rs, rn) -> mapInvitation(rs), invitationId);
        } catch (Exception ex) {
            return null;
        }
    }

    public int deleteByGroup(int groupId) {
        if (!isInvitationTableAvailable()) {
            return 0;
        }
        try {
            String sql = "DELETE FROM Group_Invitations WHERE GroupID = ?";
            return db.update(sql, groupId);
        } catch (Exception ex) {
            return 0;
        }
    }

    private GroupInvitation mapInvitation(java.sql.ResultSet rs) throws java.sql.SQLException {
        GroupInvitation inv = new GroupInvitation();
        inv.setInvitationId(rs.getInt("InvitationID"));
        inv.setGroupId(rs.getInt("GroupID"));
        inv.setStudentId(rs.getInt("StudentID"));
        inv.setInvitedByStudentId(rs.getInt("InvitedByStudentID"));
        inv.setStatus(rs.getString("Status"));
        inv.setGroupName(rs.getString("GroupName"));
        inv.setStudentName(rs.getString("StudentName"));
        inv.setStudentCode(rs.getString("StudentCode"));
        inv.setInvitedByName(rs.getString("InvitedByName"));
        inv.setInvitedByCode(rs.getString("InvitedByCode"));

        java.sql.Timestamp ts1 = rs.getTimestamp("InvitedDate");
        if (ts1 != null) {
            inv.setInvitedDate(ts1.toLocalDateTime());
        }

        java.sql.Timestamp ts2 = rs.getTimestamp("RespondedDate");
        if (ts2 != null) {
            inv.setRespondedDate(ts2.toLocalDateTime());
        }
        return inv;
    }
}

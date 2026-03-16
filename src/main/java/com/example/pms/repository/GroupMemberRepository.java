package com.example.pms.repository;

import com.example.pms.model.GroupMember;
import com.example.pms.model.Student;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class GroupMemberRepository {
    @Autowired
    private JdbcTemplate db;
    private static final int MAX_GROUP_MEMBERS = 4;

    public int addMember(int groupId, int studentId) {
        try {
            String sql = "INSERT INTO Group_Members (GroupID, StudentID, IsActive, JoinedDate) " +
                    "SELECT ?, ?, 1, GETDATE() " +
                    "WHERE NOT EXISTS ( " +
                    "    SELECT 1 FROM Group_Members " +
                    "    WHERE GroupID = ? AND StudentID = ? AND IsActive = 1 " +
                    ") " +
                    "AND ( " +
                    "    SELECT COUNT(DISTINCT StudentID) FROM Group_Members " +
                    "    WHERE GroupID = ? AND IsActive = 1 " +
                    ") < ?";
            return db.update(sql, groupId, studentId, groupId, studentId, groupId, MAX_GROUP_MEMBERS);
        } catch (Exception ex) {
            ex.printStackTrace();
            return 0;
        }
    }

    public List<GroupMember> findByGroupId(int groupId) {
        try {
            String sql = "SELECT * FROM Group_Members WHERE GroupID = ? AND IsActive = 1";
            return db.query(sql, (rs, rn) -> {
                GroupMember gm = new GroupMember();
                gm.setId(rs.getInt("ID"));
                gm.setGroupId(rs.getInt("GroupID"));
                gm.setStudentId(rs.getInt("StudentID"));
                gm.setActive(rs.getBoolean("IsActive"));
                
                java.sql.Timestamp ts = rs.getTimestamp("JoinedDate");
                if (ts != null) gm.setJoinedDate(ts.toLocalDateTime());
                
                return gm;
            }, groupId);
        } catch (Exception ex) {
            return List.of();
        }
    }

    public List<Student> findMemberDetailsOfGroup(int groupId) {
        try {
            String sql = "SELECT s.* FROM Students s " +
                    "INNER JOIN Group_Members gm ON s.StudentID = gm.StudentID " +
                    "WHERE gm.GroupID = ? AND gm.IsActive = 1";
            return db.query(sql, (rs, rn) -> {
                Student s = new Student();
                s.setStudentId(rs.getInt("StudentID"));
                s.setStudentCode(rs.getString("StudentCode"));
                s.setFullName(rs.getString("FullName"));
                s.setSchoolEmail(rs.getString("SchoolEmail"));
                s.setPhoneNumber(rs.getString("PhoneNumber"));
                int cid = rs.getInt("ClassID");
                if (!rs.wasNull()) s.setClassId(cid);
                int aid = rs.getInt("AccountID");
                if (!rs.wasNull()) s.setAccountId(aid);
                s.setAvatar(rs.getString("Avatar"));
                return s;
            }, groupId);
        } catch (Exception ex) {
            return List.of();
        }
    }

    public int removeMember(int groupId, int studentId) {
        try {
            String sql = "DELETE FROM Group_Members WHERE GroupID = ? AND StudentID = ?";
            return db.update(sql, groupId, studentId);
        } catch (Exception ex) {
            ex.printStackTrace();
            return 0;
        }
    }

    public int removeByGroup(int groupId) {
        try {
            String sql = "DELETE FROM Group_Members WHERE GroupID = ?";
            return db.update(sql, groupId);
        } catch (Exception ex) {
            ex.printStackTrace();
            return 0;
        }
    }

    public boolean isMember(int groupId, int studentId) {
        try {
            String sql = "SELECT COUNT(*) FROM Group_Members WHERE GroupID = ? AND StudentID = ? AND IsActive = 1";
            Integer count = db.queryForObject(sql, Integer.class, groupId, studentId);
            return count != null && count > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    public int countMembers(int groupId) {
        try {
            String sql = "SELECT COUNT(DISTINCT StudentID) FROM Group_Members WHERE GroupID = ? AND IsActive = 1";
            Integer count = db.queryForObject(sql, Integer.class, groupId);
            return count != null ? count : 0;
        } catch (Exception ex) {
            return 0;
        }
    }
}

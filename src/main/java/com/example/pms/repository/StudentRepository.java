package com.example.pms.repository;

import com.example.pms.model.Student;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StudentRepository {
    @Autowired
    private JdbcTemplate db;

    private volatile Boolean invitationTableAvailable;

    private boolean isInvitationTableAvailable() {
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

    public List<Student> findInvitableStudents(int classId, int semesterId, int groupId, int excludeStudentId) {
        try {
            String sql;
            Object[] params;
            if (isInvitationTableAvailable()) {
                sql = "SELECT s.* " +
                        "FROM Students s " +
                        "INNER JOIN Accounts a ON a.AccountID = s.AccountID " +
                        "WHERE s.ClassID = ? " +
                        "AND s.StudentID <> ? " +
                        "AND a.IsActive = 1 " +
                        "AND a.PasswordHash IS NOT NULL " +
                        "AND LEN(LTRIM(RTRIM(a.PasswordHash))) > 0 " +
                        "AND NOT EXISTS ( " +
                        "    SELECT 1 " +
                        "    FROM Group_Members gm " +
                        "    INNER JOIN Groups g ON g.GroupID = gm.GroupID " +
                        "    WHERE gm.StudentID = s.StudentID " +
                        "    AND gm.IsActive = 1 " +
                        "    AND g.SemesterID = ? " +
                        "    AND g.IsLocked = 0 " +
                        ") " +
                        "AND NOT EXISTS ( " +
                        "    SELECT 1 " +
                        "    FROM Group_Invitations gi " +
                        "    WHERE gi.GroupID = ? " +
                        "    AND gi.StudentID = s.StudentID " +
                        "    AND gi.Status = 'PENDING' " +
                        ") " +
                        "ORDER BY s.FullName ASC";
                params = new Object[] { classId, excludeStudentId, semesterId, groupId };
            } else {
                sql = "SELECT s.* " +
                        "FROM Students s " +
                        "INNER JOIN Accounts a ON a.AccountID = s.AccountID " +
                        "WHERE s.ClassID = ? " +
                        "AND s.StudentID <> ? " +
                        "AND a.IsActive = 1 " +
                        "AND a.PasswordHash IS NOT NULL " +
                        "AND LEN(LTRIM(RTRIM(a.PasswordHash))) > 0 " +
                        "AND NOT EXISTS ( " +
                        "    SELECT 1 " +
                        "    FROM Group_Members gm " +
                        "    INNER JOIN Groups g ON g.GroupID = gm.GroupID " +
                        "    WHERE gm.StudentID = s.StudentID " +
                        "    AND gm.IsActive = 1 " +
                        "    AND g.SemesterID = ? " +
                        "    AND g.IsLocked = 0 " +
                        ") " +
                        "ORDER BY s.FullName ASC";
                params = new Object[] { classId, excludeStudentId, semesterId };
            }

            return db.query(sql, (rs, rn) -> {
                Student s = new Student();
                s.setStudentId(rs.getInt("StudentID"));
                s.setStudentCode(rs.getString("StudentCode"));
                s.setFullName(rs.getString("FullName"));
                s.setSchoolEmail(rs.getString("SchoolEmail"));
                s.setPhoneNumber(rs.getString("PhoneNumber"));
                int cid = rs.getInt("ClassID");
                if (rs.wasNull()) s.setClassId(null); else s.setClassId(cid);
                int accId = rs.getInt("AccountID");
                if (rs.wasNull()) s.setAccountId(null); else s.setAccountId(accId);
                return s;
            }, params);
        } catch (Exception ex) {
            return List.of();
        }
    }

    public boolean isStudentActivated(int studentId) {
        try {
            String sql = "SELECT COUNT(*) " +
                    "FROM Students s " +
                    "INNER JOIN Accounts a ON a.AccountID = s.AccountID " +
                    "WHERE s.StudentID = ? " +
                    "AND a.IsActive = 1 " +
                    "AND a.PasswordHash IS NOT NULL " +
                    "AND LEN(LTRIM(RTRIM(a.PasswordHash))) > 0";
            Integer count = db.queryForObject(sql, Integer.class, studentId);
            return count != null && count > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    public Student findByStudentCode(String studentCode) {
        try {
            String sql = "SELECT * FROM Students WHERE UPPER(StudentCode) = UPPER(?)";
            return db.queryForObject(sql, (rs, rn) -> {
                Student s = new Student();
                s.setStudentId(rs.getInt("StudentID"));
                s.setStudentCode(rs.getString("StudentCode"));
                s.setFullName(rs.getString("FullName"));
                s.setSchoolEmail(rs.getString("SchoolEmail"));
                s.setPhoneNumber(rs.getString("PhoneNumber"));
                int cid = rs.getInt("ClassID");
                if (rs.wasNull()) s.setClassId(null); else s.setClassId(cid);
                int accId = rs.getInt("AccountID");
                if (rs.wasNull()) s.setAccountId(null); else s.setAccountId(accId);
                return s;
            }, studentCode);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public Student findById(int studentId) {
        try {
            String sql = "SELECT * FROM Students WHERE StudentID = ?";
            return db.queryForObject(sql, (rs, rn) -> {
                Student s = new Student();
                s.setStudentId(rs.getInt("StudentID"));
                s.setStudentCode(rs.getString("StudentCode"));
                s.setFullName(rs.getString("FullName"));
                s.setSchoolEmail(rs.getString("SchoolEmail"));
                s.setPhoneNumber(rs.getString("PhoneNumber"));
                int cid = rs.getInt("ClassID");
                if (rs.wasNull()) s.setClassId(null); else s.setClassId(cid);
                int accId = rs.getInt("AccountID");
                if (rs.wasNull()) s.setAccountId(null); else s.setAccountId(accId);
                return s;
            }, studentId);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public Student findBySchoolEmail(String email){
        try{
            String sql = "SELECT * FROM Students WHERE SchoolEmail = ?";
            return db.queryForObject(sql, (rs, rn) -> {
                Student s = new Student();
                s.setStudentId(rs.getInt("StudentID"));
                s.setStudentCode(rs.getString("StudentCode"));
                s.setFullName(rs.getString("FullName"));
                s.setSchoolEmail(rs.getString("SchoolEmail"));
                s.setPhoneNumber(rs.getString("PhoneNumber"));
                int cid = rs.getInt("ClassID"); if(rs.wasNull()) s.setClassId(null); else s.setClassId(cid);
                int accId = rs.getInt("AccountID"); if(rs.wasNull()) s.setAccountId(null); else s.setAccountId(accId);
                return s;
            }, email);
        }catch(EmptyResultDataAccessException ex){
            return null;
        }
    }
}

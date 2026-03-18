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
                s.setAvatar(rs.getString("Avatar"));
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
                s.setAvatar(rs.getString("Avatar"));
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
                s.setAvatar(rs.getString("Avatar"));
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
                s.setAvatar(rs.getString("Avatar"));
                return s;
            }, email);
        }catch(EmptyResultDataAccessException ex){
            return null;
        }
    }

    public Student findByPhoneNumber(String phoneNumber) {
        try {
            String sql = "SELECT * FROM Students WHERE PhoneNumber = ?";
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
                s.setAvatar(rs.getString("Avatar"));
                return s;
            }, phoneNumber);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public int createStudent(String studentCode, String fullName, String schoolEmail, String phoneNumber, int classId, int accountId) {
        try {
            String sql = "INSERT INTO Students (StudentCode, FullName, SchoolEmail, PhoneNumber, ClassID, AccountID) " +
                    "OUTPUT INSERTED.StudentID VALUES (?, ?, ?, ?, ?, ?)";
            Integer studentId = db.queryForObject(sql, Integer.class,
                    studentCode, fullName, schoolEmail, phoneNumber, classId, accountId);
            return studentId != null ? studentId : -1;
        } catch (Exception ex) {
            return -1;
        }
    }

    public int updateStudentInfo(int studentId, String fullName, String schoolEmail, String phoneNumber, Integer classId) {
        try {
            String sql = "UPDATE Students SET FullName = ?, SchoolEmail = ?, PhoneNumber = ?, ClassID = ? WHERE StudentID = ?";
            return db.update(sql, fullName, schoolEmail, phoneNumber, classId, studentId);
        } catch (Exception ex) {
            return 0;
        }
    }

    public int updatePhoneNumber(int studentId, String phoneNumber) {
        try {
            String sql = "UPDATE Students SET PhoneNumber = ? WHERE StudentID = ?";
            return db.update(sql, phoneNumber, studentId);
        } catch (Exception ex) {
            return 0;
        }
    }

    public int updateAvatar(int studentId, String avatar) {
        try {
            String sql = "UPDATE Students SET Avatar = ? WHERE StudentID = ?";
            return db.update(sql, avatar, studentId);
        } catch (Exception ex) {
            return 0;
        }
    }

    public int linkAccount(int studentId, int accountId) {
        try {
            String sql = "UPDATE Students SET AccountID = ? WHERE StudentID = ?";
            return db.update(sql, accountId, studentId);
        } catch (Exception ex) {
            return 0;
        }
    }
}

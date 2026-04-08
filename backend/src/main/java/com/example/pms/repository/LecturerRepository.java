package com.example.pms.repository;

import com.example.pms.model.Lecturer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LecturerRepository {

    @Autowired
    private JdbcTemplate db;

    public Lecturer findBySchoolEmail(String email) {
        try {
            String sql = "SELECT * FROM Lecturers WHERE SchoolEmail = ?";
            return db.queryForObject(sql, (rs, rowNum) -> {
                Lecturer l = new Lecturer();
                l.setLecturerId(rs.getInt("LecturerID"));
                l.setLecturerCode(rs.getString("LecturerCode"));
                l.setFullName(rs.getString("FullName"));
                l.setSchoolEmail(rs.getString("SchoolEmail"));
                l.setPhoneNumber(rs.getString("PhoneNumber"));
                
                int accId = rs.getInt("AccountID");
                if (rs.wasNull()) {
                    l.setAccountId(null);
                } else {
                    l.setAccountId(accId);
                }
                
                return l;
            }, email);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public int linkAccount(int lecturerId, int accountId) {
        try {
            String sql = "UPDATE Lecturers SET AccountID = ? WHERE LecturerID = ?";
            return db.update(sql, accountId, lecturerId);
        } catch (Exception ex) {
            return 0;
        }
    }

    public Lecturer findByAccountId(int accountId) {
        try {
            String sql = "SELECT * FROM Lecturers WHERE AccountID = ?";
            return db.queryForObject(sql, (rs, rowNum) -> {
                Lecturer l = new Lecturer();
                l.setLecturerId(rs.getInt("LecturerID"));
                l.setLecturerCode(rs.getString("LecturerCode"));
                l.setFullName(rs.getString("FullName"));
                l.setSchoolEmail(rs.getString("SchoolEmail"));
                l.setPhoneNumber(rs.getString("PhoneNumber"));
                int accId = rs.getInt("AccountID");
                if (rs.wasNull()) {
                    l.setAccountId(null);
                } else {
                    l.setAccountId(accId);
                }
                return l;
            }, accountId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public java.util.List<Lecturer> findByClassId(int classId) {
        try {
            String sql = "SELECT DISTINCT l.LecturerID, l.LecturerCode, l.FullName, l.SchoolEmail, l.PhoneNumber, l.AccountID " +
                         "FROM Class_Lecturers cl INNER JOIN Lecturers l ON l.LecturerID = cl.LecturerID " +
                         "WHERE cl.ClassID = ? ORDER BY l.FullName ASC";
            return db.query(sql, (rs, rn) -> {
                Lecturer l = new Lecturer();
                l.setLecturerId(rs.getInt("LecturerID"));
                l.setLecturerCode(rs.getString("LecturerCode"));
                l.setFullName(rs.getString("FullName"));
                l.setSchoolEmail(rs.getString("SchoolEmail"));
                l.setPhoneNumber(rs.getString("PhoneNumber"));
                int accId = rs.getInt("AccountID");
                if (rs.wasNull()) l.setAccountId(null); else l.setAccountId(accId);
                return l;
            }, classId);
        } catch (Exception ex) {
            return java.util.List.of();
        }
    }
}

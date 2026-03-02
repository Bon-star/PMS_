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
}
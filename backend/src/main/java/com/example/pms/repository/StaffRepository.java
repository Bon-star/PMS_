package com.example.pms.repository;

import com.example.pms.model.Staff;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StaffRepository {

    @Autowired
    private JdbcTemplate db;

    public Staff findBySchoolEmail(String email) {
        try {
            String sql = "SELECT * FROM Staff WHERE SchoolEmail = ?";
            return db.queryForObject(sql, (rs, rowNum) -> {
                Staff s = new Staff();
                s.setStaffId(rs.getInt("StaffID"));
                s.setStaffCode(rs.getString("StaffCode"));
                s.setFullName(rs.getString("FullName"));
                s.setSchoolEmail(rs.getString("SchoolEmail"));
                s.setPhoneNumber(rs.getString("PhoneNumber"));
                
                int accId = rs.getInt("AccountID");
                if (rs.wasNull()) {
                    s.setAccountId(null);
                } else {
                    s.setAccountId(accId);
                }
                
                return s;
            }, email);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public int linkAccount(int staffId, int accountId) {
        try {
            String sql = "UPDATE Staff SET AccountID = ? WHERE StaffID = ?";
            return db.update(sql, accountId, staffId);
        } catch (Exception ex) {
            return 0;
        }
    }

    public Staff findByAccountId(int accountId) {
        try {
            String sql = "SELECT * FROM Staff WHERE AccountID = ?";
            return db.queryForObject(sql, (rs, rowNum) -> {
                Staff s = new Staff();
                s.setStaffId(rs.getInt("StaffID"));
                s.setStaffCode(rs.getString("StaffCode"));
                s.setFullName(rs.getString("FullName"));
                s.setSchoolEmail(rs.getString("SchoolEmail"));
                s.setPhoneNumber(rs.getString("PhoneNumber"));
                int accId = rs.getInt("AccountID");
                if (rs.wasNull()) {
                    s.setAccountId(null);
                } else {
                    s.setAccountId(accId);
                }
                return s;
            }, accountId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
}

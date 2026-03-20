package com.example.pms.repository;

import com.example.pms.model.Classes;
import java.sql.Types;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ClassRepository {
    @Autowired
    private JdbcTemplate db;

    public Classes findById(int classId) {
        try {
            String sql = "SELECT ClassID, ClassName, StartDate, EndDate FROM Classes WHERE ClassID = ?";
            return db.queryForObject(sql, (rs, rn) -> {
                Classes c = new Classes();
                c.setClassId(rs.getInt("ClassID"));
                c.setClassName(rs.getString("ClassName"));
                java.sql.Date start = rs.getDate("StartDate");
                java.sql.Date end = rs.getDate("EndDate");
                c.setStartDate(start != null ? start.toLocalDate() : null);
                c.setEndDate(end != null ? end.toLocalDate() : null);
                return c;
            }, classId);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public List<Classes> findAll() {
        String sql = "SELECT ClassID, ClassName, StartDate, EndDate FROM Classes ORDER BY ClassName ASC";
        return db.query(sql, (rs, rn) -> {
            Classes c = new Classes();
            c.setClassId(rs.getInt("ClassID"));
            c.setClassName(rs.getString("ClassName"));
            java.sql.Date start = rs.getDate("StartDate");
            java.sql.Date end = rs.getDate("EndDate");
            c.setStartDate(start != null ? start.toLocalDate() : null);
            c.setEndDate(end != null ? end.toLocalDate() : null);
            return c;
        });
    }

    public int createClass(String className, java.time.LocalDate startDate, java.time.LocalDate endDate) {
        String sql = "INSERT INTO Classes (ClassName, StartDate, EndDate) VALUES (?, ?, ?)";
        org.springframework.jdbc.support.GeneratedKeyHolder keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        db.update(con -> {
            java.sql.PreparedStatement ps = con.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, className);
            if (startDate != null) {
                ps.setDate(2, java.sql.Date.valueOf(startDate));
            } else {
                ps.setNull(2, Types.DATE);
            }
            if (endDate != null) {
                ps.setDate(3, java.sql.Date.valueOf(endDate));
            } else {
                ps.setNull(3, Types.DATE);
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.intValue() : -1;
    }

    public int updateClass(int classId, String className, java.time.LocalDate startDate, java.time.LocalDate endDate) {
        String sql = "UPDATE Classes SET ClassName = ?, StartDate = ?, EndDate = ? WHERE ClassID = ?";
        return db.update(con -> {
            java.sql.PreparedStatement ps = con.prepareStatement(sql);
            ps.setString(1, className);
            if (startDate != null) {
                ps.setDate(2, java.sql.Date.valueOf(startDate));
            } else {
                ps.setNull(2, Types.DATE);
            }
            if (endDate != null) {
                ps.setDate(3, java.sql.Date.valueOf(endDate));
            } else {
                ps.setNull(3, Types.DATE);
            }
            ps.setInt(4, classId);
            return ps;
        });
    }

    public int deleteById(int classId) {
        String sql = "DELETE FROM Classes WHERE ClassID = ?";
        return db.update(sql, classId);
    }
}

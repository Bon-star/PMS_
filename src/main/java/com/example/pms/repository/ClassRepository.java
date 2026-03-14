package com.example.pms.repository;

import com.example.pms.model.Classes;
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
            String sql = "SELECT * FROM Classes WHERE ClassID = ?";
            return db.queryForObject(sql, (rs, rn) -> {
                Classes c = new Classes();
                c.setClassId(rs.getInt("ClassID"));
                c.setClassName(rs.getString("ClassName"));
                c.setCourseYear(rs.getString("CourseYear"));
                return c;
            }, classId);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public List<Classes> findAll() {
        String sql = "SELECT * FROM Classes ORDER BY ClassName ASC";
        return db.query(sql, (rs, rn) -> {
            Classes c = new Classes();
            c.setClassId(rs.getInt("ClassID"));
            c.setClassName(rs.getString("ClassName"));
            c.setCourseYear(rs.getString("CourseYear"));
            return c;
        });
    }

    public int createClass(String className, String courseYear) {
        String sql = "INSERT INTO Classes (ClassName, CourseYear) VALUES (?, ?)";
        org.springframework.jdbc.support.GeneratedKeyHolder keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        db.update(con -> {
            java.sql.PreparedStatement ps = con.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, className);
            ps.setString(2, courseYear);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.intValue() : -1;
    }

    public int updateClass(int classId, String className, String courseYear) {
        String sql = "UPDATE Classes SET ClassName = ?, CourseYear = ? WHERE ClassID = ?";
        return db.update(sql, className, courseYear, classId);
    }

    public int deleteById(int classId) {
        String sql = "DELETE FROM Classes WHERE ClassID = ?";
        return db.update(sql, classId);
    }
}

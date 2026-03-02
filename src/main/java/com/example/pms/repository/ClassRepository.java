package com.example.pms.repository;

import com.example.pms.model.Classes;
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
}

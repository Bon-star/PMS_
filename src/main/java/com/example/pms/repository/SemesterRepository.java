package com.example.pms.repository;

import com.example.pms.model.Semester;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SemesterRepository {
    @Autowired
    private JdbcTemplate db;

    public Semester findCurrentSemester() {
        try {
            String sql = "SELECT TOP 1 * FROM Semesters WHERE GETDATE() BETWEEN StartDate AND EndDate ORDER BY StartDate DESC";
            return db.queryForObject(sql, (rs, rn) -> {
                Semester s = new Semester();
                s.setSemesterId(rs.getInt("SemesterID"));
                s.setSemesterName(rs.getString("SemesterName"));
                s.setStartDate(rs.getDate("StartDate").toLocalDate());
                s.setEndDate(rs.getDate("EndDate").toLocalDate());
                return s;
            });
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public Semester findById(int semesterId) {
        try {
            String sql = "SELECT * FROM Semesters WHERE SemesterID = ?";
            return db.queryForObject(sql, (rs, rn) -> {
                Semester s = new Semester();
                s.setSemesterId(rs.getInt("SemesterID"));
                s.setSemesterName(rs.getString("SemesterName"));
                s.setStartDate(rs.getDate("StartDate").toLocalDate());
                s.setEndDate(rs.getDate("EndDate").toLocalDate());
                return s;
            }, semesterId);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public List<Semester> findAll() {
        String sql = "SELECT * FROM Semesters ORDER BY StartDate DESC, SemesterID DESC";
        return db.query(sql, (rs, rn) -> {
            Semester s = new Semester();
            s.setSemesterId(rs.getInt("SemesterID"));
            s.setSemesterName(rs.getString("SemesterName"));
            java.sql.Date start = rs.getDate("StartDate");
            java.sql.Date end = rs.getDate("EndDate");
            if (start != null) {
                s.setStartDate(start.toLocalDate());
            }
            if (end != null) {
                s.setEndDate(end.toLocalDate());
            }
            return s;
        });
    }
}

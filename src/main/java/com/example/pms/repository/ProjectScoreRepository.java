package com.example.pms.repository;

import com.example.pms.model.ProjectScore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ProjectScoreRepository {

    @Autowired
    private JdbcTemplate db;

    public List<ProjectScore> findByProjectId(int projectId) {
        String sql = "SELECT ps.ScoreID, ps.ProjectID, ps.StudentID, s.StudentCode, s.FullName as StudentName, " +
                "ps.LecturerID, l.FullName as LecturerName, ps.LecturerScore, ps.LecturerComment, " +
                "ps.StaffAdjustedScore, ps.StaffNote, ps.IsPublished " +
                "FROM Project_Scores ps " +
                "INNER JOIN Students s ON s.StudentID = ps.StudentID " +
                "LEFT JOIN Lecturers l ON l.LecturerID = ps.LecturerID " +
                "WHERE ps.ProjectID = ? ORDER BY s.FullName";
        return db.query(sql, (rs, rn) -> {
            ProjectScore score = new ProjectScore();
            score.setScoreId(rs.getInt("ScoreID"));
            score.setProjectId(rs.getInt("ProjectID"));
            score.setStudentId(rs.getInt("StudentID"));
            score.setStudentCode(rs.getString("StudentCode"));
            score.setStudentName(rs.getString("StudentName"));
            score.setLecturerId(rs.getInt("LecturerID"));
            score.setLecturerName(rs.getString("LecturerName"));
            score.setLecturerScore(rs.getDouble("LecturerScore"));
            score.setLecturerComment(rs.getString("LecturerComment"));
            score.setStaffAdjustedScore(rs.getObject("StaffAdjustedScore") != null ? rs.getDouble("StaffAdjustedScore") : null);
            score.setStaffNote(rs.getString("StaffNote"));
            score.setPublished(rs.getBoolean("IsPublished"));
            return score;
        }, projectId);
    }

    public ProjectScore findByProjectStudentLecturer(int projectId, int studentId, int lecturerId) {
        String sql = "SELECT TOP 1 ps.* , s.StudentCode, s.FullName as StudentName, l.FullName as LecturerName " +
                "FROM Project_Scores ps " +
                "INNER JOIN Students s ON s.StudentID = ps.StudentID " +
                "INNER JOIN Lecturers l ON l.LecturerID = ps.LecturerID " +
                "WHERE ps.ProjectID = ? AND ps.StudentID = ? AND ps.LecturerID = ?";
        try {
            return db.queryForObject(sql, (rs, rn) -> {
                ProjectScore score = new ProjectScore();
                score.setScoreId(rs.getInt("ScoreID"));
                score.setProjectId(rs.getInt("ProjectID"));
                score.setStudentId(rs.getInt("StudentID"));
                score.setStudentName(rs.getString("StudentName"));
                score.setStudentCode(rs.getString("StudentCode"));
                score.setLecturerId(rs.getInt("LecturerID"));
                score.setLecturerName(rs.getString("LecturerName"));
                score.setLecturerScore(rs.getObject("LecturerScore") != null ? rs.getDouble("LecturerScore") : null);
                score.setLecturerComment(rs.getString("LecturerComment"));
                score.setStaffAdjustedScore(rs.getObject("StaffAdjustedScore") != null ? rs.getDouble("StaffAdjustedScore") : null);
                score.setStaffNote(rs.getString("StaffNote"));
                score.setPublished(rs.getBoolean("IsPublished"));
                return score;
            }, projectId, studentId, lecturerId);
        } catch (Exception e) {
            return null;
        }
    }

    public int upsertLecturerScore(int projectId, int studentId, int lecturerId, Double score, String comment) {
        String sql = """
            MERGE Project_Scores AS target
            USING (SELECT ? as ProjectID, ? as StudentID, ? as LecturerID) AS source 
            ON target.ProjectID = source.ProjectID AND target.StudentID = source.StudentID AND target.LecturerID = source.LecturerID
            WHEN MATCHED THEN 
                UPDATE SET LecturerScore = ?, LecturerComment = ?
            WHEN NOT MATCHED THEN 
                INSERT (ProjectID, StudentID, LecturerID, LecturerScore, LecturerComment, IsPublished) 
                VALUES (source.ProjectID, source.StudentID, source.LecturerID, ?, ? , 0);
            """;
        return db.update(sql, projectId, studentId, lecturerId, score, comment, score, comment);
    }

    public int findGradedCountByProject(int projectId, int lecturerId) {
        String sql = "SELECT COUNT(*) FROM Project_Scores WHERE ProjectID = ? AND LecturerID = ?";
        Integer count = db.queryForObject(sql, Integer.class, projectId, lecturerId);
        return count != null ? count : 0;
    }
}


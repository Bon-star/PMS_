package com.example.pms.repository;

import com.example.pms.model.ProjectScoreRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectScoreRepository {

    @Autowired
    private JdbcTemplate db;

    private volatile boolean schemaEnsured;

    private void ensureSchema() {
        if (schemaEnsured) {
            return;
        }
        synchronized (this) {
            if (schemaEnsured) {
                return;
            }
            db.execute(
                    "IF OBJECT_ID('Project_Scores', 'U') IS NULL " +
                            "BEGIN " +
                            "   CREATE TABLE Project_Scores ( " +
                            "       ScoreID INT PRIMARY KEY IDENTITY(1,1), " +
                            "       ProjectID INT NOT NULL, " +
                            "       StudentID INT NOT NULL, " +
                            "       LecturerID INT NOT NULL, " +
                            "       LecturerScore FLOAT NULL, " +
                            "       LecturerComment NVARCHAR(MAX) NULL, " +
                            "       StaffAdjustedScore FLOAT NULL, " +
                            "       StaffNote NVARCHAR(MAX) NULL, " +
                            "       IsPublished BIT NOT NULL DEFAULT 0, " +
                            "       CONSTRAINT FK_ProjectScores_Projects FOREIGN KEY (ProjectID) REFERENCES Projects(ProjectID), " +
                            "       CONSTRAINT FK_ProjectScores_Students FOREIGN KEY (StudentID) REFERENCES Students(StudentID), " +
                            "       CONSTRAINT FK_ProjectScores_Lecturers FOREIGN KEY (LecturerID) REFERENCES Lecturers(LecturerID) " +
                            "   ); " +
                            "END");
            schemaEnsured = true;
        }
    }

    private ProjectScoreRecord mapScore(java.sql.ResultSet rs) throws java.sql.SQLException {
        ProjectScoreRecord score = new ProjectScoreRecord();
        score.setScoreId(rs.getInt("ScoreID"));
        score.setProjectId(rs.getInt("ProjectID"));
        score.setStudentId(rs.getInt("StudentID"));
        score.setLecturerId(rs.getInt("LecturerID"));

        double lecturerScore = rs.getDouble("LecturerScore");
        score.setLecturerScore(rs.wasNull() ? null : lecturerScore);

        score.setLecturerComment(rs.getString("LecturerComment"));

        double staffAdjustedScore = rs.getDouble("StaffAdjustedScore");
        score.setStaffAdjustedScore(rs.wasNull() ? null : staffAdjustedScore);

        score.setStaffNote(rs.getString("StaffNote"));
        score.setPublished(rs.getBoolean("IsPublished"));
        return score;
    }

    public ProjectScoreRecord findLatestByProjectAndStudent(int projectId, int studentId) {
        ensureSchema();
        try {
            String sql = "SELECT TOP 1 ScoreID, ProjectID, StudentID, LecturerID, LecturerScore, LecturerComment, " +
                    "StaffAdjustedScore, StaffNote, IsPublished " +
                    "FROM Project_Scores " +
                    "WHERE ProjectID = ? AND StudentID = ? " +
                    "ORDER BY ScoreID DESC";
            return db.queryForObject(sql, (rs, rn) -> mapScore(rs), projectId, studentId);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }
}

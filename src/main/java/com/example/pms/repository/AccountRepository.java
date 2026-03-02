package com.example.pms.repository;

import com.example.pms.model.Account;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {
    @Autowired
    private JdbcTemplate db;

    public Account findByUsername(String email) {
        try {
            String sql = "SELECT AccountID, Username, PasswordHash, Role, IsActive, AuthProvider FROM Accounts WHERE Username = ?";
            return db.queryForObject(sql, (rs, rowNum) -> {
                Account acc = new Account();
                acc.setId(rs.getInt("AccountID"));
                acc.setEmail(rs.getString("Username"));
                acc.setPasswordHash(rs.getString("PasswordHash"));
                acc.setRole(rs.getString("Role"));
                acc.setIsActive(rs.getBoolean("IsActive"));
                acc.setAuthProvider(rs.getString("AuthProvider"));
                return acc;
            }, email);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public int save(Account account) {
        String sql = "INSERT INTO Accounts (Username, PasswordHash, Role, IsActive, AuthProvider) VALUES (?, ?, ?, ?, ?)";
        return db.update(sql, 
            account.getEmail(), 
            account.getPasswordHash(), 
            account.getRole(), 
            account.getIsActive(),
            account.getAuthProvider()
        );
    }

    public void updatePassword(String email, String newPasswordHash) {
        String sql = "UPDATE Accounts SET PasswordHash = ?, IsActive = 1 WHERE Username = ?";
        db.update(sql, newPasswordHash, email);
    }
}
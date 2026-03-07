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

    private Account mapAccount(java.sql.ResultSet rs) throws java.sql.SQLException {
        Account acc = new Account();
        acc.setId(rs.getInt("AccountID"));
        acc.setEmail(rs.getString("Username"));
        acc.setPasswordHash(rs.getString("PasswordHash"));
        acc.setRole(rs.getString("Role"));
        acc.setIsActive(rs.getBoolean("IsActive"));
        acc.setAuthProvider(rs.getString("AuthProvider"));
        return acc;
    }

    public Account findByUsername(String email) {
        try {
            String sql = "SELECT AccountID, Username, PasswordHash, Role, IsActive, AuthProvider FROM Accounts WHERE Username = ?";
            return db.queryForObject(sql, (rs, rowNum) -> mapAccount(rs), email);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public Account findById(int accountId) {
        try {
            String sql = "SELECT AccountID, Username, PasswordHash, Role, IsActive, AuthProvider FROM Accounts WHERE AccountID = ?";
            return db.queryForObject(sql, (rs, rowNum) -> mapAccount(rs), accountId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public int createLocalAccount(String email, String role) {
        try {
            String sql = "INSERT INTO Accounts (Username, PasswordHash, Role, IsActive, AuthProvider) " +
                    "OUTPUT INSERTED.AccountID VALUES (?, NULL, ?, 0, 'LOCAL')";
            Integer accountId = db.queryForObject(sql, Integer.class, email, role);
            return accountId != null ? accountId : -1;
        } catch (Exception ex) {
            return -1;
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

    public void updatePasswordById(int accountId, String newPasswordHash) {
        String sql = "UPDATE Accounts SET PasswordHash = ?, IsActive = 1 WHERE AccountID = ?";
        db.update(sql, newPasswordHash, accountId);
    }
}

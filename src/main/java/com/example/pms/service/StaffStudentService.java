package com.example.pms.service;

import com.example.pms.model.Account;
import com.example.pms.repository.AccountRepository;
import com.example.pms.repository.ClassRepository;
import com.example.pms.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffStudentService {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ClassRepository classRepository;

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    @Transactional
    public void createStudentWithAccount(String studentCode,
            String fullName,
            String schoolEmail,
            String phoneNumber,
            Integer classId) {

        String normalizedCode = normalize(studentCode);
        String normalizedName = normalize(fullName);
        String normalizedEmail = normalize(schoolEmail).toLowerCase();
        String normalizedPhone = normalize(phoneNumber);

        if (normalizedCode.isEmpty() || normalizedName.isEmpty() || normalizedEmail.isEmpty() || normalizedPhone.isEmpty()
                || classId == null) {
            throw new IllegalArgumentException("Vui lòng nhập đầy đủ thông tin học viên.");
        }

        if (normalizedCode.length() > 20 || normalizedName.length() > 100 || normalizedEmail.length() > 100
                || normalizedPhone.length() > 15) {
            throw new IllegalArgumentException("Thông tin nhập quá độ dài cho phép.");
        }

        if (!normalizedEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            throw new IllegalArgumentException("Email không đúng định dạng.");
        }

        if (classRepository.findById(classId) == null) {
            throw new IllegalArgumentException("Lớp không tồn tại.");
        }

        if (studentRepository.findByStudentCode(normalizedCode) != null) {
            throw new IllegalArgumentException("Mã học viên đã tồn tại.");
        }
        if (studentRepository.findBySchoolEmail(normalizedEmail) != null) {
            throw new IllegalArgumentException("Email học viên đã tồn tại.");
        }
        if (studentRepository.findByPhoneNumber(normalizedPhone) != null) {
            throw new IllegalArgumentException("Số điện thoại đã tồn tại.");
        }

        Account account = accountRepository.findByUsername(normalizedEmail);
        if (account != null) {
            throw new IllegalArgumentException("Email này đã có tài khoản trong hệ thống.");
        }

        int accountId = accountRepository.createLocalAccount(normalizedEmail, "Student");
        if (accountId <= 0) {
            throw new IllegalStateException("Không thể tạo tài khoản học viên.");
        }

        int studentId = studentRepository.createStudent(
                normalizedCode,
                normalizedName,
                normalizedEmail,
                normalizedPhone,
                classId,
                accountId);
        if (studentId <= 0) {
            throw new IllegalStateException("Không thể tạo hồ sơ học viên.");
        }
    }
}

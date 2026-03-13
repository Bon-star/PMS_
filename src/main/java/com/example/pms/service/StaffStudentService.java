package com.example.pms.service;

import com.example.pms.model.Account;
import com.example.pms.model.Student;
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

    public Student findByReference(String reference) {
        String normalizedRef = normalize(reference);
        if (normalizedRef.isEmpty()) {
            throw new IllegalArgumentException("Vui long nhap ma hoc vien hoac email.");
        }

        Student student = null;
        if (normalizedRef.contains("@")) {
            student = studentRepository.findBySchoolEmail(normalizedRef.toLowerCase());
        } else if (normalizedRef.matches("\\d+")) {
            try {
                student = studentRepository.findById(Integer.parseInt(normalizedRef));
            } catch (NumberFormatException ex) {
                student = null;
            }
        }

        if (student == null) {
            student = studentRepository.findByStudentCode(normalizedRef);
        }

        if (student == null) {
            throw new IllegalArgumentException("Khong tim thay hoc vien.");
        }

        return student;
    }

    @Transactional
    public Student updateStudentInfo(int studentId,
            String fullName,
            String schoolEmail,
            String phoneNumber,
            Integer classId) {

        Student existing = studentRepository.findById(studentId);
        if (existing == null) {
            throw new IllegalArgumentException("Khong tim thay hoc vien.");
        }

        String normalizedName = normalize(fullName);
        String normalizedEmail = normalize(schoolEmail).toLowerCase();
        String normalizedPhone = normalize(phoneNumber);
        Integer normalizedClassId = classId;

        if (normalizedName.isEmpty()) {
            normalizedName = normalize(existing.getFullName());
        }
        if (normalizedEmail.isEmpty()) {
            normalizedEmail = normalize(existing.getSchoolEmail()).toLowerCase();
        }
        if (normalizedPhone.isEmpty()) {
            normalizedPhone = normalize(existing.getPhoneNumber());
        }
        if (normalizedClassId == null) {
            normalizedClassId = existing.getClassId();
        }

        if (normalizedName.isEmpty() || normalizedEmail.isEmpty() || normalizedPhone.isEmpty()) {
            throw new IllegalArgumentException("Vui long nhap day du thong tin hoc vien.");
        }

        if (normalizedName.length() > 100 || normalizedEmail.length() > 100 || normalizedPhone.length() > 15) {
            throw new IllegalArgumentException("Thong tin nhap qua do dai cho phep.");
        }

        if (!normalizedEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            throw new IllegalArgumentException("Email khong dung dinh dang.");
        }

        if (normalizedClassId != null && classRepository.findById(normalizedClassId) == null) {
            throw new IllegalArgumentException("Lop khong ton tai.");
        }

        if (!normalizedEmail.equalsIgnoreCase(existing.getSchoolEmail())) {
            Student emailOwner = studentRepository.findBySchoolEmail(normalizedEmail);
            if (emailOwner != null && emailOwner.getStudentId() != existing.getStudentId()) {
                throw new IllegalArgumentException("Email hoc vien da ton tai.");
            }

            Account account = accountRepository.findByUsername(normalizedEmail);
            if (account != null) {
                Integer accountId = existing.getAccountId();
                if (accountId == null || account.getId() != accountId) {
                    throw new IllegalArgumentException("Email nay da co tai khoan trong he thong.");
                }
            }
        }

        if (!normalizedPhone.equals(existing.getPhoneNumber())) {
            Student phoneOwner = studentRepository.findByPhoneNumber(normalizedPhone);
            if (phoneOwner != null && phoneOwner.getStudentId() != existing.getStudentId()) {
                throw new IllegalArgumentException("So dien thoai da ton tai.");
            }
        }

        if (!normalizedEmail.equalsIgnoreCase(existing.getSchoolEmail())) {
            Integer accountId = existing.getAccountId();
            if (accountId == null) {
                int newAccountId = accountRepository.createLocalAccount(normalizedEmail, "Student");
                if (newAccountId <= 0) {
                    throw new IllegalStateException("Khong the tao tai khoan hoc vien.");
                }
                int linked = studentRepository.linkAccount(existing.getStudentId(), newAccountId);
                if (linked <= 0) {
                    throw new IllegalStateException("Khong the lien ket tai khoan hoc vien.");
                }
                existing.setAccountId(newAccountId);
            } else {
                int updatedAccount = accountRepository.updateUsernameById(accountId, normalizedEmail);
                if (updatedAccount <= 0) {
                    throw new IllegalStateException("Khong the cap nhat email dang nhap.");
                }
            }
        }

        int updated = studentRepository.updateStudentInfo(
                existing.getStudentId(),
                normalizedName,
                normalizedEmail,
                normalizedPhone,
                normalizedClassId);
        if (updated <= 0) {
            throw new IllegalStateException("Khong the cap nhat ho so hoc vien.");
        }

        existing.setFullName(normalizedName);
        existing.setSchoolEmail(normalizedEmail);
        existing.setPhoneNumber(normalizedPhone);
        existing.setClassId(normalizedClassId);
        return existing;
    }
}

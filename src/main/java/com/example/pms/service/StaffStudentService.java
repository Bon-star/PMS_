package com.example.pms.service;

import com.example.pms.model.Account;
import com.example.pms.model.Student;
import com.example.pms.repository.AccountRepository;
import com.example.pms.repository.ClassRepository;
import com.example.pms.repository.StudentRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffStudentService {

    public static class ImportResult {
        private final int successCount;
        private final List<String> errors;

        public ImportResult(int successCount, List<String> errors) {
            this.successCount = successCount;
            this.errors = errors == null ? List.of() : errors;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public List<String> getErrors() {
            return errors;
        }

        public boolean hasErrors() {
            return errors != null && !errors.isEmpty();
        }
    }

    private static class ImportRow {
        private final String studentCode;
        private final String fullName;
        private final String schoolEmail;
        private final String phoneNumber;

        private ImportRow(String studentCode, String fullName, String schoolEmail, String phoneNumber) {
            this.studentCode = studentCode;
            this.fullName = fullName;
            this.schoolEmail = schoolEmail;
            this.phoneNumber = phoneNumber;
        }
    }

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ClassRepository classRepository;

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeHeader(String value) {
        String normalized = normalize(value).toLowerCase(Locale.ROOT);
        normalized = normalized.replace(" ", "").replace("_", "");
        return normalized;
    }

    private boolean isEmailValid(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
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

    @Transactional
    public ImportResult importStudentsFromExcel(InputStream inputStream, Integer classId) throws IOException {
        List<String> errors = new ArrayList<>();
        if (classId == null || classRepository.findById(classId) == null) {
            errors.add("Lop khong ton tai.");
            return new ImportResult(0, errors);
        }

        List<ImportRow> rows = new ArrayList<>();
        Set<String> seenCodes = new HashSet<>();
        Set<String> seenEmails = new HashSet<>();
        Set<String> seenPhones = new HashSet<>();

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                errors.add("Khong tim thay sheet trong file.");
                return new ImportResult(0, errors);
            }

            int headerRowIndex = sheet.getFirstRowNum();
            Row headerRow = sheet.getRow(headerRowIndex);
            if (headerRow == null) {
                errors.add("Khong tim thay dong tieu de.");
                return new ImportResult(0, errors);
            }

            DataFormatter formatter = new DataFormatter();
            Map<String, Integer> headerIndex = new HashMap<>();
            for (Cell cell : headerRow) {
                String header = normalizeHeader(formatter.formatCellValue(cell));
                if (!header.isEmpty() && !headerIndex.containsKey(header)) {
                    headerIndex.put(header, cell.getColumnIndex());
                }
            }

            String[] requiredHeaders = new String[] { "studentcode", "fullname", "schoolemail", "phonenumber" };
            for (String required : requiredHeaders) {
                if (!headerIndex.containsKey(required)) {
                    errors.add("Thieu cot bat buoc: " + required);
                }
            }
            if (!errors.isEmpty()) {
                return new ImportResult(0, errors);
            }

            int lastRow = sheet.getLastRowNum();
            for (int i = headerRowIndex + 1; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

                String studentCode = normalize(formatter.formatCellValue(row.getCell(headerIndex.get("studentcode"))));
                String fullName = normalize(formatter.formatCellValue(row.getCell(headerIndex.get("fullname"))));
                String schoolEmail = normalize(formatter.formatCellValue(row.getCell(headerIndex.get("schoolemail")))).toLowerCase(Locale.ROOT);
                String phoneNumber = normalize(formatter.formatCellValue(row.getCell(headerIndex.get("phonenumber"))));

                boolean allBlank = studentCode.isEmpty() && fullName.isEmpty() && schoolEmail.isEmpty() && phoneNumber.isEmpty();
                if (allBlank) {
                    continue;
                }

                List<String> rowErrors = new ArrayList<>();
                if (studentCode.isEmpty()) {
                    rowErrors.add("Thieu StudentCode");
                }
                if (fullName.isEmpty()) {
                    rowErrors.add("Thieu FullName");
                }
                if (schoolEmail.isEmpty()) {
                    rowErrors.add("Thieu SchoolEmail");
                }
                if (phoneNumber.isEmpty()) {
                    rowErrors.add("Thieu PhoneNumber");
                }

                if (!studentCode.isEmpty() && studentCode.length() > 20) {
                    rowErrors.add("StudentCode qua dai");
                }
                if (!fullName.isEmpty() && fullName.length() > 100) {
                    rowErrors.add("FullName qua dai");
                }
                if (!schoolEmail.isEmpty() && schoolEmail.length() > 100) {
                    rowErrors.add("SchoolEmail qua dai");
                }
                if (!phoneNumber.isEmpty() && phoneNumber.length() > 15) {
                    rowErrors.add("PhoneNumber qua dai");
                }
                if (!schoolEmail.isEmpty() && !isEmailValid(schoolEmail)) {
                    rowErrors.add("SchoolEmail sai dinh dang");
                }

                String codeKey = studentCode.toUpperCase(Locale.ROOT);
                if (!studentCode.isEmpty() && !seenCodes.add(codeKey)) {
                    rowErrors.add("StudentCode trung trong file");
                }
                if (!schoolEmail.isEmpty() && !seenEmails.add(schoolEmail)) {
                    rowErrors.add("SchoolEmail trung trong file");
                }
                if (!phoneNumber.isEmpty() && !seenPhones.add(phoneNumber)) {
                    rowErrors.add("PhoneNumber trung trong file");
                }

                if (rowErrors.isEmpty()) {
                    if (studentRepository.findByStudentCode(studentCode) != null) {
                        rowErrors.add("StudentCode da ton tai");
                    }
                    if (studentRepository.findBySchoolEmail(schoolEmail) != null) {
                        rowErrors.add("SchoolEmail da ton tai");
                    }
                    if (studentRepository.findByPhoneNumber(phoneNumber) != null) {
                        rowErrors.add("PhoneNumber da ton tai");
                    }
                    Account existing = accountRepository.findByUsername(schoolEmail);
                    if (existing != null) {
                        rowErrors.add("Email da co tai khoan");
                    }
                }

                if (!rowErrors.isEmpty()) {
                    errors.add("Dong " + (i + 1) + ": " + String.join("; ", rowErrors));
                    continue;
                }

                rows.add(new ImportRow(studentCode, fullName, schoolEmail, phoneNumber));
            }
        } catch (Exception ex) {
            errors.add("Khong the doc file Excel.");
            return new ImportResult(0, errors);
        }

        if (!errors.isEmpty()) {
            return new ImportResult(0, errors);
        }
        if (rows.isEmpty()) {
            errors.add("Khong co dong du lieu hop le.");
            return new ImportResult(0, errors);
        }

        for (ImportRow row : rows) {
            int accountId = accountRepository.createLocalAccount(row.schoolEmail, "Student");
            if (accountId <= 0) {
                throw new IllegalStateException("Khong the tao tai khoan hoc vien.");
            }
            int studentId = studentRepository.createStudent(
                    row.studentCode,
                    row.fullName,
                    row.schoolEmail,
                    row.phoneNumber,
                    classId,
                    accountId);
            if (studentId <= 0) {
                throw new IllegalStateException("Khong the tao ho so hoc vien.");
            }
        }

        return new ImportResult(rows.size(), List.of());
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

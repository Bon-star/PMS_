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
            throw new IllegalArgumentException("Please provide all required student information.");
        }

        if (normalizedCode.length() > 20 || normalizedName.length() > 100 || normalizedEmail.length() > 100
                || normalizedPhone.length() > 15) {
            throw new IllegalArgumentException("Input exceeds allowed length.");
        }

        if (!normalizedEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            throw new IllegalArgumentException("Invalid email format.");
        }

        if (classRepository.findById(classId) == null) {
            throw new IllegalArgumentException("Class does not exist.");
        }

        if (studentRepository.findByStudentCode(normalizedCode) != null) {
            throw new IllegalArgumentException("Student code already exists.");
        }
        if (studentRepository.findBySchoolEmail(normalizedEmail) != null) {
            throw new IllegalArgumentException("Student email already exists.");
        }
        if (studentRepository.findByPhoneNumber(normalizedPhone) != null) {
            throw new IllegalArgumentException("Phone number already exists.");
        }

        Account account = accountRepository.findByUsername(normalizedEmail);
        if (account != null) {
            throw new IllegalArgumentException("This email already has an account in the system.");
        }

        int accountId = accountRepository.createLocalAccount(normalizedEmail, "Student");
        if (accountId <= 0) {
            throw new IllegalStateException("Unable to create student account.");
        }

        int studentId = studentRepository.createStudent(
                normalizedCode,
                normalizedName,
                normalizedEmail,
                normalizedPhone,
                classId,
                accountId);
        if (studentId <= 0) {
            throw new IllegalStateException("Unable to create student profile.");
        }
    }

    @Transactional
    public ImportResult importStudentsFromExcel(InputStream inputStream, Integer classId) throws IOException {
        List<String> errors = new ArrayList<>();
        if (classId == null || classRepository.findById(classId) == null) {
            errors.add("Class does not exist.");
            return new ImportResult(0, errors);
        }

        List<ImportRow> rows = new ArrayList<>();
        Set<String> seenCodes = new HashSet<>();
        Set<String> seenEmails = new HashSet<>();
        Set<String> seenPhones = new HashSet<>();

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                errors.add("No sheet found in the file.");
                return new ImportResult(0, errors);
            }

            int headerRowIndex = sheet.getFirstRowNum();
            Row headerRow = sheet.getRow(headerRowIndex);
            if (headerRow == null) {
                errors.add("Header row not found.");
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
                    errors.add("Missing required column: " + required);
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
                    rowErrors.add("Missing StudentCode");
                }
                if (fullName.isEmpty()) {
                    rowErrors.add("Missing FullName");
                }
                if (schoolEmail.isEmpty()) {
                    rowErrors.add("Missing SchoolEmail");
                }
                if (phoneNumber.isEmpty()) {
                    rowErrors.add("Missing PhoneNumber");
                }

                if (!studentCode.isEmpty() && studentCode.length() > 20) {
                    rowErrors.add("StudentCode too long");
                }
                if (!fullName.isEmpty() && fullName.length() > 100) {
                    rowErrors.add("FullName too long");
                }
                if (!schoolEmail.isEmpty() && schoolEmail.length() > 100) {
                    rowErrors.add("SchoolEmail too long");
                }
                if (!phoneNumber.isEmpty() && phoneNumber.length() > 15) {
                    rowErrors.add("PhoneNumber too long");
                }
                if (!schoolEmail.isEmpty() && !isEmailValid(schoolEmail)) {
                    rowErrors.add("SchoolEmail invalid format");
                }

                String codeKey = studentCode.toUpperCase(Locale.ROOT);
                if (!studentCode.isEmpty() && !seenCodes.add(codeKey)) {
                    rowErrors.add("Duplicate StudentCode in file");
                }
                if (!schoolEmail.isEmpty() && !seenEmails.add(schoolEmail)) {
                    rowErrors.add("Duplicate SchoolEmail in file");
                }
                if (!phoneNumber.isEmpty() && !seenPhones.add(phoneNumber)) {
                    rowErrors.add("Duplicate PhoneNumber in file");
                }

                if (rowErrors.isEmpty()) {
                    if (studentRepository.findByStudentCode(studentCode) != null) {
                        rowErrors.add("StudentCode already exists");
                    }
                    if (studentRepository.findBySchoolEmail(schoolEmail) != null) {
                        rowErrors.add("SchoolEmail already exists");
                    }
                    if (studentRepository.findByPhoneNumber(phoneNumber) != null) {
                        rowErrors.add("PhoneNumber already exists");
                    }
                    Account existing = accountRepository.findByUsername(schoolEmail);
                    if (existing != null) {
                        rowErrors.add("Email already has an account");
                    }
                }

                if (!rowErrors.isEmpty()) {
                    errors.add("Row " + (i + 1) + ": " + String.join("; ", rowErrors));
                    continue;
                }

                rows.add(new ImportRow(studentCode, fullName, schoolEmail, phoneNumber));
            }
        } catch (Exception ex) {
            errors.add("Unable to read Excel file.");
            return new ImportResult(0, errors);
        }

        if (!errors.isEmpty()) {
            return new ImportResult(0, errors);
        }
        if (rows.isEmpty()) {
            errors.add("No valid data rows.");
            return new ImportResult(0, errors);
        }

        for (ImportRow row : rows) {
            int accountId = accountRepository.createLocalAccount(row.schoolEmail, "Student");
            if (accountId <= 0) {
                throw new IllegalStateException("Unable to create student account.");
            }
            int studentId = studentRepository.createStudent(
                    row.studentCode,
                    row.fullName,
                    row.schoolEmail,
                    row.phoneNumber,
                    classId,
                    accountId);
            if (studentId <= 0) {
                throw new IllegalStateException("Unable to create student profile.");
            }
        }

        return new ImportResult(rows.size(), List.of());
    }

    public Student findByReference(String reference) {
        String normalizedRef = normalize(reference);
        if (normalizedRef.isEmpty()) {
            throw new IllegalArgumentException("Please enter a student code or email.");
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
            throw new IllegalArgumentException("Student not found.");
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
            throw new IllegalArgumentException("Student not found.");
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
            throw new IllegalArgumentException("Please provide all required student information.");
        }

        if (normalizedName.length() > 100 || normalizedEmail.length() > 100 || normalizedPhone.length() > 15) {
            throw new IllegalArgumentException("Input exceeds allowed length.");
        }

        if (!normalizedEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            throw new IllegalArgumentException("Invalid email format.");
        }

        if (normalizedClassId != null && classRepository.findById(normalizedClassId) == null) {
            throw new IllegalArgumentException("Class does not exist.");
        }

        if (!normalizedEmail.equalsIgnoreCase(existing.getSchoolEmail())) {
            Student emailOwner = studentRepository.findBySchoolEmail(normalizedEmail);
            if (emailOwner != null && emailOwner.getStudentId() != existing.getStudentId()) {
                throw new IllegalArgumentException("Student email already exists.");
            }

            Account account = accountRepository.findByUsername(normalizedEmail);
            if (account != null) {
                Integer accountId = existing.getAccountId();
                if (accountId == null || account.getId() != accountId) {
                    throw new IllegalArgumentException("This email already has an account in the system.");
                }
            }
        }

        if (!normalizedPhone.equals(existing.getPhoneNumber())) {
            Student phoneOwner = studentRepository.findByPhoneNumber(normalizedPhone);
            if (phoneOwner != null && phoneOwner.getStudentId() != existing.getStudentId()) {
                throw new IllegalArgumentException("Phone number already exists.");
            }
        }

        if (!normalizedEmail.equalsIgnoreCase(existing.getSchoolEmail())) {
            Integer accountId = existing.getAccountId();
            if (accountId == null) {
                int newAccountId = accountRepository.createLocalAccount(normalizedEmail, "Student");
                if (newAccountId <= 0) {
                    throw new IllegalStateException("Unable to create student account.");
                }
                int linked = studentRepository.linkAccount(existing.getStudentId(), newAccountId);
                if (linked <= 0) {
                    throw new IllegalStateException("Unable to link student account.");
                }
                existing.setAccountId(newAccountId);
            } else {
                int updatedAccount = accountRepository.updateUsernameById(accountId, normalizedEmail);
                if (updatedAccount <= 0) {
                    throw new IllegalStateException("Unable to update login email.");
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
            throw new IllegalStateException("Unable to update student profile.");
        }

        existing.setFullName(normalizedName);
        existing.setSchoolEmail(normalizedEmail);
        existing.setPhoneNumber(normalizedPhone);
        existing.setClassId(normalizedClassId);
        return existing;
    }
}

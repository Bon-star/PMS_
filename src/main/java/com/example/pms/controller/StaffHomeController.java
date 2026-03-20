package com.example.pms.controller;

import com.example.pms.model.Account;
import com.example.pms.model.Student;
import com.example.pms.repository.ClassRepository;
import com.example.pms.service.StaffStudentService;
import com.example.pms.util.RoleDisplayUtil;
import jakarta.servlet.http.HttpSession;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/staff")
public class StaffHomeController {

    private static final long MAX_EXCEL_BYTES = 10L * 1024L * 1024L;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private StaffStudentService staffStudentService;

    private boolean isStaffSession(HttpSession session) {
        Account account = (Account) session.getAttribute("account");
        return account != null && "Staff".equalsIgnoreCase(account.getRole());
    }

    private void bindCommon(Model model, HttpSession session) {
        Object fullName = session.getAttribute("fullName");
        model.addAttribute("staffName", fullName != null ? fullName : "Nh\u00e2n vi\u00ean");
        model.addAttribute("displayName", fullName != null ? fullName : "Nh\u00e2n vi\u00ean");
        model.addAttribute("displayRole", RoleDisplayUtil.toDisplayRole("Staff"));
        model.addAttribute("classes", classRepository.findAll());
    }

    private void bindEditStudent(Model model, Student student) {
        if (student == null) {
            return;
        }
        model.addAttribute("editStudentId", student.getStudentId());
        model.addAttribute("editStudentCode", student.getStudentCode());
        model.addAttribute("editFullName", student.getFullName());
        model.addAttribute("editSchoolEmail", student.getSchoolEmail());
        model.addAttribute("editPhoneNumber", student.getPhoneNumber());
        model.addAttribute("editSelectedClassId", student.getClassId());
    }

    @GetMapping("/home")
    public String index(Model model, HttpSession session) {
        if (!isStaffSession(session)) {
            return "redirect:/acc/log";
        }

        bindCommon(model, session);
        return "staff/home";
    }

    @GetMapping("/students")
    public String studentsPage(Model model, HttpSession session) {
        if (!isStaffSession(session)) {
            return "redirect:/acc/log";
        }

        bindCommon(model, session);
        return "staff/students";
    }

    @PostMapping("/students/create")
    public String createStudent(
            @RequestParam("studentCode") String studentCode,
            @RequestParam("fullName") String fullName,
            @RequestParam("schoolEmail") String schoolEmail,
            @RequestParam("phoneNumber") String phoneNumber,
            @RequestParam("classId") Integer classId,
            Model model,
            HttpSession session) {

        if (!isStaffSession(session)) {
            return "redirect:/acc/log";
        }

        bindCommon(model, session);
        model.addAttribute("addMode", "single");
        model.addAttribute("studentCode", studentCode);
        model.addAttribute("fullNameInput", fullName);
        model.addAttribute("schoolEmail", schoolEmail);
        model.addAttribute("phoneNumber", phoneNumber);
        model.addAttribute("selectedClassId", classId);

        try {
            staffStudentService.createStudentWithAccount(studentCode, fullName, schoolEmail, phoneNumber, classId);
            model.addAttribute("success",
                    "Đã tạo học viên và tài khoản thành công. Học viên có thể vào trang đăng ký để đặt mật khẩu.");

            model.addAttribute("studentCode", "");
            model.addAttribute("fullNameInput", "");
            model.addAttribute("schoolEmail", "");
            model.addAttribute("phoneNumber", "");
            model.addAttribute("selectedClassId", null);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            model.addAttribute("error", "Không thể tạo học viên. Vui lòng thử lại.");
        }

        return "staff/students";
    }

    @PostMapping("/students/import")
    public String importStudents(@RequestParam("excelFile") MultipartFile excelFile,
            @RequestParam("classId") Integer classId,
            Model model,
            HttpSession session) {

        if (!isStaffSession(session)) {
            return "redirect:/acc/log";
        }

        bindCommon(model, session);
        model.addAttribute("addMode", "bulk");
        model.addAttribute("bulkSelectedClassId", classId);

        if (excelFile == null || excelFile.isEmpty()) {
            model.addAttribute("error", "Vui long chon file Excel.");
            return "staff/students";
        }

        String fileName = excelFile.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".xlsx")) {
            model.addAttribute("error", "Chi ho tro file .xlsx.");
            return "staff/students";
        }

        if (excelFile.getSize() > MAX_EXCEL_BYTES) {
            model.addAttribute("error", "File Excel qua lon (toi da 10MB).");
            return "staff/students";
        }

        try (InputStream inputStream = excelFile.getInputStream()) {
            StaffStudentService.ImportResult result = staffStudentService.importStudentsFromExcel(inputStream, classId);
            if (result.hasErrors()) {
                model.addAttribute("error", "Import that bai. Vui long kiem tra file.");
                model.addAttribute("importErrors", result.getErrors());
                return "staff/students";
            }
            model.addAttribute("importSuccessCount", result.getSuccessCount());
            model.addAttribute("success", "Da import " + result.getSuccessCount() + " hoc vien.");
        } catch (Exception ex) {
            model.addAttribute("error", "Khong the import hoc vien. Vui long thu lai.");
        }

        return "staff/students";
    }

    private String handleLookup(String studentRef, Model model, HttpSession session) {
        bindCommon(model, session);
        model.addAttribute("studentRef", studentRef);

        try {
            Student student = staffStudentService.findByReference(studentRef);
            bindEditStudent(model, student);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            model.addAttribute("error", "Khong the tra cuu hoc vien. Vui long thu lai.");
        }

        return "staff/students";
    }

    @PostMapping("/students/lookup")
    public String lookupStudent(@RequestParam("studentRef") String studentRef,
            Model model,
            HttpSession session) {

        if (!isStaffSession(session)) {
            return "redirect:/acc/log";
        }

        return handleLookup(studentRef, model, session);
    }

    @GetMapping("/students/lookup")
    public String lookupStudentGet(
            @RequestParam(name = "studentRef", required = false) String studentRef,
            Model model,
            HttpSession session) {

        if (!isStaffSession(session)) {
            return "redirect:/acc/log";
        }

        if (studentRef == null || studentRef.trim().isEmpty()) {
            bindCommon(model, session);
            return "staff/students";
        }

        return handleLookup(studentRef, model, session);
    }

    @PostMapping("/students/update")
    public String updateStudent(@RequestParam("studentId") Integer studentId,
            @RequestParam("fullName") String fullName,
            @RequestParam("schoolEmail") String schoolEmail,
            @RequestParam("phoneNumber") String phoneNumber,
            @RequestParam("classId") Integer classId,
            Model model,
            HttpSession session) {

        if (!isStaffSession(session)) {
            return "redirect:/acc/log";
        }

        bindCommon(model, session);

        if (studentId == null || studentId <= 0) {
            model.addAttribute("error", "Vui long tra cuu hoc vien truoc khi cap nhat.");
            return "staff/students";
        }

        try {
            Student updated = staffStudentService.updateStudentInfo(
                    studentId,
                    fullName,
                    schoolEmail,
                    phoneNumber,
                    classId);
            bindEditStudent(model, updated);
            model.addAttribute("studentRef", updated.getStudentCode());
            model.addAttribute("success", "Da cap nhat thong tin hoc vien.");
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            try {
                Student existing = staffStudentService.findByReference(String.valueOf(studentId));
                bindEditStudent(model, existing);
                model.addAttribute("studentRef", existing.getStudentCode());
            } catch (Exception ignored) {
                // ignore
            }
        } catch (Exception ex) {
            model.addAttribute("error", "Khong the cap nhat hoc vien. Vui long thu lai.");
            try {
                Student existing = staffStudentService.findByReference(String.valueOf(studentId));
                bindEditStudent(model, existing);
                model.addAttribute("studentRef", existing.getStudentCode());
            } catch (Exception ignored) {
                // ignore
            }
        }

        return "staff/students";
    }

    @PostMapping("/students/update-ajax")
    @ResponseBody
    public java.util.Map<String, Object> updateStudentAjax(@RequestParam("studentId") Integer studentId,
                                                           @RequestParam("fullName") String fullName,
                                                           @RequestParam("schoolEmail") String schoolEmail,
                                                           @RequestParam("phoneNumber") String phoneNumber,
                                                           @RequestParam("classId") Integer classId,
                                                           HttpSession session) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        if (!isStaffSession(session)) {
            result.put("success", false);
            result.put("error", "Unauthorized");
            return result;
        }

        try {
            Student updated = staffStudentService.updateStudentInfo(studentId, fullName, schoolEmail, phoneNumber, classId);
            java.util.Map<String, Object> s = new java.util.HashMap<>();
            s.put("studentId", updated.getStudentId());
            s.put("studentCode", updated.getStudentCode());
            s.put("fullName", updated.getFullName());
            s.put("schoolEmail", updated.getSchoolEmail());
            s.put("phoneNumber", updated.getPhoneNumber());
            s.put("classId", updated.getClassId());
            result.put("success", true);
            result.put("student", s);
        } catch (IllegalArgumentException ex) {
            result.put("success", false);
            result.put("error", ex.getMessage());
        } catch (Exception ex) {
            result.put("success", false);
            result.put("error", "Unable to update student.");
        }

        return result;
    }
}

package com.example.pms.controller;

import com.example.pms.model.Account;
import com.example.pms.repository.ClassRepository;
import com.example.pms.service.StaffStudentService;
import com.example.pms.util.RoleDisplayUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/staff")
public class StaffHomeController {

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

    @GetMapping("/home")
    public String index(Model model, HttpSession session) {
        if (!isStaffSession(session)) {
            return "redirect:/acc/log";
        }

        bindCommon(model, session);
        return "staff/home";
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

        return "staff/home";
    }
}

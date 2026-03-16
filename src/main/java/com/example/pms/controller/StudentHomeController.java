package com.example.pms.controller;

import com.example.pms.model.*;
import com.example.pms.util.RoleDisplayUtil;
import com.example.pms.repository.*;
import com.example.pms.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/student")
public class StudentHomeController {

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OtpService otpService;

    @Autowired
    private MailService mailService;

    @GetMapping("/home")
    public String index(Model model, HttpSession session) {
        Account account = (Account) session.getAttribute("account");
        Object profile = session.getAttribute("userProfile");
        if (account == null || !(profile instanceof Student) || !"Student".equalsIgnoreCase(account.getRole())) {
            return "redirect:/acc/log";
        }

        Student student = (Student) profile;
        String role = (String) session.getAttribute("role");
        String fullName = (String) session.getAttribute("fullName");

        if (student.getClassId() != null) {
            Classes classObj = classRepository.findById(student.getClassId());
            model.addAttribute("className", classObj != null ? classObj.getClassName() : "PMS");
        } else {
            model.addAttribute("className", "PMS");
        }

        if (fullName != null && !fullName.isEmpty()) {
            model.addAttribute("studentName", fullName);
        } else {
            model.addAttribute("studentName", student.getFullName());
        }

        model.addAttribute("userRole", RoleDisplayUtil.toDisplayRole(role != null ? role : "Student"));

        return "student/home/index";
    }

    @GetMapping("/profile")
    public String profile(@RequestParam(value = "otpRequired", required = false) Boolean otpRequired,
                         Model model, HttpSession session) {
        Account account = (Account) session.getAttribute("account");
        Object profile = session.getAttribute("userProfile");
        if (account == null || !(profile instanceof Student) || !"Student".equalsIgnoreCase(account.getRole())) {
            return "redirect:/acc/log";
        }

        Student student = (Student) profile;
        String role = (String) session.getAttribute("role");
        String fullName = (String) session.getAttribute("fullName");

        if (student.getClassId() != null) {
            Classes classObj = classRepository.findById(student.getClassId());
            model.addAttribute("className", classObj != null ? classObj.getClassName() : "PMS");
        } else {
            model.addAttribute("className", "PMS");
        }

        if (fullName != null && !fullName.isEmpty()) {
            model.addAttribute("studentName", fullName);
        } else {
            model.addAttribute("studentName", student.getFullName());
        }

        model.addAttribute("userRole", RoleDisplayUtil.toDisplayRole(role != null ? role : "Student"));
        model.addAttribute("student", student);
        model.addAttribute("account", account);
        model.addAttribute("otpRequired", otpRequired != null && otpRequired);

        return "student/profile";
    }

    @PostMapping("/profile/change-password")
    public String requestChangePassword(HttpSession session, RedirectAttributes redirectAttributes) {
        Account account = (Account) session.getAttribute("account");
        Object profile = session.getAttribute("userProfile");
        if (account == null || !(profile instanceof Student) || !"Student".equalsIgnoreCase(account.getRole())) {
            return "redirect:/acc/log";
        }

        Student student = (Student) profile;

        String otp = otpService.generateOtp(student.getSchoolEmail());
        try {
            mailService.sendOtp(student.getSchoolEmail(), otp);
            session.setAttribute("changePasswordEmail", student.getSchoolEmail());
            session.setAttribute("changePasswordAccountId", account.getId());
            redirectAttributes.addFlashAttribute("success", "Mã OTP đã được gửi đến email của bạn!");
            return "redirect:/student/profile?otpRequired=true";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi gửi email. Vui lòng thử lại!");
        }

        return "redirect:/student/profile";
    }

    @PostMapping("/profile/verify-otp")
    public String verifyChangePasswordOtp(@RequestParam("otp") String otp,
                                         HttpSession session,
                                         RedirectAttributes redirectAttributes) {
        String email = (String) session.getAttribute("changePasswordEmail");
        if (email == null) {
            return "redirect:/student/profile";
        }

        if (otpService.verify(email, otp)) {
            session.setAttribute("changePasswordVerified", true);
            redirectAttributes.addFlashAttribute("showPasswordForm", true);
            return "redirect:/student/profile";
        }

        redirectAttributes.addFlashAttribute("error", "Mã OTP không đúng!");
        return "redirect:/student/profile";
    }

    @PostMapping("/profile/update-password")
    public String updatePassword(@RequestParam("newPassword") String newPassword,
                                @RequestParam("confirmPassword") String confirmPassword,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        Account account = (Account) session.getAttribute("account");
        if (account == null || session.getAttribute("changePasswordVerified") == null) {
            return "redirect:/student/profile";
        }

        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "Mật khẩu xác nhận không khớp!");
            redirectAttributes.addFlashAttribute("showPasswordForm", true);
            return "redirect:/student/profile";
        }

        if (newPassword.length() < 6) {
            redirectAttributes.addFlashAttribute("error", "Mật khẩu phải có ít nhất 6 ký tự!");
            redirectAttributes.addFlashAttribute("showPasswordForm", true);
            return "redirect:/student/profile";
        }

        // Update password in database
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        Integer accountId = (Integer) session.getAttribute("changePasswordAccountId");
        if (accountId != null) {
            accountRepository.updatePasswordById(accountId, passwordEncoder.encode(newPassword));
        }

        session.removeAttribute("changePasswordVerified");
        session.removeAttribute("changePasswordEmail");
        session.removeAttribute("changePasswordAccountId");

        redirectAttributes.addFlashAttribute("success", "Mật khẩu đã được thay đổi thành công!");
        return "redirect:/student/profile";
    }
}

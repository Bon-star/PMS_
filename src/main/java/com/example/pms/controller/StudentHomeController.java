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
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Controller
@RequestMapping("/student")
public class StudentHomeController {

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private StudentRepository studentRepository;

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
        model.addAttribute("student", student);

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
        model.addAttribute("pendingPhoneNumber", session.getAttribute("changePhoneNumber"));

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

        // Clear any pending phone change so we don't mix operations
        session.removeAttribute("changePhoneEmail");
        session.removeAttribute("changePhoneNumber");
        session.removeAttribute("changePhoneVerified");
        session.removeAttribute("changePhoneAccountId");

        String otp = otpService.generateOtp(student.getSchoolEmail());
        try {
            mailService.sendOtp(student.getSchoolEmail(), otp);
            session.setAttribute("changePasswordEmail", student.getSchoolEmail());
            session.setAttribute("changePasswordAccountId", account.getId());
            redirectAttributes.addFlashAttribute("success", "Mã OTP đã được gửi đến email của bạn!");
            return "redirect:/student/profile?otpType=password#password-section";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi gửi email. Vui lòng thử lại!");
        }

        return "redirect:/student/profile";
    }

    @PostMapping("/profile/change-phone")
    public String requestChangePhone(HttpSession session, RedirectAttributes redirectAttributes) {
        Account account = (Account) session.getAttribute("account");
        Object profile = session.getAttribute("userProfile");
        if (account == null || !(profile instanceof Student) || !"Student".equalsIgnoreCase(account.getRole())) {
            return "redirect:/acc/log";
        }

        Student student = (Student) profile;

        // Clear any pending password change to avoid conflicts
        session.removeAttribute("changePasswordEmail");
        session.removeAttribute("changePasswordVerified");
        session.removeAttribute("changePasswordAccountId");

        String otp = otpService.generateOtp(student.getSchoolEmail());
        try {
            mailService.sendOtp(student.getSchoolEmail(), otp);
            session.setAttribute("changePhoneEmail", student.getSchoolEmail());
            session.setAttribute("changePhoneAccountId", account.getId());
            redirectAttributes.addFlashAttribute("phoneSuccess", "Mã OTP đã được gửi đến email của bạn!");
            return "redirect:/student/profile?otpType=phone#phone-section";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("phoneError", "Lỗi gửi email. Vui lòng thử lại!");
        }

        return "redirect:/student/profile";
    }

    @PostMapping("/profile/verify-otp")
    public String verifyChangePasswordOtp(@RequestParam("otp") String otp,
                                         HttpSession session,
                                         RedirectAttributes redirectAttributes) {
        // Determine which operation is pending (phone or password)
        String phoneEmail = (String) session.getAttribute("changePhoneEmail");
        String passwordEmail = (String) session.getAttribute("changePasswordEmail");

        if (phoneEmail != null) {
            if (otpService.verify(phoneEmail, otp)) {
                session.setAttribute("changePhoneVerified", true);
                redirectAttributes.addFlashAttribute("showPhoneForm", true);
                return "redirect:/student/profile#phone-section";
            }

            redirectAttributes.addFlashAttribute("phoneError", "Mã OTP không đúng!");
            return "redirect:/student/profile?otpType=phone#phone-section";
        }

        if (passwordEmail != null) {
            if (otpService.verify(passwordEmail, otp)) {
                session.setAttribute("changePasswordVerified", true);
                redirectAttributes.addFlashAttribute("showPasswordForm", true);
                return "redirect:/student/profile#password-section";
            }

            redirectAttributes.addFlashAttribute("error", "Mã OTP không đúng!");
            return "redirect:/student/profile?otpType=password#password-section";
        }

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
        return "redirect:/student/profile#password-section";
    }

    @PostMapping("/profile/update-phone")
    public String updatePhone(@RequestParam("newPhone") String newPhone,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        Account account = (Account) session.getAttribute("account");
        if (account == null || session.getAttribute("changePhoneVerified") == null) {
            return "redirect:/student/profile#phone-section";
        }

        Student student = (Student) session.getAttribute("userProfile");
        if (student == null) {
            return "redirect:/student/profile";
        }

        if (newPhone == null || newPhone.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("phoneError", "Vui lòng nhập số điện thoại mới!");
            redirectAttributes.addFlashAttribute("showPhoneForm", true);
            return "redirect:/student/profile";
        }

        String desiredPhone = newPhone.trim();
        Student existing = studentRepository.findByPhoneNumber(desiredPhone);
        if (existing != null && existing.getStudentId() != student.getStudentId()) {
            redirectAttributes.addFlashAttribute("phoneError", "Số điện thoại này đã có người sử dụng. Vui lòng chọn số khác!");
            redirectAttributes.addFlashAttribute("showPhoneForm", true);
            return "redirect:/student/profile";
        }

        studentRepository.updatePhoneNumber(student.getStudentId(), desiredPhone);
        student.setPhoneNumber(desiredPhone);
        session.setAttribute("userProfile", student);

        session.removeAttribute("changePhoneVerified");
        session.removeAttribute("changePhoneEmail");
        session.removeAttribute("changePhoneAccountId");

        redirectAttributes.addFlashAttribute("phoneSuccess", "Số điện thoại đã được cập nhật thành công!");
        return "redirect:/student/profile#phone-section";
    }

    @PostMapping("/profile/change-avatar")
    public String changeAvatar(@RequestParam("avatarFile") MultipartFile file,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        Account account = (Account) session.getAttribute("account");
        Object profile = session.getAttribute("userProfile");
        if (account == null || !(profile instanceof Student) || !"Student".equalsIgnoreCase(account.getRole())) {
            return "redirect:/acc/log";
        }

        Student student = (Student) profile;

        // Validate file
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("avatarError", "Vui lòng chọn một tệp ảnh!");
            return "redirect:/student/profile";
        }

        // Check file type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            redirectAttributes.addFlashAttribute("avatarError", "Chỉ chấp nhận tệp ảnh!");
            return "redirect:/student/profile";
        }

        // Check file size (max 5MB)
        if (file.getSize() > 5 * 1024 * 1024) {
            redirectAttributes.addFlashAttribute("avatarError", "Kích thước tệp không được vượt quá 5MB!");
            return "redirect:/student/profile";
        }

        try {
            // Create resources/img/avatar directory if it doesn't exist
            Path uploadDir = Paths.get(System.getProperty("user.dir"), "src", "main", "resources", "static", "img", "avatar");
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String newFilename = "student_" + student.getStudentId() + "_" + System.currentTimeMillis() + fileExtension;

            // Save file
            Path filePath = uploadDir.resolve(newFilename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // Update student avatar in database
            studentRepository.updateAvatar(student.getStudentId(), newFilename);

            // Update session
            student.setAvatar(newFilename);
            session.setAttribute("userProfile", student);

            redirectAttributes.addFlashAttribute("avatarSuccess", "Ảnh đại diện đã được cập nhật thành công!");

        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("avatarError", "Lỗi khi tải lên ảnh. Vui lòng thử lại!");
        }

        return "redirect:/student/profile";
    }
}

package com.example.pms.controller;

import com.example.pms.model.Account;
import com.example.pms.model.Classes;
import com.example.pms.model.Student;
import com.example.pms.repository.AccountRepository;
import com.example.pms.repository.ClassRepository;
import com.example.pms.repository.GroupInvitationRepository;
import com.example.pms.service.StudentNotificationService;
import com.example.pms.util.RoleDisplayUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/student")
public class StudentHomeController {

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private GroupInvitationRepository groupInvitationRepository;

    @Autowired
    private StudentNotificationService studentNotificationService;

    @Autowired
    private AccountRepository accountRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

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
        boolean invitationEnabled = groupInvitationRepository.isInvitationTableAvailable();
        model.addAttribute("invitationFeatureEnabled", invitationEnabled);
        model.addAttribute("notificationCount",
                studentNotificationService.countHeaderNotifications(student, invitationEnabled));

        return "student/home/index";
    }

    @GetMapping("/profile")
    public String profile(Model model, HttpSession session) {
        Account account = (Account) session.getAttribute("account");
        Object profile = session.getAttribute("userProfile");
        if (account == null || !(profile instanceof Student) || !"Student".equalsIgnoreCase(account.getRole())) {
            return "redirect:/acc/log";
        }

        Student student = (Student) profile;
        model.addAttribute("studentProfile", student);
        model.addAttribute("profileAuthProvider",
                account.getAuthProvider() == null || account.getAuthProvider().isBlank()
                        ? "LOCAL"
                        : account.getAuthProvider().trim().toUpperCase());
        return "student/profile";
    }

    @PostMapping("/profile/change-password")
    public String changePassword(@RequestParam("currentPassword") String currentPassword,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Account account = (Account) session.getAttribute("account");
        Object profile = session.getAttribute("userProfile");
        if (account == null || !(profile instanceof Student) || !"Student".equalsIgnoreCase(account.getRole())) {
            return "redirect:/acc/log";
        }

        String authProvider = account.getAuthProvider() == null ? "LOCAL" : account.getAuthProvider().trim();
        if (!"LOCAL".equalsIgnoreCase(authProvider)) {
            redirectAttributes.addFlashAttribute("error",
                    "This account uses a social provider, so the password cannot be changed inside PMS.");
            return "redirect:/student/profile";
        }

        String current = currentPassword == null ? "" : currentPassword.trim();
        String next = newPassword == null ? "" : newPassword.trim();
        String confirm = confirmPassword == null ? "" : confirmPassword.trim();

        if (current.isEmpty() || next.isEmpty() || confirm.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please fill in all password fields.");
            return "redirect:/student/profile";
        }

        Account freshAccount = accountRepository.findById(account.getId());
        if (freshAccount == null || freshAccount.getPasswordHash() == null || freshAccount.getPasswordHash().isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Account password data is unavailable.");
            return "redirect:/student/profile";
        }

        if (!passwordEncoder.matches(current, freshAccount.getPasswordHash())) {
            redirectAttributes.addFlashAttribute("error", "Current password is incorrect.");
            return "redirect:/student/profile";
        }

        if (!next.equals(confirm)) {
            redirectAttributes.addFlashAttribute("error", "New password and confirmation do not match.");
            return "redirect:/student/profile";
        }

        if (current.equals(next)) {
            redirectAttributes.addFlashAttribute("error", "New password must be different from the current password.");
            return "redirect:/student/profile";
        }

        accountRepository.updatePasswordById(account.getId(), passwordEncoder.encode(next));
        session.invalidate();
        return "redirect:/acc/log?passwordChanged=1";
    }
}

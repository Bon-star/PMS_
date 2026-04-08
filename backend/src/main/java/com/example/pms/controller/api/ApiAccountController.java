package com.example.pms.controller.api;

import com.example.pms.model.Account;
import com.example.pms.model.Lecturer;
import com.example.pms.model.Staff;
import com.example.pms.model.Student;
import com.example.pms.repository.AccountRepository;
import com.example.pms.repository.LecturerRepository;
import com.example.pms.repository.StaffRepository;
import com.example.pms.repository.StudentRepository;
import com.example.pms.service.MailService;
import com.example.pms.service.OtpService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class ApiAccountController {

    @Autowired
    private AccountRepository accountRepo;

    @Autowired
    private OtpService otpService;

    @Autowired
    private MailService mailService;

    @Autowired
    private StudentRepository studentRepo;

    @Autowired
    private LecturerRepository lecturerRepo;

    @Autowired
    private StaffRepository staffRepo;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private static class ResolvedIdentity {
        String role;
        String fullName;
        String phoneNumber;
        Integer accountId;
        Object profile;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private ResolvedIdentity resolveIdentityByEmail(String email) {
        Student student = studentRepo.findBySchoolEmail(email);
        if (student != null) {
            ResolvedIdentity identity = new ResolvedIdentity();
            identity.role = "Student";
            identity.fullName = student.getFullName();
            identity.phoneNumber = student.getPhoneNumber();
            identity.accountId = student.getAccountId();
            identity.profile = student;
            return identity;
        }

        Lecturer lecturer = lecturerRepo.findBySchoolEmail(email);
        if (lecturer != null) {
            ResolvedIdentity identity = new ResolvedIdentity();
            identity.role = "Lecturer";
            identity.fullName = lecturer.getFullName();
            identity.phoneNumber = lecturer.getPhoneNumber();
            identity.accountId = lecturer.getAccountId();
            identity.profile = lecturer;
            return identity;
        }

        Staff staff = staffRepo.findBySchoolEmail(email);
        if (staff != null) {
            ResolvedIdentity identity = new ResolvedIdentity();
            identity.role = "Staff";
            identity.fullName = staff.getFullName();
            identity.phoneNumber = staff.getPhoneNumber();
            identity.accountId = staff.getAccountId();
            identity.profile = staff;
            return identity;
        }

        return null;
    }

    private void linkAccountToProfile(ResolvedIdentity identity, int accountId) {
        if (identity == null) return;

        if ("Student".equalsIgnoreCase(identity.role) && identity.profile instanceof Student student) {
            studentRepo.linkAccount(student.getStudentId(), accountId);
            student.setAccountId(accountId);
        } else if ("Lecturer".equalsIgnoreCase(identity.role) && identity.profile instanceof Lecturer lecturer) {
            lecturerRepo.linkAccount(lecturer.getLecturerId(), accountId);
            lecturer.setAccountId(accountId);
        } else if ("Staff".equalsIgnoreCase(identity.role) && identity.profile instanceof Staff staff) {
            staffRepo.linkAccount(staff.getStaffId(), accountId);
            staff.setAccountId(accountId);
        }

        identity.accountId = accountId;
    }

    private Account resolveAccountFromIdentity(ResolvedIdentity identity, String email, boolean createIfMissing) {
        if (identity == null) return null;

        if (identity.accountId != null) {
            Account accountById = accountRepo.findById(identity.accountId);
            if (accountById != null) return accountById;
        }

        Account accountByUsername = accountRepo.findByUsername(email);
        if (accountByUsername != null) {
            if (accountByUsername.getRole() != null && identity.role.equalsIgnoreCase(accountByUsername.getRole())) {
                linkAccountToProfile(identity, accountByUsername.getId());
                return accountByUsername;
            }
            return null;
        }

        if (!createIfMissing) return null;

        int newAccountId = accountRepo.createLocalAccount(email, identity.role);
        if (newAccountId <= 0) return null;

        linkAccountToProfile(identity, newAccountId);
        return accountRepo.findById(newAccountId);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        String email = normalize(body.getOrDefault("email", ""));
        String pass = normalize(body.getOrDefault("password", ""));

        ResolvedIdentity identity = resolveIdentityByEmail(email);
        if (identity == null) {
            result.put("success", false);
            result.put("message", "Incorrect email or password!");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }

        Account account = resolveAccountFromIdentity(identity, email, false);
        if (account == null || account.getPasswordHash() == null || account.getPasswordHash().trim().isEmpty()
                || !passwordEncoder.matches(pass, account.getPasswordHash())) {
            result.put("success", false);
            result.put("message", "Incorrect email or password!");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }

        if (account.getRole() == null || !identity.role.equalsIgnoreCase(account.getRole())) {
            result.put("success", false);
            result.put("message", "Account data is not synchronized.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }

        if (!account.getIsActive()) {
            result.put("success", false);
            result.put("message", "Account is not activated yet.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }

        session.setAttribute("account", account);
        session.setAttribute("role", identity.role);
        session.setAttribute("userProfile", identity.profile);
        session.setAttribute("fullName", identity.fullName);

        result.put("success", true);
        result.put("role", identity.role);
        result.put("fullName", identity.fullName);
        result.put("email", email);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(HttpSession session) {
        Account account = (Account) session.getAttribute("account");
        if (account == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("authenticated", false));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("authenticated", true);
        result.put("role", session.getAttribute("role"));
        result.put("fullName", session.getAttribute("fullName"));
        result.put("email", account.getEmail());
        result.put("accountId", account.getId());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request, HttpServletResponse response, HttpSession session) {
        session.invalidate();

        Cookie cookie = new Cookie("JSESSIONID", null);
        String contextPath = request.getContextPath();
        cookie.setPath((contextPath == null || contextPath.isEmpty()) ? "/" : contextPath);
        cookie.setMaxAge(0);
        cookie.setHttpOnly(true);
        response.addCookie(cookie);

        return ResponseEntity.ok(Map.of("success", true, "message", "Logged out successfully."));
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        String email = normalize(body.getOrDefault("email", ""));
        String phone = normalize(body.getOrDefault("phone", ""));
        String pass = normalize(body.getOrDefault("password", ""));
        String repass = normalize(body.getOrDefault("repassword", ""));

        if (!pass.equals(repass)) {
            result.put("success", false);
            result.put("message", "Passwords do not match!");
            return ResponseEntity.badRequest().body(result);
        }

        ResolvedIdentity identity = resolveIdentityByEmail(email);
        if (identity == null) {
            result.put("success", false);
            result.put("message", "This email is not in the school records.");
            return ResponseEntity.badRequest().body(result);
        }

        if (!normalize(identity.phoneNumber).equals(phone)) {
            result.put("success", false);
            result.put("message", "Phone number does not match the school record.");
            return ResponseEntity.badRequest().body(result);
        }

        Account account = resolveAccountFromIdentity(identity, email, true);
        if (account == null) {
            result.put("success", false);
            result.put("message", "Unable to create the account.");
            return ResponseEntity.badRequest().body(result);
        }

        if (account.getIsActive()) {
            result.put("success", false);
            result.put("message", "This account is already activated. Please sign in.");
            return ResponseEntity.badRequest().body(result);
        }

        String otp = otpService.generateOtp(email);
        try {
            mailService.sendOtp(email, otp);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Failed to send OTP email.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }

        result.put("success", true);
        result.put("message", "OTP sent to your email.");
        result.put("email", email);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<Map<String, Object>> verifyOtp(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        String email = normalize(body.getOrDefault("email", ""));
        String otp = normalize(body.getOrDefault("otp", ""));
        String password = normalize(body.getOrDefault("password", ""));

        if (otpService.verify(email, otp)) {
            ResolvedIdentity identity = resolveIdentityByEmail(email);
            Account acc = resolveAccountFromIdentity(identity, email, false);
            if (acc != null) {
                accountRepo.updatePasswordById(acc.getId(), passwordEncoder.encode(password));
                result.put("success", true);
                result.put("message", "Account activated successfully!");
                return ResponseEntity.ok(result);
            }
        }

        result.put("success", false);
        result.put("message", "Invalid or expired OTP.");
        return ResponseEntity.badRequest().body(result);
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<Map<String, Object>> resendOtp(@RequestBody Map<String, String> body, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        String type = normalize(body.getOrDefault("type", "")).toUpperCase();
        String email = normalize(body.getOrDefault("email", ""));

        if ("RESET".equals(type) && email.isEmpty()) {
            email = (String) session.getAttribute("forgotEmail");
        }

        if (email == null || email.isBlank()) {
            result.put("success", false);
            result.put("message", "Email is required.");
            return ResponseEntity.badRequest().body(result);
        }

        try {
            String otp = otpService.generateOtp(email);
            mailService.sendOtp(email, otp);
            result.put("success", true);
            result.put("message", "A new OTP has been sent.");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Failed to resend OTP email.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    @PostMapping("/forgot")
    public ResponseEntity<Map<String, Object>> forgot(@RequestBody Map<String, String> body, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        String email = normalize(body.getOrDefault("email", ""));

        ResolvedIdentity identity = resolveIdentityByEmail(email);
        if (identity == null) {
            result.put("success", false);
            result.put("message", "Email does not exist.");
            return ResponseEntity.badRequest().body(result);
        }

        Account acc = resolveAccountFromIdentity(identity, email, false);
        if (acc == null) {
            result.put("success", false);
            result.put("message", "Account has not been created or is invalid.");
            return ResponseEntity.badRequest().body(result);
        }

        if (acc.getAuthProvider() != null && !"LOCAL".equalsIgnoreCase(acc.getAuthProvider())) {
            result.put("success", false);
            result.put("message", "Social login accounts cannot change the password.");
            return ResponseEntity.badRequest().body(result);
        }

        String otp = otpService.generateOtp(email);
        try {
            mailService.sendOtp(email, otp);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Failed to send OTP email.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }

        session.setAttribute("forgotEmail", email);
        session.setAttribute("forgotAccountId", acc.getId());

        result.put("success", true);
        result.put("message", "OTP sent to your email.");
        return ResponseEntity.ok(result);
    }

    @PostMapping("/verify-reset")
    public ResponseEntity<Map<String, Object>> verifyReset(@RequestBody Map<String, String> body, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        String otp = normalize(body.getOrDefault("otp", ""));
        String email = (String) session.getAttribute("forgotEmail");

        if (email == null) {
            result.put("success", false);
            result.put("message", "Session expired. Please start the forgot password process again.");
            return ResponseEntity.badRequest().body(result);
        }

        if (otpService.verify(email, otp)) {
            session.setAttribute("resetVerified", true);
            result.put("success", true);
            result.put("message", "OTP verified. You can now reset your password.");
            return ResponseEntity.ok(result);
        }

        result.put("success", false);
        result.put("message", "Invalid OTP.");
        return ResponseEntity.badRequest().body(result);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody Map<String, String> body, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        String email = (String) session.getAttribute("forgotEmail");
        Boolean verified = (Boolean) session.getAttribute("resetVerified");

        if (email == null || verified == null || !verified) {
            result.put("success", false);
            result.put("message", "Session expired. Please start the forgot password process again.");
            return ResponseEntity.badRequest().body(result);
        }

        String pass = normalize(body.getOrDefault("password", ""));
        String repass = normalize(body.getOrDefault("repassword", ""));

        if (!pass.equals(repass)) {
            result.put("success", false);
            result.put("message", "Passwords do not match.");
            return ResponseEntity.badRequest().body(result);
        }

        Integer accountId = (Integer) session.getAttribute("forgotAccountId");
        if (accountId == null) {
            ResolvedIdentity identity = resolveIdentityByEmail(email);
            Account acc = resolveAccountFromIdentity(identity, email, false);
            if (acc != null) {
                accountId = acc.getId();
            }
        }

        if (accountId != null) {
            accountRepo.updatePasswordById(accountId, passwordEncoder.encode(pass));
        }

        session.invalidate();
        result.put("success", true);
        result.put("message", "Password changed successfully!");
        return ResponseEntity.ok(result);
    }
}

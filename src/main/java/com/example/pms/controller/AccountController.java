package com.example.pms.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

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

@Controller
@RequestMapping("/acc")
public class AccountController {

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
        private String role;
        private String fullName;
        private String phoneNumber;
        private Integer accountId;
        private Object profile;
    }

    private String normalizeInput(String value) {
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
        if (identity == null) {
            return;
        }

        if ("Student".equalsIgnoreCase(identity.role) && identity.profile instanceof Student) {
            Student student = (Student) identity.profile;
            studentRepo.linkAccount(student.getStudentId(), accountId);
            student.setAccountId(accountId);
        } else if ("Lecturer".equalsIgnoreCase(identity.role) && identity.profile instanceof Lecturer) {
            Lecturer lecturer = (Lecturer) identity.profile;
            lecturerRepo.linkAccount(lecturer.getLecturerId(), accountId);
            lecturer.setAccountId(accountId);
        } else if ("Staff".equalsIgnoreCase(identity.role) && identity.profile instanceof Staff) {
            Staff staff = (Staff) identity.profile;
            staffRepo.linkAccount(staff.getStaffId(), accountId);
            staff.setAccountId(accountId);
        }

        identity.accountId = accountId;
    }

    private Account resolveAccountFromIdentity(ResolvedIdentity identity, String email, boolean createIfMissing) {
        if (identity == null) {
            return null;
        }

        if (identity.accountId != null) {
            Account accountById = accountRepo.findById(identity.accountId);
            if (accountById != null) {
                return accountById;
            }
        }

        Account accountByUsername = accountRepo.findByUsername(email);
        if (accountByUsername != null) {
            if (accountByUsername.getRole() != null && identity.role.equalsIgnoreCase(accountByUsername.getRole())) {
                linkAccountToProfile(identity, accountByUsername.getId());
                return accountByUsername;
            }
            return null;
        }

        if (!createIfMissing) {
            return null;
        }

        int newAccountId = accountRepo.createLocalAccount(email, identity.role);
        if (newAccountId <= 0) {
            return null;
        }

        linkAccountToProfile(identity, newAccountId);
        return accountRepo.findById(newAccountId);
    }

    private boolean isPhoneMatch(ResolvedIdentity identity, String phone) {
        if (identity == null) {
            return false;
        }
        return normalizeInput(identity.phoneNumber).equals(phone);
    }

    @GetMapping("/log")
    public String loginPage(HttpSession session) {
        Account acc = (Account) session.getAttribute("account");
        if (acc != null) {
            return redirectByRole(acc.getRole());
        }
        return "login";
    }

    @PostMapping("/ck")
    public String checkLogin(@RequestParam("email") String email,
            @RequestParam("password") String pass,
            HttpSession session,
            Model model) {

        String normalizedEmail = normalizeInput(email);
        ResolvedIdentity identity = resolveIdentityByEmail(normalizedEmail);
        if (identity == null) {
            model.addAttribute("error", "Sai email hoặc mật khẩu!");
            model.addAttribute("email", normalizedEmail);
            return "login";
        }

        Account account = resolveAccountFromIdentity(identity, normalizedEmail, false);
        if (account == null || account.getPasswordHash() == null || account.getPasswordHash().trim().isEmpty()
                || !passwordEncoder.matches(pass, account.getPasswordHash())) {
            model.addAttribute("error", "Sai email hoặc mật khẩu!");
            model.addAttribute("email", normalizedEmail);
            return "login";
        }

        if (account.getRole() == null || !identity.role.equalsIgnoreCase(account.getRole())) {
            model.addAttribute("error", "Dữ liệu tài khoản không đồng bộ với hồ sơ người dùng.");
            model.addAttribute("email", normalizedEmail);
            return "login";
        }

        if (!account.getIsActive()) {
            model.addAttribute("error", "Tài khoản chưa được kích hoạt!");
            model.addAttribute("email", normalizedEmail);
            return "login";
        }

        session.setAttribute("account", account);
        session.setAttribute("role", identity.role);
        session.setAttribute("userProfile", identity.profile);
        session.setAttribute("fullName", identity.fullName);

        return redirectByRole(identity.role);
    }

    private String redirectByRole(String role) {
        if ("Staff".equalsIgnoreCase(role)) {
            return "redirect:/staff/home";
        } else if ("Lecturer".equalsIgnoreCase(role)) {
            return "redirect:/lecturer/home";
        } else {
            return "redirect:/student/home";
        }
    }

    @GetMapping("/ck")
    public String checkLoginGet() {
        return "redirect:/acc/log";
    }

    @GetMapping({ "/reg", "/register" })
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam("email") String email,
            @RequestParam("phone") String phone,
            @RequestParam("password") String pass,
            @RequestParam("repassword") String repass,
            Model model) {

        String normalizedEmail = normalizeInput(email);
        String normalizedPhone = normalizeInput(phone);

        if (!pass.equals(repass)) {
            model.addAttribute("error", "Mật khẩu nhập lại không khớp!");
            model.addAttribute("email", normalizedEmail);
            model.addAttribute("phone", normalizedPhone);
            return "register";
        }

        ResolvedIdentity identity = resolveIdentityByEmail(normalizedEmail);
        if (identity == null) {
            model.addAttribute("error", "Email này chưa có trong dữ liệu nhà trường!");
            model.addAttribute("email", normalizedEmail);
            model.addAttribute("phone", normalizedPhone);
            return "register";
        }

        if (!isPhoneMatch(identity, normalizedPhone)) {
            model.addAttribute("error", "Số điện thoại không khớp với hồ sơ nhà trường!");
            model.addAttribute("email", normalizedEmail);
            model.addAttribute("phone", normalizedPhone);
            return "register";
        }

        Account account = resolveAccountFromIdentity(identity, normalizedEmail, true);
        if (account == null) {
            model.addAttribute("error", "Không thể khởi tạo tài khoản. Vui lòng liên hệ quản trị hệ thống.");
            model.addAttribute("email", normalizedEmail);
            model.addAttribute("phone", normalizedPhone);
            return "register";
        }

        if (account.getIsActive()) {
            model.addAttribute("error", "Tài khoản này đã được kích hoạt. Vui lòng đăng nhập.");
            return "register";
        }

        String otp = otpService.generateOtp(normalizedEmail);
        try {
            mailService.sendOtp(normalizedEmail, otp);
        } catch (Exception e) {
            model.addAttribute("error", "Lỗi gửi mail OTP. Vui lòng thử lại!");
            model.addAttribute("email", normalizedEmail);
            model.addAttribute("phone", normalizedPhone);
            return "register";
        }

        model.addAttribute("email", normalizedEmail);
        model.addAttribute("password", pass);
        model.addAttribute("phone", normalizedPhone);
        model.addAttribute("type", "REGISTER");
        return "verify-otp";
    }

    @PostMapping("/verify-otp")
    public String verifyOtp(@RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam("otp") String otp,
            Model model) {

        String normalizedEmail = normalizeInput(email);

        if (otpService.verify(normalizedEmail, otp)) {
            ResolvedIdentity identity = resolveIdentityByEmail(normalizedEmail);
            Account acc = resolveAccountFromIdentity(identity, normalizedEmail, false);
            if (acc != null) {
                accountRepo.updatePasswordById(acc.getId(), passwordEncoder.encode(password));
                model.addAttribute("success", "Kích hoạt tài khoản thành công! Hãy đăng nhập.");
                return "login";
            }
        }

        model.addAttribute("error", "Mã OTP không đúng hoặc hết hạn!");
        model.addAttribute("email", normalizedEmail);
        model.addAttribute("password", password);
        model.addAttribute("type", "REGISTER");
        return "verify-otp";
    }

    @GetMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response, HttpSession session) {
        session.invalidate();

        Cookie cookie = new Cookie("JSESSIONID", null);
        String contextPath = request.getContextPath();
        cookie.setPath((contextPath == null || contextPath.isEmpty()) ? "/" : contextPath);
        cookie.setMaxAge(0);
        cookie.setHttpOnly(true);
        response.addCookie(cookie);

        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        return "redirect:/acc/log";
    }

    @GetMapping("/forgot")
    public String forgotPage() {
        return "forgot-password";
    }

    @PostMapping("/forgot")
    public String sendForgotOtp(@RequestParam("email") String email, HttpSession session, Model model) {
        String normalizedEmail = normalizeInput(email);

        ResolvedIdentity identity = resolveIdentityByEmail(normalizedEmail);
        if (identity == null) {
            model.addAttribute("error", "Email không tồn tại!");
            return "forgot-password";
        }

        Account acc = resolveAccountFromIdentity(identity, normalizedEmail, false);
        if (acc == null) {
            model.addAttribute("error", "Tài khoản chưa được khởi tạo hoặc không hợp lệ!");
            return "forgot-password";
        }

        if (acc.getAuthProvider() != null && !"LOCAL".equalsIgnoreCase(acc.getAuthProvider())) {
            model.addAttribute("error", "Tài khoản mạng xã hội không thể đổi mật khẩu!");
            return "forgot-password";
        }

        String otp = otpService.generateOtp(normalizedEmail);
        try {
            mailService.sendOtp(normalizedEmail, otp);
        } catch (Exception e) {
            model.addAttribute("error", "Lỗi gửi mail!");
            return "forgot-password";
        }

        session.setAttribute("forgotEmail", normalizedEmail);
        session.setAttribute("forgotAccountId", acc.getId());

        model.addAttribute("email", normalizedEmail);
        model.addAttribute("type", "RESET");
        return "verify-otp";
    }

    @PostMapping("/verify-reset")
    public String verifyResetOtp(@RequestParam("otp") String otp, HttpSession session, Model model) {
        String email = (String) session.getAttribute("forgotEmail");
        if (email == null) {
            return "redirect:/acc/forgot";
        }

        if (otpService.verify(email, otp)) {
            session.setAttribute("resetVerified", true);
            return "redirect:/acc/reset-pass";
        }

        model.addAttribute("error", "Mã OTP sai!");
        model.addAttribute("email", email);
        model.addAttribute("type", "RESET");
        return "verify-otp";
    }

    @GetMapping("/reset-pass")
    public String resetPassPage(HttpSession session) {
        if (session.getAttribute("resetVerified") == null) {
            return "redirect:/acc/forgot";
        }
        return "reset-password";
    }

    @PostMapping("/reset-pass")
    public String processReset(@RequestParam("password") String pass,
            @RequestParam("repassword") String repass,
            HttpSession session,
            Model model) {

        String email = (String) session.getAttribute("forgotEmail");
        if (email == null) {
            return "redirect:/acc/forgot";
        }

        if (!pass.equals(repass)) {
            model.addAttribute("error", "Mật khẩu không khớp!");
            return "reset-password";
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
        model.addAttribute("success", "Đổi mật khẩu thành công!");
        return "login";
    }
}

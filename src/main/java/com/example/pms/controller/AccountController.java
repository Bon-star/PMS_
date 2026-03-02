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

    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @GetMapping("/log")
    public String loginPage(HttpSession session) {
        Account acc = (Account) session.getAttribute("account");
        if (acc != null) {
            return redirectByRole(acc.getRole());
        }
        return "login";
    }

    @PostMapping("/ck")
    public String checkLogin(@RequestParam("email") String email, @RequestParam("password") String pass,
            HttpSession session, Model model) {

        Account account = accountRepo.findByUsername(email);

        if (account != null && account.getPasswordHash() != null && passwordEncoder.matches(pass, account.getPasswordHash())) {
            
            if (!account.getIsActive()) {
                model.addAttribute("error", "Tài khoản chưa được kích hoạt!");
                return "login";
            }

            session.setAttribute("account", account);
            session.setAttribute("role", account.getRole());

            try {
                String role = account.getRole();
                if ("Student".equalsIgnoreCase(role)) {
                    Student s = studentRepo.findBySchoolEmail(email);
                    if (s != null) {
                        session.setAttribute("userProfile", s);
                        session.setAttribute("fullName", s.getFullName());
                    }
                } 
                else if ("Lecturer".equalsIgnoreCase(role)) {
                    Lecturer l = lecturerRepo.findBySchoolEmail(email);
                    if (l != null) {
                        session.setAttribute("userProfile", l);
                        session.setAttribute("fullName", l.getFullName());
                    }
                } 
                else if ("Staff".equalsIgnoreCase(role)) {
                    Staff st = staffRepo.findBySchoolEmail(email);
                    if (st != null) {
                        session.setAttribute("userProfile", st);
                        session.setAttribute("fullName", st.getFullName());
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            return redirectByRole(account.getRole());

        } else {
            model.addAttribute("error", "Sai email hoặc mật khẩu!");
            model.addAttribute("email", email);
            return "login";
        }
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

    @GetMapping({"/reg", "/register"})
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam("email") String email, @RequestParam("phone") String phone,
            @RequestParam("password") String pass, @RequestParam("repassword") String repass, Model model) {

        if (!pass.equals(repass)) {
            model.addAttribute("error", "Mật khẩu nhập lại không khớp!");
            model.addAttribute("email", email);
            return "register";
        }

        Account existingAcc = accountRepo.findByUsername(email);
        if (existingAcc == null) {
            model.addAttribute("error", "Email này chưa có trong hệ thống nhà trường!");
            return "register";
        }

        if (existingAcc.getIsActive()) {
            model.addAttribute("error", "Tài khoản này đã được kích hoạt. Vui lòng đăng nhập.");
            return "register";
        }

        boolean isPhoneMatch = false;
        String role = existingAcc.getRole();

        if ("Student".equalsIgnoreCase(role)) {
            Student s = studentRepo.findBySchoolEmail(email);
            if (s != null && s.getPhoneNumber().equals(phone)) isPhoneMatch = true;
        } else if ("Lecturer".equalsIgnoreCase(role)) {
            Lecturer l = lecturerRepo.findBySchoolEmail(email);
            if (l != null && l.getPhoneNumber().equals(phone)) isPhoneMatch = true;
        } else if ("Staff".equalsIgnoreCase(role)) {
            Staff st = staffRepo.findBySchoolEmail(email);
            if (st != null && st.getPhoneNumber().equals(phone)) isPhoneMatch = true;
        }

        if (!isPhoneMatch) {
            model.addAttribute("error", "Số điện thoại không khớp với hồ sơ nhà trường!");
            model.addAttribute("email", email);
            return "register";
        }

        String otp = otpService.generateOtp(email);
        try {
            mailService.sendOtp(email, otp);
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "Lỗi gửi mail OTP. Vui lòng thử lại!");
            return "register";
        }

        model.addAttribute("email", email);
        model.addAttribute("password", pass);
        model.addAttribute("phone", phone);
        model.addAttribute("type", "REGISTER");
        return "verify-otp";
    }

    @PostMapping("/verify-otp")
    public String verifyOtp(@RequestParam("email") String email, 
                            @RequestParam("password") String password, 
                            @RequestParam("otp") String otp, Model model) {

        if (otpService.verify(email, otp)) {
            Account acc = accountRepo.findByUsername(email);
            if (acc != null) {
                accountRepo.updatePassword(email, passwordEncoder.encode(password));
                
                model.addAttribute("success", "Kích hoạt tài khoản thành công! Hãy đăng nhập.");
                return "login";
            }
        } 
        
        model.addAttribute("error", "Mã OTP không đúng hoặc hết hạn!");
        model.addAttribute("email", email);
        model.addAttribute("password", password);
        model.addAttribute("type", "REGISTER");
        return "verify-otp";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/acc/log";
    }

    @GetMapping("/forgot")
    public String forgotPage() {
        return "forgot-password";
    }

    @PostMapping("/forgot")
    public String sendForgotOtp(@RequestParam("email") String email, HttpSession session, Model model) {
        Account acc = accountRepo.findByUsername(email);

        if (acc == null) {
            model.addAttribute("error", "Email không tồn tại!");
            return "forgot-password";
        }
        
        if (acc.getAuthProvider() != null && !"LOCAL".equals(acc.getAuthProvider())) {
             model.addAttribute("error", "Tài khoản mạng xã hội không thể đổi mật khẩu!");
             return "forgot-password";
        }

        String otp = otpService.generateOtp(email);
        try {
            mailService.sendOtp(email, otp);
        } catch (Exception e) {
            model.addAttribute("error", "Lỗi gửi mail!");
            return "forgot-password";
        }

        session.setAttribute("forgotEmail", email);
        
        model.addAttribute("email", email); 
        model.addAttribute("type", "RESET");
        return "verify-otp";
    }
    
    @PostMapping("/verify-reset")
    public String verifyResetOtp(@RequestParam("otp") String otp, HttpSession session, Model model) {
        String email = (String) session.getAttribute("forgotEmail");
        if(email == null) return "redirect:/acc/forgot";
        
        if(otpService.verify(email, otp)) {
            session.setAttribute("resetVerified", true);
            return "redirect:/acc/reset-pass";
        } else {
            model.addAttribute("error", "Mã OTP sai!");
            model.addAttribute("email", email);
            model.addAttribute("type", "RESET");
            return "verify-otp";
        }
    }

    @GetMapping("/reset-pass")
    public String resetPassPage(HttpSession session) {
        if(session.getAttribute("resetVerified") == null) return "redirect:/acc/forgot";
        return "reset-password";
    }

    @PostMapping("/reset-pass")
    public String processReset(@RequestParam("password") String pass, @RequestParam("repassword") String repass, HttpSession session, Model model) {
        String email = (String) session.getAttribute("forgotEmail");
        if(email == null) return "redirect:/acc/forgot";
        
        if(!pass.equals(repass)) {
            model.addAttribute("error", "Mật khẩu không khớp!");
            return "reset-password";
        }
        
        Account acc = accountRepo.findByUsername(email);
        if(acc != null) {
            accountRepo.updatePassword(email, passwordEncoder.encode(pass));
        }
        
        session.invalidate();
        model.addAttribute("success", "Đổi mật khẩu thành công!");
        return "login";
    }
}
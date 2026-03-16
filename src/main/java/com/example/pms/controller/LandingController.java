package com.example.pms.controller;

import com.example.pms.model.Account;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LandingController {

    @GetMapping("/")
    public String index(HttpSession session) {
        Account account = (Account) session.getAttribute("account");
        if (account == null || account.getRole() == null) {
            return "redirect:/acc/log";
        }

        String role = account.getRole();
        if ("Staff".equalsIgnoreCase(role)) {
            return "redirect:/staff/home";
        }
        if ("Lecturer".equalsIgnoreCase(role)) {
            return "redirect:/lecturer/home";
        }
        return "redirect:/student/home";
    }
}

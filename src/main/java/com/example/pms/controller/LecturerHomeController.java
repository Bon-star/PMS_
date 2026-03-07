package com.example.pms.controller;

import com.example.pms.model.Account;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/lecturer")
public class LecturerHomeController {

    @GetMapping("/home")
    public String index(Model model, HttpSession session) {
        Account account = (Account) session.getAttribute("account");
        if (account == null || !"Lecturer".equalsIgnoreCase(account.getRole())) {
            return "redirect:/acc/log";
        }

        Object fullName = session.getAttribute("fullName");
        model.addAttribute("lecturerName", fullName != null ? fullName : "Giảng viên");
        return "lecturer/home";
    }
}

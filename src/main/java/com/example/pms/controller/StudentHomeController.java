package com.example.pms.controller;

import com.example.pms.model.Account;
import com.example.pms.model.Classes;
import com.example.pms.model.Student;
import com.example.pms.util.RoleDisplayUtil;
import com.example.pms.repository.ClassRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/student")
public class StudentHomeController {

    @Autowired
    private ClassRepository classRepository;

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
}

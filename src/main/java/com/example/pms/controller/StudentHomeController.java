package com.example.pms.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.example.pms.model.Account;
import com.example.pms.model.Student;
import com.example.pms.model.Classes;
import com.example.pms.repository.ClassRepository;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/student")
public class StudentHomeController {
	
	@Autowired
	private ClassRepository classRepository;
	
	@GetMapping("/home")
	public String index(Model model, HttpSession session) {
		try {
			Account account = (Account) session.getAttribute("account");
			Student student = (Student) session.getAttribute("userProfile");
			String role = (String) session.getAttribute("role");
			String fullName = (String) session.getAttribute("fullName");
			
			// Lấy tên lớp
			if (student != null && student.getClassId() != null) {
				Classes classObj = classRepository.findById(student.getClassId());
				if (classObj != null) {
					model.addAttribute("className", classObj.getClassName());
				} else {
					model.addAttribute("className", "PMS");
				}
			} else {
				model.addAttribute("className", "PMS");
			}
			
			// Lấy tên học sinh
			if (fullName != null && !fullName.isEmpty()) {
				model.addAttribute("studentName", fullName);
			} else if (student != null) {
				model.addAttribute("studentName", student.getFullName());
			} else {
				model.addAttribute("studentName", "Học sinh");
			}
			
			// Lấy role
			if (role != null && !role.isEmpty()) {
				String displayRole = "Student".equalsIgnoreCase(role) ? "Học sinh" : role;
				model.addAttribute("userRole", displayRole);
			} else {
				model.addAttribute("userRole", "Học sinh");
			}
				
		} catch (Exception ex) {
			ex.printStackTrace();
			model.addAttribute("className", "PMS");
			model.addAttribute("studentName", "Học sinh");
			model.addAttribute("userRole", "Học sinh");
		}
		return "student/home";
	}

}

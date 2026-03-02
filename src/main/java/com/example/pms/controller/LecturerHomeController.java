package com.example.pms.controller;

import org.springframework.stereotype.Controller;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/lecturer")
public class LecturerHomeController {
	@GetMapping("/home")
	public String index(Model model) {
		try {
			Object name = org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes()
				.getAttribute("lecturerName", org.springframework.web.context.request.RequestAttributes.SCOPE_SESSION);
			model.addAttribute("lecturerName", name != null ? name : null);
		} catch (Exception ex) {}
		return "lecturer/home";
	}

}

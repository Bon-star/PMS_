package com.example.pms.controller;

import org.springframework.stereotype.Controller;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/staff")
public class StaffHomeController {
	@GetMapping("/home")
	public String index(Model model) {
		try {
			Object name = org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes()
				.getAttribute("staffName", org.springframework.web.context.request.RequestAttributes.SCOPE_SESSION);
			model.addAttribute("staffName", name != null ? name : null);
		} catch (Exception ex) {}
		return "staff/home";
	}

}

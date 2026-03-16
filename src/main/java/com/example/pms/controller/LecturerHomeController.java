package com.example.pms.controller;

import com.example.pms.model.Account;
import com.example.pms.model.Lecturer;
import com.example.pms.repository.LecturerRepository;
import com.example.pms.repository.ProjectChangeRequestRepository;
import com.example.pms.repository.ProjectRepository;
import com.example.pms.util.RoleDisplayUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/lecturer")
public class LecturerHomeController {

    @Autowired
    private LecturerRepository lecturerRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectChangeRequestRepository projectChangeRequestRepository;

    @GetMapping("/home")
    public String index(Model model, HttpSession session) {
        Account account = (Account) session.getAttribute("account");
        if (account == null || !"Lecturer".equalsIgnoreCase(account.getRole())) {
            return "redirect:/acc/log";
        }
        Lecturer lecturer = lecturerRepository.findByAccountId(account.getId());
        if (lecturer == null) {
            return "redirect:/acc/log";
        }

        Object fullName = session.getAttribute("fullName");
        model.addAttribute("lecturerName", fullName != null ? fullName : "Giảng viên");
        model.addAttribute("displayName", fullName != null ? fullName : "Giảng viên");
        model.addAttribute("displayRole", RoleDisplayUtil.toDisplayRole("Lecturer"));
        model.addAttribute("pendingProjectCount", projectRepository.findPendingForLecturer(lecturer.getLecturerId()).size());
        model.addAttribute("pendingChangeCount", projectChangeRequestRepository.findPendingForLecturer(lecturer.getLecturerId()).size());
        model.addAttribute("trackedProjectCount", projectRepository.findApprovedForLecturer(lecturer.getLecturerId()).size());
        return "lecturer/home";
    }
}

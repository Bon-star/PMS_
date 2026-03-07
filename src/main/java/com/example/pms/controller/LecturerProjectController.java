package com.example.pms.controller;

import com.example.pms.model.Account;
import com.example.pms.model.Lecturer;
import com.example.pms.model.Project;
import com.example.pms.repository.LecturerRepository;
import com.example.pms.repository.ProjectRepository;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/lecturer/projects")
public class LecturerProjectController {

    @Autowired
    private LecturerRepository lecturerRepository;

    @Autowired
    private ProjectRepository projectRepository;

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private Account getSessionAccount(HttpSession session) {
        return (Account) session.getAttribute("account");
    }

    private Lecturer getSessionLecturer(HttpSession session) {
        Account account = getSessionAccount(session);
        if (account == null || !"Lecturer".equalsIgnoreCase(account.getRole())) {
            return null;
        }
        return lecturerRepository.findByAccountId(account.getId());
    }

    private boolean canReviewProject(int lecturerId, int projectId) {
        List<Project> pendingProjects = projectRepository.findPendingForLecturer(lecturerId);
        for (Project pending : pendingProjects) {
            if (pending.getProjectId() == projectId) {
                return true;
            }
        }
        return false;
    }

    @GetMapping
    public String index(Model model, HttpSession session) {
        Lecturer lecturer = getSessionLecturer(session);
        if (lecturer == null) {
            return "redirect:/acc/log";
        }

        Object fullName = session.getAttribute("fullName");
        model.addAttribute("lecturerName", fullName != null ? fullName : lecturer.getFullName());
        model.addAttribute("pendingProjects", projectRepository.findPendingForLecturer(lecturer.getLecturerId()));
        return "lecturer/projects";
    }

    @PostMapping("/{projectId}/approve")
    public String approveProject(@PathVariable("projectId") int projectId,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Lecturer lecturer = getSessionLecturer(session);
        if (lecturer == null) {
            return "redirect:/acc/log";
        }

        if (!canReviewProject(lecturer.getLecturerId(), projectId)) {
            redirectAttributes.addFlashAttribute("error", "Bạn không có quyền duyệt project này hoặc project không còn chờ duyệt.");
            return "redirect:/lecturer/projects";
        }

        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(normalize(startDate));
            end = LocalDate.parse(normalize(endDate));
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Ngày bắt đầu/kết thúc không hợp lệ.");
            return "redirect:/lecturer/projects";
        }

        if (!end.isAfter(start)) {
            redirectAttributes.addFlashAttribute("error", "Ngày kết thúc phải sau ngày bắt đầu.");
            return "redirect:/lecturer/projects";
        }

        LocalDateTime startTime = start.atStartOfDay();
        LocalDateTime endTime = end.atTime(LocalTime.of(23, 59, 59));
        int updated = projectRepository.approveByLecturer(projectId, startTime, endTime);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Không thể duyệt project.");
            return "redirect:/lecturer/projects";
        }

        redirectAttributes.addFlashAttribute("success", "Đã duyệt project thành công.");
        return "redirect:/lecturer/projects";
    }

    @PostMapping("/{projectId}/reject")
    public String rejectProject(@PathVariable("projectId") int projectId,
            @RequestParam("reason") String reason,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Lecturer lecturer = getSessionLecturer(session);
        if (lecturer == null) {
            return "redirect:/acc/log";
        }

        if (!canReviewProject(lecturer.getLecturerId(), projectId)) {
            redirectAttributes.addFlashAttribute("error", "Bạn không có quyền từ chối project này hoặc project không còn chờ duyệt.");
            return "redirect:/lecturer/projects";
        }

        String normalizedReason = normalize(reason);
        if (normalizedReason.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng nhập lý do từ chối.");
            return "redirect:/lecturer/projects";
        }

        int updated = projectRepository.rejectByLecturer(projectId, normalizedReason);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Không thể từ chối project.");
            return "redirect:/lecturer/projects";
        }

        redirectAttributes.addFlashAttribute("success", "Đã từ chối project và gửi lý do cho học viên.");
        return "redirect:/lecturer/projects";
    }
}

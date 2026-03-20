package com.example.pms.controller;

import com.example.pms.model.Account;
import com.example.pms.model.Classes;
import com.example.pms.repository.ClassRepository;
import com.example.pms.service.StaffStudentService;
import com.example.pms.util.RoleDisplayUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;


@Controller
@RequestMapping("/staff")
public class StaffClassController {

    @Autowired
    private ClassRepository classRepository;
    @Autowired
    private com.example.pms.repository.StudentRepository studentRepository;
    @Autowired
    private com.example.pms.repository.LecturerRepository lecturerRepository;

    private boolean isStaffSession(HttpSession session) {
        Account account = (Account) session.getAttribute("account");
        return account != null && "Staff".equalsIgnoreCase(account.getRole());
    }

    private void bindCommon(Model model, HttpSession session) {
        Object fullName = session.getAttribute("fullName");
        model.addAttribute("staffName", fullName != null ? fullName : "Staff");
        model.addAttribute("displayName", fullName != null ? fullName : "Staff");
        model.addAttribute("displayRole", RoleDisplayUtil.toDisplayRole("Staff"));
        model.addAttribute("classes", classRepository.findAll());
    }

    @GetMapping("/classrooms")
    public String classroomsPage(Model model, HttpSession session) {
        if (!isStaffSession(session)) {
            return "redirect:/acc/log";
        }
        bindCommon(model, session);
        return "staff/classroom";
    }

    @GetMapping("/classrooms/{id}")
    public String classDetail(@RequestParam(required = false) String unused, @org.springframework.web.bind.annotation.PathVariable("id") Integer id, Model model, HttpSession session) {
        if (!isStaffSession(session)) {
            return "redirect:/acc/log";
        }

        bindCommon(model, session);
        if (id == null || id <= 0) {
            model.addAttribute("error", "Invalid class selected.");
            return "staff/classroom";
        }

        Classes cls = classRepository.findById(id);
        if (cls == null) {
            model.addAttribute("error", "Class not found.");
            return "staff/classroom";
        }

        model.addAttribute("classInfo", cls);
        java.util.List<com.example.pms.model.Student> students = studentRepository.findByClassId(id);
        java.util.List<com.example.pms.model.Lecturer> lecturers = lecturerRepository.findByClassId(id);
        model.addAttribute("students", students != null ? students : java.util.List.of());
        model.addAttribute("lecturers", lecturers != null ? lecturers : java.util.List.of());

        return "staff/classroom-detail";
    }

    @PostMapping("/classrooms/create")
    public String createClass(@RequestParam("className") String className,
                              @RequestParam(name = "courseStart", required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate courseStart,
                              @RequestParam(name = "courseEnd", required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate courseEnd,
                              Model model,
                              HttpSession session) {
        if (!isStaffSession(session)) {
            return "redirect:/acc/log";
        }

        bindCommon(model, session);
        model.addAttribute("inputClassName", className);
        model.addAttribute("inputCourseStart", courseStart);
        model.addAttribute("inputCourseEnd", courseEnd);

        try {
            String name = className == null ? "" : className.trim();
            if (name.isEmpty() || name.length() > 50) {
                throw new IllegalArgumentException("Please provide a valid class name (max 50 chars).");
            }

            if (courseEnd != null && courseStart == null) {
                throw new IllegalArgumentException("Course start date is required when providing an end date.");
            }
            if (courseStart != null && courseEnd != null && courseEnd.isBefore(courseStart)) {
                throw new IllegalArgumentException("Course end date cannot be before start date.");
            }

            int id = classRepository.createClass(name, courseStart, courseEnd);
            if (id <= 0) {
                throw new IllegalStateException("Unable to create class.");
            }
            model.addAttribute("success", "Class created successfully.");
            model.addAttribute("inputClassName", "");
            model.addAttribute("inputCourseStart", null);
            model.addAttribute("inputCourseEnd", null);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            model.addAttribute("error", "Unable to create class. Please try again.");
        }

        return "staff/classroom";
    }

    @PostMapping("/classrooms/update")
    public String updateClass(@RequestParam("classId") Integer classId,
                              @RequestParam("className") String className,
                              @RequestParam(name = "courseStart", required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate courseStart,
                              @RequestParam(name = "courseEnd", required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate courseEnd,
                              Model model,
                              HttpSession session) {
        if (!isStaffSession(session)) {
            return "redirect:/acc/log";
        }

        bindCommon(model, session);

        if (classId == null || classId <= 0) {
            model.addAttribute("error", "Invalid class selected.");
            return "staff/classroom";
        }

        try {
            Classes existing = classRepository.findById(classId);
            if (existing == null) {
                model.addAttribute("error", "Class not found.");
                return "staff/classroom";
            }

            String name = className == null ? "" : className.trim();
            if (name.isEmpty() || name.length() > 50) {
                throw new IllegalArgumentException("Please provide a valid class name (max 50 chars).");
            }
            if (courseEnd != null && courseStart == null) {
                throw new IllegalArgumentException("Course start date is required when providing an end date.");
            }
            if (courseStart != null && courseEnd != null && courseEnd.isBefore(courseStart)) {
                throw new IllegalArgumentException("Course end date cannot be before start date.");
            }
            int updated = classRepository.updateClass(classId, name, courseStart, courseEnd);
            if (updated <= 0) {
                throw new IllegalStateException("Unable to update class.");
            }
            model.addAttribute("success", "Class updated successfully.");
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            model.addAttribute("error", "Unable to update class. Please try again.");
        }

        return "staff/classroom";
    }

    @PostMapping("/classrooms/delete")
    public String deleteClass(@RequestParam("classId") Integer classId,
                              Model model,
                              HttpSession session) {
        if (!isStaffSession(session)) {
            return "redirect:/acc/log";
        }

        bindCommon(model, session);

        if (classId == null || classId <= 0) {
            model.addAttribute("error", "Invalid class selected.");
            return "staff/classroom";
        }

        try {
            int deleted = classRepository.deleteById(classId);
            if (deleted <= 0) {
                throw new IllegalStateException("Unable to delete class.");
            }
            model.addAttribute("success", "Class deleted successfully.");
        } catch (Exception ex) {
            model.addAttribute("error", "Unable to delete class. Please try again.");
        }

        return "staff/classroom";
    }
}

package com.example.pms.controller.api;

import com.example.pms.model.*;
import com.example.pms.repository.*;
import jakarta.servlet.http.HttpSession;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/staff")
public class ApiStaffController {

    @Autowired private StaffRepository staffRepo;
    @Autowired private StudentRepository studentRepo;
    @Autowired private ClassRepository classRepository;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private GroupRepository groupRepository;
    @Autowired private ProjectRepository projectRepository;

    private Staff getSessionStaff(HttpSession session) {
        Account account = (Account) session.getAttribute("account");
        if (account == null || !"Staff".equalsIgnoreCase(account.getRole())) return null;
        Object profile = session.getAttribute("userProfile");
        return profile instanceof Staff ? (Staff) profile : staffRepo.findBySchoolEmail(account.getEmail());
    }

    @GetMapping("/home")
    public ResponseEntity<Map<String, Object>> home(HttpSession session) {
        Staff staff = getSessionStaff(session);
        if (staff == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Map<String, Object> result = new HashMap<>();
        result.put("totalStudents", "—");
        try { result.put("totalClasses", classRepository.findAll().size()); } catch (Exception e) { result.put("totalClasses", 0); }
        result.put("totalProjects", "—");
        result.put("pendingRequests", 0);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/students")
    public ResponseEntity<Map<String, Object>> students(@RequestParam(value = "classId", required = false) String classId,
                                                        @RequestParam(value = "page", defaultValue = "1") int page,
                                                        HttpSession session) {
        Staff staff = getSessionStaff(session);
        if (staff == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Map<String, Object> result = new HashMap<>();
        try {
            List<Student> students;
            if (classId != null && !classId.isBlank()) {
                students = studentRepo.findByClassId(Integer.parseInt(classId));
            } else {
                students = List.of();
            }
            List<Map<String, Object>> list = new ArrayList<>();
            for (Student s : students) {
                Map<String, Object> sd = new HashMap<>();
                sd.put("studentId", s.getStudentId());
                sd.put("fullName", s.getFullName());
                sd.put("schoolEmail", s.getSchoolEmail());
                sd.put("phoneNumber", s.getPhoneNumber());
                sd.put("studentCode", s.getStudentCode());
                sd.put("classId", s.getClassId());
                if (s.getClassId() != null) {
                    Classes cls = classRepository.findById(s.getClassId());
                    sd.put("className", cls != null ? cls.getClassName() : null);
                }
                list.add(sd);
            }
            result.put("students", list);
        } catch (Exception e) {
            result.put("students", List.of());
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/students")
    public ResponseEntity<Map<String, Object>> createStudent(@RequestBody Map<String, String> body, HttpSession session) {
        Staff staff = getSessionStaff(session);
        if (staff == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String fullName = body.getOrDefault("fullName", "").trim();
        String email = body.getOrDefault("email", "").trim();
        String phone = body.getOrDefault("phone", "").trim();
        String studentCode = body.getOrDefault("studentCode", "").trim();
        String classIdStr = body.getOrDefault("classId", "").trim();

        if (fullName.isEmpty() || email.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Full name and email are required."));
        }

        try {
            Integer classId = classIdStr.isEmpty() ? null : Integer.parseInt(classIdStr);
            int id = studentRepo.createStudent(studentCode, fullName, email, phone, classId != null ? classId : 0, 0);
            if (id > 0) return ResponseEntity.ok(Map.of("success", true, "message", "Student created!"));
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Failed to create student."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        }
    }

    @GetMapping("/classrooms")
    public ResponseEntity<Map<String, Object>> classrooms(HttpSession session) {
        Staff staff = getSessionStaff(session);
        if (staff == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Map<String, Object> result = new HashMap<>();
        try {
            List<Classes> classes = classRepository.findAll();
            List<Map<String, Object>> list = new ArrayList<>();
            for (Classes c : classes) {
                Map<String, Object> cd = new HashMap<>();
                cd.put("classId", c.getClassId());
                cd.put("className", c.getClassName());
                try {
                    List<Student> classStudents = studentRepo.findByClassId(c.getClassId());
                    cd.put("studentCount", classStudents != null ? classStudents.size() : 0);
                } catch (Exception e) { cd.put("studentCount", 0); }
                list.add(cd);
            }
            result.put("classes", list);
        } catch (Exception e) {
            result.put("classes", List.of());
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/classrooms")
    public ResponseEntity<Map<String, Object>> createClass(@RequestBody Map<String, String> body, HttpSession session) {
        Staff staff = getSessionStaff(session);
        if (staff == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String className = body.getOrDefault("className", "").trim();
        if (className.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Class name is required."));
        }

        try {
            int id = classRepository.createClass(className, null, null);
            if (id > 0) return ResponseEntity.ok(Map.of("success", true, "message", "Class created!"));
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Failed to create class."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        }
    }

    @PostMapping("/classrooms/delete")
    public ResponseEntity<Map<String, Object>> deleteClass(@RequestBody Map<String, Object> body, HttpSession session) {
        Staff staff = getSessionStaff(session);
        if (staff == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        int classId = ((Number) body.getOrDefault("classId", 0)).intValue();
        try {
            classRepository.deleteById(classId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Class deleted."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        }
    }

    @GetMapping("/projects")
    public ResponseEntity<Map<String, Object>> projects(@RequestParam(value = "semesterId", required = false) String semesterId,
                                                        HttpSession session) {
        Staff staff = getSessionStaff(session);
        if (staff == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Map<String, Object> result = new HashMap<>();
        try {
            int semId = (semesterId != null && !semesterId.isBlank()) ? Integer.parseInt(semesterId) : resolveCurrentSemesterId();
            List<Project> projects = projectRepository.findProjectOverviewBySemester(semId);
            List<Map<String, Object>> list = new ArrayList<>();
            for (Project p : projects) {
                Map<String, Object> pd = new HashMap<>();
                pd.put("projectId", p.getProjectId());
                pd.put("projectName", p.getProjectName());
                pd.put("description", p.getDescription());
                pd.put("groupId", p.getGroupId());
                pd.put("approvalStatus", p.getApprovalStatus());
                pd.put("approvalStatusLabel", p.getApprovalStatus() == Project.STATUS_APPROVED ? "Approved"
                        : p.getApprovalStatus() == Project.STATUS_REJECTED ? "Rejected" : "Pending");
                pd.put("startDate", p.getStartDate());
                pd.put("endDate", p.getEndDate());
                list.add(pd);
            }
            result.put("projects", list);
        } catch (Exception e) {
            result.put("projects", List.of());
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/projects/templates-manager")
    public ResponseEntity<Map<String, Object>> templates(HttpSession session) {
        Staff staff = getSessionStaff(session);
        if (staff == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Map<String, Object> result = new HashMap<>();
        result.put("templates", List.of());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/projects/requests")
    public ResponseEntity<Map<String, Object>> requests(@RequestParam(value = "semesterId", required = false) String semesterId,
                                                        HttpSession session) {
        Staff staff = getSessionStaff(session);
        if (staff == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Map<String, Object> result = new HashMap<>();
        result.put("editRequests", List.of());
        result.put("changeRequests", List.of());
        return ResponseEntity.ok(result);
    }

    private int resolveCurrentSemesterId() {
        Semester semester = semesterRepository.findCurrentSemester();
        if (semester == null) semester = semesterRepository.findById(1);
        return semester != null ? semester.getSemesterId() : 1;
    }
}

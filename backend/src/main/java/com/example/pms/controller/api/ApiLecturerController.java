package com.example.pms.controller.api;

import com.example.pms.model.*;
import com.example.pms.repository.*;
import com.example.pms.service.*;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lecturer")
public class ApiLecturerController {

    @Autowired private LecturerRepository lecturerRepo;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectChangeRequestRepository projectChangeRequestRepository;
    @Autowired private ProjectCommentRepository projectCommentRepository;
    @Autowired private ProjectTaskRepository projectTaskRepository;
    @Autowired private SprintRepository sprintRepository;
    @Autowired private StudentNotificationService studentNotificationService;

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private Lecturer getSessionLecturer(HttpSession session) {
        Account account = (Account) session.getAttribute("account");
        if (account == null || !"Lecturer".equalsIgnoreCase(account.getRole())) return null;
        return lecturerRepo.findByAccountId(account.getId());
    }

    @GetMapping("/home")
    public ResponseEntity<Map<String, Object>> home(HttpSession session) {
        Lecturer lecturer = getSessionLecturer(session);
        if (lecturer == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Map<String, Object> result = new HashMap<>();
        List<Project> pending = projectRepository.findPendingForLecturer(lecturer.getLecturerId());
        List<Project> approved = projectRepository.findApprovedForLecturer(lecturer.getLecturerId());
        result.put("pendingProjects", pending.size());
        result.put("approvedProjects", approved.size());
        result.put("totalProjects", pending.size() + approved.size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/projects")
    public ResponseEntity<Map<String, Object>> projects(HttpSession session) {
        Lecturer lecturer = getSessionLecturer(session);
        if (lecturer == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Map<String, Object> result = new HashMap<>();

        // Pending projects
        List<Project> pending = projectRepository.findPendingForLecturer(lecturer.getLecturerId());
        List<Map<String, Object>> pendingList = new ArrayList<>();
        for (Project p : pending) {
            Map<String, Object> pd = new HashMap<>();
            pd.put("projectId", p.getProjectId());
            pd.put("projectName", p.getProjectName());
            pd.put("description", p.getDescription());
            pd.put("groupName", p.getGroupName() != null ? p.getGroupName() : "Group #" + p.getGroupId());
            pendingList.add(pd);
        }
        result.put("pendingProjects", pendingList);

        // Change requests
        List<ProjectChangeRequest> changeReqs = projectChangeRequestRepository.findPendingForLecturer(lecturer.getLecturerId());
        List<Map<String, Object>> changeList = new ArrayList<>();
        for (ProjectChangeRequest r : changeReqs) {
            Map<String, Object> rd = new HashMap<>();
            rd.put("requestId", r.getRequestId());
            rd.put("projectId", r.getProjectId());
            rd.put("proposedProjectName", r.getProposedProjectName());
            rd.put("proposedDescription", r.getProposedDescription());
            rd.put("reason", r.getChangeReason());
            changeList.add(rd);
        }
        result.put("pendingChangeRequests", changeList);

        // Tracked projects
        List<Project> tracked = projectRepository.findApprovedForLecturer(lecturer.getLecturerId());
        List<Map<String, Object>> trackedList = new ArrayList<>();
        for (Project p : tracked) {
            Map<String, Object> pd = new HashMap<>();
            pd.put("projectId", p.getProjectId());
            pd.put("projectName", p.getProjectName());
            pd.put("groupName", p.getGroupName() != null ? p.getGroupName() : "Group #" + p.getGroupId());
            trackedList.add(pd);
        }
        result.put("trackedProjects", trackedList);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/projects/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable("id") int projectId,
                                                        @RequestBody Map<String, String> body,
                                                        HttpSession session) {
        Lecturer lecturer = getSessionLecturer(session);
        if (lecturer == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String startDateStr = normalize(body.getOrDefault("startDate", ""));
        String endDateStr = normalize(body.getOrDefault("endDate", ""));

        try {
            LocalDate start = LocalDate.parse(startDateStr);
            LocalDate end = LocalDate.parse(endDateStr);
            if (!end.isAfter(start)) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "End date must be after start date."));
            }

            LocalDateTime startTime = start.atStartOfDay();
            LocalDateTime endTime = end.atTime(LocalTime.of(23, 59, 59));
            int updated = projectRepository.approveByLecturer(projectId, startTime, endTime);
            if (updated <= 0) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Unable to approve project."));
            }

            Project approvedProject = projectRepository.findById(projectId);
            if (approvedProject != null) studentNotificationService.notifyProjectApproved(approvedProject);

            return ResponseEntity.ok(Map.of("success", true, "message", "Project approved!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid dates: " + e.getMessage()));
        }
    }

    @PostMapping("/projects/{id}/reject")
    public ResponseEntity<Map<String, Object>> reject(@PathVariable("id") int projectId,
                                                       @RequestBody Map<String, String> body,
                                                       HttpSession session) {
        Lecturer lecturer = getSessionLecturer(session);
        if (lecturer == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String reason = normalize(body.getOrDefault("reason", ""));
        if (reason.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Please provide a rejection reason."));
        }

        int updated = projectRepository.rejectByLecturer(projectId, reason);
        if (updated <= 0) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Unable to reject project."));
        }

        Project project = projectRepository.findById(projectId);
        if (project != null) studentNotificationService.notifyProjectRejected(project, reason);

        return ResponseEntity.ok(Map.of("success", true, "message", "Project rejected."));
    }

    @GetMapping("/projects/{id}/progress")
    public ResponseEntity<Map<String, Object>> progress(@PathVariable("id") int projectId, HttpSession session) {
        Lecturer lecturer = getSessionLecturer(session);
        if (lecturer == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Project project = projectRepository.findById(projectId);
        if (project == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Project not found."));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("project", project);
        result.put("sprints", sprintRepository.findByProject(projectId));
        result.put("tasks", projectTaskRepository.findByProject(projectId));
        result.put("comments", projectCommentRepository.findByProject(projectId));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/projects/{id}/comments")
    public ResponseEntity<Map<String, Object>> addComment(@PathVariable("id") int projectId,
                                                           @RequestBody Map<String, String> body,
                                                           HttpSession session) {
        Lecturer lecturer = getSessionLecturer(session);
        if (lecturer == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String content = normalize(body.getOrDefault("commentContent", ""));
        if (content.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Comment content is required."));
        }

        int created = projectCommentRepository.create(projectId, lecturer.getLecturerId(), content);
        if (created <= 0) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Failed to save comment."));
        }

        Project project = projectRepository.findById(projectId);
        if (project != null) studentNotificationService.notifyLecturerComment(project, content);

        return ResponseEntity.ok(Map.of("success", true, "message", "Comment saved."));
    }
}

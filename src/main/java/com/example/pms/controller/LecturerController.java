package com.example.pms.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.pms.model.MemberPerformance;
import com.example.pms.model.Project;
import com.example.pms.model.ProjectComment;
import com.example.pms.model.ProjectScore;
import com.example.pms.model.ProjectTask;
import com.example.pms.service.LecturerService;

@Controller
@RequestMapping("/lecturer")
public class LecturerController {

    @Autowired
    private LecturerService lecturerService;

    // Assume lecturerId from session/session attribute, hardcoded for demo or use Principal
    private int getCurrentLecturerId() {
        // TODO: Implement from SecurityContext or Session
        return 1; // LEC001
    }

    @GetMapping("/projects")
    public String guidedProjects(Model model) {
        int lecturerId = getCurrentLecturerId();
        List<Project> projects = lecturerService.getGuidedProjects(lecturerId);
        model.addAttribute("guidedProjects", projects);
        model.addAttribute("projectsToGrade", lecturerService.getProjectsToGrade(lecturerId));
        return "lecturer/projects";
    }

    @PostMapping("/projects/{projectId}/approve")
    public String approveProject(@PathVariable int projectId,
                                 @RequestParam boolean approve,
                                 @RequestParam(required = false) String reason,
                                 @RequestParam(required = false) String startDate,
                                 @RequestParam(required = false) String endDate,
                                 org.springframework.ui.Model model) {
        int lecturerId = getCurrentLecturerId();
        try {
            LocalDateTime start = startDate != null ? LocalDateTime.parse(startDate) : null;
            LocalDateTime end = endDate != null ? LocalDateTime.parse(endDate) : null;
            boolean success = lecturerService.approveProjectContent(lecturerId, projectId, approve, reason, start, end);
            model.addAttribute("success", success ? "Action completed" : "Failed");
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        return "redirect:/lecturer/projects";
    }

    @GetMapping("/projects/{projectId}/tasks")
    @ResponseBody
    public List<ProjectTask> getTasks(@PathVariable int projectId) {
        int lecturerId = getCurrentLecturerId();
        if (lecturerService.canAccessProject(lecturerId, projectId)) {
            return lecturerService.getProjectTasks(projectId);
        }
        return List.of();
    }

    @GetMapping("/projects/{projectId}/comments")
    @ResponseBody
    public List<ProjectComment> getComments(@PathVariable int projectId) {
        int lecturerId = getCurrentLecturerId();
        if (lecturerService.canAccessProject(lecturerId, projectId)) {
            return lecturerService.getProjectComments(projectId);
        }
        return List.of();
    }

    @PostMapping("/projects/{projectId}/comment")
    @ResponseBody
    public Map<String, Object> addComment(@PathVariable int projectId,
                                           @RequestParam int taskId,
                                           @RequestParam String comment) {
        int lecturerId = getCurrentLecturerId();
        Map<String, Object> result = Map.of("success", false);
        try {
            boolean success = lecturerService.addTaskComment(lecturerId, projectId, taskId, comment);
            result = Map.of("success", success);
        } catch (Exception e) {
            result = Map.of("success", false, "error", e.getMessage());
        }
        return result;
    }

    @GetMapping("/projects/{projectId}/stats")
    @ResponseBody
    public List<MemberPerformance> getStats(@PathVariable int projectId) {
        int lecturerId = getCurrentLecturerId();
        if (lecturerService.canAccessProject(lecturerId, projectId)) {
            return lecturerService.getStudentPerformanceStats(projectId);
        }
        return List.of();
    }

    @GetMapping("/grades/{projectId}")
    public String projectGrades(@PathVariable int projectId, Model model) {
        int lecturerId = getCurrentLecturerId();
        List<ProjectScore> scores = lecturerService.getProjectScores(projectId, lecturerId);
        model.addAttribute("scores", scores);
        model.addAttribute("projectId", projectId);
        return "lecturer/grades"; // assume template
    }

    @PostMapping("/submit-grades")
    @ResponseBody
    public Map<String, Object> submitGrades(@RequestParam int projectId,
                                            @RequestParam List<Integer> studentIds,
                                            @RequestParam List<Double> scores,
                                            @RequestParam List<String> comments) {
        int lecturerId = getCurrentLecturerId();
        Map<String, Object> result = Map.of("success", false);
        try {
            List<ProjectScore> grades = new java.util.ArrayList<>();
            for (int i = 0; i < studentIds.size(); i++) {
                ProjectScore g = new ProjectScore();
                g.setStudentId(studentIds.get(i));
                g.setLecturerScore(scores.get(i));
                g.setLecturerComment(comments.size() > i ? comments.get(i) : null);
                grades.add(g);
            }
            boolean success = lecturerService.submitStudentGrades(lecturerId, projectId, grades);
            result = Map.of("success", success);
        } catch (Exception e) {
            result = Map.of("success", false, "error", e.getMessage());
        }
        return result;
    }
}


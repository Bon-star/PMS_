package com.example.pms.controller;

import com.example.pms.model.Account;
import com.example.pms.model.Lecturer;
import com.example.pms.model.Project;
import com.example.pms.model.Student;
import com.example.pms.repository.GroupMemberRepository;
import com.example.pms.repository.LecturerRepository;
import com.example.pms.repository.ProjectRepository;
import com.example.pms.service.ProjectRequirementFileService;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/project-files")
public class ProjectFileController {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private LecturerRepository lecturerRepository;

    @Autowired
    private ProjectRequirementFileService projectRequirementFileService;

    @GetMapping("/requirements/{projectId}")
    public ResponseEntity<Resource> downloadRequirementFile(@PathVariable("projectId") int projectId,
            HttpSession session) {
        Account account = (Account) session.getAttribute("account");
        if (account == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Project project = projectRepository.findById(projectId);
        if (project == null || project.getProjectId() <= 0
                || project.getRequirementFilePath() == null || project.getRequirementFilePath().isBlank()) {
            return ResponseEntity.notFound().build();
        }

        if (!canAccessProjectFile(account, session, project)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            Resource resource = projectRequirementFileService.loadAsResource(project.getRequirementFilePath());
            MediaType mediaType = resolveMediaType(resource);
            String fileName = project.getRequirementFileName() == null || project.getRequirementFileName().isBlank()
                    ? "project-requirement"
                    : project.getRequirementFileName();
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment()
                                    .filename(fileName, StandardCharsets.UTF_8)
                                    .build()
                                    .toString())
                    .body(resource);
        } catch (IOException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    private boolean canAccessProjectFile(Account account, HttpSession session, Project project) {
        if (account == null || account.getRole() == null || project == null) {
            return false;
        }

        if ("Staff".equalsIgnoreCase(account.getRole())) {
            return true;
        }

        if ("Student".equalsIgnoreCase(account.getRole())) {
            Object profile = session.getAttribute("userProfile");
            if (profile instanceof Student student) {
                return groupMemberRepository.isMember(project.getGroupId(), student.getStudentId());
            }
            return false;
        }

        if ("Lecturer".equalsIgnoreCase(account.getRole())) {
            Object profile = session.getAttribute("userProfile");
            Lecturer lecturer = profile instanceof Lecturer ? (Lecturer) profile : lecturerRepository.findByAccountId(account.getId());
            return lecturer != null && projectRepository.canLecturerAccessProject(lecturer.getLecturerId(), project.getProjectId());
        }

        return false;
    }

    private MediaType resolveMediaType(Resource resource) {
        try {
            Path path = resource.getFile().toPath();
            String contentType = Files.probeContentType(path);
            if (contentType != null && !contentType.isBlank()) {
                return MediaType.parseMediaType(contentType);
            }
        } catch (Exception ex) {
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}

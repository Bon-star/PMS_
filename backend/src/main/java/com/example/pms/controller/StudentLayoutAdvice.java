package com.example.pms.controller;

import com.example.pms.model.Classes;
import com.example.pms.model.Group;
import com.example.pms.model.Student;
import com.example.pms.model.Semester;
import com.example.pms.repository.ClassRepository;
import com.example.pms.repository.GroupInvitationRepository;
import com.example.pms.repository.GroupRepository;
import com.example.pms.repository.SemesterRepository;
import com.example.pms.service.OpenAiChatService;
import com.example.pms.service.StudentNotificationService;
import com.example.pms.util.RoleDisplayUtil;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(assignableTypes = {
        StudentHomeController.class,
        StudentProjectController.class,
        GroupController.class
})
public class StudentLayoutAdvice {

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired
    private GroupInvitationRepository groupInvitationRepository;

    @Autowired
    private StudentNotificationService studentNotificationService;

    @Autowired
    private OpenAiChatService openAiChatService;

    @ModelAttribute
    public void populateStudentLayout(Model model, HttpSession session) {
        Object profile = session.getAttribute("userProfile");
        if (!(profile instanceof Student student)) {
            return;
        }

        Object fullName = session.getAttribute("fullName");
        Object role = session.getAttribute("role");

        model.addAttribute("studentName",
                fullName != null ? fullName : (student.getFullName() != null ? student.getFullName() : "Student"));
        model.addAttribute("userRole", RoleDisplayUtil.toDisplayRole(role != null ? role : "Student"));
        model.addAttribute("className", resolveClassName(student));
        model.addAttribute("sessionStudentId", student.getStudentId());

        boolean invitationEnabled = groupInvitationRepository.isInvitationTableAvailable();
        model.addAttribute("invitationFeatureEnabled", invitationEnabled);
        model.addAttribute("notificationCount",
                studentNotificationService.countHeaderNotifications(student, invitationEnabled));
        model.addAttribute("aiAssistantEnabled", openAiChatService.isEnabled());

        Group activeGroup = resolveActiveGroup(student);
        model.addAttribute("activeGroup", activeGroup);
        model.addAttribute("hasActiveGroup", activeGroup != null);
        model.addAttribute("activeGroupDetailUrl",
                activeGroup != null ? "/student/group/" + activeGroup.getGroupId() : "/student/group/list");
    }

    private Group resolveActiveGroup(Student student) {
        if (student == null) {
            return null;
        }
        int semesterId = resolveCurrentSemesterId();
        List<Group> groups = groupRepository.findByStudentAndSemester(student.getStudentId(), semesterId);
        return groups.isEmpty() ? null : groups.get(0);
    }

    private int resolveCurrentSemesterId() {
        Semester semester = semesterRepository.findCurrentSemester();
        if (semester == null) {
            semester = semesterRepository.findById(1);
        }
        return semester != null ? semester.getSemesterId() : 1;
    }

    private String resolveClassName(Student student) {
        if (student == null || student.getClassId() == null) {
            return "PMS";
        }
        Classes classObj = classRepository.findById(student.getClassId());
        return classObj != null ? classObj.getClassName() : "PMS";
    }
}

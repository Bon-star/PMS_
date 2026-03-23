package com.example.pms.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.pms.model.Classes;
import com.example.pms.model.Group;
import com.example.pms.model.GroupInvitation;
import com.example.pms.model.GroupNotificationItem;
import com.example.pms.model.Project;
import com.example.pms.model.Semester;
import com.example.pms.model.Student;
import com.example.pms.model.StudentNotification;
import com.example.pms.repository.ClassRepository;
import com.example.pms.repository.GroupInvitationRepository;
import com.example.pms.repository.GroupMemberRepository;
import com.example.pms.repository.GroupRepository;
import com.example.pms.repository.ProjectRepository;
import com.example.pms.repository.SemesterRepository;
import com.example.pms.repository.StudentRepository;
import com.example.pms.service.StudentNotificationService;
import com.example.pms.util.RoleDisplayUtil;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/student/group")
public class GroupController {
    private static final int MAX_GROUP_MEMBERS = 6;
    private static final int INVITE_PAGE_SIZE = 5;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private GroupInvitationRepository groupInvitationRepository;

    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private StudentNotificationService studentNotificationService;

    @GetMapping("/list")
    public String listGroups(Model model, HttpSession session) {
        Student student = (Student) session.getAttribute("userProfile");
        if (student == null) {
            return "redirect:/acc/log";
        }

        int semesterId = getCurrentSemesterId();
        boolean hasActiveGroup = groupRepository.hasActiveGroup(student.getStudentId(), semesterId);
        if (hasActiveGroup) {
            List<Group> myGroups = groupRepository.findByStudentAndSemester(student.getStudentId(), semesterId);
            if (!myGroups.isEmpty()) {
                return "redirect:/student/group/" + myGroups.get(0).getGroupId();
            }
        }
        List<Group> groups = student.getClassId() != null
                ? groupRepository.findOpenByClassAndSemester(student.getClassId(), semesterId)
                : List.of();
        boolean invitationFeatureEnabled = groupInvitationRepository.isInvitationTableAvailable();
        Set<Integer> pendingJoinGroupIds = new LinkedHashSet<>();
        if (invitationFeatureEnabled) {
            for (GroupInvitation invitation : groupInvitationRepository.findPendingByStudent(student.getStudentId())) {
                if (invitation != null) {
                    pendingJoinGroupIds.add(invitation.getGroupId());
                }
            }
        }
        Set<Integer> projectAssignedGroupIds = new LinkedHashSet<>();
        for (Group group : groups) {
            if (hasAssignedProject(group)) {
                projectAssignedGroupIds.add(group.getGroupId());
            }
        }

        addCommonPageAttributes(model, session, student);
        addNotificationCount(model, student);
        model.addAttribute("invitationFeatureEnabled", invitationFeatureEnabled);
        model.addAttribute("groups", groups);
        model.addAttribute("studentId", student.getStudentId());
        model.addAttribute("hasActiveGroup", false);
        model.addAttribute("canCreateGroup", true);
        model.addAttribute("pendingJoinGroupIds", List.copyOf(pendingJoinGroupIds));
        model.addAttribute("projectAssignedGroupIds", List.copyOf(projectAssignedGroupIds));
        return "student/group/list";
    }

    @GetMapping("/create")
    public String createGroupForm(Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        Student student = (Student) session.getAttribute("userProfile");
        if (student == null) {
            return "redirect:/acc/log";
        }

        int semesterId = getCurrentSemesterId();
        if (groupRepository.hasActiveGroup(student.getStudentId(), semesterId)) {
            redirectAttributes.addFlashAttribute("error", "You are already in a group and cannot create a new one.");
            return "redirect:/student/group/list";
        }

        addCommonPageAttributes(model, session, student);
        addNotificationCount(model, student);
        model.addAttribute("invitationFeatureEnabled", groupInvitationRepository.isInvitationTableAvailable());
        return "student/group/create";
    }

    @PostMapping("/create")
    public String createGroup(@RequestParam("groupName") String groupName,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student student = (Student) session.getAttribute("userProfile");
        if (student == null) {
            return "redirect:/acc/log";
        }

        String normalizedGroupName = groupName == null ? "" : groupName.trim();
        if (normalizedGroupName.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Group name cannot be empty.");
            return "redirect:/student/group/create";
        }

        int semesterId = getCurrentSemesterId();
        if (groupRepository.hasActiveGroup(student.getStudentId(), semesterId)) {
            redirectAttributes.addFlashAttribute("error", "You are already in a group and cannot create a new one.");
            return "redirect:/student/group/list";
        }

        if (student.getClassId() == null) {
            redirectAttributes.addFlashAttribute("error", "Your class was not found, so the group cannot be created.");
            return "redirect:/student/group/list";
        }

        int groupId = groupRepository.create(normalizedGroupName, student.getClassId(), semesterId, student.getStudentId());
        if (groupId <= 0) {
            if (groupId == -2) {
                redirectAttributes.addFlashAttribute("error", "A group with this name already exists in your class this semester.");
                return "redirect:/student/group/create";
            }
            redirectAttributes.addFlashAttribute("error", "Failed to create group.");
            return "redirect:/student/group/create";
        }

        int addResult = groupMemberRepository.addMember(groupId, student.getStudentId());
        if (addResult <= 0) {
            redirectAttributes.addFlashAttribute("error", "Group created but could not add the leader.");
            return "redirect:/student/group/list";
        }

        redirectAttributes.addFlashAttribute("success", "Group created successfully.");
        return "redirect:/student/group/" + groupId;
    }

    @GetMapping("/{id}")
    public String viewGroup(@PathVariable("id") int groupId,
            @RequestParam(name = "invitePage", defaultValue = "1") int invitePage,
            Model model,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student student = (Student) session.getAttribute("userProfile");
        if (student == null) {
            return "redirect:/acc/log";
        }

        Group group = groupRepository.findById(groupId);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "Group does not exist.");
            return "redirect:/student/group/list";
        }

        if (student.getClassId() == null || student.getClassId() != group.getClassId()) {
            redirectAttributes.addFlashAttribute("error", "You can only view groups in your class.");
            return "redirect:/student/group/list";
        }

        boolean isMember = groupMemberRepository.isMember(groupId, student.getStudentId());
        boolean isLeader = isMember && group.getLeaderId() != null && group.getLeaderId() == student.getStudentId();
        List<Student> members = groupMemberRepository.findMemberDetailsOfGroup(groupId);
        int memberCount = groupMemberRepository.countMembers(groupId);
        Project groupProject = projectRepository.findByGroupId(group.getGroupId());
        boolean groupHasProject = groupProject != null;

        boolean hasActiveGroup = groupRepository.hasActiveGroup(student.getStudentId(), group.getSemesterId());
        boolean hasPendingJoinRequest = !isMember
                && groupInvitationRepository.existsPendingByGroupAndStudent(groupId, student.getStudentId());
        boolean canRequestJoin = !isMember
                && !hasActiveGroup
                && !hasPendingJoinRequest
                && memberCount < MAX_GROUP_MEMBERS
                && !groupHasProject
                && groupInvitationRepository.isInvitationTableAvailable();
        boolean membershipLockedByProjectStart = isGroupMembershipLockedByStartedProject(groupProject);

        addCommonPageAttributes(model, session, student);
        addNotificationCount(model, student);
        model.addAttribute("invitationFeatureEnabled", groupInvitationRepository.isInvitationTableAvailable());
        model.addAttribute("group", group);
        model.addAttribute("members", members);
        model.addAttribute("isMember", isMember);
        model.addAttribute("isLeader", isLeader);
        model.addAttribute("studentId", student.getStudentId());
        model.addAttribute("memberCount", memberCount);
        addInvitePaginationAttributes(model, group, student, isMember, groupHasProject, invitePage);
        model.addAttribute("hasActiveGroup", hasActiveGroup);
        model.addAttribute("hasPendingJoinRequest", hasPendingJoinRequest);
        model.addAttribute("groupHasProject", groupHasProject);
        model.addAttribute("canRequestJoin", canRequestJoin);
        model.addAttribute("membershipLockedByProjectStart", membershipLockedByProjectStart);
        model.addAttribute("canDeleteGroup", isLeader && memberCount == 1 && !membershipLockedByProjectStart);
        return "student/group/detail";
    }

    @GetMapping("/{id}/invite-candidates")
    public String inviteCandidatesFragment(@PathVariable("id") int groupId,
            @RequestParam(name = "invitePage", defaultValue = "1") int invitePage,
            Model model,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student student = (Student) session.getAttribute("userProfile");
        if (student == null) {
            return "redirect:/acc/log";
        }

        Group group = groupRepository.findById(groupId);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "Group does not exist.");
            return "redirect:/student/group/list";
        }

        if (student.getClassId() == null || student.getClassId() != group.getClassId()) {
            redirectAttributes.addFlashAttribute("error", "You can only view groups in your class.");
            return "redirect:/student/group/list";
        }

        boolean isMember = groupMemberRepository.isMember(groupId, student.getStudentId());
        Project groupProject = projectRepository.findByGroupId(group.getGroupId());
        boolean groupHasProject = groupProject != null;

        model.addAttribute("group", group);
        model.addAttribute("isMember", isMember);
        model.addAttribute("groupHasProject", groupHasProject);
        model.addAttribute("invitationFeatureEnabled", groupInvitationRepository.isInvitationTableAvailable());
        addInvitePaginationAttributes(model, group, student, isMember, groupHasProject, invitePage);
        return "student/group/invite-candidates :: inviteCandidatesPanel";
    }

    @GetMapping("/notifications")
    public String notifications(@RequestParam(name = "page", defaultValue = "1") int page,
            Model model,
            HttpSession session) {
        Student me = (Student) session.getAttribute("userProfile");
        if (me == null) {
            return "redirect:/acc/log";
        }

        populateNotificationsModel(model, me, page, true);
        addCommonPageAttributes(model, session, me);
        addNotificationCount(model, me);
        return "student/group/notifications";
    }

    @GetMapping("/notifications/fragment")
    public String notificationsFragment(@RequestParam(name = "page", defaultValue = "1") int page,
            Model model,
            HttpSession session) {
        Student me = (Student) session.getAttribute("userProfile");
        if (me == null) {
            return "redirect:/acc/log";
        }

        populateNotificationsModel(model, me, page, true);
        return "student/group/notifications :: notificationsFeed";
    }

    @GetMapping("/notifications/count")
    @ResponseBody
    public ResponseEntity<Map<String, Integer>> notificationCount(HttpSession session) {
        Student me = (Student) session.getAttribute("userProfile");
        if (me == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        boolean invitationEnabled = groupInvitationRepository.isInvitationTableAvailable();
        int count = studentNotificationService.countHeaderNotifications(me, invitationEnabled);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PostMapping("/{id}/invite")
    public String inviteMember(@PathVariable("id") int groupId,
            @RequestParam("studentRef") String studentRef,
            @RequestParam(name = "invitePage", defaultValue = "1") int invitePage,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student me = (Student) session.getAttribute("userProfile");
        if (me == null) {
            return "redirect:/acc/log";
        }

        Group group = groupRepository.findById(groupId);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "Group does not exist.");
            return "redirect:/student/group/list";
        }

        if (!groupMemberRepository.isMember(groupId, me.getStudentId())) {
            redirectAttributes.addFlashAttribute("error", "You are not a member of this group.");
            return "redirect:/student/group/list";
        }
        if (hasAssignedProject(group)) {
            redirectAttributes.addFlashAttribute("error", "This group already has a project, so you cannot invite more members.");
            return redirectToGroupDetail(groupId, invitePage);
        }
        if (groupMemberRepository.countMembers(groupId) >= MAX_GROUP_MEMBERS) {
            redirectAttributes.addFlashAttribute("error", "This group already has 6 members, so you cannot send more invites.");
            return redirectToGroupDetail(groupId, invitePage);
        }

        String normalizedRef = studentRef == null ? "" : studentRef.trim();
        if (normalizedRef.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please enter the student ID or code to invite.");
            return redirectToGroupDetail(groupId, invitePage);
        }

        Student targetStudent;
        if (normalizedRef.matches("\\d+")) {
            targetStudent = studentRepository.findById(Integer.parseInt(normalizedRef));
        } else {
            targetStudent = studentRepository.findByStudentCode(normalizedRef);
        }

        if (targetStudent == null) {
            redirectAttributes.addFlashAttribute("error", "No student found with the provided ID.");
            return redirectToGroupDetail(groupId, invitePage);
        }

        int targetId = targetStudent.getStudentId();
        if (!studentRepository.isStudentActivated(targetId)) {
            redirectAttributes.addFlashAttribute("error",
                    "The student has not activated their account and cannot be invited.");
            return redirectToGroupDetail(groupId, invitePage);
        }
        if (targetStudent.getClassId() == null || targetStudent.getClassId() != group.getClassId()) {
            redirectAttributes.addFlashAttribute("error", "You can only invite students from the same class.");
            return redirectToGroupDetail(groupId, invitePage);
        }

        if (targetId == me.getStudentId()) {
            redirectAttributes.addFlashAttribute("error", "You cannot invite yourself to the group.");
            return redirectToGroupDetail(groupId, invitePage);
        }

        if (groupMemberRepository.isMember(groupId, targetId)) {
            redirectAttributes.addFlashAttribute("error", "This student is already in the group.");
            return redirectToGroupDetail(groupId, invitePage);
        }

        if (groupRepository.hasActiveGroup(targetId, group.getSemesterId())) {
            redirectAttributes.addFlashAttribute("error", "This student is already in another group.");
            return redirectToGroupDetail(groupId, invitePage);
        }

        if (groupInvitationRepository.existsPendingByGroupAndStudent(groupId, targetId)) {
            redirectAttributes.addFlashAttribute("error", "This student already has a pending invite request.");
            return redirectToGroupDetail(groupId, invitePage);
        }

        int invitationId = groupInvitationRepository.create(groupId, targetId, me.getStudentId());
        if (invitationId == -2) {
            redirectAttributes.addFlashAttribute("error",
                    "The database does not have the Group_Invitations table. Please run the DB update script first.");
            return redirectToGroupDetail(groupId, invitePage);
        }
        if (invitationId <= 0) {
            redirectAttributes.addFlashAttribute("error", "Failed to send invite request.");
            return redirectToGroupDetail(groupId, invitePage);
        }

        boolean isLeader = group.getLeaderId() != null && group.getLeaderId() == me.getStudentId();
        if (isLeader) {
            redirectAttributes.addFlashAttribute("success",
                    "Invite sent. The student must confirm before joining the group.");
        } else {
            redirectAttributes.addFlashAttribute("success",
                    "Invite suggestion sent to the leader. The student can join only after the leader approves.");
        }
        return redirectToGroupDetail(groupId, invitePage);
    }

    @PostMapping("/{id}/join-request")
    public String requestJoinGroup(@PathVariable("id") int groupId,
            @RequestParam(name = "source", defaultValue = "detail") String source,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student me = (Student) session.getAttribute("userProfile");
        if (me == null) {
            return "redirect:/acc/log";
        }

        Group group = groupRepository.findById(groupId);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "Group does not exist.");
            return "redirect:/student/group/list";
        }

        if (me.getClassId() == null || me.getClassId() != group.getClassId()) {
            redirectAttributes.addFlashAttribute("error", "You can only request to join a group in the same class.");
            return "redirect:/student/group/list";
        }

        if (groupMemberRepository.isMember(groupId, me.getStudentId())) {
            redirectAttributes.addFlashAttribute("error", "You are already a member of this group.");
            return redirectAfterJoinRequest(groupId, source);
        }

        if (groupRepository.hasActiveGroup(me.getStudentId(), group.getSemesterId())) {
            redirectAttributes.addFlashAttribute("error", "You are already in another group.");
            return "redirect:/student/group/list";
        }
        if (hasAssignedProject(group)) {
            redirectAttributes.addFlashAttribute("error", "This group already has a project and cannot accept more join requests.");
            return redirectAfterJoinRequest(groupId, source);
        }
        if (groupMemberRepository.countMembers(groupId) >= MAX_GROUP_MEMBERS) {
            redirectAttributes.addFlashAttribute("error", "This group already has 6 members, so you cannot send a join request.");
            return redirectAfterJoinRequest(groupId, source);
        }

        if (!groupInvitationRepository.isInvitationTableAvailable()) {
            redirectAttributes.addFlashAttribute("error", "Join request feature is not enabled.");
            return redirectAfterJoinRequest(groupId, source);
        }

        if (groupInvitationRepository.existsPendingByGroupAndStudent(groupId, me.getStudentId())) {
            redirectAttributes.addFlashAttribute("error", "You already sent a join request to this group and it is pending.");
            return redirectAfterJoinRequest(groupId, source);
        }

        int invitationId = groupInvitationRepository.create(groupId, me.getStudentId(), me.getStudentId());
        if (invitationId <= 0) {
            redirectAttributes.addFlashAttribute("error", "Failed to send join request.");
            return redirectAfterJoinRequest(groupId, source);
        }

        redirectAttributes.addFlashAttribute("success", "Join request sent. Please wait for the group leader to approve.");
        return redirectAfterJoinRequest(groupId, source);
    }

    @PostMapping("/{id}/invite/{invId}/review")
    public String reviewInviteRequest(@PathVariable("id") int groupId,
            @PathVariable("invId") int invitationId,
            @RequestParam("action") String action,
            @RequestParam(name = "source", defaultValue = "detail") String source,
            @RequestParam(name = "page", defaultValue = "1") int page,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student me = (Student) session.getAttribute("userProfile");
        if (me == null) {
            return "redirect:/acc/log";
        }

        Group group = groupRepository.findById(groupId);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "Group does not exist.");
            return redirectAfterGroupNotificationAction(groupId, source, page);
        }

        boolean isLeader = group.getLeaderId() != null && group.getLeaderId() == me.getStudentId();
        if (!isLeader) {
            redirectAttributes.addFlashAttribute("error", "Only the group leader can approve requests.");
            return redirectAfterGroupNotificationAction(groupId, source, page);
        }

        GroupInvitation invitation = groupInvitationRepository.findById(invitationId);
        if (invitation == null || invitation.getGroupId() != groupId) {
            redirectAttributes.addFlashAttribute("error", "Invalid invite request.");
            return redirectAfterGroupNotificationAction(groupId, source, page);
        }

        if (!"PENDING".equalsIgnoreCase(invitation.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "Invite request has already been processed.");
            return redirectAfterGroupNotificationAction(groupId, source, page);
        }

        // Only review proposals sent by members to the group leader
        if (invitation.getInvitedByStudentId() == me.getStudentId()) {
            redirectAttributes.addFlashAttribute("error", "This request is awaiting student confirmation, not leader approval.");
            return redirectAfterGroupNotificationAction(groupId, source, page);
        }

        if ("approve".equalsIgnoreCase(action)) {
            if (hasAssignedProject(group)) {
                groupInvitationRepository.updateStatus(invitationId, "REJECTED");
                redirectAttributes.addFlashAttribute("error",
                        "This group already has a project and cannot approve new members.");
                return redirectAfterGroupNotificationAction(groupId, source, page);
            }
            if (groupRepository.hasActiveGroup(invitation.getStudentId(), group.getSemesterId())) {
                groupInvitationRepository.updateStatus(invitationId, "REJECTED");
                redirectAttributes.addFlashAttribute("error", "The student has joined another group. The request was rejected.");
                return redirectAfterGroupNotificationAction(groupId, source, page);
            }
            if (groupMemberRepository.countMembers(groupId) >= MAX_GROUP_MEMBERS) {
                groupInvitationRepository.updateStatus(invitationId, "REJECTED");
                redirectAttributes.addFlashAttribute("error", "The group already has 6 members. The request was rejected.");
                return redirectAfterGroupNotificationAction(groupId, source, page);
            }

            if (!groupMemberRepository.isMember(groupId, invitation.getStudentId())) {
                int addResult = groupMemberRepository.addMember(groupId, invitation.getStudentId());
                if (addResult <= 0) {
                    redirectAttributes.addFlashAttribute("error", "Unable to add member after approval.");
                    return redirectAfterGroupNotificationAction(groupId, source, page);
                }
            }

            groupInvitationRepository.updateStatus(invitationId, "ACCEPTED");
            redirectAttributes.addFlashAttribute("success", "Request approved; the student has joined the group.");
            return redirectAfterGroupNotificationAction(groupId, source, page);
        }

        groupInvitationRepository.updateStatus(invitationId, "REJECTED");
        redirectAttributes.addFlashAttribute("success", "Invite request rejected.");
        return redirectAfterGroupNotificationAction(groupId, source, page);
    }

    @PostMapping("/{id}/invite/{invId}/respond")
    public String respondInviteAsStudent(@PathVariable("id") int groupId,
            @PathVariable("invId") int invitationId,
            @RequestParam("action") String action,
            @RequestParam(name = "source", defaultValue = "notifications") String source,
            @RequestParam(name = "page", defaultValue = "1") int page,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student me = (Student) session.getAttribute("userProfile");
        if (me == null) {
            return "redirect:/acc/log";
        }

        Group group = groupRepository.findById(groupId);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "Group does not exist.");
            return "redirect:/student/group/list";
        }

        GroupInvitation invitation = groupInvitationRepository.findById(invitationId);
        if (invitation == null || invitation.getGroupId() != groupId) {
            redirectAttributes.addFlashAttribute("error", "Invalid invitation.");
            return "redirect:/student/group/notifications";
        }

        if (invitation.getStudentId() != me.getStudentId()) {
            redirectAttributes.addFlashAttribute("error", "You don't have permission to handle this invitation.");
            return "redirect:/student/group/notifications";
        }

        if (!"PENDING".equalsIgnoreCase(invitation.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "Invitation has already been processed.");
            return "redirect:/student/group/notifications";
        }

        boolean invitedByLeader = group.getLeaderId() != null && invitation.getInvitedByStudentId() == group.getLeaderId();
        if (!invitedByLeader) {
            redirectAttributes.addFlashAttribute("error", "This request must be handled by the group leader, not you.");
            return "redirect:/student/group/notifications";
        }

        if ("accept".equalsIgnoreCase(action)) {
            if (hasAssignedProject(group)) {
                groupInvitationRepository.updateStatus(invitationId, "REJECTED");
                redirectAttributes.addFlashAttribute("error",
                        "This group already has a project, so you cannot join.");
                return "redirect:/student/group/notifications";
            }
            if (groupRepository.hasActiveGroup(me.getStudentId(), group.getSemesterId())) {
                groupInvitationRepository.updateStatus(invitationId, "REJECTED");
                redirectAttributes.addFlashAttribute("error", "You are already in another group and cannot join.");
                return "redirect:/student/group/notifications";
            }
            if (groupMemberRepository.countMembers(groupId) >= MAX_GROUP_MEMBERS) {
                groupInvitationRepository.updateStatus(invitationId, "REJECTED");
                redirectAttributes.addFlashAttribute("error", "This group already has 6 members and cannot accept more.");
                return "redirect:/student/group/notifications";
            }

            if (!groupMemberRepository.isMember(groupId, me.getStudentId())) {
                int addResult = groupMemberRepository.addMember(groupId, me.getStudentId());
                if (addResult <= 0) {
                    redirectAttributes.addFlashAttribute("error", "Unable to join the group.");
                    return "redirect:/student/group/notifications";
                }
            }
            groupInvitationRepository.updateStatus(invitationId, "ACCEPTED");
            redirectAttributes.addFlashAttribute("success", "You accepted the invitation and joined the group.");
            return redirectAfterGroupNotificationAction(groupId, source, page);
        }

        groupInvitationRepository.updateStatus(invitationId, "REJECTED");
        redirectAttributes.addFlashAttribute("success", "You declined the invitation.");
        return redirectAfterGroupNotificationAction(groupId, source, page);
    }

    @PostMapping("/{id}/kick")
    public String kickMember(@PathVariable("id") int groupId,
            @RequestParam("studentId") int targetId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student me = (Student) session.getAttribute("userProfile");
        if (me == null) {
            return "redirect:/acc/log";
        }

        Group group = groupRepository.findById(groupId);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "Group does not exist.");
            return "redirect:/student/group/list";
        }

        boolean isLeader = group.getLeaderId() != null && group.getLeaderId() == me.getStudentId();
        if (!isLeader) {
            redirectAttributes.addFlashAttribute("error", "Only the group leader can remove members.");
            return "redirect:/student/group/" + groupId;
        }

        if (group.getLeaderId() != null && targetId == group.getLeaderId()) {
            redirectAttributes.addFlashAttribute("error", "The group leader cannot remove themselves.");
            return "redirect:/student/group/" + groupId;
        }

        if (!groupMemberRepository.isMember(groupId, targetId)) {
            redirectAttributes.addFlashAttribute("error", "This student is not in the group.");
            return "redirect:/student/group/" + groupId;
        }

        Student removedStudent = studentRepository.findById(targetId);

        if (isGroupMembershipLockedByStartedProject(group)) {
            redirectAttributes.addFlashAttribute("error",
                    "The project has already started, so members cannot be removed from the group.");
            return "redirect:/student/group/" + groupId;
        }

        int removed = groupMemberRepository.removeMember(groupId, targetId);
        if (removed > 0) {
            studentNotificationService.notifyRemovedFromGroup(group, removedStudent, me);
            redirectAttributes.addFlashAttribute("success", "Member removed from the group.");
        } else {
            redirectAttributes.addFlashAttribute("error", "Failed to remove member from the group.");
        }
        return "redirect:/student/group/" + groupId;
    }

    @PostMapping("/{id}/transfer-leader")
    public String transferLeader(@PathVariable("id") int groupId,
            @RequestParam("newLeaderId") int newLeaderId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student me = (Student) session.getAttribute("userProfile");
        if (me == null) {
            return "redirect:/acc/log";
        }

        Group group = groupRepository.findById(groupId);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "Group does not exist.");
            return "redirect:/student/group/list";
        }

        boolean isLeader = group.getLeaderId() != null && group.getLeaderId() == me.getStudentId();
        if (!isLeader) {
            redirectAttributes.addFlashAttribute("error", "Only the current group leader can transfer leadership.");
            return "redirect:/student/group/" + groupId;
        }

        if (newLeaderId == me.getStudentId()) {
            redirectAttributes.addFlashAttribute("error", "Please choose another member as the new leader.");
            return "redirect:/student/group/" + groupId;
        }

        if (!groupMemberRepository.isMember(groupId, newLeaderId)) {
            redirectAttributes.addFlashAttribute("error", "The selected student is not an active member of this group.");
            return "redirect:/student/group/" + groupId;
        }

        int updated = groupRepository.updateLeader(groupId, newLeaderId);
        if (updated <= 0) {
            redirectAttributes.addFlashAttribute("error", "Unable to transfer group leadership.");
            return "redirect:/student/group/" + groupId;
        }

        redirectAttributes.addFlashAttribute("success", "Group leadership transferred successfully.");
        return "redirect:/student/group/" + groupId;
    }

    @PostMapping("/{id}/leave")
    public String leaveGroup(@PathVariable("id") int groupId, HttpSession session, RedirectAttributes redirectAttributes) {
        Student me = (Student) session.getAttribute("userProfile");
        if (me == null) {
            return "redirect:/acc/log";
        }

        Group group = groupRepository.findById(groupId);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "Group does not exist.");
            return "redirect:/student/group/list";
        }

        if (!groupMemberRepository.isMember(groupId, me.getStudentId())) {
            redirectAttributes.addFlashAttribute("error", "You are not a member of this group.");
            return "redirect:/student/group/list";
        }

        boolean isLeader = group.getLeaderId() != null && group.getLeaderId() == me.getStudentId();
        if (isLeader) {
            redirectAttributes.addFlashAttribute("error", "The group leader cannot leave. Delete the group when you are the only member.");
            return "redirect:/student/group/" + groupId;
        }

        if (isGroupMembershipLockedByStartedProject(group)) {
            redirectAttributes.addFlashAttribute("error",
                    "The project has already started, so you cannot leave the group.");
            return "redirect:/student/group/" + groupId;
        }

        int removed = groupMemberRepository.removeMember(groupId, me.getStudentId());
        if (removed > 0) {
            studentNotificationService.notifyMemberLeftGroup(group, me);
            redirectAttributes.addFlashAttribute("success", "You left the group.");
        } else {
            redirectAttributes.addFlashAttribute("error", "Unable to leave the group.");
        }
        return "redirect:/student/group/list";
    }

    @PostMapping("/{id}/delete")
    public String deleteGroup(@PathVariable("id") int groupId, HttpSession session, RedirectAttributes redirectAttributes) {
        Student me = (Student) session.getAttribute("userProfile");
        if (me == null) {
            return "redirect:/acc/log";
        }

        Group group = groupRepository.findById(groupId);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "Group does not exist.");
            return "redirect:/student/group/list";
        }

        boolean isLeader = group.getLeaderId() != null && group.getLeaderId() == me.getStudentId();
        if (!isLeader) {
            redirectAttributes.addFlashAttribute("error", "Only the group leader can delete the group.");
            return "redirect:/student/group/" + groupId;
        }

        int memberCount = groupMemberRepository.countMembers(groupId);
        if (memberCount > 1) {
            redirectAttributes.addFlashAttribute("error", "You can only delete the group when the leader is the only member.");
            return "redirect:/student/group/" + groupId;
        }

        if (isGroupMembershipLockedByStartedProject(group)) {
            redirectAttributes.addFlashAttribute("error",
                    "The project has already started, so the group cannot be deleted.");
            return "redirect:/student/group/" + groupId;
        }

        groupInvitationRepository.deleteByGroup(groupId);
        groupMemberRepository.removeByGroup(groupId);
        int deleted = groupRepository.deleteGroup(groupId);
        if (deleted > 0) {
            redirectAttributes.addFlashAttribute("success", "Group deleted.");
        } else {
            redirectAttributes.addFlashAttribute("error", "Failed to delete group.");
        }
        return "redirect:/student/group/list";
    }

    private int getCurrentSemesterId() {
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

    private LocalDateTime resolveNotificationTime(GroupInvitation invitation) {
        if (invitation == null) {
            return null;
        }
        return invitation.getRespondedDate() != null ? invitation.getRespondedDate() : invitation.getInvitedDate();
    }

    private boolean hasAssignedProject(Group group) {
        if (group == null) {
            return false;
        }
        return projectRepository.findByGroupId(group.getGroupId()) != null;
    }

    private boolean isGroupMembershipLockedByStartedProject(Group group) {
        return group != null && isGroupMembershipLockedByStartedProject(projectRepository.findByGroupId(group.getGroupId()));
    }

    private boolean isGroupMembershipLockedByStartedProject(Project project) {
        return project != null
                && project.getApprovalStatus() == Project.STATUS_APPROVED
                && project.getStartDate() != null
                && !project.getStartDate().isAfter(LocalDateTime.now());
    }

    private List<Student> resolveAvailableStudents(Group group, Student student, boolean isMember, boolean groupHasProject) {
        if (group == null || student == null || !isMember || groupHasProject) {
            return List.of();
        }
        return studentRepository.findInvitableStudents(
                group.getClassId(),
                group.getSemesterId(),
                group.getGroupId(),
                student.getStudentId());
    }

    private void populateNotificationsModel(Model model, Student student, int page, boolean markAsRead) {
        List<StudentNotification> recentNotifications = studentNotificationService.findRecentForStudent(student, 30);
        List<StudentNotification> projectNotifications = new ArrayList<>();
        List<StudentNotification> groupActivityNotifications = new ArrayList<>();
        for (StudentNotification notification : recentNotifications) {
            if (isGroupActivityNotification(notification)) {
                groupActivityNotifications.add(notification);
            } else {
                projectNotifications.add(notification);
            }
        }
        List<GroupInvitation> incomingInvites = groupInvitationRepository.findByStudentFromLeader(student.getStudentId());
        List<GroupInvitation> leaderRequests = groupInvitationRepository.findForLeader(student.getStudentId());
        List<GroupInvitation> sentRequests = groupInvitationRepository.findSentByStudent(student.getStudentId());
        List<GroupNotificationItem> allNotifications = new ArrayList<>();

        for (GroupInvitation invitation : incomingInvites) {
            allNotifications.add(new GroupNotificationItem(invitation, "RECEIVED_INVITE"));
        }
        for (GroupInvitation invitation : leaderRequests) {
            allNotifications.add(new GroupNotificationItem(invitation, "LEADER_REVIEW"));
        }
        for (GroupInvitation invitation : sentRequests) {
            allNotifications.add(new GroupNotificationItem(invitation, "SENT_REQUEST"));
        }

        allNotifications.sort(
                Comparator.comparing(
                        (GroupNotificationItem item) -> resolveNotificationTime(item.getInvitation()),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(item -> item.getInvitation().getInvitationId(), Comparator.reverseOrder()));

        int pageSize = 10;
        int totalItems = allNotifications.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / pageSize));
        int currentPage = Math.min(Math.max(page, 1), totalPages);
        int fromIndex = (currentPage - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, totalItems);
        List<GroupNotificationItem> pageNotifications = totalItems == 0
                ? List.of()
                : allNotifications.subList(fromIndex, toIndex);

        if (markAsRead) {
            studentNotificationService.markAllAsRead(student);
        }

        model.addAttribute("invitationFeatureEnabled", groupInvitationRepository.isInvitationTableAvailable());
        model.addAttribute("projectNotifications", projectNotifications);
        model.addAttribute("groupActivityNotifications", groupActivityNotifications);
        model.addAttribute("notifications", pageNotifications);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("hasPrev", currentPage > 1);
        model.addAttribute("hasNext", currentPage < totalPages);
        model.addAttribute("prevPage", currentPage - 1);
        model.addAttribute("nextPage", currentPage + 1);
    }

    private boolean isGroupActivityNotification(StudentNotification notification) {
        if (notification == null || notification.getNotificationType() == null) {
            return false;
        }
        return notification.getNotificationType().startsWith("GROUP_");
    }

    private void addInvitePaginationAttributes(Model model,
            Group group,
            Student student,
            boolean isMember,
            boolean groupHasProject,
            int invitePage) {
        List<Student> availableStudents = resolveAvailableStudents(group, student, isMember, groupHasProject);
        int inviteTotalStudents = availableStudents.size();
        int inviteTotalPages = Math.max(1, (int) Math.ceil((double) inviteTotalStudents / INVITE_PAGE_SIZE));
        int currentInvitePage = Math.min(Math.max(invitePage, 1), inviteTotalPages);
        int inviteFromIndex = (currentInvitePage - 1) * INVITE_PAGE_SIZE;
        int inviteToIndex = Math.min(inviteFromIndex + INVITE_PAGE_SIZE, inviteTotalStudents);
        List<Student> pagedAvailableStudents = inviteTotalStudents == 0
                ? List.of()
                : availableStudents.subList(inviteFromIndex, inviteToIndex);

        model.addAttribute("availableStudents", pagedAvailableStudents);
        model.addAttribute("inviteCurrentPage", currentInvitePage);
        model.addAttribute("inviteTotalPages", inviteTotalPages);
        model.addAttribute("inviteHasPrev", currentInvitePage > 1);
        model.addAttribute("inviteHasNext", currentInvitePage < inviteTotalPages);
        model.addAttribute("invitePrevPage", currentInvitePage - 1);
        model.addAttribute("inviteNextPage", currentInvitePage + 1);
        model.addAttribute("inviteTotalStudents", inviteTotalStudents);
        model.addAttribute("invitePageSize", INVITE_PAGE_SIZE);
    }

    private String redirectToGroupDetail(int groupId, int invitePage) {
        int safePage = Math.max(invitePage, 1);
        return "redirect:/student/group/" + groupId + "?invitePage=" + safePage;
    }

    private String redirectAfterJoinRequest(int groupId, String source) {
        return "list".equalsIgnoreCase(source)
                ? "redirect:/student/group/list"
                : "redirect:/student/group/" + groupId;
    }

    private String redirectAfterGroupNotificationAction(int groupId, String source, int page) {
        int safePage = Math.max(page, 1);
        return "notifications".equalsIgnoreCase(source)
                ? "redirect:/student/group/notifications?page=" + safePage
                : "redirect:/student/group/" + groupId;
    }

    private void addNotificationCount(Model model, Student student) {
        boolean invitationEnabled = groupInvitationRepository.isInvitationTableAvailable();
        model.addAttribute("notificationCount",
                studentNotificationService.countHeaderNotifications(student, invitationEnabled));
    }

    private void addCommonPageAttributes(Model model, HttpSession session, Student student) {
        Object fullName = session.getAttribute("fullName");
        Object role = session.getAttribute("role");
        model.addAttribute("studentName",
                fullName != null ? fullName : (student != null ? student.getFullName() : "Student"));
        model.addAttribute("userRole", RoleDisplayUtil.toDisplayRole(role != null ? role : "Student"));
        model.addAttribute("className", resolveClassName(student));
    }
}

package com.example.pms.controller.api;

import com.example.pms.model.*;
import com.example.pms.repository.*;
import com.example.pms.service.StudentNotificationService;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/student")
public class ApiStudentController {

    private static final int MAX_GROUP_MEMBERS = 6;

    @Autowired private StudentRepository studentRepo;
    @Autowired private GroupRepository groupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private GroupInvitationRepository groupInvitationRepository;
    @Autowired private ClassRepository classRepository;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectTaskRepository projectTaskRepository;
    @Autowired private SprintRepository sprintRepository;
    @Autowired private StudentNotificationService studentNotificationService;
    @Autowired private AccountRepository accountRepository;

    private Student getSessionStudent(HttpSession session) {
        Account account = (Account) session.getAttribute("account");
        Object profile = session.getAttribute("userProfile");
        if (account == null || !(profile instanceof Student) || !"Student".equalsIgnoreCase(account.getRole())) {
            return null;
        }
        return (Student) profile;
    }

    private int resolveCurrentSemesterId() {
        Semester semester = semesterRepository.findCurrentSemester();
        if (semester == null) semester = semesterRepository.findById(1);
        return semester != null ? semester.getSemesterId() : 1;
    }

    private boolean hasAssignedProject(Group group) {
        return group != null && projectRepository.findByGroupId(group.getGroupId()) != null;
    }

    private boolean isGroupMembershipLockedByStartedProject(Group group) {
        if (group == null) return false;
        Project project = projectRepository.findByGroupId(group.getGroupId());
        return project != null
                && project.getApprovalStatus() == Project.STATUS_APPROVED
                && project.getStartDate() != null
                && !project.getStartDate().isAfter(LocalDateTime.now());
    }

    // ===================== HOME =====================

    @GetMapping("/home")
    public ResponseEntity<Map<String, Object>> home(HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Map<String, Object> result = new HashMap<>();
        int semesterId = resolveCurrentSemesterId();

        List<Group> groups = groupRepository.findByStudentAndSemester(student.getStudentId(), semesterId);
        Group myGroup = groups.isEmpty() ? null : groups.get(0);
        result.put("groupName", myGroup != null ? myGroup.getGroupName() : null);

        if (myGroup != null) {
            Project project = projectRepository.findByGroupId(myGroup.getGroupId());
            if (project != null) {
                result.put("projectName", project.getProjectName());
                result.put("projectDescription", project.getDescription());
                result.put("projectEndDate", project.getEndDate() != null ? project.getEndDate().toLocalDate().toString() : null);
                List<ProjectTask> tasks = projectTaskRepository.findByProject(project.getProjectId());
                result.put("tasksDone", tasks != null ? tasks.stream().filter(t -> t.getStatus() == ProjectTask.STATUS_DONE).count() : 0);
            }
        }

        boolean invitationEnabled = groupInvitationRepository.isInvitationTableAvailable();
        result.put("notificationCount", studentNotificationService.countHeaderNotifications(student, invitationEnabled));
        return ResponseEntity.ok(result);
    }

    // ===================== PROFILE =====================

    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> profile(HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Map<String, Object> result = new HashMap<>();
        result.put("fullName", student.getFullName());
        result.put("email", student.getSchoolEmail());
        result.put("phone", student.getPhoneNumber());
        result.put("studentCode", student.getStudentCode());
        if (student.getClassId() != null) {
            Classes cls = classRepository.findById(student.getClassId());
            result.put("className", cls != null ? cls.getClassName() : null);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/profile/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody Map<String, String> body, HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Account account = (Account) session.getAttribute("account");
        String oldPassword = body.getOrDefault("oldPassword", "").trim();
        String newPassword = body.getOrDefault("newPassword", "").trim();

        org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        if (!encoder.matches(oldPassword, account.getPasswordHash())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Current password is incorrect."));
        }

        accountRepository.updatePasswordById(account.getId(), encoder.encode(newPassword));
        return ResponseEntity.ok(Map.of("success", true, "message", "Password changed successfully!"));
    }

    // ===================== GROUPS =====================

    @GetMapping("/groups")
    public ResponseEntity<Map<String, Object>> groups(HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        int semesterId = resolveCurrentSemesterId();
        List<Group> myGroups = groupRepository.findByStudentAndSemester(student.getStudentId(), semesterId);
        Map<String, Object> result = new HashMap<>();

        if (!myGroups.isEmpty()) {
            Group myGroup = myGroups.get(0);
            Map<String, Object> groupData = new HashMap<>();
            groupData.put("groupId", myGroup.getGroupId());
            groupData.put("groupName", myGroup.getGroupName());
            boolean isLeader = myGroup.getLeaderId() != null && myGroup.getLeaderId() == student.getStudentId();
            groupData.put("isLeader", isLeader);

            // Check project & lock states
            Project groupProject = projectRepository.findByGroupId(myGroup.getGroupId());
            boolean groupHasProject = groupProject != null;
            boolean membershipLocked = isGroupMembershipLockedByStartedProject(myGroup);
            groupData.put("groupHasProject", groupHasProject);
            groupData.put("membershipLocked", membershipLocked);

            // Members
            List<Student> members = groupMemberRepository.findMemberDetailsOfGroup(myGroup.getGroupId());
            List<Map<String, Object>> memberList = new ArrayList<>();
            for (Student m : members) {
                Map<String, Object> md = new HashMap<>();
                md.put("studentId", m.getStudentId());
                md.put("fullName", m.getFullName());
                md.put("schoolEmail", m.getSchoolEmail());
                md.put("studentCode", m.getStudentCode());
                md.put("isLeader", myGroup.getLeaderId() != null && myGroup.getLeaderId() == m.getStudentId());
                memberList.add(md);
            }
            groupData.put("members", memberList);
            int memberCount = memberList.size();
            groupData.put("memberCount", memberCount);
            groupData.put("canDeleteGroup", isLeader && memberCount == 1 && !membershipLocked);

            // Pending invitations for leader
            if (isLeader) {
                try {
                    List<GroupInvitation> pendingForLeader = groupInvitationRepository.findPendingByGroupForLeaderApproval(myGroup.getGroupId(), student.getStudentId());
                    List<Map<String, Object>> pendingList = new ArrayList<>();
                    for (GroupInvitation inv : pendingForLeader) {
                        Map<String, Object> invData = new HashMap<>();
                        invData.put("invitationId", inv.getInvitationId());
                        invData.put("studentId", inv.getStudentId());
                        Student invStudent = studentRepo.findById(inv.getStudentId());
                        invData.put("studentName", invStudent != null ? invStudent.getFullName() : "Unknown");
                        invData.put("studentCode", invStudent != null ? invStudent.getStudentCode() : "");
                        pendingList.add(invData);
                    }
                    groupData.put("pendingInvitations", pendingList);
                } catch (Exception e) {
                    groupData.put("pendingInvitations", List.of());
                }
            }

            result.put("myGroup", groupData);
        }

        // Available groups (only if not in a group)
        if (myGroups.isEmpty()) {
            int classId = student.getClassId() != null ? student.getClassId() : 0;
            List<Group> availableGroups = classId > 0 ? groupRepository.findOpenByClassAndSemester(classId, semesterId) : List.of();
            List<Map<String, Object>> available = new ArrayList<>();
            if (availableGroups != null) {
                // Check which groups student already has pending request
                Set<Integer> pendingGroupIds = new HashSet<>();
                try {
                    List<GroupInvitation> myPending = groupInvitationRepository.findPendingByStudent(student.getStudentId());
                    for (GroupInvitation inv : myPending) {
                        pendingGroupIds.add(inv.getGroupId());
                    }
                } catch (Exception e) { /* ignore */ }

                for (Group g : availableGroups) {
                    Map<String, Object> gd = new HashMap<>();
                    gd.put("groupId", g.getGroupId());
                    gd.put("groupName", g.getGroupName());
                    gd.put("memberCount", g.getMemberCount());
                    gd.put("leaderName", g.getLeaderName() != null ? g.getLeaderName() : "Unknown");
                    gd.put("hasPendingRequest", pendingGroupIds.contains(g.getGroupId()));
                    gd.put("hasProject", hasAssignedProject(g));
                    available.add(gd);
                }
            }
            result.put("availableGroups", available);
        }

        return ResponseEntity.ok(result);
    }

    @PostMapping("/groups")
    public ResponseEntity<Map<String, Object>> createGroup(@RequestBody Map<String, String> body, HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String groupName = body.getOrDefault("groupName", "").trim();
        if (groupName.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Group name cannot be empty."));
        }

        int semesterId = resolveCurrentSemesterId();
        if (groupRepository.hasActiveGroup(student.getStudentId(), semesterId)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "You are already in a group this semester."));
        }

        if (student.getClassId() == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Your class was not found, so the group cannot be created."));
        }

        int groupId = groupRepository.create(groupName, student.getClassId(), semesterId, student.getStudentId());
        if (groupId == -2) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "A group with this name already exists in your class this semester."));
        }
        if (groupId <= 0) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Failed to create group."));
        }

        int addResult = groupMemberRepository.addMember(groupId, student.getStudentId());
        if (addResult <= 0) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Group created but could not add the leader."));
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Group created successfully!", "groupId", groupId));
    }

    @PostMapping("/groups/{id}/rename")
    public ResponseEntity<Map<String, Object>> renameGroup(@PathVariable("id") int groupId, @RequestBody Map<String, String> body, HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Group group = groupRepository.findById(groupId);
        if (group == null) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Group does not exist."));

        boolean isLeader = group.getLeaderId() != null && group.getLeaderId() == student.getStudentId();
        if (!isLeader) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Only the group leader can rename the group."));

        String groupName = body.getOrDefault("groupName", "").trim();
        if (groupName.isEmpty()) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Group name cannot be empty."));

        int updated = groupRepository.updateGroupName(groupId, groupName);
        if (updated == -2) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "A group with this name already exists in your class this semester."));
        if (updated <= 0) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Unable to update the group name."));

        return ResponseEntity.ok(Map.of("success", true, "message", "Group name updated successfully.", "groupName", groupName));
    }

    @GetMapping("/groups/{id}/invitable")
    public ResponseEntity<Map<String, Object>> getInvitableStudents(@PathVariable("id") int groupId, HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Group group = groupRepository.findById(groupId);
        if (group == null) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Group does not exist."));

        if (!groupMemberRepository.isMember(groupId, student.getStudentId())) {
             return ResponseEntity.badRequest().body(Map.of("success", false, "message", "You are not a member of this group."));
        }

        int semesterId = resolveCurrentSemesterId();
        List<Student> invitableList = studentRepo.findInvitableStudents(student.getClassId(), semesterId, groupId, student.getStudentId());

        return ResponseEntity.ok(Map.of("success", true, "students", invitableList));
    }

    @PostMapping("/groups/{id}/invite")
    public ResponseEntity<Map<String, Object>> inviteMember(@PathVariable("id") int groupId, @RequestBody Map<String, String> body, HttpSession session) {
        Student me = getSessionStudent(session);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Group group = groupRepository.findById(groupId);
        if (group == null) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Group does not exist."));

        if (!groupMemberRepository.isMember(groupId, me.getStudentId())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "You are not a member of this group."));
        }
        if (hasAssignedProject(group)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "This group already has a project, so you cannot invite more members."));
        }
        if (groupMemberRepository.countMembers(groupId) >= MAX_GROUP_MEMBERS) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "This group already has 6 members."));
        }

        String studentRef = body.getOrDefault("studentRef", "").trim();
        if (studentRef.isEmpty()) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Please enter the student ID or code to invite."));

        Student targetStudent;
        if (studentRef.matches("\\d+")) {
            targetStudent = studentRepo.findById(Integer.parseInt(studentRef));
        } else {
            targetStudent = studentRepo.findByStudentCode(studentRef);
            if (targetStudent == null) targetStudent = studentRepo.findBySchoolEmail(studentRef);
        }

        if (targetStudent == null) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No student found with the provided ID."));

        int targetId = targetStudent.getStudentId();
        if (targetId == me.getStudentId()) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "You cannot invite yourself."));
        if (targetStudent.getClassId() == null || targetStudent.getClassId() != group.getClassId()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "You can only invite students from the same class."));
        }
        if (groupMemberRepository.isMember(groupId, targetId)) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "This student is already in the group."));
        if (groupRepository.hasActiveGroup(targetId, group.getSemesterId())) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "This student is already in another group."));
        if (groupInvitationRepository.existsPendingByGroupAndStudent(groupId, targetId)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "This student already has a pending invite request."));
        }

        int invitationId = groupInvitationRepository.create(groupId, targetId, me.getStudentId());
        if (invitationId <= 0) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Failed to send invite request."));

        boolean isLeader = group.getLeaderId() != null && group.getLeaderId() == me.getStudentId();
        if (isLeader) {
            studentNotificationService.notifyGroupInvitation(group, me, targetStudent);
        }

        String msg = isLeader ? "Invite sent. The student must confirm before joining." : "Invite suggestion sent to the leader.";
        return ResponseEntity.ok(Map.of("success", true, "message", msg));
    }

    @PostMapping("/groups/{id}/join-request")
    public ResponseEntity<Map<String, Object>> joinRequest(@PathVariable("id") int groupId, HttpSession session) {
        Student me = getSessionStudent(session);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Group group = groupRepository.findById(groupId);
        if (group == null) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Group does not exist."));

        if (me.getClassId() == null || me.getClassId() != group.getClassId()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "You can only request to join a group in the same class."));
        }
        if (groupMemberRepository.isMember(groupId, me.getStudentId())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "You are already a member of this group."));
        }
        if (groupRepository.hasActiveGroup(me.getStudentId(), group.getSemesterId())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "You are already in another group."));
        }
        if (hasAssignedProject(group)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "This group already has a project and cannot accept more join requests."));
        }
        if (groupMemberRepository.countMembers(groupId) >= MAX_GROUP_MEMBERS) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "This group already has 6 members."));
        }
        if (groupInvitationRepository.existsPendingByGroupAndStudent(groupId, me.getStudentId())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "You already sent a join request and it is pending."));
        }

        int invitationId = groupInvitationRepository.create(groupId, me.getStudentId(), me.getStudentId());
        if (invitationId <= 0) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Failed to send join request."));

        if (group.getLeaderId() != null) {
            Student leader = studentRepo.findById(group.getLeaderId());
            if (leader != null) {
                studentNotificationService.notifyJoinRequest(group, me, leader);
            }
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Join request sent. Please wait for the group leader to approve."));
    }

    @PostMapping("/groups/{groupId}/invite/{invId}/review")
    public ResponseEntity<Map<String, Object>> reviewInvite(@PathVariable("groupId") int groupId, @PathVariable("invId") int invitationId, @RequestBody Map<String, String> body, HttpSession session) {
        Student me = getSessionStudent(session);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Group group = groupRepository.findById(groupId);
        if (group == null) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Group does not exist."));

        boolean isLeader = group.getLeaderId() != null && group.getLeaderId() == me.getStudentId();
        if (!isLeader) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Only the group leader can approve requests."));

        GroupInvitation invitation = groupInvitationRepository.findById(invitationId);
        if (invitation == null || invitation.getGroupId() != groupId) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid invite request."));
        }
        if (!"PENDING".equalsIgnoreCase(invitation.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "This request has already been processed."));
        }

        String action = body.getOrDefault("action", "").trim();

        if ("approve".equalsIgnoreCase(action)) {
            if (hasAssignedProject(group)) {
                groupInvitationRepository.updateStatus(invitationId, "REJECTED");
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Group already has a project."));
            }
            if (groupRepository.hasActiveGroup(invitation.getStudentId(), group.getSemesterId())) {
                groupInvitationRepository.updateStatus(invitationId, "REJECTED");
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "The student has joined another group."));
            }
            if (groupMemberRepository.countMembers(groupId) >= MAX_GROUP_MEMBERS) {
                groupInvitationRepository.updateStatus(invitationId, "REJECTED");
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "The group already has 6 members."));
            }

            if (!groupMemberRepository.isMember(groupId, invitation.getStudentId())) {
                int addResult = groupMemberRepository.addMember(groupId, invitation.getStudentId());
                if (addResult <= 0) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Unable to add member."));
            }

            groupInvitationRepository.updateStatus(invitationId, "ACCEPTED");
            return ResponseEntity.ok(Map.of("success", true, "message", "Request approved; the student has joined the group."));
        }

        groupInvitationRepository.updateStatus(invitationId, "REJECTED");
        return ResponseEntity.ok(Map.of("success", true, "message", "Request rejected."));
    }

    @PostMapping("/groups/{groupId}/invite/{invId}/respond")
    public ResponseEntity<Map<String, Object>> respondInvite(@PathVariable("groupId") int groupId, @PathVariable("invId") int invitationId, @RequestBody Map<String, String> body, HttpSession session) {
        Student me = getSessionStudent(session);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Group group = groupRepository.findById(groupId);
        if (group == null) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Group does not exist."));

        GroupInvitation invitation = groupInvitationRepository.findById(invitationId);
        if (invitation == null || invitation.getGroupId() != groupId) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid invitation."));
        }
        if (invitation.getStudentId() != me.getStudentId()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "You don't have permission."));
        }
        if (!"PENDING".equalsIgnoreCase(invitation.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invitation has already been processed."));
        }

        String action = body.getOrDefault("action", "").trim();

        if ("accept".equalsIgnoreCase(action)) {
            if (hasAssignedProject(group)) {
                groupInvitationRepository.updateStatus(invitationId, "REJECTED");
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Group already has a project."));
            }
            if (groupRepository.hasActiveGroup(me.getStudentId(), group.getSemesterId())) {
                groupInvitationRepository.updateStatus(invitationId, "REJECTED");
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "You are already in another group."));
            }
            if (groupMemberRepository.countMembers(groupId) >= MAX_GROUP_MEMBERS) {
                groupInvitationRepository.updateStatus(invitationId, "REJECTED");
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Group already has 6 members."));
            }

            if (!groupMemberRepository.isMember(groupId, me.getStudentId())) {
                int addResult = groupMemberRepository.addMember(groupId, me.getStudentId());
                if (addResult <= 0) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Unable to join the group."));
            }
            groupInvitationRepository.updateStatus(invitationId, "ACCEPTED");
            return ResponseEntity.ok(Map.of("success", true, "message", "You accepted the invitation and joined the group."));
        }

        groupInvitationRepository.updateStatus(invitationId, "REJECTED");
        return ResponseEntity.ok(Map.of("success", true, "message", "You declined the invitation."));
    }

    @PostMapping("/groups/{id}/kick")
    public ResponseEntity<Map<String, Object>> kickMember(@PathVariable("id") int groupId, @RequestBody Map<String, Object> body, HttpSession session) {
        Student me = getSessionStudent(session);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Group group = groupRepository.findById(groupId);
        if (group == null) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Group does not exist."));

        boolean isLeader = group.getLeaderId() != null && group.getLeaderId() == me.getStudentId();
        if (!isLeader) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Only the group leader can remove members."));

        int targetId = ((Number) body.getOrDefault("studentId", 0)).intValue();
        if (group.getLeaderId() != null && targetId == group.getLeaderId()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "The group leader cannot remove themselves."));
        }
        if (!groupMemberRepository.isMember(groupId, targetId)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "This student is not in the group."));
        }
        if (isGroupMembershipLockedByStartedProject(group)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "The project has already started, so members cannot be removed."));
        }

        Student removedStudent = studentRepo.findById(targetId);
        int removed = groupMemberRepository.removeMember(groupId, targetId);
        if (removed > 0) {
            studentNotificationService.notifyRemovedFromGroup(group, removedStudent, me);
            return ResponseEntity.ok(Map.of("success", true, "message", "Member removed from the group."));
        }
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Failed to remove member."));
    }

    @PostMapping("/groups/{id}/transfer-leader")
    public ResponseEntity<Map<String, Object>> transferLeader(@PathVariable("id") int groupId, @RequestBody Map<String, Object> body, HttpSession session) {
        Student me = getSessionStudent(session);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Group group = groupRepository.findById(groupId);
        if (group == null) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Group does not exist."));

        boolean isLeader = group.getLeaderId() != null && group.getLeaderId() == me.getStudentId();
        if (!isLeader) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Only the current group leader can transfer leadership."));

        int newLeaderId = ((Number) body.getOrDefault("newLeaderId", 0)).intValue();
        if (newLeaderId == me.getStudentId()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Please choose another member as the new leader."));
        }
        if (!groupMemberRepository.isMember(groupId, newLeaderId)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "The selected student is not an active member of this group."));
        }

        int updated = groupRepository.updateLeader(groupId, newLeaderId);
        if (updated <= 0) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Unable to transfer group leadership."));

        return ResponseEntity.ok(Map.of("success", true, "message", "Group leadership transferred successfully."));
    }

    @PostMapping("/groups/{id}/leave")
    public ResponseEntity<Map<String, Object>> leaveGroup(@PathVariable("id") int groupId, HttpSession session) {
        Student me = getSessionStudent(session);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Group group = groupRepository.findById(groupId);
        if (group == null) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Group does not exist."));

        if (!groupMemberRepository.isMember(groupId, me.getStudentId())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "You are not a member of this group."));
        }

        boolean isLeader = group.getLeaderId() != null && group.getLeaderId() == me.getStudentId();
        if (isLeader) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "The group leader cannot leave. Transfer leadership first or delete if you're the only member."));
        }

        if (isGroupMembershipLockedByStartedProject(group)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "The project has already started, so you cannot leave the group."));
        }

        int removed = groupMemberRepository.removeMember(groupId, me.getStudentId());
        if (removed > 0) {
            studentNotificationService.notifyMemberLeftGroup(group, me);
            return ResponseEntity.ok(Map.of("success", true, "message", "You left the group."));
        }
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Unable to leave the group."));
    }

    @PostMapping("/groups/{id}/delete")
    public ResponseEntity<Map<String, Object>> deleteGroup(@PathVariable("id") int groupId, HttpSession session) {
        Student me = getSessionStudent(session);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Group group = groupRepository.findById(groupId);
        if (group == null) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Group does not exist."));

        boolean isLeader = group.getLeaderId() != null && group.getLeaderId() == me.getStudentId();
        if (!isLeader) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Only the group leader can delete the group."));
        }

        int memberCount = groupMemberRepository.countMembers(groupId);
        if (memberCount > 1) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "You can only delete the group when the leader is the only member."));
        }

        if (isGroupMembershipLockedByStartedProject(group)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "The project has already started, so the group cannot be deleted."));
        }

        groupInvitationRepository.deleteByGroup(groupId);
        groupMemberRepository.removeByGroup(groupId);
        int deleted = groupRepository.deleteGroup(groupId);
        if (deleted > 0) {
            return ResponseEntity.ok(Map.of("success", true, "message", "Group deleted."));
        }
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Failed to delete group."));
    }

    // ===================== PROJECT =====================

    @GetMapping("/project")
    public ResponseEntity<Map<String, Object>> project(HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        int semesterId = resolveCurrentSemesterId();
        List<Group> groups = groupRepository.findByStudentAndSemester(student.getStudentId(), semesterId);
        Map<String, Object> result = new HashMap<>();

        if (groups.isEmpty()) { result.put("noGroup", true); return ResponseEntity.ok(result); }

        Group group = groups.get(0);
        Project project = projectRepository.findByGroupId(group.getGroupId());
        if (project == null) { result.put("noProject", true); return ResponseEntity.ok(result); }

        Map<String, Object> pd = new HashMap<>();
        pd.put("projectId", project.getProjectId());
        pd.put("projectName", project.getProjectName());
        pd.put("description", project.getDescription());
        pd.put("approvalStatus", project.getApprovalStatus());
        pd.put("approvalStatusLabel", project.getApprovalStatus() == Project.STATUS_APPROVED ? "Approved"
                : project.getApprovalStatus() == Project.STATUS_REJECTED ? "Rejected" : "Pending");
        pd.put("startDate", project.getStartDate());
        pd.put("endDate", project.getEndDate());
        result.put("project", pd);

        List<Sprint> sprints = sprintRepository.findByProject(project.getProjectId());
        List<Map<String, Object>> sprintList = new ArrayList<>();
        for (Sprint s : sprints) {
            Map<String, Object> sd = new HashMap<>();
            sd.put("sprintId", s.getSprintId());
            sd.put("sprintName", s.getSprintName());
            sd.put("startDate", s.getStartDate());
            sd.put("endDate", s.getEndDate());
            sprintList.add(sd);
        }
        result.put("sprints", sprintList);

        List<ProjectTask> tasks = projectTaskRepository.findByProject(project.getProjectId());
        List<Map<String, Object>> taskList = new ArrayList<>();
        for (ProjectTask t : tasks) {
            Map<String, Object> td = new HashMap<>();
            td.put("taskId", t.getTaskId());
            td.put("taskName", t.getTaskName());
            td.put("status", t.getStatus());
            td.put("sprintId", t.getSprintId());
            td.put("assigneeId", t.getAssigneeId());
            td.put("estimatedPoints", t.getEstimatedPoints());
            if (t.getAssigneeId() > 0) {
                Student assignee = studentRepo.findById(t.getAssigneeId());
                td.put("assigneeName", assignee != null ? assignee.getFullName() : "Unknown");
            }
            taskList.add(td);
        }
        result.put("tasks", taskList);

        return ResponseEntity.ok(result);
    }

    // ===================== NOTIFICATIONS =====================

    @GetMapping("/notifications")
    public ResponseEntity<Map<String, Object>> notifications(HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Map<String, Object> result = new HashMap<>();

        // Get invitations sent by leader that student received
        List<Map<String, Object>> invitations = new ArrayList<>();
        try {
            List<GroupInvitation> leaderInvites = groupInvitationRepository.findByStudentFromLeader(student.getStudentId());
            for (GroupInvitation inv : leaderInvites) {
                Map<String, Object> invData = new HashMap<>();
                invData.put("invitationId", inv.getInvitationId());
                invData.put("groupId", inv.getGroupId());
                Group g = groupRepository.findById(inv.getGroupId());
                invData.put("groupName", g != null ? g.getGroupName() : "Unknown");
                Student inviter = studentRepo.findById(inv.getInvitedByStudentId());
                invData.put("inviterName", inviter != null ? inviter.getFullName() : "Unknown");
                invData.put("type", "leader_invite");
                invData.put("status", inv.getStatus());
                invData.put("invitedDate", inv.getInvitedDate() != null ? inv.getInvitedDate().toString() : null);
                invData.put("respondedDate", inv.getRespondedDate() != null ? inv.getRespondedDate().toString() : null);
                invitations.add(invData);
            }
            
            // Get requests sent by students to join the leader's group
            List<GroupInvitation> joinRequests = groupInvitationRepository.findForLeader(student.getStudentId());
            for (GroupInvitation req : joinRequests) {
                Map<String, Object> reqData = new HashMap<>();
                reqData.put("invitationId", req.getInvitationId());
                reqData.put("groupId", req.getGroupId());
                Group g = groupRepository.findById(req.getGroupId());
                reqData.put("groupName", g != null ? g.getGroupName() : "Unknown");
                Student requester = studentRepo.findById(req.getStudentId());
                reqData.put("requesterName", requester != null ? requester.getFullName() : "Unknown");
                reqData.put("type", "join_request");
                reqData.put("status", req.getStatus());
                reqData.put("invitedDate", req.getInvitedDate() != null ? req.getInvitedDate().toString() : null);
                reqData.put("respondedDate", req.getRespondedDate() != null ? req.getRespondedDate().toString() : null);
                invitations.add(reqData);
            }
        } catch (Exception e) { /* ignore */ }

        // Get student notifications
        List<Map<String, Object>> systemNotifications = new ArrayList<>();
        try {
            List<StudentNotification> notifs = studentNotificationService.findRecentForStudent(student, 50);
            for (StudentNotification n : notifs) {
                Map<String, Object> nd = new HashMap<>();
                nd.put("notificationId", n.getNotificationId());
                nd.put("title", n.getTitle());
                nd.put("message", n.getMessage());
                nd.put("createdDate", n.getCreatedAt());
                nd.put("isRead", n.isRead());
                nd.put("targetUrl", n.getTargetUrl());
                systemNotifications.add(nd);
            }
            
            // Mark notifications as read once they are fetched for viewing
            studentNotificationService.markAllAsRead(student);
        } catch (Exception e) { /* ignore */ }

        result.put("invitations", invitations);
        result.put("notifications", systemNotifications);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/notifications/count")
    public ResponseEntity<Map<String, Object>> notificationCount(HttpSession session) {
        Student student = getSessionStudent(session);
        if (student == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        boolean invitationEnabled = groupInvitationRepository.isInvitationTableAvailable();
        int count = studentNotificationService.countHeaderNotifications(student, invitationEnabled);
        return ResponseEntity.ok(Map.of("count", count));
    }
}

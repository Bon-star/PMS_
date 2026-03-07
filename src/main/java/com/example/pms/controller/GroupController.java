package com.example.pms.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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

import com.example.pms.model.Classes;
import com.example.pms.model.Group;
import com.example.pms.model.GroupInvitation;
import com.example.pms.model.GroupNotificationItem;
import com.example.pms.model.Semester;
import com.example.pms.model.Student;
import com.example.pms.repository.ClassRepository;
import com.example.pms.repository.GroupInvitationRepository;
import com.example.pms.repository.GroupMemberRepository;
import com.example.pms.repository.GroupRepository;
import com.example.pms.repository.SemesterRepository;
import com.example.pms.repository.StudentRepository;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/student/group")
public class GroupController {
    private static final int MAX_GROUP_MEMBERS = 4;

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

        addCommonPageAttributes(model, session, student);
        addNotificationCount(model, student.getStudentId());
        model.addAttribute("invitationFeatureEnabled", groupInvitationRepository.isInvitationTableAvailable());
        model.addAttribute("groups", groups);
        model.addAttribute("studentId", student.getStudentId());
        model.addAttribute("hasActiveGroup", false);
        model.addAttribute("canCreateGroup", true);
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
            redirectAttributes.addFlashAttribute("error", "Bạn đã ở trong một nhóm, không thể tạo nhóm mới.");
            return "redirect:/student/group/list";
        }

        addCommonPageAttributes(model, session, student);
        addNotificationCount(model, student.getStudentId());
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
            redirectAttributes.addFlashAttribute("error", "Tên nhóm không được để trống.");
            return "redirect:/student/group/create";
        }

        int semesterId = getCurrentSemesterId();
        if (groupRepository.hasActiveGroup(student.getStudentId(), semesterId)) {
            redirectAttributes.addFlashAttribute("error", "Bạn đã ở trong một nhóm, không thể tạo nhóm mới.");
            return "redirect:/student/group/list";
        }

        if (student.getClassId() == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy lớp của bạn để tạo nhóm.");
            return "redirect:/student/group/list";
        }

        int groupId = groupRepository.create(normalizedGroupName, student.getClassId(), semesterId, student.getStudentId());
        if (groupId <= 0) {
            redirectAttributes.addFlashAttribute("error", "Tạo nhóm thất bại.");
            return "redirect:/student/group/create";
        }

        int addResult = groupMemberRepository.addMember(groupId, student.getStudentId());
        if (addResult <= 0) {
            redirectAttributes.addFlashAttribute("error", "Tạo nhóm thành công nhưng không thể thêm trưởng nhóm.");
            return "redirect:/student/group/list";
        }

        redirectAttributes.addFlashAttribute("success", "Tạo nhóm thành công.");
        return "redirect:/student/group/" + groupId;
    }

    @GetMapping("/{id}")
    public String viewGroup(@PathVariable("id") int groupId,
            Model model,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student student = (Student) session.getAttribute("userProfile");
        if (student == null) {
            return "redirect:/acc/log";
        }

        Group group = groupRepository.findById(groupId);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "Nhóm không tồn tại.");
            return "redirect:/student/group/list";
        }

        if (student.getClassId() == null || student.getClassId() != group.getClassId()) {
            redirectAttributes.addFlashAttribute("error", "Bạn chỉ được xem nhóm trong lớp của mình.");
            return "redirect:/student/group/list";
        }

        boolean isMember = groupMemberRepository.isMember(groupId, student.getStudentId());
        boolean isLeader = isMember && group.getLeaderId() != null && group.getLeaderId() == student.getStudentId();
        List<Student> members = groupMemberRepository.findMemberDetailsOfGroup(groupId);
        List<Student> availableStudents = isMember
                ? studentRepository.findInvitableStudents(
                        group.getClassId(),
                        group.getSemesterId(),
                        groupId,
                        student.getStudentId())
                : List.of();
        int memberCount = groupMemberRepository.countMembers(groupId);

        boolean hasActiveGroup = groupRepository.hasActiveGroup(student.getStudentId(), group.getSemesterId());
        boolean hasPendingJoinRequest = !isMember
                && groupInvitationRepository.existsPendingByGroupAndStudent(groupId, student.getStudentId());
        boolean canRequestJoin = !isMember
                && !hasActiveGroup
                && !hasPendingJoinRequest
                && memberCount < MAX_GROUP_MEMBERS
                && groupInvitationRepository.isInvitationTableAvailable();

        addCommonPageAttributes(model, session, student);
        addNotificationCount(model, student.getStudentId());
        model.addAttribute("invitationFeatureEnabled", groupInvitationRepository.isInvitationTableAvailable());
        model.addAttribute("group", group);
        model.addAttribute("members", members);
        model.addAttribute("isMember", isMember);
        model.addAttribute("isLeader", isLeader);
        model.addAttribute("studentId", student.getStudentId());
        model.addAttribute("memberCount", memberCount);
        model.addAttribute("availableStudents", availableStudents);
        model.addAttribute("hasActiveGroup", hasActiveGroup);
        model.addAttribute("hasPendingJoinRequest", hasPendingJoinRequest);
        model.addAttribute("canRequestJoin", canRequestJoin);
        model.addAttribute("canDeleteGroup", isLeader && memberCount == 1);
        return "student/group/detail";
    }

    @GetMapping("/notifications")
    public String notifications(@RequestParam(name = "page", defaultValue = "1") int page,
            Model model,
            HttpSession session) {
        Student me = (Student) session.getAttribute("userProfile");
        if (me == null) {
            return "redirect:/acc/log";
        }

        List<GroupInvitation> incomingInvites = groupInvitationRepository.findByStudentFromLeader(me.getStudentId());
        List<GroupInvitation> leaderRequests = groupInvitationRepository.findForLeader(me.getStudentId());
        List<GroupInvitation> sentRequests = groupInvitationRepository.findSentByStudent(me.getStudentId());
        List<GroupNotificationItem> allNotifications = new ArrayList<>();

        for (GroupInvitation inv : incomingInvites) {
            allNotifications.add(new GroupNotificationItem(inv, "RECEIVED_INVITE"));
        }
        for (GroupInvitation req : leaderRequests) {
            allNotifications.add(new GroupNotificationItem(req, "LEADER_REVIEW"));
        }
        for (GroupInvitation req : sentRequests) {
            allNotifications.add(new GroupNotificationItem(req, "SENT_REQUEST"));
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

        addCommonPageAttributes(model, session, me);
        addNotificationCount(model, me.getStudentId());
        model.addAttribute("invitationFeatureEnabled", groupInvitationRepository.isInvitationTableAvailable());
        model.addAttribute("notifications", pageNotifications);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("hasPrev", currentPage > 1);
        model.addAttribute("hasNext", currentPage < totalPages);
        model.addAttribute("prevPage", currentPage - 1);
        model.addAttribute("nextPage", currentPage + 1);
        return "student/group/notifications";
    }

    @PostMapping("/{id}/invite")
    public String inviteMember(@PathVariable("id") int groupId,
            @RequestParam("studentRef") String studentRef,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student me = (Student) session.getAttribute("userProfile");
        if (me == null) {
            return "redirect:/acc/log";
        }

        Group group = groupRepository.findById(groupId);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "Nhóm không tồn tại.");
            return "redirect:/student/group/list";
        }

        if (!groupMemberRepository.isMember(groupId, me.getStudentId())) {
            redirectAttributes.addFlashAttribute("error", "Bạn không phải thành viên nhóm này.");
            return "redirect:/student/group/list";
        }
        if (groupMemberRepository.countMembers(groupId) >= MAX_GROUP_MEMBERS) {
            redirectAttributes.addFlashAttribute("error", "Nhóm đã đủ 4 thành viên, không thể gửi thêm lời mời.");
            return "redirect:/student/group/" + groupId;
        }

        String normalizedRef = studentRef == null ? "" : studentRef.trim();
        if (normalizedRef.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng nhập MSSV hoặc mã sinh viên cần mời.");
            return "redirect:/student/group/" + groupId;
        }

        Student targetStudent;
        if (normalizedRef.matches("\\d+")) {
            targetStudent = studentRepository.findById(Integer.parseInt(normalizedRef));
        } else {
            targetStudent = studentRepository.findByStudentCode(normalizedRef);
        }

        if (targetStudent == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy sinh viên với MSSV/ID đã nhập.");
            return "redirect:/student/group/" + groupId;
        }

        int targetId = targetStudent.getStudentId();
        if (!studentRepository.isStudentActivated(targetId)) {
            redirectAttributes.addFlashAttribute("error",
                    "Sinh viên chưa kích hoạt tài khoản nên chưa thể được mời vào nhóm.");
            return "redirect:/student/group/" + groupId;
        }
        if (targetStudent.getClassId() == null || targetStudent.getClassId() != group.getClassId()) {
            redirectAttributes.addFlashAttribute("error", "Chỉ được mời sinh viên cùng lớp.");
            return "redirect:/student/group/" + groupId;
        }

        if (targetId == me.getStudentId()) {
            redirectAttributes.addFlashAttribute("error", "Không thể mời chính bạn vào nhóm.");
            return "redirect:/student/group/" + groupId;
        }

        if (groupMemberRepository.isMember(groupId, targetId)) {
            redirectAttributes.addFlashAttribute("error", "Sinh viên này đã ở trong nhóm.");
            return "redirect:/student/group/" + groupId;
        }

        if (groupRepository.hasActiveGroup(targetId, group.getSemesterId())) {
            redirectAttributes.addFlashAttribute("error", "Sinh viên này đã ở trong nhóm khác.");
            return "redirect:/student/group/" + groupId;
        }

        if (groupInvitationRepository.existsPendingByGroupAndStudent(groupId, targetId)) {
            redirectAttributes.addFlashAttribute("error", "Sinh viên này đã có yêu cầu mời đang chờ xử lý.");
            return "redirect:/student/group/" + groupId;
        }

        int invitationId = groupInvitationRepository.create(groupId, targetId, me.getStudentId());
        if (invitationId == -2) {
            redirectAttributes.addFlashAttribute("error",
                    "CSDL chưa có bảng Group_Invitations. Vui lòng chạy script cập nhật DB trước.");
            return "redirect:/student/group/" + groupId;
        }
        if (invitationId <= 0) {
            redirectAttributes.addFlashAttribute("error", "Gửi yêu cầu mời thất bại.");
            return "redirect:/student/group/" + groupId;
        }

        boolean isLeader = group.getLeaderId() != null && group.getLeaderId() == me.getStudentId();
        if (isLeader) {
            redirectAttributes.addFlashAttribute("success",
                    "Đã gửi lời mời tới sinh viên. Sinh viên cần xác nhận trước khi vào nhóm.");
        } else {
            redirectAttributes.addFlashAttribute("success",
                    "Đã gửi đề xuất mời tới nhóm trưởng. Chỉ vào nhóm sau khi được nhóm trưởng duyệt.");
        }
        return "redirect:/student/group/" + groupId;
    }

    @PostMapping("/{id}/join-request")
    public String requestJoinGroup(@PathVariable("id") int groupId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student me = (Student) session.getAttribute("userProfile");
        if (me == null) {
            return "redirect:/acc/log";
        }

        Group group = groupRepository.findById(groupId);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "Nhóm không tồn tại.");
            return "redirect:/student/group/list";
        }

        if (me.getClassId() == null || me.getClassId() != group.getClassId()) {
            redirectAttributes.addFlashAttribute("error", "Chỉ được gửi yêu cầu vào nhóm trong cùng lớp.");
            return "redirect:/student/group/list";
        }

        if (groupMemberRepository.isMember(groupId, me.getStudentId())) {
            redirectAttributes.addFlashAttribute("error", "Bạn đã là thành viên của nhóm này.");
            return "redirect:/student/group/" + groupId;
        }

        if (groupRepository.hasActiveGroup(me.getStudentId(), group.getSemesterId())) {
            redirectAttributes.addFlashAttribute("error", "Bạn đang ở trong một nhóm khác.");
            return "redirect:/student/group/list";
        }
        if (groupMemberRepository.countMembers(groupId) >= MAX_GROUP_MEMBERS) {
            redirectAttributes.addFlashAttribute("error", "Nhóm đã đủ 4 thành viên, không thể gửi yêu cầu tham gia.");
            return "redirect:/student/group/" + groupId;
        }

        if (!groupInvitationRepository.isInvitationTableAvailable()) {
            redirectAttributes.addFlashAttribute("error", "Chức năng yêu cầu tham gia nhóm chưa được bật.");
            return "redirect:/student/group/" + groupId;
        }

        if (groupInvitationRepository.existsPendingByGroupAndStudent(groupId, me.getStudentId())) {
            redirectAttributes.addFlashAttribute("error", "Bạn đã gửi yêu cầu vào nhóm này và đang chờ duyệt.");
            return "redirect:/student/group/" + groupId;
        }

        int invitationId = groupInvitationRepository.create(groupId, me.getStudentId(), me.getStudentId());
        if (invitationId <= 0) {
            redirectAttributes.addFlashAttribute("error", "Gửi yêu cầu tham gia nhóm thất bại.");
            return "redirect:/student/group/" + groupId;
        }

        redirectAttributes.addFlashAttribute("success", "Đã gửi yêu cầu tham gia nhóm. Vui lòng chờ nhóm trưởng duyệt.");
        return "redirect:/student/group/" + groupId;
    }

    @PostMapping("/{id}/invite/{invId}/review")
    public String reviewInviteRequest(@PathVariable("id") int groupId,
            @PathVariable("invId") int invitationId,
            @RequestParam("action") String action,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student me = (Student) session.getAttribute("userProfile");
        if (me == null) {
            return "redirect:/acc/log";
        }

        Group group = groupRepository.findById(groupId);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "Nhóm không tồn tại.");
            return "redirect:/student/group/list";
        }

        boolean isLeader = group.getLeaderId() != null && group.getLeaderId() == me.getStudentId();
        if (!isLeader) {
            redirectAttributes.addFlashAttribute("error", "Chỉ nhóm trưởng mới được duyệt yêu cầu.");
            return "redirect:/student/group/" + groupId;
        }

        GroupInvitation invitation = groupInvitationRepository.findById(invitationId);
        if (invitation == null || invitation.getGroupId() != groupId) {
            redirectAttributes.addFlashAttribute("error", "Yêu cầu mời không hợp lệ.");
            return "redirect:/student/group/" + groupId;
        }

        if (!"PENDING".equalsIgnoreCase(invitation.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "Yêu cầu mời đã được xử lý.");
            return "redirect:/student/group/" + groupId;
        }

        // Chỉ duyệt các đề xuất do thành viên gửi lên nhóm trưởng
        if (invitation.getInvitedByStudentId() == me.getStudentId()) {
            redirectAttributes.addFlashAttribute("error", "Yêu cầu này đang chờ sinh viên xác nhận, không phải nhóm trưởng duyệt.");
            return "redirect:/student/group/" + groupId;
        }

        if ("approve".equalsIgnoreCase(action)) {
            if (groupRepository.hasActiveGroup(invitation.getStudentId(), group.getSemesterId())) {
                groupInvitationRepository.updateStatus(invitationId, "REJECTED");
                redirectAttributes.addFlashAttribute("error", "Sinh viên đã vào nhóm khác. Yêu cầu được từ chối.");
                return "redirect:/student/group/" + groupId;
            }
            if (groupMemberRepository.countMembers(groupId) >= MAX_GROUP_MEMBERS) {
                groupInvitationRepository.updateStatus(invitationId, "REJECTED");
                redirectAttributes.addFlashAttribute("error", "Nhóm đã đủ 4 thành viên. Yêu cầu được từ chối.");
                return "redirect:/student/group/" + groupId;
            }

            if (!groupMemberRepository.isMember(groupId, invitation.getStudentId())) {
                int addResult = groupMemberRepository.addMember(groupId, invitation.getStudentId());
                if (addResult <= 0) {
                    redirectAttributes.addFlashAttribute("error", "Không thể thêm thành viên sau khi duyệt.");
                    return "redirect:/student/group/" + groupId;
                }
            }

            groupInvitationRepository.updateStatus(invitationId, "ACCEPTED");
            redirectAttributes.addFlashAttribute("success", "Đã duyệt yêu cầu, sinh viên chính thức vào nhóm.");
            return "redirect:/student/group/" + groupId;
        }

        groupInvitationRepository.updateStatus(invitationId, "REJECTED");
        redirectAttributes.addFlashAttribute("success", "Đã từ chối yêu cầu mời.");
        return "redirect:/student/group/" + groupId;
    }

    @PostMapping("/{id}/invite/{invId}/respond")
    public String respondInviteAsStudent(@PathVariable("id") int groupId,
            @PathVariable("invId") int invitationId,
            @RequestParam("action") String action,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Student me = (Student) session.getAttribute("userProfile");
        if (me == null) {
            return "redirect:/acc/log";
        }

        Group group = groupRepository.findById(groupId);
        if (group == null) {
            redirectAttributes.addFlashAttribute("error", "Nhóm không tồn tại.");
            return "redirect:/student/group/list";
        }

        GroupInvitation invitation = groupInvitationRepository.findById(invitationId);
        if (invitation == null || invitation.getGroupId() != groupId) {
            redirectAttributes.addFlashAttribute("error", "Lời mời không hợp lệ.");
            return "redirect:/student/group/notifications";
        }

        if (invitation.getStudentId() != me.getStudentId()) {
            redirectAttributes.addFlashAttribute("error", "Bạn không có quyền xử lý lời mời này.");
            return "redirect:/student/group/notifications";
        }

        if (!"PENDING".equalsIgnoreCase(invitation.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "Lời mời đã được xử lý.");
            return "redirect:/student/group/notifications";
        }

        boolean invitedByLeader = group.getLeaderId() != null && invitation.getInvitedByStudentId() == group.getLeaderId();
        if (!invitedByLeader) {
            redirectAttributes.addFlashAttribute("error", "Yêu cầu này cần nhóm trưởng xử lý, không phải bạn.");
            return "redirect:/student/group/notifications";
        }

        if ("accept".equalsIgnoreCase(action)) {
            if (groupRepository.hasActiveGroup(me.getStudentId(), group.getSemesterId())) {
                groupInvitationRepository.updateStatus(invitationId, "REJECTED");
                redirectAttributes.addFlashAttribute("error", "Bạn đã ở trong nhóm khác, không thể tham gia.");
                return "redirect:/student/group/notifications";
            }
            if (groupMemberRepository.countMembers(groupId) >= MAX_GROUP_MEMBERS) {
                groupInvitationRepository.updateStatus(invitationId, "REJECTED");
                redirectAttributes.addFlashAttribute("error", "Nhóm đã đủ 4 thành viên, không thể tham gia.");
                return "redirect:/student/group/notifications";
            }

            if (!groupMemberRepository.isMember(groupId, me.getStudentId())) {
                int addResult = groupMemberRepository.addMember(groupId, me.getStudentId());
                if (addResult <= 0) {
                    redirectAttributes.addFlashAttribute("error", "Không thể tham gia nhóm.");
                    return "redirect:/student/group/notifications";
                }
            }
            groupInvitationRepository.updateStatus(invitationId, "ACCEPTED");
            redirectAttributes.addFlashAttribute("success", "Bạn đã chấp nhận lời mời và vào nhóm thành công.");
            return "redirect:/student/group/" + groupId;
        }

        groupInvitationRepository.updateStatus(invitationId, "REJECTED");
        redirectAttributes.addFlashAttribute("success", "Bạn đã từ chối lời mời.");
        return "redirect:/student/group/notifications";
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
            redirectAttributes.addFlashAttribute("error", "Nhóm không tồn tại.");
            return "redirect:/student/group/list";
        }

        boolean isLeader = group.getLeaderId() != null && group.getLeaderId() == me.getStudentId();
        if (!isLeader) {
            redirectAttributes.addFlashAttribute("error", "Chỉ nhóm trưởng mới được mời thành viên ra khỏi nhóm.");
            return "redirect:/student/group/" + groupId;
        }

        if (group.getLeaderId() != null && targetId == group.getLeaderId()) {
            redirectAttributes.addFlashAttribute("error", "Nhóm trưởng không thể tự kick chính mình.");
            return "redirect:/student/group/" + groupId;
        }

        if (!groupMemberRepository.isMember(groupId, targetId)) {
            redirectAttributes.addFlashAttribute("error", "Sinh viên này không ở trong nhóm.");
            return "redirect:/student/group/" + groupId;
        }

        int removed = groupMemberRepository.removeMember(groupId, targetId);
        if (removed > 0) {
            redirectAttributes.addFlashAttribute("success", "Đã mời thành viên ra khỏi nhóm.");
        } else {
            redirectAttributes.addFlashAttribute("error", "Mời thành viên ra khỏi nhóm thất bại.");
        }
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
            redirectAttributes.addFlashAttribute("error", "Nhóm không tồn tại.");
            return "redirect:/student/group/list";
        }

        if (!groupMemberRepository.isMember(groupId, me.getStudentId())) {
            redirectAttributes.addFlashAttribute("error", "Bạn không phải thành viên nhóm này.");
            return "redirect:/student/group/list";
        }

        boolean isLeader = group.getLeaderId() != null && group.getLeaderId() == me.getStudentId();
        if (isLeader) {
            redirectAttributes.addFlashAttribute("error", "Nhóm trưởng không thể rời nhóm. Hãy xóa nhóm khi chỉ còn một mình bạn.");
            return "redirect:/student/group/" + groupId;
        }

        int removed = groupMemberRepository.removeMember(groupId, me.getStudentId());
        if (removed > 0) {
            redirectAttributes.addFlashAttribute("success", "Bạn đã rời nhóm.");
        } else {
            redirectAttributes.addFlashAttribute("error", "Không thể rời nhóm.");
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
            redirectAttributes.addFlashAttribute("error", "Nhóm không tồn tại.");
            return "redirect:/student/group/list";
        }

        boolean isLeader = group.getLeaderId() != null && group.getLeaderId() == me.getStudentId();
        if (!isLeader) {
            redirectAttributes.addFlashAttribute("error", "Chỉ nhóm trưởng mới được xóa nhóm.");
            return "redirect:/student/group/" + groupId;
        }

        int memberCount = groupMemberRepository.countMembers(groupId);
        if (memberCount > 1) {
            redirectAttributes.addFlashAttribute("error", "Chỉ được xóa nhóm khi nhóm chỉ còn nhóm trưởng.");
            return "redirect:/student/group/" + groupId;
        }

        groupInvitationRepository.deleteByGroup(groupId);
        groupMemberRepository.removeByGroup(groupId);
        int deleted = groupRepository.deleteGroup(groupId);
        if (deleted > 0) {
            redirectAttributes.addFlashAttribute("success", "Đã xóa nhóm.");
        } else {
            redirectAttributes.addFlashAttribute("error", "Xóa nhóm thất bại.");
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

    private void addNotificationCount(Model model, int studentId) {
        int incomingFromLeader = groupInvitationRepository.countPendingByStudentFromLeader(studentId);
        int needLeaderApprove = groupInvitationRepository.countPendingForLeader(studentId);
        model.addAttribute("notificationCount", incomingFromLeader + needLeaderApprove);
    }

    private void addCommonPageAttributes(Model model, HttpSession session, Student student) {
        Object fullName = session.getAttribute("fullName");
        Object role = session.getAttribute("role");
        model.addAttribute("studentName",
                fullName != null ? fullName : (student != null ? student.getFullName() : "Học sinh"));
        model.addAttribute("userRole", role != null ? role : "Học sinh");
        model.addAttribute("className", resolveClassName(student));
    }
}

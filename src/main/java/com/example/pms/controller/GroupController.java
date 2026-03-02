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
            redirectAttributes.addFlashAttribute("error", "Ban da o trong mot nhom, khong the tao nhom moi.");
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
            redirectAttributes.addFlashAttribute("error", "TÃƒÆ’Ã‚Âªn nhÃƒÆ’Ã‚Â³m khÃƒÆ’Ã‚Â´ng Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£c Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚Â»Ã†â€™ trÃƒÂ¡Ã‚Â»Ã¢â‚¬Ëœng.");
            return "redirect:/student/group/create";
        }

        int semesterId = getCurrentSemesterId();
        if (groupRepository.hasActiveGroup(student.getStudentId(), semesterId)) {
            redirectAttributes.addFlashAttribute("error", "Ban da o trong mot nhom, khong the tao nhom moi.");
            return "redirect:/student/group/list";
        }

        if (student.getClassId() == null) {
            redirectAttributes.addFlashAttribute("error", "KhÃƒÆ’Ã‚Â´ng tÃƒÆ’Ã‚Â¬m thÃƒÂ¡Ã‚ÂºÃ‚Â¥y lÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºp cÃƒÂ¡Ã‚Â»Ã‚Â§a bÃƒÂ¡Ã‚ÂºÃ‚Â¡n Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚Â»Ã†â€™ tÃƒÂ¡Ã‚ÂºÃ‚Â¡o nhÃƒÆ’Ã‚Â³m.");
            return "redirect:/student/group/list";
        }

        int groupId = groupRepository.create(normalizedGroupName, student.getClassId(), semesterId, student.getStudentId());
        if (groupId <= 0) {
            redirectAttributes.addFlashAttribute("error", "TÃƒÂ¡Ã‚ÂºÃ‚Â¡o nhÃƒÆ’Ã‚Â³m thÃƒÂ¡Ã‚ÂºÃ‚Â¥t bÃƒÂ¡Ã‚ÂºÃ‚Â¡i.");
            return "redirect:/student/group/create";
        }

        int addResult = groupMemberRepository.addMember(groupId, student.getStudentId());
        if (addResult <= 0) {
            redirectAttributes.addFlashAttribute("error", "TÃƒÂ¡Ã‚ÂºÃ‚Â¡o nhÃƒÆ’Ã‚Â³m thÃƒÆ’Ã‚Â nh cÃƒÆ’Ã‚Â´ng nhÃƒâ€ Ã‚Â°ng khÃƒÆ’Ã‚Â´ng thÃƒÂ¡Ã‚Â»Ã†â€™ thÃƒÆ’Ã‚Âªm trÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã…Â¸ng nhÃƒÆ’Ã‚Â³m.");
            return "redirect:/student/group/list";
        }

        redirectAttributes.addFlashAttribute("success", "TÃƒÂ¡Ã‚ÂºÃ‚Â¡o nhÃƒÆ’Ã‚Â³m thÃƒÆ’Ã‚Â nh cÃƒÆ’Ã‚Â´ng.");
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
            redirectAttributes.addFlashAttribute("error", "Nhom khong ton tai.");
            return "redirect:/student/group/list";
        }

        if (student.getClassId() == null || student.getClassId() != group.getClassId()) {
            redirectAttributes.addFlashAttribute("error", "Ban chi duoc xem nhom trong lop cua minh.");
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
            redirectAttributes.addFlashAttribute("error", "NhÃƒÆ’Ã‚Â³m khÃƒÆ’Ã‚Â´ng tÃƒÂ¡Ã‚Â»Ã¢â‚¬Å“n tÃƒÂ¡Ã‚ÂºÃ‚Â¡i.");
            return "redirect:/student/group/list";
        }

        if (!groupMemberRepository.isMember(groupId, me.getStudentId())) {
            redirectAttributes.addFlashAttribute("error", "Ban khong phai thanh vien nhom nay.");
            return "redirect:/student/group/list";
        }
        if (groupMemberRepository.countMembers(groupId) >= MAX_GROUP_MEMBERS) {
            redirectAttributes.addFlashAttribute("error", "Nhom da du 4 thanh vien, khong the gui them loi moi.");
            return "redirect:/student/group/" + groupId;
        }

        String normalizedRef = studentRef == null ? "" : studentRef.trim();
        if (normalizedRef.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Vui lÃƒÆ’Ã‚Â²ng nhÃƒÂ¡Ã‚ÂºÃ‚Â­p MSSV hoÃƒÂ¡Ã‚ÂºÃ‚Â·c Student ID cÃƒÂ¡Ã‚ÂºÃ‚Â§n mÃƒÂ¡Ã‚Â»Ã‚Âi.");
            return "redirect:/student/group/" + groupId;
        }

        Student targetStudent;
        if (normalizedRef.matches("\\d+")) {
            targetStudent = studentRepository.findById(Integer.parseInt(normalizedRef));
        } else {
            targetStudent = studentRepository.findByStudentCode(normalizedRef);
        }

        if (targetStudent == null) {
            redirectAttributes.addFlashAttribute("error", "Khong tim thay sinh vien voi MSSV/ID da nhap.");
            return "redirect:/student/group/" + groupId;
        }

        int targetId = targetStudent.getStudentId();
        if (!studentRepository.isStudentActivated(targetId)) {
            redirectAttributes.addFlashAttribute("error",
                    "Sinh vien chua kich hoat tai khoan nen chua the duoc moi vao nhom.");
            return "redirect:/student/group/" + groupId;
        }
        if (targetStudent.getClassId() == null || targetStudent.getClassId() != group.getClassId()) {
            redirectAttributes.addFlashAttribute("error", "ChÃƒÂ¡Ã‚Â»Ã¢â‚¬Â° Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£c mÃƒÂ¡Ã‚Â»Ã‚Âi sinh viÃƒÆ’Ã‚Âªn cÃƒÆ’Ã‚Â¹ng lÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºp.");
            return "redirect:/student/group/" + groupId;
        }

        if (targetId == me.getStudentId()) {
            redirectAttributes.addFlashAttribute("error", "KhÃƒÆ’Ã‚Â´ng thÃƒÂ¡Ã‚Â»Ã†â€™ mÃƒÂ¡Ã‚Â»Ã‚Âi chÃƒÆ’Ã‚Â­nh bÃƒÂ¡Ã‚ÂºÃ‚Â¡n vÃƒÆ’Ã‚Â o nhÃƒÆ’Ã‚Â³m.");
            return "redirect:/student/group/" + groupId;
        }

        if (groupMemberRepository.isMember(groupId, targetId)) {
            redirectAttributes.addFlashAttribute("error", "Sinh vien nay da o trong nhom.");
            return "redirect:/student/group/" + groupId;
        }

        if (groupRepository.hasActiveGroup(targetId, group.getSemesterId())) {
            redirectAttributes.addFlashAttribute("error", "Sinh vien nay da o trong nhom khac.");
            return "redirect:/student/group/" + groupId;
        }

        if (groupInvitationRepository.existsPendingByGroupAndStudent(groupId, targetId)) {
            redirectAttributes.addFlashAttribute("error", "Sinh vien nay da co yeu cau moi dang cho xu ly.");
            return "redirect:/student/group/" + groupId;
        }

        int invitationId = groupInvitationRepository.create(groupId, targetId, me.getStudentId());
        if (invitationId == -2) {
            redirectAttributes.addFlashAttribute("error",
                    "CSDL chÃƒâ€ Ã‚Â°a cÃƒÆ’Ã‚Â³ bÃƒÂ¡Ã‚ÂºÃ‚Â£ng Group_Invitations. Vui lÃƒÆ’Ã‚Â²ng chÃƒÂ¡Ã‚ÂºÃ‚Â¡y script cÃƒÂ¡Ã‚ÂºÃ‚Â­p nhÃƒÂ¡Ã‚ÂºÃ‚Â­t DB trÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºc.");
            return "redirect:/student/group/" + groupId;
        }
        if (invitationId <= 0) {
            redirectAttributes.addFlashAttribute("error", "GÃƒÂ¡Ã‚Â»Ã‚Â­i yÃƒÆ’Ã‚Âªu cÃƒÂ¡Ã‚ÂºÃ‚Â§u mÃƒÂ¡Ã‚Â»Ã‚Âi thÃƒÂ¡Ã‚ÂºÃ‚Â¥t bÃƒÂ¡Ã‚ÂºÃ‚Â¡i.");
            return "redirect:/student/group/" + groupId;
        }

        boolean isLeader = group.getLeaderId() != null && group.getLeaderId() == me.getStudentId();
        if (isLeader) {
            redirectAttributes.addFlashAttribute("success",
                    "Da gui loi moi toi sinh vien. Sinh vien can xac nhan truoc khi vao nhom.");
        } else {
            redirectAttributes.addFlashAttribute("success",
                    "Da gui de xuat moi toi nhom truong. Chi vao nhom sau khi duoc nhom truong duyet.");
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
            redirectAttributes.addFlashAttribute("error", "Nhom khong ton tai.");
            return "redirect:/student/group/list";
        }

        if (me.getClassId() == null || me.getClassId() != group.getClassId()) {
            redirectAttributes.addFlashAttribute("error", "Chi duoc gui yeu cau vao nhom trong cung lop.");
            return "redirect:/student/group/list";
        }

        if (groupMemberRepository.isMember(groupId, me.getStudentId())) {
            redirectAttributes.addFlashAttribute("error", "Ban da la thanh vien cua nhom nay.");
            return "redirect:/student/group/" + groupId;
        }

        if (groupRepository.hasActiveGroup(me.getStudentId(), group.getSemesterId())) {
            redirectAttributes.addFlashAttribute("error", "Ban dang o trong mot nhom khac.");
            return "redirect:/student/group/list";
        }
        if (groupMemberRepository.countMembers(groupId) >= MAX_GROUP_MEMBERS) {
            redirectAttributes.addFlashAttribute("error", "Nhom da du 4 thanh vien, khong the gui yeu cau tham gia.");
            return "redirect:/student/group/" + groupId;
        }

        if (!groupInvitationRepository.isInvitationTableAvailable()) {
            redirectAttributes.addFlashAttribute("error", "Chuc nang yeu cau tham gia nhom chua duoc bat.");
            return "redirect:/student/group/" + groupId;
        }

        if (groupInvitationRepository.existsPendingByGroupAndStudent(groupId, me.getStudentId())) {
            redirectAttributes.addFlashAttribute("error", "Ban da gui yeu cau vao nhom nay va dang cho duyet.");
            return "redirect:/student/group/" + groupId;
        }

        int invitationId = groupInvitationRepository.create(groupId, me.getStudentId(), me.getStudentId());
        if (invitationId <= 0) {
            redirectAttributes.addFlashAttribute("error", "Gui yeu cau tham gia nhom that bai.");
            return "redirect:/student/group/" + groupId;
        }

        redirectAttributes.addFlashAttribute("success", "Da gui yeu cau tham gia nhom. Vui long cho nhom truong duyet.");
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
            redirectAttributes.addFlashAttribute("error", "NhÃƒÆ’Ã‚Â³m khÃƒÆ’Ã‚Â´ng tÃƒÂ¡Ã‚Â»Ã¢â‚¬Å“n tÃƒÂ¡Ã‚ÂºÃ‚Â¡i.");
            return "redirect:/student/group/list";
        }

        boolean isLeader = group.getLeaderId() != null && group.getLeaderId() == me.getStudentId();
        if (!isLeader) {
            redirectAttributes.addFlashAttribute("error", "ChÃƒÂ¡Ã‚Â»Ã¢â‚¬Â° nhÃƒÆ’Ã‚Â³m trÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã…Â¸ng mÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºi Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£c duyÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡t yÃƒÆ’Ã‚Âªu cÃƒÂ¡Ã‚ÂºÃ‚Â§u.");
            return "redirect:/student/group/" + groupId;
        }

        GroupInvitation invitation = groupInvitationRepository.findById(invitationId);
        if (invitation == null || invitation.getGroupId() != groupId) {
            redirectAttributes.addFlashAttribute("error", "YÃƒÆ’Ã‚Âªu cÃƒÂ¡Ã‚ÂºÃ‚Â§u mÃƒÂ¡Ã‚Â»Ã‚Âi khÃƒÆ’Ã‚Â´ng hÃƒÂ¡Ã‚Â»Ã‚Â£p lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡.");
            return "redirect:/student/group/" + groupId;
        }

        if (!"PENDING".equalsIgnoreCase(invitation.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "Yeu cau moi da duoc xu ly.");
            return "redirect:/student/group/" + groupId;
        }

        // ChÃƒÂ¡Ã‚Â»Ã¢â‚¬Â° duyÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡t cÃƒÆ’Ã‚Â¡c Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚Â»Ã‚Â xuÃƒÂ¡Ã‚ÂºÃ‚Â¥t do thÃƒÆ’Ã‚Â nh viÃƒÆ’Ã‚Âªn gÃƒÂ¡Ã‚Â»Ã‚Â­i lÃƒÆ’Ã‚Âªn nhÃƒÆ’Ã‚Â³m trÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã…Â¸ng
        if (invitation.getInvitedByStudentId() == me.getStudentId()) {
            redirectAttributes.addFlashAttribute("error", "YÃƒÆ’Ã‚Âªu cÃƒÂ¡Ã‚ÂºÃ‚Â§u nÃƒÆ’Ã‚Â y Ãƒâ€žÃ¢â‚¬Ëœang chÃƒÂ¡Ã‚Â»Ã‚Â sinh viÃƒÆ’Ã‚Âªn xÃƒÆ’Ã‚Â¡c nhÃƒÂ¡Ã‚ÂºÃ‚Â­n, khÃƒÆ’Ã‚Â´ng phÃƒÂ¡Ã‚ÂºÃ‚Â£i nhÃƒÆ’Ã‚Â³m trÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã…Â¸ng duyÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡t.");
            return "redirect:/student/group/" + groupId;
        }

        if ("approve".equalsIgnoreCase(action)) {
            if (groupRepository.hasActiveGroup(invitation.getStudentId(), group.getSemesterId())) {
                groupInvitationRepository.updateStatus(invitationId, "REJECTED");
                redirectAttributes.addFlashAttribute("error", "Sinh vien da vao nhom khac. Yeu cau duoc tu choi.");
                return "redirect:/student/group/" + groupId;
            }
            if (groupMemberRepository.countMembers(groupId) >= MAX_GROUP_MEMBERS) {
                groupInvitationRepository.updateStatus(invitationId, "REJECTED");
                redirectAttributes.addFlashAttribute("error", "Nhom da du 4 thanh vien. Yeu cau duoc tu choi.");
                return "redirect:/student/group/" + groupId;
            }

            if (!groupMemberRepository.isMember(groupId, invitation.getStudentId())) {
                int addResult = groupMemberRepository.addMember(groupId, invitation.getStudentId());
                if (addResult <= 0) {
                    redirectAttributes.addFlashAttribute("error", "KhÃƒÆ’Ã‚Â´ng thÃƒÂ¡Ã‚Â»Ã†â€™ thÃƒÆ’Ã‚Âªm thÃƒÆ’Ã‚Â nh viÃƒÆ’Ã‚Âªn sau khi duyÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡t.");
                    return "redirect:/student/group/" + groupId;
                }
            }

            groupInvitationRepository.updateStatus(invitationId, "ACCEPTED");
            redirectAttributes.addFlashAttribute("success", "Da duyet yeu cau, sinh vien chinh thuc vao nhom.");
            return "redirect:/student/group/" + groupId;
        }

        groupInvitationRepository.updateStatus(invitationId, "REJECTED");
        redirectAttributes.addFlashAttribute("success", "Da tu choi yeu cau moi.");
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
            redirectAttributes.addFlashAttribute("error", "NhÃƒÆ’Ã‚Â³m khÃƒÆ’Ã‚Â´ng tÃƒÂ¡Ã‚Â»Ã¢â‚¬Å“n tÃƒÂ¡Ã‚ÂºÃ‚Â¡i.");
            return "redirect:/student/group/list";
        }

        GroupInvitation invitation = groupInvitationRepository.findById(invitationId);
        if (invitation == null || invitation.getGroupId() != groupId) {
            redirectAttributes.addFlashAttribute("error", "LÃƒÂ¡Ã‚Â»Ã‚Âi mÃƒÂ¡Ã‚Â»Ã‚Âi khÃƒÆ’Ã‚Â´ng hÃƒÂ¡Ã‚Â»Ã‚Â£p lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡.");
            return "redirect:/student/group/notifications";
        }

        if (invitation.getStudentId() != me.getStudentId()) {
            redirectAttributes.addFlashAttribute("error", "BÃƒÂ¡Ã‚ÂºÃ‚Â¡n khÃƒÆ’Ã‚Â´ng cÃƒÆ’Ã‚Â³ quyÃƒÂ¡Ã‚Â»Ã‚Ân xÃƒÂ¡Ã‚Â»Ã‚Â­ lÃƒÆ’Ã‚Â½ lÃƒÂ¡Ã‚Â»Ã‚Âi mÃƒÂ¡Ã‚Â»Ã‚Âi nÃƒÆ’Ã‚Â y.");
            return "redirect:/student/group/notifications";
        }

        if (!"PENDING".equalsIgnoreCase(invitation.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "Loi moi da duoc xu ly.");
            return "redirect:/student/group/notifications";
        }

        boolean invitedByLeader = group.getLeaderId() != null && invitation.getInvitedByStudentId() == group.getLeaderId();
        if (!invitedByLeader) {
            redirectAttributes.addFlashAttribute("error", "YÃƒÆ’Ã‚Âªu cÃƒÂ¡Ã‚ÂºÃ‚Â§u nÃƒÆ’Ã‚Â y cÃƒÂ¡Ã‚ÂºÃ‚Â§n nhÃƒÆ’Ã‚Â³m trÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã…Â¸ng xÃƒÂ¡Ã‚Â»Ã‚Â­ lÃƒÆ’Ã‚Â½, khÃƒÆ’Ã‚Â´ng phÃƒÂ¡Ã‚ÂºÃ‚Â£i bÃƒÂ¡Ã‚ÂºÃ‚Â¡n.");
            return "redirect:/student/group/notifications";
        }

        if ("accept".equalsIgnoreCase(action)) {
            if (groupRepository.hasActiveGroup(me.getStudentId(), group.getSemesterId())) {
                groupInvitationRepository.updateStatus(invitationId, "REJECTED");
                redirectAttributes.addFlashAttribute("error", "Ban da o trong nhom khac, khong the tham gia.");
                return "redirect:/student/group/notifications";
            }
            if (groupMemberRepository.countMembers(groupId) >= MAX_GROUP_MEMBERS) {
                groupInvitationRepository.updateStatus(invitationId, "REJECTED");
                redirectAttributes.addFlashAttribute("error", "Nhom da du 4 thanh vien, khong the tham gia.");
                return "redirect:/student/group/notifications";
            }

            if (!groupMemberRepository.isMember(groupId, me.getStudentId())) {
                int addResult = groupMemberRepository.addMember(groupId, me.getStudentId());
                if (addResult <= 0) {
                    redirectAttributes.addFlashAttribute("error", "KhÃƒÆ’Ã‚Â´ng thÃƒÂ¡Ã‚Â»Ã†â€™ tham gia nhÃƒÆ’Ã‚Â³m.");
                    return "redirect:/student/group/notifications";
                }
            }
            groupInvitationRepository.updateStatus(invitationId, "ACCEPTED");
            redirectAttributes.addFlashAttribute("success", "Ban da chap nhan loi moi va vao nhom thanh cong.");
            return "redirect:/student/group/" + groupId;
        }

        groupInvitationRepository.updateStatus(invitationId, "REJECTED");
        redirectAttributes.addFlashAttribute("success", "Ban da tu choi loi moi.");
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
            redirectAttributes.addFlashAttribute("error", "NhÃƒÆ’Ã‚Â³m khÃƒÆ’Ã‚Â´ng tÃƒÂ¡Ã‚Â»Ã¢â‚¬Å“n tÃƒÂ¡Ã‚ÂºÃ‚Â¡i.");
            return "redirect:/student/group/list";
        }

        boolean isLeader = group.getLeaderId() != null && group.getLeaderId() == me.getStudentId();
        if (!isLeader) {
            redirectAttributes.addFlashAttribute("error", "ChÃƒÂ¡Ã‚Â»Ã¢â‚¬Â° nhÃƒÆ’Ã‚Â³m trÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã…Â¸ng mÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºi Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£c kick thÃƒÆ’Ã‚Â nh viÃƒÆ’Ã‚Âªn.");
            return "redirect:/student/group/" + groupId;
        }

        if (group.getLeaderId() != null && targetId == group.getLeaderId()) {
            redirectAttributes.addFlashAttribute("error", "NhÃƒÆ’Ã‚Â³m trÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã…Â¸ng khÃƒÆ’Ã‚Â´ng thÃƒÂ¡Ã‚Â»Ã†â€™ tÃƒÂ¡Ã‚Â»Ã‚Â± kick chÃƒÆ’Ã‚Â­nh mÃƒÆ’Ã‚Â¬nh.");
            return "redirect:/student/group/" + groupId;
        }

        if (!groupMemberRepository.isMember(groupId, targetId)) {
            redirectAttributes.addFlashAttribute("error", "Sinh viÃƒÆ’Ã‚Âªn nÃƒÆ’Ã‚Â y khÃƒÆ’Ã‚Â´ng ÃƒÂ¡Ã‚Â»Ã…Â¸ trong nhÃƒÆ’Ã‚Â³m.");
            return "redirect:/student/group/" + groupId;
        }

        int removed = groupMemberRepository.removeMember(groupId, targetId);
        if (removed > 0) {
            redirectAttributes.addFlashAttribute("success", "Da kick thanh vien ra khoi nhom.");
        } else {
            redirectAttributes.addFlashAttribute("error", "Kick thÃƒÆ’Ã‚Â nh viÃƒÆ’Ã‚Âªn thÃƒÂ¡Ã‚ÂºÃ‚Â¥t bÃƒÂ¡Ã‚ÂºÃ‚Â¡i.");
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
            redirectAttributes.addFlashAttribute("error", "NhÃƒÆ’Ã‚Â³m khÃƒÆ’Ã‚Â´ng tÃƒÂ¡Ã‚Â»Ã¢â‚¬Å“n tÃƒÂ¡Ã‚ÂºÃ‚Â¡i.");
            return "redirect:/student/group/list";
        }

        if (!groupMemberRepository.isMember(groupId, me.getStudentId())) {
            redirectAttributes.addFlashAttribute("error", "Ban khong phai thanh vien nhom nay.");
            return "redirect:/student/group/list";
        }

        boolean isLeader = group.getLeaderId() != null && group.getLeaderId() == me.getStudentId();
        if (isLeader) {
            redirectAttributes.addFlashAttribute("error", "Nhom truong khong the roi nhom. Hay xoa nhom khi chi con mot minh ban.");
            return "redirect:/student/group/" + groupId;
        }

        int removed = groupMemberRepository.removeMember(groupId, me.getStudentId());
        if (removed > 0) {
            redirectAttributes.addFlashAttribute("success", "Ban da roi nhom.");
        } else {
            redirectAttributes.addFlashAttribute("error", "KhÃƒÆ’Ã‚Â´ng thÃƒÂ¡Ã‚Â»Ã†â€™ rÃƒÂ¡Ã‚Â»Ã‚Âi nhÃƒÆ’Ã‚Â³m.");
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
            redirectAttributes.addFlashAttribute("error", "NhÃƒÆ’Ã‚Â³m khÃƒÆ’Ã‚Â´ng tÃƒÂ¡Ã‚Â»Ã¢â‚¬Å“n tÃƒÂ¡Ã‚ÂºÃ‚Â¡i.");
            return "redirect:/student/group/list";
        }

        boolean isLeader = group.getLeaderId() != null && group.getLeaderId() == me.getStudentId();
        if (!isLeader) {
            redirectAttributes.addFlashAttribute("error", "ChÃƒÂ¡Ã‚Â»Ã¢â‚¬Â° nhÃƒÆ’Ã‚Â³m trÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã…Â¸ng mÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºi Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£c xÃƒÆ’Ã‚Â³a nhÃƒÆ’Ã‚Â³m.");
            return "redirect:/student/group/" + groupId;
        }

        int memberCount = groupMemberRepository.countMembers(groupId);
        if (memberCount > 1) {
            redirectAttributes.addFlashAttribute("error", "ChÃƒÂ¡Ã‚Â»Ã¢â‚¬Â° Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£c xÃƒÆ’Ã‚Â³a nhÃƒÆ’Ã‚Â³m khi nhÃƒÆ’Ã‚Â³m chÃƒÂ¡Ã‚Â»Ã¢â‚¬Â° cÃƒÆ’Ã‚Â²n nhÃƒÆ’Ã‚Â³m trÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã…Â¸ng.");
            return "redirect:/student/group/" + groupId;
        }

        groupInvitationRepository.deleteByGroup(groupId);
        groupMemberRepository.removeByGroup(groupId);
        int deleted = groupRepository.deleteGroup(groupId);
        if (deleted > 0) {
            redirectAttributes.addFlashAttribute("success", "Da xoa nhom.");
        } else {
            redirectAttributes.addFlashAttribute("error", "XÃƒÆ’Ã‚Â³a nhÃƒÆ’Ã‚Â³m thÃƒÂ¡Ã‚ÂºÃ‚Â¥t bÃƒÂ¡Ã‚ÂºÃ‚Â¡i.");
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

    private void addNotificationCount(Model model, int studentId) {
        int incomingFromLeader = groupInvitationRepository.countPendingByStudentFromLeader(studentId);
        int needLeaderApprove = groupInvitationRepository.countPendingForLeader(studentId);
        model.addAttribute("notificationCount", incomingFromLeader + needLeaderApprove);
    }

    private void addCommonPageAttributes(Model model, HttpSession session, Student student) {
        Object fullName = session.getAttribute("fullName");
        Object role = session.getAttribute("role");
        model.addAttribute("studentName",
                fullName != null ? fullName : (student != null ? student.getFullName() : "Hoc sinh"));
        model.addAttribute("userRole", role != null ? role : "Hoc sinh");
        model.addAttribute("className", resolveClassName(student));
    }
}

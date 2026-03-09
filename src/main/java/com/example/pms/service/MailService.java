package com.example.pms.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendOtp(String to, String otp) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(to);
        mail.setSubject("Mã xác thực đăng ký (OTP)");
        mail.setText("Mã OTP của bạn là: " + otp + "\nMã này có hiệu lực trong 5 phút. Vui lòng không chia sẻ cho ai.");
        mailSender.send(mail);
    }

    public void sendProjectReviewRequest(String to, String projectName, String groupName) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(to);
        mail.setSubject("PMS - Có project cần xét duyệt");
        mail.setText("Bạn có một project cần xét duyệt trong PMS.\n\n" +
                "Nhóm: " + groupName + "\n" +
                "Project: " + projectName + "\n\n" +
                "Vui lòng đăng nhập hệ thống PMS để xem chi tiết và duyệt/từ chối.");
        mailSender.send(mail);
    }

    public void sendProjectChangeReviewRequest(String to,
            String currentProjectName,
            String proposedProjectName,
            String groupName) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(to);
        mail.setSubject("PMS - Có yêu cầu đổi project cần xét duyệt");
        mail.setText("Có yêu cầu đổi project cần xét duyệt trong PMS.\n\n" +
                "Nhóm: " + groupName + "\n" +
                "Project hiện tại: " + currentProjectName + "\n" +
                "Project đề xuất mới: " + proposedProjectName + "\n\n" +
                "Vui lòng đăng nhập hệ thống PMS để xem chi tiết và duyệt/từ chối.");
        mailSender.send(mail);
    }
}

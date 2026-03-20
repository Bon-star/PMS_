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
        mail.setSubject("Registration verification code (OTP)");
        mail.setText("Your OTP code is: " + otp + "\nThis code is valid for 5 minutes. Please do not share it.");
        mailSender.send(mail);
    }

    public void sendProjectReviewRequest(String to, String projectName, String groupName) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(to);
        mail.setSubject("PMS - Project review required");
        mail.setText("You have a project awaiting review in PMS.\n\n" +
                "Group: " + groupName + "\n" +
                "Project: " + projectName + "\n\n" +
                "Please sign in to PMS to view details and approve or reject.");
        mailSender.send(mail);
    }

    public void sendProjectChangeReviewRequest(String to,
            String currentProjectName,
            String proposedProjectName,
            String groupName) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(to);
        mail.setSubject("PMS - Project change request review");
        mail.setText("A project change request requires review in PMS.\n\n" +
                "Group: " + groupName + "\n" +
                "Current project: " + currentProjectName + "\n" +
                "Proposed project: " + proposedProjectName + "\n\n" +
                "Please sign in to PMS to view details and approve or reject.");
        mailSender.send(mail);
    }
}

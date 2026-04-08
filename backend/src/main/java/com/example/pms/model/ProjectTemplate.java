package com.example.pms.model;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

public class ProjectTemplate {
    private int templateId;
    private String name;
    private String description;
    private String source;
    private String imageUrl;
    private int version;
    private boolean active;
    private Integer semesterId;
    private String semesterName;
    private Integer year;
    private Integer createdByStaffId;
    private String createdByName;
    private LocalDateTime createdAt;
    private List<ProjectTemplateAttachment> attachments = new ArrayList<>();

    public int getTemplateId() {
        return templateId;
    }

    public void setTemplateId(int templateId) {
        this.templateId = templateId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Integer getSemesterId() {
        return semesterId;
    }

    public void setSemesterId(Integer semesterId) {
        this.semesterId = semesterId;
    }

    public String getSemesterName() {
        return semesterName;
    }

    public void setSemesterName(String semesterName) {
        this.semesterName = semesterName;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Integer getCreatedByStaffId() {
        return createdByStaffId;
    }

    public void setCreatedByStaffId(Integer createdByStaffId) {
        this.createdByStaffId = createdByStaffId;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<ProjectTemplateAttachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<ProjectTemplateAttachment> attachments) {
        this.attachments = attachments;
    }
}
package com.example.pms.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class Classes {
    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private int classId;
    private String className;
    private LocalDate startDate;
    private LocalDate endDate;

    public int getClassId() { return classId; }
    public void setClassId(int classId) { this.classId = classId; }
    
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public String getCourseLabel() {
        if (startDate == null && endDate == null) {
            return "";
        }
        if (startDate != null && endDate != null) {
            return DISPLAY_FORMATTER.format(startDate) + " - " + DISPLAY_FORMATTER.format(endDate);
        }
        LocalDate single = startDate != null ? startDate : endDate;
        return DISPLAY_FORMATTER.format(single);
    }
}

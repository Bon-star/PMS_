package com.example.pms.util;

public final class RoleDisplayUtil {

    private RoleDisplayUtil() {
    }

    public static String toDisplayRole(Object role) {
        String normalized = role == null ? "" : role.toString().trim();
        if (normalized.isEmpty()) {
            return "User";
        }
        if ("Student".equalsIgnoreCase(normalized)) {
            return "Student";
        }
        if ("Staff".equalsIgnoreCase(normalized)) {
            return "Staff";
        }
        if ("Lecturer".equalsIgnoreCase(normalized)) {
            return "Lecturer";
        }
        return normalized;
    }
}

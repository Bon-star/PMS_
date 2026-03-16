package com.example.pms.util;

public final class RoleDisplayUtil {

    private RoleDisplayUtil() {
    }

    public static String toDisplayRole(Object role) {
        String normalized = role == null ? "" : role.toString().trim();
        if (normalized.isEmpty()) {
            return "Ng\u01b0\u1eddi d\u00f9ng";
        }
        if ("Student".equalsIgnoreCase(normalized)) {
            return "H\u1ecdc vi\u00ean";
        }
        if ("Staff".equalsIgnoreCase(normalized)) {
            return "Nh\u00e2n vi\u00ean";
        }
        if ("Lecturer".equalsIgnoreCase(normalized)) {
            return "Gi\u1ea3ng vi\u00ean";
        }
        return normalized;
    }
}

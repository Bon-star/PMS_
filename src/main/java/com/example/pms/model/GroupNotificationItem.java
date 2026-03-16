package com.example.pms.model;

public class GroupNotificationItem {
    private final GroupInvitation invitation;
    private final String notificationType;

    public GroupNotificationItem(GroupInvitation invitation, String notificationType) {
        this.invitation = invitation;
        this.notificationType = notificationType;
    }

    public GroupInvitation getInvitation() {
        return invitation;
    }

    public String getNotificationType() {
        return notificationType;
    }
}

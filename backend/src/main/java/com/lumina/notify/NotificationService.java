package com.lumina.notify;

import com.lumina.domain.Notification;
import com.lumina.repo.NotificationRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class NotificationService {
    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    public void notify(String userId, String type, String title, String body, String link) {
        if (userId == null) return;
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setLink(link);
        notification.setCreatedAt(Instant.now());
        notifications.save(notification);
    }
}

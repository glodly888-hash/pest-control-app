package com.sws.pest.application.platform;

import com.sws.pest.domain.platform.Notification;
import com.sws.pest.domain.platform.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 员工通知用例：查询 + 标记已读。
 */
@Service
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    public NotificationRepository.Page list(UUID userId, String cursor, int limit) {
        return repository.listByUser(userId, cursor, limit);
    }

    /** 标记已读；通知不存在或不属于该员工时返回 false（上层映射 404）。 */
    public boolean markRead(UUID userId, UUID notificationId) {
        return repository.markRead(userId, notificationId);
    }

    /** 通知不存在（404）。 */
    public static class NotificationNotFound extends RuntimeException {
        public NotificationNotFound(String message) {
            super(message);
        }
    }
}

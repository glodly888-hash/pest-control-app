package com.sws.pest.domain.platform;

import java.util.List;
import java.util.UUID;

/**
 * 员工通知读/写端口（对应 notification 表）。
 */
public interface NotificationRepository {

    /** 游标分页查询某员工的通知，按 (createdAt,id) 倒序。 */
    Page listByUser(UUID userId, String cursor, int limit);

    /** 标记通知已读（幂等：已读再标不报错）。返回是否命中该员工的通知。 */
    boolean markRead(UUID userId, UUID notificationId);

    record Page(List<Notification> items, String nextCursor, boolean hasMore) {
    }
}

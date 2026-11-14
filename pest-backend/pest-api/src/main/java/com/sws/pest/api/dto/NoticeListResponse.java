package com.sws.pest.api.dto;

import java.util.List;

/**
 * 服务须知列表响应（对应 openapi listNotices 的 data）。
 */
public record NoticeListResponse(List<NoticeResponse> items, String nextCursor, boolean hasMore) {
}

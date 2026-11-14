package com.sws.pest.api.dto;

import java.util.List;

/**
 * 校验结果响应（对应 ValidationResult）。
 */
public record ValidationResultResponse(
        boolean canSign,
        long draftRevision,
        List<String> issues,
        String contentHash) {
}

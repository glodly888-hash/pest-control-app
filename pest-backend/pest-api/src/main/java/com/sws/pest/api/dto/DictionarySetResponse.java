package com.sws.pest.api.dto;

import java.util.List;

/**
 * 字典全集响应（对应 openapi DictionarySet）。
 */
public record DictionarySetResponse(String version, List<DictionaryItemResponse> items) {
}

package com.sws.pest.domain.platform;

import java.util.List;

/**
 * 字典全集（对应 openapi DictionarySet）：一个版本号 + 一组字典项。
 */
public record DictionarySet(String version, List<DictionaryItem> items) {
}

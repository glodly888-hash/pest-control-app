package com.sws.pest.domain.platform;

import java.util.List;

/**
 * 基础支撑读端口：应用配置、字典、服务须知。
 *
 * 三者均来自版本化配置表 config_release（kind=APP/DICTIONARY/NOTICE），
 * 读取「最新生效版本」。空数据时返回空结果，由上层决定默认值。
 */
public interface PlatformConfigRepository {

    /** 最新 APP 配置（未配置时返回 null）。 */
    AppConfig latestAppConfig();

    /** 最新字典全集（未配置时返回 null）。 */
    DictionarySet latestDictionary();

    /** 全部 NOTICE 公告，按 effectiveAt 倒序。 */
    List<Notice> listNotices();
}

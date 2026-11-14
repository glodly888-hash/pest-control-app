package com.sws.pest.application.platform;

import com.sws.pest.domain.platform.AppConfig;
import com.sws.pest.domain.platform.DictionarySet;
import com.sws.pest.domain.platform.Notice;
import com.sws.pest.domain.platform.PlatformConfigRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 基础支撑查询用例：应用配置、字典、服务须知。
 *
 * JSON 解析已由 infrastructure 层完成，这里只做编排与默认值兜底。
 */
@Service
public class PlatformConfigService {

    private final PlatformConfigRepository repository;

    public PlatformConfigService(PlatformConfigRepository repository) {
        this.repository = repository;
    }

    /** 客户端配置；未配置时返回内置默认值。 */
    public AppConfig getAppConfig() {
        AppConfig c = repository.latestAppConfig();
        return c != null ? c : AppConfig.defaults();
    }

    /** 字典全集；未配置时返回空集。 */
    public DictionarySet getDictionaries() {
        DictionarySet d = repository.latestDictionary();
        return d != null ? d : new DictionarySet("0", List.of());
    }

    /** 服务须知列表（全部，按 effectiveAt 倒序）。 */
    public List<Notice> listNotices() {
        return repository.listNotices();
    }
}

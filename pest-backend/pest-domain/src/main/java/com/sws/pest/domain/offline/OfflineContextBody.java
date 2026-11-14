package com.sws.pest.domain.offline;

import com.sws.pest.domain.ledger.Risk;
import com.sws.pest.domain.masterdata.Facility;
import com.sws.pest.domain.masterdata.Store;
import com.sws.pest.domain.platform.DictionaryItem;

import java.util.List;

/**
 * 离线上下文正文（contextBody）的结构化值对象。
 *
 * 由 application 层组装（只依赖 domain 类型，不依赖 Jackson），
 * 由 infrastructure 层序列化为 JSON 字符串后透传落库。
 * 对应 openapi OfflineContext.contextBody 的子字段：
 * store / facilities / openRisks / areas / dictionaries / notices 等。
 */
public final class OfflineContextBody {

    private final StoreRef store;
    private final List<Facility> facilities;
    private final List<Risk> openRisks;
    private final List<Store.Area> areas;
    private final List<DictionaryItem> dictionaries;

    public OfflineContextBody(StoreRef store,
                              List<Facility> facilities,
                              List<Risk> openRisks,
                              List<Store.Area> areas,
                              List<DictionaryItem> dictionaries) {
        this.store = store;
        this.facilities = facilities;
        this.openRisks = openRisks;
        this.areas = areas;
        this.dictionaries = dictionaries;
    }

    public StoreRef store() { return store; }
    public List<Facility> facilities() { return facilities; }
    public List<Risk> openRisks() { return openRisks; }
    public List<Store.Area> areas() { return areas; }
    public List<DictionaryItem> dictionaries() { return dictionaries; }

    /** 门店快照（仅 id / code / name，避免泄露无关字段）。 */
    public record StoreRef(String id, String code, String name) {
    }
}

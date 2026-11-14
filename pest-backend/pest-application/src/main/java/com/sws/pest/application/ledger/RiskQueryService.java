package com.sws.pest.application.ledger;

import com.sws.pest.domain.ledger.Risk;
import com.sws.pest.domain.ledger.RiskRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 风险台账查询用例。
 */
@Service
public class RiskQueryService {

    private final RiskRepository riskRepository;

    public RiskQueryService(RiskRepository riskRepository) {
        this.riskRepository = riskRepository;
    }

    public record RiskPage(List<Risk> items, String nextCursor, boolean hasMore) {}

    public RiskPage listRisks(UUID storeId, Risk.Status status, String cursor, int limit) {
        if (!riskRepository.storeExists(storeId)) {
            throw new RiskRepository.RiskNotFound("门店不存在");
        }
        List<Risk> all = riskRepository.listByStore(storeId, status, cursor, limit);
        boolean hasMore = all.size() > limit;
        if (hasMore) {
            all = all.subList(0, limit);
        }
        String nextCursor = null;
        if (hasMore && !all.isEmpty()) {
            Risk last = all.get(all.size() - 1);
            String due = last.dueDate() == null ? "" : last.dueDate();
            nextCursor = due + "|" + last.id();
        }
        return new RiskPage(all, nextCursor == null ? "" : nextCursor, hasMore);
    }

    public Risk getRisk(UUID riskId) {
        return riskRepository.findById(riskId)
                .orElseThrow(() -> new RiskRepository.RiskNotFound("风险不存在"));
    }
}

package com.sws.pest.application.ledger;

import com.sws.pest.domain.ledger.FieldCommitRepository;
import com.sws.pest.domain.workorder.TaskRepository;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 现场台账提交用例（commitFieldRecords）。
 *
 * 首期简化：输入仅含 draftRevision，无逐条台账事件，
 * 本用例完成「草稿版本 → 台账提交回执」的原子对齐与幂等。
 */
@Service
public class FieldCommitService {

    private final FieldCommitRepository fieldCommitRepository;

    public FieldCommitService(FieldCommitRepository fieldCommitRepository) {
        this.fieldCommitRepository = fieldCommitRepository;
    }

    public FieldCommitRepository.CommitReceipt commit(UUID serviceId, long draftRevision) {
        String requestHash = sha256(serviceId.toString() + ":" + draftRevision);
        return fieldCommitRepository.commit(serviceId, draftRevision, requestHash);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}

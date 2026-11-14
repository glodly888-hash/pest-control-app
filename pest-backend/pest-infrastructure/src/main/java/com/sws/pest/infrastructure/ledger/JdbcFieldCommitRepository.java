package com.sws.pest.infrastructure.ledger;

import com.sws.pest.domain.ledger.FieldCommitRepository;
import com.sws.pest.domain.workorder.TaskRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * field_commit 的 JDBC 实现。
 *
 * 首期简化：输入 FieldCommitInput 仅含 draftRevision，不含具体台账事件，
 * 因此 effects 返回空数组，仅完成「草稿版本 → 台账提交回执」的对齐。
 * 完整事件逐条应用（facility/risk/followup 变更）在离线同步与后续模块展开。
 */
@Repository
public class JdbcFieldCommitRepository implements FieldCommitRepository {

    private final JdbcTemplate jdbc;

    public JdbcFieldCommitRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public CommitReceipt commit(UUID serviceId, long draftRevision, String requestHash) {
        // 幂等：同 serviceId+draftRevision 已提交则返回原回执
        var existing = findExisting(serviceId, draftRevision);
        if (existing != null) {
            return existing;
        }

        // 校验草稿版本匹配
        Long current = jdbc.queryForObject(
                "SELECT revision FROM service_draft WHERE service_id = ?",
                Long.class, uuidBytes(serviceId));
        if (current == null) {
            throw new IllegalArgumentException("服务草稿不存在");
        }
        if (current.longValue() != draftRevision) {
            throw new TaskRepository.OptimisticLockException("草稿版本冲突，请刷新后重试");
        }

        Instant committedAt = Instant.now();
        UUID commitId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO field_commit (id, service_id, draft_revision, request_hash, receipt_json)
                VALUES (?, ?, ?, ?, '{"effects":[]}')
                """,
                uuidBytes(commitId),
                uuidBytes(serviceId),
                draftRevision,
                hexToBytes(requestHash));

        // 对齐 field_commit_revision
        jdbc.update("""
                UPDATE service_draft SET field_commit_revision = ?
                WHERE service_id = ?
                """, draftRevision, uuidBytes(serviceId));

        return new CommitReceipt(serviceId, draftRevision, committedAt, List.of());
    }

    private CommitReceipt findExisting(UUID serviceId, long draftRevision) {
        var rows = jdbc.query("""
                SELECT created_at FROM field_commit
                WHERE service_id = ? AND draft_revision = ?
                """, (rs, i) -> rs.getTimestamp("created_at").toInstant(),
                uuidBytes(serviceId), draftRevision);
        if (rows.isEmpty()) {
            return null;
        }
        return new CommitReceipt(serviceId, draftRevision, rows.get(0), List.of());
    }

    static byte[] uuidBytes(UUID uuid) {
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }

    private static byte[] hexToBytes(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}

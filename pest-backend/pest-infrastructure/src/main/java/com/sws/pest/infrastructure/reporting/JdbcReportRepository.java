package com.sws.pest.infrastructure.reporting;

import com.sws.pest.domain.reporting.Report;
import com.sws.pest.domain.reporting.ReportRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * service_report 的 JDBC 实现。
 *
 * 首期简化：
 * - 报告归属员工：按 service.actor_id 过滤（历史报告查询 x-roles 首期不做角色细分）；
 * - openRiskCountAtService：从 snapshot_finding 统计（无则 0）；
 * - templateVersion：占位 'v1'。
 */
@Repository
public class JdbcReportRepository implements ReportRepository {

    private final JdbcTemplate jdbc;
    private final Path reportRoot;

    public JdbcReportRepository(JdbcTemplate jdbc,
                                @Value("${pest.media.report-root:/tmp/pest-media}") String reportRoot) {
        this.jdbc = jdbc;
        this.reportRoot = Paths.get(reportRoot).toAbsolutePath().normalize();
    }

    @Override
    public ReportPage list(UUID actorId, String cursor, int limit, UUID storeId, UUID serviceId, String status) {
        StringBuilder where = new StringBuilder(" WHERE s.actor_id = ? ");
        List<Object> params = new ArrayList<>();
        params.add(uuidBytes(actorId));

        if (storeId != null) {
            where.append(" AND s.store_id = ? ");
            params.add(uuidBytes(storeId));
        }
        if (serviceId != null) {
            where.append(" AND r.service_id = ? ");
            params.add(uuidBytes(serviceId));
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND r.status = ? ");
            params.add(status);
        }

        // 游标：published_at 倒序，稳定排序用 (published_at, id)
        if (cursor != null && !cursor.isBlank()) {
            String[] parts = decodeCursor(cursor);
            if (parts.length == 2) {
                where.append(" AND (r.published_at < ? OR (r.published_at = ? AND r.id < ?)) ");
                params.add(java.sql.Timestamp.valueOf(parts[0].replace('T', ' ')));
                params.add(java.sql.Timestamp.valueOf(parts[0].replace('T', ' ')));
                params.add(uuidBytes(UUID.fromString(parts[1])));
            }
        }

        String sql = """
                SELECT r.id, r.service_id, s.task_id, s.store_id, r.report_number, r.version_no,
                       r.status, r.snapshot_id, r.content_hash, r.pdf_sha256, r.published_at,
                       r.service_started_at, r.service_ended_at, r.supersedes_report_id,
                       r.object_key, r.byte_size
                FROM service_report r
                JOIN service s ON s.id = r.service_id
                """
                + where
                + " ORDER BY r.published_at DESC, r.id DESC LIMIT " + (limit + 1);

        List<Report> items = jdbc.query(sql, rs -> {
            List<Report> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapReport(rs, false));
            }
            return list;
        }, params.toArray());

        boolean hasMore = items.size() > limit;
        if (hasMore) {
            items = items.subList(0, limit);
        }
        String nextCursor = "";
        if (hasMore && !items.isEmpty()) {
            Report last = items.get(items.size() - 1);
            nextCursor = encodeCursor(last.publishedAt(), last.id());
        }
        return new ReportPage(items, hasMore, nextCursor);
    }

    @Override
    public Optional<Report> findById(UUID reportId) {
        String sql = """
                SELECT r.id, r.service_id, s.task_id, s.store_id, r.report_number, r.version_no,
                       r.status, r.snapshot_id, r.content_hash, r.pdf_sha256, r.published_at,
                       r.service_started_at, r.service_ended_at, r.supersedes_report_id,
                       r.object_key, r.byte_size
                FROM service_report r
                JOIN service s ON s.id = r.service_id
                WHERE r.id = ?
                """;
        return jdbc.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.<Report>empty();
            }
            return Optional.of(mapReport(rs, true));
        }, uuidBytes(reportId));
    }

    @Override
    public byte[] readPdf(UUID reportId) {
        Report report = findById(reportId)
                .orElseThrow(() -> new IllegalStateException("报告不存在"));
        if (!report.downloadable()) {
            throw new IllegalStateException("报告尚未发布，不能下载");
        }
        Path file = reportRoot.resolve(report.objectKey());
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new IllegalStateException("报告 PDF 读取失败: " + e.getMessage(), e);
        }
    }

    private Report mapReport(ResultSet rs, boolean withSignatures) throws SQLException {
        UUID id = bytesToUuid(rs.getBytes("id"));
        UUID snapshotId = bytesToUuid(rs.getBytes("snapshot_id"));

        List<Report.ReportSignature> signatures = withSignatures ? loadSignatures(snapshotId) : List.of();

        return new Report(
                id,
                bytesToUuid(rs.getBytes("service_id")),
                bytesToUuid(rs.getBytes("task_id")),
                bytesToUuid(rs.getBytes("store_id")),
                rs.getString("report_number"),
                rs.getInt("version_no"),
                Report.Status.valueOf(rs.getString("status")),
                snapshotId,
                hex(rs.getBytes("content_hash")),
                hex(rs.getBytes("pdf_sha256")),
                rs.getTimestamp("published_at").toInstant(),
                rs.getTimestamp("service_started_at").toInstant(),
                rs.getTimestamp("service_ended_at").toInstant(),
                signatures,
                rs.getBytes("supersedes_report_id") != null ? bytesToUuid(rs.getBytes("supersedes_report_id")) : null,
                countOpenRisks(snapshotId),
                "v1",
                rs.getString("object_key"),
                rs.getLong("byte_size"));
    }

    private List<Report.ReportSignature> loadSignatures(UUID snapshotId) {
        String sql = """
                SELECT role, signer_name, signed_at, rating, comment
                FROM service_signature
                WHERE snapshot_id = ? AND invalidated_at IS NULL
                ORDER BY (role = 'STAFF') DESC, signed_at
                """;
        return jdbc.query(sql, rs -> {
            List<Report.ReportSignature> list = new ArrayList<>();
            while (rs.next()) {
                list.add(new Report.ReportSignature(
                        rs.getString("role"),
                        rs.getString("signer_name"),
                        rs.getTimestamp("signed_at").toInstant(),
                        rs.getObject("rating") != null ? rs.getInt("rating") : null,
                        rs.getString("comment")));
            }
            return list;
        }, uuidBytes(snapshotId));
    }

    private int countOpenRisks(UUID snapshotId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM snapshot_finding WHERE snapshot_id = ?",
                Integer.class, uuidBytes(snapshotId));
        return count != null ? count : 0;
    }

    private static String encodeCursor(Instant publishedAt, UUID id) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((publishedAt.toString() + "|" + id).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String[] decodeCursor(String cursor) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor),
                    java.nio.charset.StandardCharsets.UTF_8);
            return decoded.split("\\|");
        } catch (Exception e) {
            return new String[0];
        }
    }

    static byte[] uuidBytes(UUID uuid) {
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }

    static UUID bytesToUuid(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        return new UUID(bb.getLong(), bb.getLong());
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

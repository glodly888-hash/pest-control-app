package com.sws.pest.infrastructure.platform;

import com.sws.pest.domain.platform.AppConfig;
import com.sws.pest.domain.platform.DictionaryItem;
import com.sws.pest.domain.platform.DictionarySet;
import com.sws.pest.domain.platform.Notice;
import com.sws.pest.domain.platform.PlatformConfigRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * config_release 表的 JDBC 实现。
 *
 * APP / DICTIONARY 各取「最新生效版本」（version_no 最大），
 * NOTICE 取全部公告按 effectiveAt 倒序（公告本身按版本递增，全部展示）。
 * JSON 解析统一在本层完成，容错返回空字段/默认值。
 */
@Repository
public class JdbcPlatformConfigRepository implements PlatformConfigRepository {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbc;

    public JdbcPlatformConfigRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AppConfig latestAppConfig() {
        List<String> rows = jdbc.query(
                "SELECT payload_json FROM config_release WHERE kind = 'APP' ORDER BY version_no DESC LIMIT 1",
                (rs, i) -> rs.getString(1));
        if (rows.isEmpty()) {
            return null;
        }
        String payload = rows.get(0);
        if (payload == null || payload.isBlank() || "{}".equals(payload.trim())) {
            return null;
        }
        try {
            JsonNode n = MAPPER.readTree(payload);
            if (n == null || n.isEmpty()) {
                return null;
            }
            AppConfig d = AppConfig.defaults();
            return new AppConfig(
                    text(n, "configVersion", d.configVersion()),
                    integer(n, "recommendedMinutes", d.recommendedMinutes()),
                    integer(n, "previewMinSeconds", d.previewMinSeconds()),
                    integer(n, "maxImageBytes", d.maxImageBytes()),
                    integer(n, "maxDocumentBytes", d.maxDocumentBytes()),
                    integer(n, "maxSubmissionBytes", d.maxSubmissionBytes()),
                    integer(n, "uploadSessionTtlSeconds", d.uploadSessionTtlSeconds()),
                    integer(n, "offlineGrantHours", d.offlineGrantHours()),
                    integer(n, "maxOfflineTasks", d.maxOfflineTasks()),
                    text(n, "supportContact", d.supportContact()),
                    text(n, "privacyVersion", d.privacyVersion()),
                    text(n, "termsVersion", d.termsVersion()),
                    text(n, "templateVersion", d.templateVersion()));
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public DictionarySet latestDictionary() {
        List<DictionarySet> rows = jdbc.query(
                "SELECT version_no, payload_json FROM config_release WHERE kind = 'DICTIONARY' ORDER BY version_no DESC LIMIT 1",
                (rs, i) -> {
                    String version = String.valueOf(rs.getInt(1));
                    String payload = rs.getString(2);
                    return new DictionarySet(version, parseDictionary(payload));
                });
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public List<Notice> listNotices() {
        return jdbc.query("""
                SELECT id, version_no, payload_json, published_at
                FROM config_release
                WHERE kind = 'NOTICE'
                ORDER BY published_at DESC, id
                """, (rs, rowNum) -> mapNotice(rs));
    }

    private List<DictionaryItem> parseDictionary(String payload) {
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        List<DictionaryItem> items = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(payload);
            JsonNode arr = (root != null && root.isArray()) ? root : (root != null ? root.get("items") : null);
            if (arr != null && arr.isArray()) {
                for (JsonNode n : arr) {
                    items.add(new DictionaryItem(
                            n.has("id") && !n.get("id").isNull() ? UUID.fromString(n.get("id").asText()) : null,
                            n.has("kind") ? n.get("kind").asText() : "",
                            n.has("code") ? n.get("code").asText() : "",
                            n.has("name") ? n.get("name").asText() : "",
                            n.has("isActive") && n.get("isActive").asBoolean(),
                            n.has("revision") ? n.get("revision").asInt(0) : 0,
                            n.has("formulation") && !n.get("formulation").isNull() ? n.get("formulation").asText() : null));
                }
            }
        } catch (Exception ignored) {
            // 容错返回空
        }
        return items;
    }

    private Notice mapNotice(ResultSet rs) throws SQLException {
        UUID id = bytesToUuid(rs.getBytes("id"));
        String version = String.valueOf(rs.getInt("version_no"));
        String payload = rs.getString("payload_json");
        String title = "";
        String content = "";
        try {
            JsonNode node = MAPPER.readTree(payload);
            if (node != null) {
                title = node.has("title") ? node.get("title").asText("") : "";
                content = node.has("content") ? node.get("content").asText("") : "";
            }
        } catch (Exception ignored) {
            // 容错
        }
        Instant effectiveAt = rs.getTimestamp("published_at") == null
                ? Instant.EPOCH
                : rs.getTimestamp("published_at").toInstant();
        return new Notice(id, version, title, content, effectiveAt);
    }

    private static String text(JsonNode node, String field, String def) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText(def) : def;
    }

    private static int integer(JsonNode node, String field, int def) {
        return node.has(field) && node.get(field).isNumber() ? node.get(field).asInt(def) : def;
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
}

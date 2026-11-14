package com.sws.pest.infrastructure.ledger;

import com.sws.pest.domain.ledger.RiskFollowup;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * risk.content_json / risk_followup.content_json 的轻量解析（首期简化）。
 *
 * risk.content_json 结构约定：
 * {
 *   "description": "...", "suggestion": "...", "floor": "...",
 *   "pestTypeIds": ["uuid", ...],
 *   "photos": [{ "attachmentId": "uuid", ... }, ...]
 * }
 *
 * risk_followup.content_json 结构约定：
 * {
 *   "description": "...", "notCheckedReason": "...", "materialSource": "...",
 *   "materialProvidedBy": "...", "photos": [...]
 * }
 *
 * 解析失败时返回空字段，不抛异常（台账读取应容错）。
 */
final class JsonContent {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String description;
    private final String suggestion;
    private final String floor;
    private final String notCheckedReason;
    private final RiskFollowup.MaterialSource materialSource;
    private final String materialProvidedBy;
    private final List<String> pestTypeIds;
    private final List<String> photos;

    private JsonContent(String description, String suggestion, String floor,
                        String notCheckedReason, RiskFollowup.MaterialSource materialSource,
                        String materialProvidedBy, List<String> pestTypeIds, List<String> photos) {
        this.description = description;
        this.suggestion = suggestion;
        this.floor = floor;
        this.notCheckedReason = notCheckedReason;
        this.materialSource = materialSource;
        this.materialProvidedBy = materialProvidedBy;
        this.pestTypeIds = pestTypeIds;
        this.photos = photos;
    }

    String description() { return description; }
    String suggestion() { return suggestion; }
    String floor() { return floor; }
    String notCheckedReason() { return notCheckedReason; }
    RiskFollowup.MaterialSource materialSource() { return materialSource; }
    String materialProvidedBy() { return materialProvidedBy; }
    List<String> pestTypeIds() { return pestTypeIds; }
    List<String> photos() { return photos; }

    static JsonContent parse(String json) {
        if (json == null || json.isBlank()) {
            return new JsonContent(null, null, null, null, null, null, List.of(), List.of());
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            String description = text(root, "description");
            String suggestion = text(root, "suggestion");
            String floor = text(root, "floor");
            String notCheckedReason = text(root, "notCheckedReason");
            String materialProvidedBy = text(root, "materialProvidedBy");

            RiskFollowup.MaterialSource materialSource = null;
            String ms = text(root, "materialSource");
            if (ms != null && !ms.isBlank()) {
                try {
                    materialSource = RiskFollowup.MaterialSource.valueOf(ms);
                } catch (IllegalArgumentException ignored) {
                }
            }

            List<String> pests = new ArrayList<>();
            JsonNode pestNode = root.get("pestTypeIds");
            if (pestNode != null && pestNode.isArray()) {
                for (JsonNode n : pestNode) {
                    if (n.isTextual()) pests.add(n.asText());
                }
            }

            List<String> photos = new ArrayList<>();
            JsonNode photoNode = root.get("photos");
            if (photoNode != null && photoNode.isArray()) {
                for (JsonNode n : photoNode) {
                    JsonNode aid = n.get("attachmentId");
                    if (aid != null && aid.isTextual()) photos.add(aid.asText());
                }
            }
            return new JsonContent(description, suggestion, floor, notCheckedReason,
                    materialSource, materialProvidedBy, pests, photos);
        } catch (Exception e) {
            return new JsonContent(null, null, null, null, null, null, List.of(), List.of());
        }
    }

    private static String text(JsonNode root, String field) {
        JsonNode n = root.get(field);
        return (n != null && n.isTextual()) ? n.asText() : null;
    }
}

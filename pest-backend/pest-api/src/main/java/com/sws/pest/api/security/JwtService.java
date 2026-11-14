package com.sws.pest.api.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * 极简 JWT 签发与校验（HS256）。零第三方依赖，用 JDK 自带 HMAC。
 * access token 短时效，内容含 userId / permissionVersion / sessionId。
 * 后续若需标准库，可无缝替换为 jjwt，不影响调用方。
 */
public final class JwtService {

    private final byte[] secret;

    public JwtService(String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 签发 access token。
     */
    public String issue(UUID userId, UUID sessionId, long permissionVersion, Instant expiresAt) {
        String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = b64(String.format(
                "{\"sub\":\"%s\",\"sid\":\"%s\",\"pv\":%d,\"exp\":%d}",
                userId, sessionId, permissionVersion, expiresAt.getEpochSecond()));
        String signingInput = header + "." + payload;
        String signature = b64(hmac(signingInput));
        return signingInput + "." + signature;
    }

    /**
     * 校验并解析。签名无效或过期抛 TokenException。
     */
    public Claims verify(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new TokenException("malformed token");
        }
        String signingInput = parts[0] + "." + parts[1];
        String expected = b64(hmac(signingInput));
        if (!constantTimeEquals(expected, parts[2])) {
            throw new TokenException("invalid signature");
        }
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        return parseClaims(payloadJson);
    }

    private Claims parseClaims(String json) {
        // 极简 JSON 提取（字段固定，避免引入 JSON 库到安全层）
        UUID userId = UUID.fromString(extract(json, "sub"));
        UUID sessionId = UUID.fromString(extract(json, "sid"));
        long pv = Long.parseLong(extract(json, "pv"));
        long exp = Long.parseLong(extract(json, "exp"));
        if (Instant.now().getEpochSecond() > exp) {
            throw new TokenException("token expired");
        }
        return new Claims(userId, sessionId, pv);
    }

    private static String extract(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) {
            throw new TokenException("missing claim: " + key);
        }
        int start = json.indexOf(':', i) + 1;
        int end = json.indexOf(',', start);
        if (end < 0) {
            end = json.indexOf('}', start);
        }
        return json.substring(start, end).trim().replace("\"", "");
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /** JWT 声明的解析结果。 */
    public record Claims(UUID userId, UUID sessionId, long permissionVersion) {
    }

    /** Token 校验失败。 */
    public static class TokenException extends RuntimeException {
        public TokenException(String message) {
            super(message);
        }
    }
}

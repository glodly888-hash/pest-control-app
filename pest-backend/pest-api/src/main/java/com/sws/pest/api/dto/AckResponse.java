package com.sws.pest.api.dto;

/**
 * 上传字节确认（对应 openapi Ack）。
 */
public record AckResponse(boolean isAccepted, String receivedAt) {
}

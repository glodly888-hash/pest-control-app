package com.sws.pest.api.controller;

import com.sws.pest.api.UnauthorizedException;
import com.sws.pest.api.dto.ApiResponse;
import com.sws.pest.api.dto.FieldCommitInput;
import com.sws.pest.api.dto.FieldCommitReceiptResponse;
import com.sws.pest.api.security.JwtAuthFilter;
import com.sws.pest.application.ledger.FieldCommitService;
import com.sws.pest.domain.ledger.FieldCommitRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 现场台账提交接口（对应 openapi commitFieldRecords）。
 *
 * POST /services/{serviceId}/field-commits —— 提交现场台账变更。
 */
@RestController
@RequestMapping("/services/{serviceId}/field-commits")
public class FieldCommitController {

    private final FieldCommitService fieldCommitService;

    public FieldCommitController(FieldCommitService fieldCommitService) {
        this.fieldCommitService = fieldCommitService;
    }

    @PostMapping
    public ApiResponse<FieldCommitReceiptResponse> commit(
            HttpServletRequest request,
            @PathVariable UUID serviceId,
            @RequestBody FieldCommitInput input) {
        requireLogin(request);

        FieldCommitRepository.CommitReceipt receipt =
                fieldCommitService.commit(serviceId, input.draftRevision());

        List<FieldCommitReceiptResponse.EffectEcho> effects = receipt.effects().stream()
                .map(e -> new FieldCommitReceiptResponse.EffectEcho(
                        e.eventId().toString(),
                        e.resourceId().toString(),
                        e.revision(),
                        e.effect()))
                .toList();

        return ApiResponse.of(new FieldCommitReceiptResponse(
                receipt.serviceId().toString(),
                receipt.draftRevision(),
                receipt.committedAt().toString(),
                effects));
    }

    private static void requireLogin(HttpServletRequest request) {
        if (JwtAuthFilter.currentUserId(request) == null) {
            throw new UnauthorizedException("未登录或 token 无效");
        }
    }
}

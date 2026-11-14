package com.sws.pest.api;

import com.sws.pest.api.dto.ErrorResponse;
import com.sws.pest.application.confirmation.SignatureService;
import com.sws.pest.application.identity.AuthenticateService;
import com.sws.pest.application.ledger.FollowupService;
import com.sws.pest.application.reporting.PublicationService;
import com.sws.pest.application.workorder.ServiceDraftService;
import com.sws.pest.domain.confirmation.ConfirmationExceptionRepository;
import com.sws.pest.domain.confirmation.PreviewSessionRepository;
import com.sws.pest.domain.ledger.FollowupRepository;
import com.sws.pest.domain.ledger.RiskRepository;
import com.sws.pest.domain.media.AttachmentRepository;
import com.sws.pest.domain.offline.OfflineRepository;
import com.sws.pest.domain.workorder.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

/**
 * 全局异常处理：统一错误响应格式，不泄露内部细节。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AuthenticateService.AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuth(AuthenticateService.AuthenticationException e) {
        // 登录失败统一 401，不暴露账号是否存在
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "账号或密码错误");
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException e) {
        // 未登录/令牌无效：401，并提示客户端令牌已失效
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(TaskRepository.OptimisticLockException.class)
    public ResponseEntity<ErrorResponse> handleConflict(TaskRepository.OptimisticLockException e) {
        return build(HttpStatus.PRECONDITION_FAILED, "PRECONDITION_FAILED", e.getMessage());
    }

    @ExceptionHandler(ServiceDraftService.ServiceNotFound.class)
    public ResponseEntity<ErrorResponse> handleServiceNotFound(ServiceDraftService.ServiceNotFound e) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(ServiceDraftService.ServiceForbidden.class)
    public ResponseEntity<ErrorResponse> handleServiceForbidden(ServiceDraftService.ServiceForbidden e) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", e.getMessage());
    }

    @ExceptionHandler(ServiceDraftService.ServiceConflict.class)
    public ResponseEntity<ErrorResponse> handleServiceConflict(ServiceDraftService.ServiceConflict e) {
        return build(HttpStatus.CONFLICT, "CONFLICT", e.getMessage());
    }

    @ExceptionHandler(SignatureService.SignatureNotFound.class)
    public ResponseEntity<ErrorResponse> handleSignatureNotFound(SignatureService.SignatureNotFound e) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(SignatureService.SignatureForbidden.class)
    public ResponseEntity<ErrorResponse> handleSignatureForbidden(SignatureService.SignatureForbidden e) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", e.getMessage());
    }

    @ExceptionHandler(SignatureService.SignatureConflict.class)
    public ResponseEntity<ErrorResponse> handleSignatureConflict(SignatureService.SignatureConflict e) {
        return build(HttpStatus.CONFLICT, "CONFLICT", e.getMessage());
    }

    @ExceptionHandler(PublicationService.PublicationNotFound.class)
    public ResponseEntity<ErrorResponse> handlePublicationNotFound(PublicationService.PublicationNotFound e) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(PublicationService.PublicationConflict.class)
    public ResponseEntity<ErrorResponse> handlePublicationConflict(PublicationService.PublicationConflict e) {
        return build(HttpStatus.CONFLICT, "CONFLICT", e.getMessage());
    }

    @ExceptionHandler(AttachmentRepository.AttachmentNotFound.class)
    public ResponseEntity<ErrorResponse> handleAttachmentNotFound(AttachmentRepository.AttachmentNotFound e) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(AttachmentRepository.UploadSessionNotFound.class)
    public ResponseEntity<ErrorResponse> handleUploadSessionNotFound(AttachmentRepository.UploadSessionNotFound e) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(PreviewSessionRepository.SnapshotNotFound.class)
    public ResponseEntity<ErrorResponse> handlePreviewSnapshotNotFound(PreviewSessionRepository.SnapshotNotFound e) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(ConfirmationExceptionRepository.ServiceConflict.class)
    public ResponseEntity<ErrorResponse> handleConfirmationConflict(ConfirmationExceptionRepository.ServiceConflict e) {
        return build(HttpStatus.CONFLICT, "CONFLICT", e.getMessage());
    }

    @ExceptionHandler(RiskRepository.RiskNotFound.class)
    public ResponseEntity<ErrorResponse> handleRiskNotFound(RiskRepository.RiskNotFound e) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(FollowupService.FollowupConflict.class)
    public ResponseEntity<ErrorResponse> handleFollowupConflict(FollowupService.FollowupConflict e) {
        return build(HttpStatus.CONFLICT, "CONFLICT", e.getMessage());
    }

    @ExceptionHandler(FollowupRepository.OptimisticLockException.class)
    public ResponseEntity<ErrorResponse> handleFollowupOptimisticLock(FollowupRepository.OptimisticLockException e) {
        return build(HttpStatus.CONFLICT, "CONFLICT", e.getMessage());
    }

    @ExceptionHandler(OfflineRepository.Conflict.class)
    public ResponseEntity<ErrorResponse> handleOfflineConflict(OfflineRepository.Conflict e) {
        return build(HttpStatus.CONFLICT, "CONFLICT", e.getMessage());
    }

    @ExceptionHandler(OfflineRepository.NotFound.class)
    public ResponseEntity<ErrorResponse> handleOfflineNotFound(OfflineRepository.NotFound e) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e) {
        // 状态机非法流转（如冻结后修改草稿、重复冻结）→ 409
        return build(HttpStatus.CONFLICT, "CONFLICT", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegal(IllegalArgumentException e) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求参数校验失败");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception e) {
        // 兜底：记录完整堆栈便于排查，但不把细节返回给客户端
        log.error("未捕获异常", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务内部错误");
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(code, message, UUID.randomUUID().toString()));
    }
}

package dev.beatward.monitor.api;

import dev.beatward.auth.AccountDisabledException;
import dev.beatward.auth.AuthValidationException;
import dev.beatward.auth.InvalidCredentialsException;
import dev.beatward.auth.InviteCodeException;
import dev.beatward.auth.ResetTokenInvalidException;
import dev.beatward.auth.UnauthenticatedException;
import dev.beatward.auth.UserNotFoundException;
import dev.beatward.auth.UsernameTakenException;
import dev.beatward.auth.InvalidCurrentPasswordException;
import dev.beatward.auth.InvalidNewPasswordException;
import dev.beatward.auth.LoginRateLimitedException;
import dev.beatward.common.ApiErrorResponse;
import dev.beatward.common.InvalidQueryException;
import dev.beatward.common.TraceIdFilter;
import dev.beatward.heartbeat.HeartbeatNotFoundException;
import dev.beatward.heartbeat.HeartbeatPayloadTooLargeException;
import dev.beatward.heartbeat.HeartbeatRateLimitedException;
import dev.beatward.heartbeat.HttpsRequiredException;
import dev.beatward.heartbeat.NotHeartbeatMonitorException;
import dev.beatward.incident.IncidentNotFoundException;
import dev.beatward.monitor.MonitorNotFoundException;
import dev.beatward.monitor.QuotaExceededException;
import dev.beatward.monitor.config.InvalidMonitorConfigException;
import dev.beatward.monitor.target.BlockedTargetException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MonitorNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleMonitorNotFound(MonitorNotFoundException ex,
                                                                  HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "MONITOR_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(IncidentNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleIncidentNotFound(IncidentNotFoundException ex,
                                                                   HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "INCIDENT_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleQuotaExceeded(QuotaExceededException ex,
                                                                HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "QUOTA_EXCEEDED", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidMonitorConfigException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidConfig(InvalidMonitorConfigException ex,
                                                                HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ex.code(), ex.getMessage(), request);
    }

    @ExceptionHandler(BlockedTargetException.class)
    public ResponseEntity<ApiErrorResponse> handleBlockedTarget(BlockedTargetException ex,
                                                                HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_TARGET_URL", ex.getMessage(), request);
    }

    @ExceptionHandler(HeartbeatNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleHeartbeatNotFound(HeartbeatNotFoundException ex,
                                                                    HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "HEARTBEAT_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(NotHeartbeatMonitorException.class)
    public ResponseEntity<ApiErrorResponse> handleNotHeartbeatMonitor(NotHeartbeatMonitorException ex,
                                                                      HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "NOT_HEARTBEAT_MONITOR", ex.getMessage(), request);
    }

    @ExceptionHandler(HeartbeatRateLimitedException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimited(HeartbeatRateLimitedException ex,
                                                              HttpServletRequest request) {
        return rateLimited(ex.getRetryAfterSeconds(), ex.getMessage(), request);
    }

    @ExceptionHandler(HeartbeatPayloadTooLargeException.class)
    public ResponseEntity<ApiErrorResponse> handlePayloadTooLarge(HeartbeatPayloadTooLargeException ex,
                                                                  HttpServletRequest request) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", ex.getMessage(), request);
    }

    @ExceptionHandler(HttpsRequiredException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpsRequired(HttpsRequiredException ex,
                                                                HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "HTTPS_REQUIRED", ex.getMessage(), request);
    }

    @ExceptionHandler(InviteCodeException.class)
    public ResponseEntity<ApiErrorResponse> handleInviteCode(InviteCodeException ex,
                                                             HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ex.code(), ex.getMessage(), request);
    }

    @ExceptionHandler(UsernameTakenException.class)
    public ResponseEntity<ApiErrorResponse> handleUsernameTaken(UsernameTakenException ex,
                                                                HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "USERNAME_TAKEN", ex.getMessage(), request);
    }

    @ExceptionHandler(ResetTokenInvalidException.class)
    public ResponseEntity<ApiErrorResponse> handleResetTokenInvalid(ResetTokenInvalidException ex,
                                                                    HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "RESET_TOKEN_INVALID", ex.getMessage(), request);
    }

    @ExceptionHandler(AuthValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthValidation(AuthValidationException ex,
                                                                 HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage(), request);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleUserNotFound(UserNotFoundException ex,
                                                               HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(AccountDisabledException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountDisabled(AccountDisabledException ex,
                                                                  HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", ex.getMessage(), request);
    }

    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthenticated(UnauthenticatedException ex,
                                                                  HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex,
                                                                     HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidCurrentPasswordException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCurrentPassword(InvalidCurrentPasswordException ex,
                                                                         HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_CURRENT_PASSWORD", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidNewPasswordException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidNewPassword(InvalidNewPasswordException ex,
                                                                     HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage(), request);
    }

    @ExceptionHandler(LoginRateLimitedException.class)
    public ResponseEntity<ApiErrorResponse> handleLoginRateLimited(LoginRateLimitedException ex,
                                                                  HttpServletRequest request) {
        return rateLimited(ex.getRetryAfterSeconds(), ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidQueryException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidQuery(InvalidQueryException ex,
                                                               HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                                 HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "request body is invalid", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                               HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                ex.getName() + " has an invalid value", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                     HttpServletRequest request) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "HTTP method is not supported for this resource", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
                                                                        HttpServletRequest request) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "content type is not supported", request);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex,
                                                                         HttpServletRequest request) {
        return error(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE",
                "requested response type is not supported", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException ex,
                                                             HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "resource was not found", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error while handling request, traceId={}",
                TraceIdFilter.currentTraceId(request), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "unexpected error", request);
    }

    private static ResponseEntity<ApiErrorResponse> rateLimited(long retryAfterSeconds, String message,
                                                                HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(retryAfterSeconds))
                .body(new ApiErrorResponse("RATE_LIMITED", message,
                        TraceIdFilter.currentTraceId(request)));
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message,
                                                          HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(code, message, TraceIdFilter.currentTraceId(request)));
    }
}

package dev.beatward.common;

public record ApiErrorResponse(String code, String message, String traceId) {
}

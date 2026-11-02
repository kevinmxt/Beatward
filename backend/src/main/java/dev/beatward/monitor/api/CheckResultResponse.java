package dev.beatward.monitor.api;

import dev.beatward.monitor.probe.CheckResultEntity;

import java.time.Instant;

public record CheckResultResponse(
        Long id,
        Instant startedAt,
        Instant finishedAt,
        long durationMs,
        boolean success,
        String failureType,
        Integer httpStatusCode,
        String errorSummary) {

    public static CheckResultResponse from(CheckResultEntity result) {
        return new CheckResultResponse(
                result.getId(),
                result.getStartedAt(),
                result.getFinishedAt(),
                result.getDurationMs(),
                result.isSuccess(),
                result.getFailureType() == null ? null : result.getFailureType().name(),
                result.getHttpStatusCode(),
                result.getErrorSummary());
    }
}

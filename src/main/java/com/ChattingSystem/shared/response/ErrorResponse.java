package com.ChattingSystem.shared.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ErrorResponse{

    private final int statusCode;
    private final String message;
    private final String path;
    private final LocalDateTime timestamp;
    private final List<FieldErrorResponse> errors;

    public ErrorResponse(
            int statusCode,
            String message,
            String path,
            LocalDateTime timestamp,
            List<FieldErrorResponse> errors
    ) {
        this.statusCode = statusCode;
        this.message = message;
        this.path = path;
        this.timestamp = timestamp;
        this.errors = errors;
    }
}

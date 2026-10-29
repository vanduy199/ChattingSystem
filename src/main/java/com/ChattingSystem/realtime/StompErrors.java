package com.ChattingSystem.realtime;

import com.ChattingSystem.shared.exeption.ErrorCode;
import com.ChattingSystem.shared.response.ErrorResponse;
import com.ChattingSystem.shared.response.FieldErrorResponse;

import java.time.LocalDateTime;
import java.util.List;

/** {@code ErrorResponse} giống REST, gửi về {@code /user/queue/errors}; {@code path} là destination STOMP. */
public final class StompErrors {

    private StompErrors() {
    }

    public static ErrorResponse of(ErrorCode code, String destination) {
        return of(code, code.getDefaultMessage(), destination, List.of());
    }

    public static ErrorResponse of(ErrorCode code, String message, String destination,
                                   List<FieldErrorResponse> errors) {
        return new ErrorResponse(
                code.getHttpStatus().value(),
                message,
                destination,
                LocalDateTime.now(),
                errors);
    }
}

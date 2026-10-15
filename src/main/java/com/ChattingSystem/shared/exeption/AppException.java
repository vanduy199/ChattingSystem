package com.ChattingSystem.shared.exeption;

import com.ChattingSystem.shared.response.FieldErrorResponse;
import lombok.Getter;

import java.util.List;

@Getter
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;
    /** Field errors for validation failures detected in the service layer (rendered as {@code errors[]}). */
    private final List<FieldErrorResponse> errors;

    public AppException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    public AppException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.errors = List.of();
    }

    public AppException(ErrorCode errorCode, List<FieldErrorResponse> errors) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.errors = List.copyOf(errors);
    }

    /** 400 {@code VALIDATION_ERROR} with a single field error. */
    public static AppException fieldError(String field, String message) {
        return new AppException(ErrorCode.VALIDATION_ERROR, List.of(new FieldErrorResponse(field, message)));
    }
}

package com.ChattingSystem.shared.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final int statusCode;
    private final String message;
    private final T data;

    private ApiResponse(
            int statusCode,
            String message,
            T data
    ) {
        this.statusCode = statusCode;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(
            String message,
            T data
    ) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                message,
                data
        );
    }

    public static ApiResponse<Void> success(
            String message
    ) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                message,
                null
        );
    }
}

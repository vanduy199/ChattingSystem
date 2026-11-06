package com.ChattingSystem.realtime;

import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;
import com.ChattingSystem.shared.response.ErrorResponse;
import com.ChattingSystem.shared.response.FieldErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.util.List;

/** Lỗi của các {@code @MessageMapping}: trả {@code ErrorResponse} về {@code /user/queue/errors} của đúng phiên gửi. */
@Slf4j
@ControllerAdvice
public class StompErrorHandler {

    private static final String ERRORS = "/queue/errors";

    @MessageExceptionHandler(AppException.class)
    @SendToUser(destinations = ERRORS, broadcast = false)
    public ErrorResponse handleAppException(AppException exception, SimpMessageHeaderAccessor headers) {
        return StompErrors.of(exception.getErrorCode(), exception.getMessage(),
                headers.getDestination(), exception.getErrors());
    }

    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser(destinations = ERRORS, broadcast = false)
    public ErrorResponse handleValidation(MethodArgumentNotValidException exception,
                                          SimpMessageHeaderAccessor headers) {
        List<FieldErrorResponse> errors = exception.getBindingResult() == null
                ? List.of()
                : exception.getBindingResult().getFieldErrors().stream()
                        .map(e -> new FieldErrorResponse(e.getField(), e.getDefaultMessage()))
                        .toList();
        return StompErrors.of(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getDefaultMessage(),
                headers.getDestination(), errors);
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(destinations = ERRORS, broadcast = false)
    public ErrorResponse handleUnexpected(Exception exception, SimpMessageHeaderAccessor headers) {
        log.error("Lỗi không mong muốn khi xử lý tin STOMP tới [{}]", headers.getDestination(), exception);
        return StompErrors.of(ErrorCode.INTERNAL_ERROR, headers.getDestination());
    }
}

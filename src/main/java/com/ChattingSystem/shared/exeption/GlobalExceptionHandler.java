package com.ChattingSystem.shared.exeption;

import com.ChattingSystem.shared.response.ErrorResponse;
import com.ChattingSystem.shared.response.FieldErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(
            AppException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                exception.getErrorCode(),
                exception.getMessage(),
                request,
                exception.getErrors()
        );
    }

    // =========================================================
    // Validation - @RequestBody
    // =========================================================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {

        List<FieldErrorResponse> errors = exception
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError ->
                        new FieldErrorResponse(
                                fieldError.getField(),
                                fieldError.getDefaultMessage()
                        )
                )
                .toList();

        return buildResponse(
                ErrorCode.VALIDATION_ERROR,
                request,
                errors
        );
    }

    // =========================================================
    // Validation - @RequestParam / @PathVariable
    // =========================================================

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {

        List<FieldErrorResponse> errors = exception
                .getConstraintViolations()
                .stream()
                .map(violation ->
                        new FieldErrorResponse(
                                violation.getPropertyPath().toString(),
                                violation.getMessage()
                        )
                )
                .toList();

        return buildResponse(
                ErrorCode.VALIDATION_ERROR,
                request,
                errors
        );
    }

    // =========================================================
    // Validation - Handler Method
    // =========================================================

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidation(
            HandlerMethodValidationException exception,
            HttpServletRequest request
    ) {

        List<FieldErrorResponse> errors = exception
                .getParameterValidationResults()
                .stream()
                .flatMap(result ->
                        result.getResolvableErrors()
                                .stream()
                                .map(error ->
                                        new FieldErrorResponse(
                                                result.getMethodParameter()
                                                        .getParameterName(),
                                                error.getDefaultMessage()
                                        )
                                )
                )
                .toList();

        return buildResponse(
                ErrorCode.VALIDATION_ERROR,
                request,
                errors
        );
    }

    // =========================================================
    // Malformed requests / routing
    // =========================================================

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            HttpMediaTypeNotSupportedException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class
    })
    public ResponseEntity<ErrorResponse> handleMalformedRequest(
            Exception exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                ErrorCode.MALFORMED_REQUEST,
                request
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                ErrorCode.FILE_TOO_LARGE,
                request
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                ErrorCode.RESOURCE_NOT_FOUND,
                request
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                ErrorCode.METHOD_NOT_ALLOWED,
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {

        log.error(
                "Unhandled exception: {} {}",
                request.getMethod(),
                request.getRequestURI(),
                exception
        );

        return buildResponse(
                ErrorCode.INTERNAL_ERROR,
                request
        );
    }

    private ResponseEntity<ErrorResponse> buildResponse(
            ErrorCode errorCode,
            HttpServletRequest request
    ) {

        return buildResponse(
                errorCode,
                request,
                Collections.emptyList()
        );
    }

    private ResponseEntity<ErrorResponse> buildResponse(
            ErrorCode errorCode,
            HttpServletRequest request,
            List<FieldErrorResponse> errors
    ) {

        ErrorResponse response = new ErrorResponse(
                errorCode.getHttpStatus().value(),
                errorCode.getDefaultMessage(),
                request.getRequestURI(),
                LocalDateTime.now(),
                errors
        );

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(response);
    }
    private ResponseEntity<ErrorResponse> buildResponse(
            ErrorCode errorCode,
            String message,
            HttpServletRequest request,
            List<FieldErrorResponse> errors
    ) {

        ErrorResponse response = new ErrorResponse(
                errorCode.getHttpStatus().value(),
                message,
                request.getRequestURI(),
                LocalDateTime.now(),
                errors
        );

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(response);
    }
}

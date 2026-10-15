package com.ChattingSystem.security.handler;

import com.ChattingSystem.shared.exeption.ErrorCode;
import com.ChattingSystem.shared.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;

/**
 * Writes the project's {@link ErrorResponse} JSON for errors raised by the security filter chain,
 * which never reach {@code GlobalExceptionHandler}.
 */
@Component
public class SecurityErrorResponseWriter {

    private final JsonMapper jsonMapper;

    public SecurityErrorResponseWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void write(HttpServletRequest request,
                      HttpServletResponse response,
                      ErrorCode errorCode) throws IOException {
        ErrorResponse body = new ErrorResponse(
                errorCode.getHttpStatus().value(),
                errorCode.getDefaultMessage(),
                request.getRequestURI(),
                LocalDateTime.now(),
                Collections.emptyList()
        );

        response.setStatus(errorCode.getHttpStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}

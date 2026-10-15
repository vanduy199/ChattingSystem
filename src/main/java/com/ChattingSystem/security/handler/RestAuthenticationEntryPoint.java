package com.ChattingSystem.security.handler;

import com.ChattingSystem.shared.exeption.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 401 for missing / invalid / expired tokens, as {@code ErrorResponse} JSON. */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final SecurityErrorResponseWriter writer;

    public RestAuthenticationEntryPoint(SecurityErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // Keeps the standard WWW-Authenticate header (RFC 6750) before writing the JSON body.
        bearerEntryPoint.commence(request, response, authException);
        writer.write(request, response, ErrorCode.UNAUTHENTICATED);
    }
}

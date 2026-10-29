package com.ChattingSystem.realtime;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.security.Principal;

public final class StompPrincipals {

    private StompPrincipals() {
    }

    /** Principal do {@code StompAuthChannelInterceptor} gắn lúc CONNECT; claim "userId" như REST. */
    public static Long userIdOf(Principal principal) {
        if (!(principal instanceof JwtAuthenticationToken token)) {
            throw new AuthenticationCredentialsNotFoundException("Chưa xác thực");
        }
        return ((Number) token.getToken().getClaim("userId")).longValue();
    }
}

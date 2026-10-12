package com.ChattingSystem.security.jwt;

import com.ChattingSystem.shared.properties.JwtProperties;
import com.ChattingSystem.user.entity.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtProperties jwtProperties;
    private final JwtEncoder jwtEncoder;

    public JwtService(
            JwtProperties jwtProperties,
            JwtEncoder jwtEncoder
    ) {
        this.jwtProperties = jwtProperties;
        this.jwtEncoder = jwtEncoder;
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getFirebaseUid())
                .id(UUID.randomUUID().toString())
                .issuer("ChattingSystem")
                .issuedAt(now)
                .expiresAt(now.plus(
                        jwtProperties.accessTokenExpiration()
                ))
                .claim("userId", user.getId())
                .claim("scope", "USER")
                .build();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }
}
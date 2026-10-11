package com.ChattingSystem.shared.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secretKey, @NotNull Duration accessTokenExpiration, @NotNull Duration refreshExpiration) {
}

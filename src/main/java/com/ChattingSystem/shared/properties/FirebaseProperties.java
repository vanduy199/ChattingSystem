package com.ChattingSystem.shared.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "firebase")
public record FirebaseProperties(
        String serviceAccount
) {
}

package com.ChattingSystem.config;


import com.ChattingSystem.shared.properties.FirebaseProperties;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Bean(destroyMethod = "delete")
    public FirebaseApp firebaseApp(FirebaseProperties props) throws IOException {

        for (FirebaseApp app : FirebaseApp.getApps()) {
            if (FirebaseApp.DEFAULT_APP_NAME.equals(app.getName())) {
                return app;
            }
        }

        String serviceAccountJson = props.serviceAccount();
        if (serviceAccountJson == null || serviceAccountJson.isBlank()) {
            throw new IllegalStateException("Firebase service account JSON is missing");
        }

        try (InputStream in = new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8))) {
            FirebaseOptions.Builder builder = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(in));

            FirebaseApp app = FirebaseApp.initializeApp(builder.build());
            log.info("Firebase initialized successfully");
            return app;
        }
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }

    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        return FirebaseAuth.getInstance(firebaseApp);
    }
}

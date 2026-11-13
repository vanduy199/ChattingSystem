package com.ChattingSystem.realtime.config;

import com.ChattingSystem.realtime.security.StompAuthChannelInterceptor;
import com.ChattingSystem.shared.properties.CorsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP qua WebSocket thuần tại {@code /api/v1/ws}. Simple broker trong bộ nhớ: chỉ đúng
 * khi chạy 1 instance; nhiều instance thì cần relay (Redis Pub/Sub hoặc RabbitMQ).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final long HEARTBEAT_MS = 10_000;

    private final CorsProperties corsProperties;
    private final StompAuthChannelInterceptor authInterceptor;

    public WebSocketConfig(CorsProperties corsProperties, StompAuthChannelInterceptor authInterceptor) {
        this.corsProperties = corsProperties;
        this.authInterceptor = authInterceptor;
    }

    @Bean
    public ThreadPoolTaskScheduler stompHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("stomp-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws", "/ws/")
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new));
        // Không bật setPreserveReceiveOrder: khi đó lỗi ở CONNECT chạy trên thread khác,
        // server không gửi frame ERROR và client treo thay vì được báo token sai
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{HEARTBEAT_MS, HEARTBEAT_MS})
                .setTaskScheduler(stompHeartbeatScheduler());
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}

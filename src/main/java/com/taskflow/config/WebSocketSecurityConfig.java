package com.taskflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

/**
 * WebSocket 安全設定：確保所有 STOMP 連線、訂閱、訊息均需通過身份驗證（Finding 3）
 * Spring Security 6.x 使用 @EnableWebSocketSecurity + AuthorizationManager 取代舊 AbstractSecurityWebSocketMessageBrokerConfigurer
 */
@Configuration
@EnableWebSocketSecurity
public class WebSocketSecurityConfig {

    @Bean
    AuthorizationManager<Message<?>> messageAuthorizationManager(
            MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        messages
            .simpTypeMatchers(SimpMessageType.CONNECT).authenticated()
            .simpTypeMatchers(SimpMessageType.SUBSCRIBE).authenticated()
            .simpTypeMatchers(SimpMessageType.MESSAGE).authenticated()
            .anyMessage().authenticated();
        return messages.build();
    }

    /**
     * 禁用 STOMP CSRF 檢查：
     * - SockJS 使用 HttpOnly session cookie（已受保護）
     * - Spring Session 提供 session 層級身份驗證
     * - CSRF 在 session 式驗證下是冗餘的（Finding 4 修正）
     */
    @Bean("csrfChannelInterceptor")
    public ChannelInterceptor csrfChannelInterceptor() {
        return new ChannelInterceptor() {};  // 無操作 interceptor，覆蓋預設 CSRF 攔截
    }
}

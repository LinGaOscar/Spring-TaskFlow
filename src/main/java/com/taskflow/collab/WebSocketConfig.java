package com.taskflow.collab;

import com.taskflow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final BoardSubscribeAuthInterceptor boardSubscribeAuthInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    // 訂閱看板頻道前驗證 canReadBoard，將部門隔離延伸到即時層
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(boardSubscribeAuthInterceptor);
    }
}

/** 監聽 STOMP 斷線事件，自動廣播 LEAVE 並從在線列表移除 */
@Component
@RequiredArgsConstructor
class SessionDisconnectListener implements
        org.springframework.context.ApplicationListener<SessionDisconnectEvent> {

    private final CollabService collabService;
    private final SimpMessagingTemplate broker;
    private final UserRepository userRepository;

    @Override
    public void onApplicationEvent(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor =
            org.springframework.messaging.simp.stomp.StompHeaderAccessor.wrap(event.getMessage());
        if (headerAccessor.getUser() == null) return;
        String email = headerAccessor.getUser().getName();
        userRepository.findByEmail(email).ifPresent(user -> {
            collabService.sessions.forEach((boardId, users) -> {
                if (users.containsKey(user.getId())) {
                    collabService.leave(boardId, user.getId());
                    PresenceMessage leave = new PresenceMessage();
                    leave.setType(PresenceMessage.Type.LEAVE);
                    leave.setUserId(user.getId());
                    leave.setDisplayName(user.getDisplayName());
                    leave.setColor(collabService.userColor(user.getId()));
                    broker.convertAndSend("/topic/board/" + boardId + "/presence", leave);
                }
            });
        });
    }
}

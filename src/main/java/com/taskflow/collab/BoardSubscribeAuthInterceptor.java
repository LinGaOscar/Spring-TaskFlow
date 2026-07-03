package com.taskflow.collab;

import com.taskflow.board.BoardService;
import com.taskflow.user.User;
import com.taskflow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 部門隔離必須延伸到即時層：REST 端有 canReadBoard 把關，若 WebSocket 訂閱不設閘，
// 任何登入者都能訂閱任意看板的 /topic 即時窺看跨科任務內容
@Component
@RequiredArgsConstructor
public class BoardSubscribeAuthInterceptor implements ChannelInterceptor {

    // 同時涵蓋 /topic/board/{id}/**（廣播頻道）與 /app/board/{id}/presence（@SubscribeMapping 初始清單）
    private static final Pattern BOARD_DEST =
        Pattern.compile("^/(?:topic|app)/board/(\\d+)(?:/.*)?$");

    private final UserRepository userRepository;
    private final BoardService boardService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (!StompCommand.SUBSCRIBE.equals(accessor.getCommand())) return message;

        String destination = accessor.getDestination();
        if (destination == null) return message;
        Matcher m = BOARD_DEST.matcher(destination);
        if (!m.matches()) return message;

        if (accessor.getUser() == null) {
            throw new AccessDeniedException("未登入不可訂閱看板頻道");
        }
        User user = userRepository.findByEmail(accessor.getUser().getName())
            .orElseThrow(() -> new AccessDeniedException("使用者不存在"));
        // canReadBoard 自帶唯讀交易，WebSocket 執行緒無 open-in-view 也能安全載入 LAZY 關聯
        if (!boardService.canReadBoard(Long.valueOf(m.group(1)), user)) {
            throw new AccessDeniedException("無檢視此看板的權限");
        }
        return message;
    }
}

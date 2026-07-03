package com.taskflow.collab;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CollabService {

    // 依 spec 定義的色板（Finding 5）
    private static final String[] COLORS = {
        "#e74c3c", "#3498db", "#2ecc71", "#f39c12",
        "#9b59b6", "#1abc9c", "#e67e22", "#34495e"
    };

    // boardId → (userId → PresenceMessage)，記憶體內追蹤在線用戶，重啟後清空不影響資料
    public final Map<Long, Map<Long, PresenceMessage>> sessions = new ConcurrentHashMap<>();

    // 使用位元遮罩確保非負數，避免 userId 為負數時造成陣列越界（Finding 1）
    public String userColor(Long userId) {
        int colorIndex = (int) (userId & 0x7FFFFFFFL) % COLORS.length;
        return COLORS[colorIndex];
    }

    // getUserColor 為 userColor 的別名，供 CollabController join/leave 端點呼叫
    public String getUserColor(Long userId) {
        return userColor(userId);
    }

    // 加入時寫入記憶體 sessions，同一用戶重複加入只覆蓋不重複
    public void join(Long boardId, Long userId, String displayName) {
        sessions.computeIfAbsent(boardId, k -> new ConcurrentHashMap<>());
        PresenceMessage msg = new PresenceMessage();
        msg.setType(PresenceMessage.Type.JOIN);
        msg.setUserId(userId);
        msg.setDisplayName(displayName);
        msg.setColor(userColor(userId));
        sessions.get(boardId).put(userId, msg);
    }

    // 前端 beforeunload 觸發，減少「假在線」殘留；WebSocket 斷線也可在事件中呼叫
    public void leave(Long boardId, Long userId) {
        if (sessions.containsKey(boardId)) {
            sessions.get(boardId).remove(userId);
        }
    }

    // 後進用戶訂閱 presence 時，回傳當前在線列表，讓其看到先來的協作者
    public List<PresenceMessage> getOnlineUsers(Long boardId) {
        return sessions.getOrDefault(boardId, Collections.emptyMap())
            .values().stream().toList();
    }
}

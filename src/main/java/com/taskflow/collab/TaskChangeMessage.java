package com.taskflow.collab;

import lombok.Data;
import java.time.Instant;

@Data
public class TaskChangeMessage {
    public enum Type { TASK_CREATE, TASK_UPDATE, TASK_MOVE, TASK_DELETE }

    private Type type;
    private Long taskId;
    private Object payload;      // TaskDto.Response；MOVE 時另含全板任務重排結果
    private UserInfo operator;
    // 使用 Instant 確保 UTC 一致，避免各端時區差異造成排序錯亂
    private Instant timestamp = Instant.now();

    @Data
    public static class UserInfo {
        private Long userId;
        private String displayName;
        private String color;
    }
}

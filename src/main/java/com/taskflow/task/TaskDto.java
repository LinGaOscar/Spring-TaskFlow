package com.taskflow.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

public class TaskDto {

    @Data
    public static class SaveRequest {
        @NotBlank(message = "標題不可為空")
        private String title;
        private String description;
        private Long assigneeId;
        private LocalDate dueDate;
        private Task.Priority priority = Task.Priority.MEDIUM;
    }

    @Data
    public static class MoveRequest {
        @NotNull
        private Task.Status status;   // 目標欄
        private int targetIndex;      // 目標欄內的插入位置（0-based）
    }

    @Data
    public static class Response {
        private Long id;
        private String title;
        private String description;
        private Long assigneeId;
        private String assigneeName;
        private LocalDate dueDate;
        private Task.Priority priority;
        private Task.Status status;
        private int sortOrder;

        public static Response from(Task t) {
            Response r = new Response();
            r.id = t.getId();
            r.title = t.getTitle();
            r.description = t.getDescription();
            if (t.getAssignee() != null) {
                r.assigneeId = t.getAssignee().getId();
                r.assigneeName = t.getAssignee().getDisplayName();
            }
            r.dueDate = t.getDueDate();
            r.priority = t.getPriority();
            r.status = t.getStatus();
            r.sortOrder = t.getSortOrder();
            return r;
        }
    }
}

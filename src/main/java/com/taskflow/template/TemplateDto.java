package com.taskflow.template;

import com.taskflow.task.Task;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

public class TemplateDto {

    @Data
    public static class SaveRequest {
        @NotBlank(message = "模板名稱不可為空")
        private String name;
        @NotBlank(message = "任務標題不可為空")
        private String title;
        private String description;
        private Task.Priority priority = Task.Priority.MEDIUM;
    }

    @Data
    public static class Response {
        private Long id;
        private String name;
        private String title;
        private String description;
        private Task.Priority priority;
        private boolean system;

        public static Response from(TaskTemplate t) {
            Response r = new Response();
            r.id = t.getId(); r.name = t.getName(); r.title = t.getTitle();
            r.description = t.getDescription(); r.priority = t.getPriority();
            r.system = t.isSystem();
            return r;
        }
    }
}

package com.taskflow.board;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDateTime;

public class BoardDto {

    @Data
    public static class CreateRequest {
        @NotBlank private String name;
    }

    @Data
    public static class Response {
        private Long id;
        private String name;
        private String departmentName;
        private Long ownerId;
        private String ownerName;
        private String ownerEmail;
        private boolean archived;
        private LocalDateTime createdAt;

        public static Response from(Board p) {
            Response r = new Response();
            r.id = p.getId();
            r.name = p.getName();
            r.departmentName = p.getDepartment() != null ? p.getDepartment().getName() : null;
            r.ownerId    = p.getOwner() != null ? p.getOwner().getId() : null;
            r.ownerName  = p.getOwner() != null ? p.getOwner().getDisplayName() : null;
            r.ownerEmail = p.getOwner() != null ? p.getOwner().getEmail() : null;
            r.archived   = p.isArchived();
            r.createdAt  = p.getCreatedAt();
            return r;
        }
    }

    @Data
    public static class MemberResponse {
        private Long userId;
        private String displayName;
        private String email;

        public static MemberResponse from(BoardMember m) {
            MemberResponse r = new MemberResponse();
            r.userId = m.getUser().getId();
            r.displayName = m.getUser().getDisplayName();
            r.email = m.getUser().getEmail();
            return r;
        }
    }
}

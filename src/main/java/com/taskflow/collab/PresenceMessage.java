package com.taskflow.collab;

import lombok.Data;

@Data
public class PresenceMessage {
    public enum Type { JOIN, LEAVE }

    private Type type;
    private Long userId;
    private String displayName;
    private String color;
}

package com.lreyes.platform.ui.zk.model;

import java.time.Instant;

public class AssistantMessage {

    private String text;
    private boolean fromUser;
    private Instant timestamp;

    public AssistantMessage() {
    }

    public AssistantMessage(String text, boolean fromUser) {
        this.text = text;
        this.fromUser = fromUser;
        this.timestamp = Instant.now();
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public boolean isFromUser() { return fromUser; }
    public void setFromUser(boolean fromUser) { this.fromUser = fromUser; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}

package com.lreyes.platform.modules.documents.event;

import com.lreyes.platform.shared.domain.DomainEvent;
import lombok.Getter;

import java.util.UUID;

@Getter
public class DocumentCreatedEvent extends DomainEvent {

    private final UUID documentId;
    private final String title;

    public DocumentCreatedEvent(String tenantId, UUID documentId, String title) {
        super(tenantId);
        this.documentId = documentId;
        this.title = title;
    }

    @Override
    public String eventType() {
        return "document.created";
    }
}

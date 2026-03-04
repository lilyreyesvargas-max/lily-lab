package com.lreyes.platform.modules.documents.dto;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String title,
        String description,
        String fileName,
        String contentType,
        Long fileSize,
        String storagePath,
        int docVersion,
        String relatedEntityType,
        String relatedEntityId,
        Instant createdAt,
        Instant updatedAt
) {}

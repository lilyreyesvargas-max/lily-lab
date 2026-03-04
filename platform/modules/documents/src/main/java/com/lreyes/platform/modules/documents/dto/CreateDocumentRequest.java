package com.lreyes.platform.modules.documents.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDocumentRequest(
        @NotBlank String title,
        String description,
        @NotBlank String fileName,
        String contentType,
        Long fileSize,
        String storagePath,
        String relatedEntityType,
        String relatedEntityId
) {}

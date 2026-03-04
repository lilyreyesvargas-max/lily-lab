package com.lreyes.platform.modules.documents.dto;

public record UpdateDocumentRequest(
        String title,
        String description,
        String fileName,
        String contentType,
        Long fileSize,
        String storagePath
) {}

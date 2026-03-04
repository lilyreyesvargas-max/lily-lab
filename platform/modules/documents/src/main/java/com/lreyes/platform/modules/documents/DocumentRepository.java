package com.lreyes.platform.modules.documents;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Page<Document> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    List<Document> findByRelatedEntityTypeAndRelatedEntityId(String entityType, String entityId);
}

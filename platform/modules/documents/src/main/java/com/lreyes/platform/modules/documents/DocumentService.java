package com.lreyes.platform.modules.documents;

import com.lreyes.platform.core.events.DomainEventPublisher;
import com.lreyes.platform.core.tenancy.TenantContext;
import com.lreyes.platform.modules.documents.dto.CreateDocumentRequest;
import com.lreyes.platform.modules.documents.dto.DocumentResponse;
import com.lreyes.platform.modules.documents.dto.UpdateDocumentRequest;
import com.lreyes.platform.modules.documents.event.DocumentCreatedEvent;
import com.lreyes.platform.shared.domain.EntityNotFoundException;
import com.lreyes.platform.shared.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentMapper documentMapper;
    private final DomainEventPublisher eventPublisher;

    public PageResponse<DocumentResponse> findAll(String search, Pageable pageable) {
        Page<Document> page;
        if (search != null && !search.isBlank()) {
            page = documentRepository.findByTitleContainingIgnoreCase(search.trim(), pageable);
        } else {
            page = documentRepository.findAll(pageable);
        }
        return PageResponse.from(page, documentMapper::toResponse);
    }

    public DocumentResponse findById(UUID id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Document", id));
        return documentMapper.toResponse(doc);
    }

    public List<DocumentResponse> findByRelatedEntity(String entityType, String entityId) {
        return documentMapper.toResponseList(
                documentRepository.findByRelatedEntityTypeAndRelatedEntityId(entityType, entityId));
    }

    @Transactional
    public DocumentResponse create(CreateDocumentRequest request) {
        Document doc = documentMapper.toEntity(request);
        doc.setDocVersion(1);
        Document saved = documentRepository.save(doc);

        eventPublisher.publish(
                new DocumentCreatedEvent(
                        TenantContext.getCurrentTenant(),
                        saved.getId(),
                        saved.getTitle()),
                "document",
                saved.getId());

        return documentMapper.toResponse(saved);
    }

    @Transactional
    public DocumentResponse update(UUID id, UpdateDocumentRequest request) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Document", id));
        documentMapper.updateEntity(request, doc);
        doc.setDocVersion(doc.getDocVersion() + 1);
        Document saved = documentRepository.save(doc);
        return documentMapper.toResponse(saved);
    }

    @Transactional
    public void delete(UUID id) {
        if (!documentRepository.existsById(id)) {
            throw new EntityNotFoundException("Document", id);
        }
        documentRepository.deleteById(id);
    }
}

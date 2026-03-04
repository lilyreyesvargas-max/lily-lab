package com.lreyes.platform.modules.documents;

import com.lreyes.platform.core.events.DomainEventPublisher;
import com.lreyes.platform.modules.documents.dto.CreateDocumentRequest;
import com.lreyes.platform.modules.documents.dto.DocumentResponse;
import com.lreyes.platform.modules.documents.dto.UpdateDocumentRequest;
import com.lreyes.platform.modules.documents.event.DocumentCreatedEvent;
import com.lreyes.platform.shared.domain.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentMapper documentMapper;
    @Mock
    private DomainEventPublisher eventPublisher;

    @InjectMocks
    private DocumentService documentService;

    private Document document;
    private DocumentResponse documentResponse;
    private UUID docId;

    @BeforeEach
    void setUp() {
        docId = UUID.randomUUID();
        document = new Document();
        document.setId(docId);
        document.setTitle("Contrato 2024");
        document.setFileName("contrato.pdf");
        document.setContentType("application/pdf");
        document.setFileSize(1024L);
        document.setDocVersion(1);
        document.setCreatedAt(Instant.now());

        documentResponse = new DocumentResponse(
                docId, "Contrato 2024", null, "contrato.pdf",
                "application/pdf", 1024L, null, 1,
                null, null, document.getCreatedAt(), null);
    }

    @Test
    @DisplayName("create - guarda documento y publica evento")
    void create_savesAndPublishesEvent() {
        var request = new CreateDocumentRequest(
                "Contrato 2024", null, "contrato.pdf", "application/pdf",
                1024L, "/files/contrato.pdf", null, null);

        when(documentMapper.toEntity(request)).thenReturn(document);
        when(documentRepository.save(any(Document.class))).thenReturn(document);
        when(documentMapper.toResponse(document)).thenReturn(documentResponse);

        DocumentResponse result = documentService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("Contrato 2024");
        verify(documentRepository).save(any(Document.class));

        ArgumentCaptor<DocumentCreatedEvent> captor = ArgumentCaptor.forClass(DocumentCreatedEvent.class);
        verify(eventPublisher).publish(captor.capture(), eq("document"), eq(docId));
        assertThat(captor.getValue().getDocumentId()).isEqualTo(docId);
        assertThat(captor.getValue().eventType()).isEqualTo("document.created");
    }

    @Test
    @DisplayName("findById - retorna documento encontrado")
    void findById_found() {
        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(documentMapper.toResponse(document)).thenReturn(documentResponse);

        DocumentResponse result = documentService.findById(docId);

        assertThat(result.id()).isEqualTo(docId);
    }

    @Test
    @DisplayName("findById - lanza excepción si no existe")
    void findById_notFound() {
        UUID id = UUID.randomUUID();
        when(documentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.findById(id))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("update - incrementa docVersion")
    void update_incrementsVersion() {
        var request = new UpdateDocumentRequest("Contrato Actualizado", null, null, null, null, null);
        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(documentRepository.save(document)).thenReturn(document);
        when(documentMapper.toResponse(document)).thenReturn(documentResponse);

        documentService.update(docId, request);

        assertThat(document.getDocVersion()).isEqualTo(2);
        verify(documentMapper).updateEntity(request, document);
        verify(documentRepository).save(document);
    }

    @Test
    @DisplayName("delete - elimina documento existente")
    void delete_existing() {
        when(documentRepository.existsById(docId)).thenReturn(true);

        documentService.delete(docId);

        verify(documentRepository).deleteById(docId);
    }

    @Test
    @DisplayName("delete - lanza excepción si no existe")
    void delete_notFound() {
        UUID id = UUID.randomUUID();
        when(documentRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> documentService.delete(id))
                .isInstanceOf(EntityNotFoundException.class);
        verify(documentRepository, never()).deleteById(any());
    }
}

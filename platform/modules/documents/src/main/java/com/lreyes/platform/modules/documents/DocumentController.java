package com.lreyes.platform.modules.documents;

import com.lreyes.platform.modules.documents.dto.CreateDocumentRequest;
import com.lreyes.platform.modules.documents.dto.DocumentResponse;
import com.lreyes.platform.modules.documents.dto.UpdateDocumentRequest;
import com.lreyes.platform.shared.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping
    public ResponseEntity<PageResponse<DocumentResponse>> findAll(
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ResponseEntity.ok(documentService.findAll(search, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.findById(id));
    }

    @GetMapping("/related/{entityType}/{entityId}")
    public ResponseEntity<List<DocumentResponse>> findByRelatedEntity(
            @PathVariable String entityType,
            @PathVariable String entityId) {
        return ResponseEntity.ok(documentService.findByRelatedEntity(entityType, entityId));
    }

    @PostMapping
    public ResponseEntity<DocumentResponse> create(
            @Valid @RequestBody CreateDocumentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documentService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DocumentResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDocumentRequest request) {
        return ResponseEntity.ok(documentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        documentService.delete(id);
    }
}

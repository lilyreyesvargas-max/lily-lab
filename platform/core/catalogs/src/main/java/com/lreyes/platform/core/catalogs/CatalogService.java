package com.lreyes.platform.core.catalogs;

import com.lreyes.platform.shared.domain.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogService {

    private final CatalogRepository catalogRepository;

    public List<Catalog> findAll() {
        return catalogRepository.findAll();
    }

    public List<Catalog> findByType(String type) {
        return catalogRepository.findByTypeOrderBySortOrder(type);
    }

    public List<String> findDistinctTypes() {
        return catalogRepository.findDistinctTypes();
    }

    public Catalog findById(UUID id) {
        return catalogRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Catalog", id));
    }

    @Transactional
    public Catalog create(Catalog catalog, UUID parentId) {
        if (parentId != null) {
            Catalog parent = catalogRepository.findById(parentId)
                    .orElseThrow(() -> new EntityNotFoundException("Catalog", parentId));
            catalog.setParent(parent);
        }
        return catalogRepository.save(catalog);
    }

    @Transactional
    public Catalog update(UUID id, String type, String code, String name,
                          String description, boolean active, int sortOrder, UUID parentId) {
        Catalog catalog = catalogRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Catalog", id));
        catalog.setType(type);
        catalog.setCode(code);
        catalog.setName(name);
        catalog.setDescription(description);
        catalog.setActive(active);
        catalog.setSortOrder(sortOrder);
        if (parentId != null) {
            Catalog parent = catalogRepository.findById(parentId)
                    .orElseThrow(() -> new EntityNotFoundException("Catalog", parentId));
            catalog.setParent(parent);
        } else {
            catalog.setParent(null);
        }
        return catalogRepository.save(catalog);
    }

    @Transactional
    public void delete(UUID id) {
        if (!catalogRepository.existsById(id)) {
            throw new EntityNotFoundException("Catalog", id);
        }
        catalogRepository.deleteById(id);
    }
}

package com.lreyes.platform.core.catalogs;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface CatalogRepository extends JpaRepository<Catalog, UUID> {

    List<Catalog> findByTypeOrderBySortOrder(String type);

    @Query("SELECT DISTINCT c.type FROM Catalog c ORDER BY c.type")
    List<String> findDistinctTypes();

    boolean existsByTypeAndCode(String type, String code);
}

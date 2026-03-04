package com.lreyes.platform.shared.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;

import java.util.List;

/**
 * Respuesta paginada genérica para cualquier listado.
 * <p>
 * Uso desde un servicio:
 * <pre>
 *   Page&lt;Customer&gt; page = repo.findAll(pageable);
 *   return PageResponse.from(page, mapper::toDto);
 * </pre>
 *
 * @param <T> tipo del elemento en la lista
 */
@JsonPropertyOrder({"content", "page", "size", "totalElements", "totalPages", "last"})
@Builder
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {

    /**
     * Factory desde Spring Data Page.
     */
    public static <S, T> PageResponse<T> from(
            org.springframework.data.domain.Page<S> springPage,
            java.util.function.Function<S, T> mapper
    ) {
        return PageResponse.<T>builder()
                .content(springPage.getContent().stream().map(mapper).toList())
                .page(springPage.getNumber())
                .size(springPage.getSize())
                .totalElements(springPage.getTotalElements())
                .totalPages(springPage.getTotalPages())
                .last(springPage.isLast())
                .build();
    }
}

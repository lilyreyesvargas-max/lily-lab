package com.lreyes.platform.shared.mapping;

import java.util.List;

/**
 * Interfaz base genérica para mappers DTO ↔ Entidad.
 * <p>
 * Cada módulo de negocio crea un mapper que extiende esta interfaz:
 * <pre>
 * &#64;Mapper(config = DefaultMapStructConfig.class)
 * public interface CustomerMapper extends BaseMapper&lt;Customer, CustomerDto&gt; {
 *     // métodos adicionales si se necesitan
 * }
 * </pre>
 *
 * @param <E> tipo entidad
 * @param <D> tipo DTO
 */
public interface BaseMapper<E, D> {

    D toDto(E entity);

    E toEntity(D dto);

    List<D> toDtoList(List<E> entities);

    List<E> toEntityList(List<D> dtos);
}

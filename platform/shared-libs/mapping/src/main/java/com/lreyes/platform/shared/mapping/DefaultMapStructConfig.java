package com.lreyes.platform.shared.mapping;

import org.mapstruct.MapperConfig;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * Configuración global de MapStruct compartida por todos los mappers.
 * <p>
 * Convenciones:
 * <ul>
 *   <li>{@code componentModel = SPRING}: los mappers se registran como beans Spring.</li>
 *   <li>{@code unmappedTargetPolicy = WARN}: avisa si faltan mappings pero no falla.</li>
 * </ul>
 * <p>
 * Uso en un mapper:
 * <pre>
 * &#64;Mapper(config = DefaultMapStructConfig.class)
 * public interface CustomerMapper {
 *     CustomerDto toDto(Customer entity);
 * }
 * </pre>
 */
@MapperConfig(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.WARN
)
public interface DefaultMapStructConfig {
}

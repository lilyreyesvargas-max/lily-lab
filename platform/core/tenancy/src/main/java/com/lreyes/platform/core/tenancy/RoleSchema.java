package com.lreyes.platform.core.tenancy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Asignacion de schema_type a un rol del tenant.
 * Tabla: role_schemas (dentro del schema del tenant).
 */
@Entity
@Table(name = "role_schemas")
@IdClass(RoleSchemaId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoleSchema {

    @Id
    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Id
    @Column(name = "schema_type", nullable = false, length = 50)
    private String schemaType;
}

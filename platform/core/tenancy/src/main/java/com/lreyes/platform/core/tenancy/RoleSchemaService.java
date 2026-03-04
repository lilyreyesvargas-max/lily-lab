package com.lreyes.platform.core.tenancy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servicio para gestionar asignaciones de schema_types a roles.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleSchemaService {

    private final RoleSchemaRepository roleSchemaRepository;
    private final RoleRepository roleRepository;

    /**
     * Obtiene los schema_types permitidos para una lista de nombres de rol.
     */
    public Set<String> getSchemaTypesForRoles(List<String> roleNames) {
        List<UUID> roleIds = roleNames.stream()
                .map(name -> roleRepository.findByName(name))
                .filter(java.util.Optional::isPresent)
                .map(opt -> opt.get().getId())
                .toList();

        if (roleIds.isEmpty()) {
            return Set.of();
        }

        return roleSchemaRepository.findByRoleIdIn(roleIds).stream()
                .map(RoleSchema::getSchemaType)
                .collect(Collectors.toSet());
    }

    /**
     * Obtiene los schema_types asignados a un rol especifico.
     */
    public List<String> getSchemaTypesForRole(UUID roleId) {
        return roleSchemaRepository.findByRoleId(roleId).stream()
                .map(RoleSchema::getSchemaType)
                .toList();
    }

    /**
     * Guarda las asignaciones de schema_types para un rol (borra y reinserta).
     */
    @Transactional
    public void saveAssignments(UUID roleId, Collection<String> schemaTypes) {
        roleSchemaRepository.deleteByRoleId(roleId);
        for (String type : schemaTypes) {
            roleSchemaRepository.save(new RoleSchema(roleId, type));
        }
    }
}

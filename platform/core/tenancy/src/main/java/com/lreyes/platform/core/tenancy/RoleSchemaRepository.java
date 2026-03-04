package com.lreyes.platform.core.tenancy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RoleSchemaRepository extends JpaRepository<RoleSchema, RoleSchemaId> {

    List<RoleSchema> findByRoleId(UUID roleId);

    List<RoleSchema> findByRoleIdIn(Collection<UUID> roleIds);

    void deleteByRoleId(UUID roleId);
}

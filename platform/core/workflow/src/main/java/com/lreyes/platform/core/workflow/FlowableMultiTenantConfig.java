package com.lreyes.platform.core.workflow;

import com.lreyes.platform.core.tenancy.platform.TenantRegistryService;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.List;

/**
 * Despliega los procesos BPMN para cada tenant configurado.
 * <p>
 * Flowable auto-deploy (Spring Boot starter) despliega sin tenant.
 * Para multi-tenancy, cada tenant necesita su propia copia de la
 * definición de proceso para que las tareas hereden el tenantId.
 */
@Configuration
@Slf4j
public class FlowableMultiTenantConfig {

    @Bean
    @Order(2)
    ApplicationRunner deployProcessesPerTenant(RepositoryService repositoryService,
                                               TenantRegistryService tenantRegistryService) {
        return args -> {
            List<String> tenants = tenantRegistryService.getActiveTenantNames();

            if (tenants.isEmpty()) {
                log.warn("No hay tenants activos en BD. Procesos solo disponibles sin tenant.");
                return;
            }

            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] bpmnResources = resolver.getResources("classpath*:processes/*.bpmn20.xml");

            for (String tenant : tenants) {
                for (Resource resource : bpmnResources) {
                    String resourceName = resource.getFilename();

                    // Verificar si ya existe un deployment para este tenant
                    long existing = repositoryService.createDeploymentQuery()
                            .deploymentTenantId(tenant)
                            .deploymentName("tenant-" + tenant)
                            .count();

                    if (existing > 0) {
                        log.debug("Proceso ya desplegado para tenant '{}': {}", tenant, resourceName);
                        continue;
                    }

                    Deployment deployment = repositoryService.createDeployment()
                            .name("tenant-" + tenant)
                            .tenantId(tenant)
                            .addInputStream(resourceName, resource.getInputStream())
                            .deploy();

                    ProcessDefinition def = repositoryService.createProcessDefinitionQuery()
                            .deploymentId(deployment.getId())
                            .singleResult();

                    log.info("Proceso desplegado para tenant '{}': key='{}', deploymentId='{}'",
                            tenant, def != null ? def.getKey() : "?", deployment.getId());
                }
            }
        };
    }
}

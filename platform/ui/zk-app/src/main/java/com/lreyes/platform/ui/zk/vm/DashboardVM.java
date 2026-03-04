package com.lreyes.platform.ui.zk.vm;

import com.lreyes.platform.core.authsecurity.RoleConstants;
import com.lreyes.platform.core.tenancy.RoleSchemaService;
import com.lreyes.platform.core.tenancy.TenantContext;
import com.lreyes.platform.core.tenancy.platform.TenantRegistryService;
import com.lreyes.platform.core.tenancy.platform.TenantSchema;
import com.lreyes.platform.core.tenancy.platform.TenantSchemaService;
import com.lreyes.platform.core.workflow.WorkflowService;
import com.lreyes.platform.modules.customers.CustomerService;
import com.lreyes.platform.modules.employees.EmployeeService;
import com.lreyes.platform.ui.zk.model.UiUser;
import org.zkoss.bind.annotation.Init;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zkplus.spring.DelegatingVariableResolver;
import org.zkoss.zkplus.spring.SpringUtil;

import java.util.Set;
import java.util.stream.Collectors;

@VariableResolver(DelegatingVariableResolver.class)
public class DashboardVM {

    @WireVariable
    private CustomerService customerService;

    @WireVariable
    private EmployeeService employeeService;

    @WireVariable
    private WorkflowService workflowService;

    @WireVariable
    private TenantRegistryService tenantRegistryService;

    private long customerCount;
    private long employeeCount;
    private long pendingTaskCount;
    private String tenantName;
    private boolean showCustomers;
    private boolean showEmployees;
    private boolean showTasks;

    @Init
    public void init() {
        UiUser user = (UiUser) Sessions.getCurrent().getAttribute("user");
        if (user == null || user.getTenantId() == null) {
            return;
        }

        tenantName = user.getTenantId();
        TenantContext.setCurrentTenant(tenantName);

        if (user.isPlatformAdmin()) {
            // Platform admin ve todas las cards sin importar schemas del tenant
            showCustomers = true;
            showEmployees = true;
            showTasks = true;
        } else {
            Set<String> allowedSchemas = resolveAllowedSchemas(user);
            showCustomers = allowedSchemas.contains("sales");
            showEmployees = allowedSchemas.contains("hr");
            showTasks = allowedSchemas.contains("sales");
        }

        if (showCustomers) {
            customerCount = customerService.count();
        }
        if (showEmployees) {
            employeeCount = employeeService.count();
        }
        if (showTasks) {
            pendingTaskCount = workflowService.getAllPendingTasks(tenantName).size();
        }
    }

    private Set<String> resolveAllowedSchemas(UiUser user) {
        TenantSchemaService tenantSchemaService = SpringUtil.getApplicationContext()
                .getBean(TenantSchemaService.class);
        RoleSchemaService roleSchemaService = SpringUtil.getApplicationContext()
                .getBean(RoleSchemaService.class);

        Set<String> tenantSchemaTypes = tenantRegistryService.findByName(tenantName)
                .map(tenant -> tenantSchemaService.findByTenantId(tenant.getId()).stream()
                        .filter(TenantSchema::isActive)
                        .map(TenantSchema::getSchemaType)
                        .collect(Collectors.toSet()))
                .orElse(Set.of());

        if (user.isPlatformAdmin() || user.hasRole(RoleConstants.ADMIN)) {
            return tenantSchemaTypes;
        }

        Set<String> roleSchemaTypes = roleSchemaService.getSchemaTypesForRoles(user.getRoles());
        return roleSchemaTypes.stream()
                .filter(tenantSchemaTypes::contains)
                .collect(Collectors.toSet());
    }

    public long getCustomerCount() { return customerCount; }
    public long getEmployeeCount() { return employeeCount; }
    public long getPendingTaskCount() { return pendingTaskCount; }
    public String getTenantName() { return tenantName; }
    public boolean isShowCustomers() { return showCustomers; }
    public boolean isShowEmployees() { return showEmployees; }
    public boolean isShowTasks() { return showTasks; }
}

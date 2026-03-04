package com.lreyes.platform.ui.zk.vm;

import com.lreyes.platform.core.tenancy.TenantContext;
import com.lreyes.platform.modules.customers.CustomerService;
import com.lreyes.platform.modules.customers.dto.CreateCustomerRequest;
import com.lreyes.platform.modules.customers.dto.CustomerResponse;
import com.lreyes.platform.modules.customers.dto.UpdateCustomerRequest;
import com.lreyes.platform.ui.zk.model.CustomerItem;
import com.lreyes.platform.ui.zk.model.UiUser;
import org.springframework.data.domain.Pageable;
import org.zkoss.bind.annotation.BindingParam;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zkplus.spring.SpringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@VariableResolver(org.zkoss.zkplus.spring.DelegatingVariableResolver.class)
public class CustomerListVM {

    private CustomerService customerService;
    private UiUser user;

    private List<CustomerItem> customers = new ArrayList<>();
    private List<CustomerItem> allCustomers = new ArrayList<>();
    private CustomerItem selectedCustomer;
    private CustomerItem editingCustomer;
    private String searchTerm;
    private boolean editing;
    private boolean newRecord;

    @Init
    public void init() {
        user = (UiUser) Sessions.getCurrent().getAttribute("user");
        customerService = SpringUtil.getApplicationContext().getBean(CustomerService.class);
        loadData();
    }

    private void loadData() {
        TenantContext.setCurrentTenant(user.getTenantId());
        List<CustomerResponse> responses = customerService
                .findAll(null, Pageable.unpaged()).content();
        allCustomers = new ArrayList<>();
        for (CustomerResponse r : responses) {
            allCustomers.add(new CustomerItem(
                    r.id().toString(), r.name(), r.email(), r.phone()));
        }
        customers = new ArrayList<>(allCustomers);
    }

    @Command
    @NotifyChange("customers")
    public void search() {
        if (searchTerm == null || searchTerm.isBlank()) {
            customers = new ArrayList<>(allCustomers);
        } else {
            String term = searchTerm.toLowerCase();
            customers = allCustomers.stream()
                    .filter(c -> c.getName().toLowerCase().contains(term)
                            || (c.getEmail() != null && c.getEmail().toLowerCase().contains(term)))
                    .toList();
        }
    }

    @Command
    @NotifyChange({"editing", "editingCustomer", "newRecord"})
    public void openNew() {
        editingCustomer = new CustomerItem();
        editing = true;
        newRecord = true;
    }

    @Command
    @NotifyChange({"editing", "editingCustomer", "newRecord"})
    public void edit(@BindingParam("customer") CustomerItem c) {
        editingCustomer = new CustomerItem(c.getId(), c.getName(), c.getEmail(), c.getPhone());
        editing = true;
        newRecord = false;
    }

    @Command
    @NotifyChange({"customers", "editing", "editingCustomer"})
    public void save() {
        if (editingCustomer.getName() == null || editingCustomer.getName().isBlank()) {
            Clients.showNotification("El nombre es obligatorio", "warning", null, "middle_center", 2000);
            return;
        }

        TenantContext.setCurrentTenant(user.getTenantId());
        if (newRecord) {
            customerService.create(new CreateCustomerRequest(
                    editingCustomer.getName(),
                    editingCustomer.getEmail(),
                    editingCustomer.getPhone(),
                    null));
            Clients.showNotification("Cliente creado", "info", null, "end_center", 1500);
        } else {
            customerService.update(
                    UUID.fromString(editingCustomer.getId()),
                    new UpdateCustomerRequest(
                            editingCustomer.getName(),
                            editingCustomer.getEmail(),
                            editingCustomer.getPhone(),
                            null, null));
            Clients.showNotification("Cliente actualizado", "info", null, "end_center", 1500);
        }

        loadData();
        editing = false;
        editingCustomer = null;
    }

    @Command
    @NotifyChange({"editing", "editingCustomer"})
    public void cancelEdit() {
        editing = false;
        editingCustomer = null;
    }

    @Command
    @NotifyChange("customers")
    public void delete(@BindingParam("customer") CustomerItem c) {
        TenantContext.setCurrentTenant(user.getTenantId());
        customerService.delete(UUID.fromString(c.getId()));
        loadData();
        Clients.showNotification("Cliente eliminado", "info", null, "end_center", 1500);
    }

    // ── Getters / Setters ──

    public String getFormTitle() {
        return newRecord ? "Nuevo Cliente" : "Editar Cliente";
    }

    public List<CustomerItem> getCustomers() { return customers; }
    public CustomerItem getSelectedCustomer() { return selectedCustomer; }
    public void setSelectedCustomer(CustomerItem c) { this.selectedCustomer = c; }
    public CustomerItem getEditingCustomer() { return editingCustomer; }
    public void setEditingCustomer(CustomerItem c) { this.editingCustomer = c; }
    public String getSearchTerm() { return searchTerm; }
    public void setSearchTerm(String searchTerm) { this.searchTerm = searchTerm; }
    public boolean isEditing() { return editing; }
    public boolean isNewRecord() { return newRecord; }
}

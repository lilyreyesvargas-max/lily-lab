package com.lreyes.platform.ui.zk.vm;

import com.lreyes.platform.core.catalogs.Catalog;
import com.lreyes.platform.core.catalogs.CatalogService;
import com.lreyes.platform.core.tenancy.TenantContext;
import com.lreyes.platform.ui.zk.model.CatalogItem;
import com.lreyes.platform.ui.zk.model.UiUser;
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
public class CatalogListVM {

    private CatalogService catalogService;
    private UiUser user;

    private List<CatalogItem> catalogs = new ArrayList<>();
    private List<CatalogItem> allCatalogs = new ArrayList<>();
    private CatalogItem selectedCatalog;
    private CatalogItem editingCatalog;
    private List<String> catalogTypes = new ArrayList<>();
    private String selectedType;
    private String searchTerm;
    private boolean editing;
    private boolean newRecord;

    @Init
    public void init() {
        user = (UiUser) Sessions.getCurrent().getAttribute("user");
        catalogService = SpringUtil.getApplicationContext().getBean(CatalogService.class);
        loadTypes();
        loadData();
    }

    private void loadTypes() {
        TenantContext.setCurrentTenant(user.getTenantId());
        catalogTypes = catalogService.findDistinctTypes();
    }

    private void loadData() {
        TenantContext.setCurrentTenant(user.getTenantId());
        List<Catalog> entities;
        if (selectedType != null && !selectedType.isBlank()) {
            entities = catalogService.findByType(selectedType);
        } else {
            entities = catalogService.findAll();
        }
        allCatalogs = new ArrayList<>();
        for (Catalog c : entities) {
            CatalogItem item = new CatalogItem();
            item.setId(c.getId().toString());
            item.setType(c.getType());
            item.setCode(c.getCode());
            item.setName(c.getName());
            item.setDescription(c.getDescription());
            item.setActive(c.isActive());
            item.setSortOrder(c.getSortOrder());
            if (c.getParent() != null) {
                item.setParentId(c.getParent().getId().toString());
                item.setParentName(c.getParent().getName());
            }
            allCatalogs.add(item);
        }
        catalogs = new ArrayList<>(allCatalogs);
    }

    @Command
    @NotifyChange({"catalogs", "allCatalogs"})
    public void filterByType() {
        loadData();
    }

    @Command
    @NotifyChange("catalogs")
    public void search() {
        if (searchTerm == null || searchTerm.isBlank()) {
            catalogs = new ArrayList<>(allCatalogs);
        } else {
            String term = searchTerm.toLowerCase();
            catalogs = allCatalogs.stream()
                    .filter(c -> c.getName().toLowerCase().contains(term)
                            || c.getCode().toLowerCase().contains(term))
                    .toList();
        }
    }

    @Command
    @NotifyChange({"editing", "editingCatalog", "newRecord"})
    public void openNew() {
        editingCatalog = new CatalogItem();
        if (selectedType != null && !selectedType.isBlank()) {
            editingCatalog.setType(selectedType);
        }
        editing = true;
        newRecord = true;
    }

    @Command
    @NotifyChange({"editing", "editingCatalog", "newRecord"})
    public void edit(@BindingParam("catalog") CatalogItem c) {
        editingCatalog = new CatalogItem();
        editingCatalog.setId(c.getId());
        editingCatalog.setType(c.getType());
        editingCatalog.setCode(c.getCode());
        editingCatalog.setName(c.getName());
        editingCatalog.setDescription(c.getDescription());
        editingCatalog.setActive(c.isActive());
        editingCatalog.setSortOrder(c.getSortOrder());
        editingCatalog.setParentId(c.getParentId());
        editing = true;
        newRecord = false;
    }

    @Command
    @NotifyChange({"catalogs", "editing", "editingCatalog", "catalogTypes"})
    public void save() {
        if (editingCatalog.getType() == null || editingCatalog.getType().isBlank()) {
            Clients.showNotification("El tipo es obligatorio", "warning", null, "middle_center", 2000);
            return;
        }
        if (editingCatalog.getCode() == null || editingCatalog.getCode().isBlank()) {
            Clients.showNotification("El código es obligatorio", "warning", null, "middle_center", 2000);
            return;
        }
        if (editingCatalog.getName() == null || editingCatalog.getName().isBlank()) {
            Clients.showNotification("El nombre es obligatorio", "warning", null, "middle_center", 2000);
            return;
        }

        UUID parentId = editingCatalog.getParentId() != null && !editingCatalog.getParentId().isBlank()
                ? UUID.fromString(editingCatalog.getParentId()) : null;

        TenantContext.setCurrentTenant(user.getTenantId());
        if (newRecord) {
            Catalog catalog = new Catalog();
            catalog.setType(editingCatalog.getType());
            catalog.setCode(editingCatalog.getCode());
            catalog.setName(editingCatalog.getName());
            catalog.setDescription(editingCatalog.getDescription());
            catalog.setActive(editingCatalog.isActive());
            catalog.setSortOrder(editingCatalog.getSortOrder());
            catalogService.create(catalog, parentId);
            Clients.showNotification("Catálogo creado", "info", null, "end_center", 1500);
        } else {
            catalogService.update(
                    UUID.fromString(editingCatalog.getId()),
                    editingCatalog.getType(),
                    editingCatalog.getCode(),
                    editingCatalog.getName(),
                    editingCatalog.getDescription(),
                    editingCatalog.isActive(),
                    editingCatalog.getSortOrder(),
                    parentId);
            Clients.showNotification("Catálogo actualizado", "info", null, "end_center", 1500);
        }

        loadTypes();
        loadData();
        editing = false;
        editingCatalog = null;
    }

    @Command
    @NotifyChange({"editing", "editingCatalog"})
    public void cancelEdit() {
        editing = false;
        editingCatalog = null;
    }

    @Command
    @NotifyChange({"catalogs", "catalogTypes"})
    public void delete(@BindingParam("catalog") CatalogItem c) {
        TenantContext.setCurrentTenant(user.getTenantId());
        catalogService.delete(UUID.fromString(c.getId()));
        loadTypes();
        loadData();
        Clients.showNotification("Catálogo eliminado", "info", null, "end_center", 1500);
    }

    // ── Getters / Setters ──

    public String getFormTitle() {
        return newRecord ? "Nuevo Catálogo" : "Editar Catálogo";
    }

    public List<CatalogItem> getCatalogs() { return catalogs; }
    public CatalogItem getSelectedCatalog() { return selectedCatalog; }
    public void setSelectedCatalog(CatalogItem selectedCatalog) { this.selectedCatalog = selectedCatalog; }
    public CatalogItem getEditingCatalog() { return editingCatalog; }
    public void setEditingCatalog(CatalogItem editingCatalog) { this.editingCatalog = editingCatalog; }
    public List<String> getCatalogTypes() { return catalogTypes; }
    public String getSelectedType() { return selectedType; }
    public void setSelectedType(String selectedType) { this.selectedType = selectedType; }
    public String getSearchTerm() { return searchTerm; }
    public void setSearchTerm(String searchTerm) { this.searchTerm = searchTerm; }
    public boolean isEditing() { return editing; }
    public boolean isNewRecord() { return newRecord; }
}

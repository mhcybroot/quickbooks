package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.domain.CompanyStatus;
import com.example.quickbooksimporter.domain.QboEnvironment;
import com.example.quickbooksimporter.persistence.CompanyEntity;
import com.example.quickbooksimporter.service.CompanyQboCredentialsService;
import com.example.quickbooksimporter.service.CompanyAdminService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "admin/companies", layout = MainLayout.class)
@PageTitle("Company Admin")
@RolesAllowed("PLATFORM_ADMIN")
public class CompanyAdminView extends VerticalLayout {

    private final Grid<CompanyEntity> grid = new Grid<>(CompanyEntity.class, false);

    public CompanyAdminView(CompanyAdminService companyAdminService,
                            CompanyQboCredentialsService companyQboCredentialsService) {
        addClassName("corp-page");
        add(new H2("Company Management"));

        TextField name = new TextField("Name");
        TextField code = new TextField("Code");
        Button create = new Button("Create", event -> {
            try {
                companyAdminService.create(name.getValue(), code.getValue());
                name.clear();
                code.clear();
                refresh(companyAdminService);
            } catch (Exception exception) {
                Notification.show(exception.getMessage());
            }
        });

        grid.addColumn(CompanyEntity::getId).setHeader("ID").setAutoWidth(true);
        grid.addColumn(CompanyEntity::getName).setHeader("Name").setAutoWidth(true);
        grid.addColumn(CompanyEntity::getCode).setHeader("Code").setAutoWidth(true);
        grid.addColumn(entity -> entity.getStatus().name()).setHeader("Status").setAutoWidth(true);
        grid.addComponentColumn(entity -> new Button("Archive", event -> {
            try {
                companyAdminService.update(entity.getId(), entity.getName(), entity.getCode(), CompanyStatus.ARCHIVED);
                refresh(companyAdminService);
            } catch (Exception exception) {
                Notification.show(exception.getMessage());
            }
        })).setHeader("Actions");
        grid.setWidthFull();
        grid.setHeight("460px");

        TextField selectedCompanyId = new TextField("Company ID");
        selectedCompanyId.setReadOnly(true);
        TextField qboClientId = new TextField("QBO Client ID");
        PasswordField qboClientSecret = new PasswordField("QBO Client Secret");
        qboClientSecret.setPlaceholder("Enter to set/rotate secret");
        TextField qboRedirectUri = new TextField("Redirect URI Override (optional)");
        ComboBox<QboEnvironment> qboEnvironment = new ComboBox<>("QBO Environment");
        qboEnvironment.setItems(QboEnvironment.values());
        qboEnvironment.setValue(QboEnvironment.SANDBOX);
        Checkbox qboActive = new Checkbox("Company credentials active", true);
        Button saveQboCredentials = new Button("Save QBO Credentials", event -> {
            try {
                if (selectedCompanyId.getValue() == null || selectedCompanyId.getValue().isBlank()) {
                    Notification.show("Select a company row first.");
                    return;
                }
                Long companyId = Long.valueOf(selectedCompanyId.getValue());
                companyQboCredentialsService.upsertForCompany(
                        companyId,
                        qboClientId.getValue(),
                        qboClientSecret.getValue(),
                        qboRedirectUri.getValue(),
                        qboEnvironment.getValue(),
                        qboActive.getValue());
                qboClientSecret.clear();
                Notification.show("Saved company QuickBooks credentials.");
            } catch (Exception exception) {
                Notification.show(exception.getMessage());
            }
        });
        Button disableQboCredentials = new Button("Disable Company Credentials", event -> {
            try {
                if (selectedCompanyId.getValue() == null || selectedCompanyId.getValue().isBlank()) {
                    Notification.show("Select a company row first.");
                    return;
                }
                Long companyId = Long.valueOf(selectedCompanyId.getValue());
                companyQboCredentialsService.disableForCompany(companyId);
                qboActive.setValue(false);
                Notification.show("Company credentials disabled. Global fallback will be used.");
            } catch (Exception exception) {
                Notification.show(exception.getMessage());
            }
        });
        Button connectSelectedCompany = new Button("Connect Selected Company", event -> {
            if (selectedCompanyId.getValue() == null || selectedCompanyId.getValue().isBlank()) {
                Notification.show("Select a company row first.");
                return;
            }
            getUI().ifPresent(ui -> ui.getPage().setLocation("/oauth/quickbooks/connect?companyId=" + selectedCompanyId.getValue()));
        });

        grid.asSingleSelect().addValueChangeListener(event -> {
            CompanyEntity selected = event.getValue();
            if (selected == null) {
                return;
            }
            selectedCompanyId.setValue(String.valueOf(selected.getId()));
            companyQboCredentialsService.findByCompanyId(selected.getId()).ifPresentOrElse(creds -> {
                qboClientId.setValue(creds.getClientId());
                qboRedirectUri.setValue(creds.getRedirectUriOverride() == null ? "" : creds.getRedirectUriOverride());
                qboEnvironment.setValue(creds.getQboEnvironment());
                qboActive.setValue(creds.isActive());
            }, () -> {
                qboClientId.clear();
                qboRedirectUri.clear();
                qboEnvironment.setValue(QboEnvironment.SANDBOX);
                qboActive.setValue(true);
            });
            qboClientSecret.clear();
        });

        VerticalLayout credentialsCard = new VerticalLayout(
                new H3("QuickBooks App Credentials (Per Company)"),
                new Paragraph("Client secret is write-only. Leave blank if you do not want to rotate it."),
                new HorizontalLayout(selectedCompanyId, qboClientId, qboClientSecret),
                new HorizontalLayout(qboRedirectUri, qboEnvironment, qboActive),
                new HorizontalLayout(saveQboCredentials, disableQboCredentials, connectSelectedCompany));
        credentialsCard.addClassName("corp-card");

        add(new HorizontalLayout(name, code, create), grid, credentialsCard);
        refresh(companyAdminService);
    }

    private void refresh(CompanyAdminService companyAdminService) {
        grid.setItems(companyAdminService.listAll());
    }
}

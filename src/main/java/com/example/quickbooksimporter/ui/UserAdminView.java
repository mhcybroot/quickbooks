package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.domain.PlatformRole;
import com.example.quickbooksimporter.persistence.AppUserEntity;
import com.example.quickbooksimporter.service.UserAdminService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "admin/users", layout = MainLayout.class)
@PageTitle("User Admin")
@RolesAllowed("PLATFORM_ADMIN")
public class UserAdminView extends VerticalLayout {

    private final Grid<AppUserEntity> grid = new Grid<>(AppUserEntity.class, false);

    public UserAdminView(UserAdminService userAdminService) {
        addClassName("corp-page");
        add(new H2("User Management"));

        TextField username = new TextField("Username");
        PasswordField password = new PasswordField("Password");
        ComboBox<PlatformRole> role = new ComboBox<>("Role");
        role.setItems(PlatformRole.values());
        role.setValue(PlatformRole.USER);

        Button create = new Button("Create", event -> {
            try {
                userAdminService.createUser(username.getValue(), password.getValue(), role.getValue());
                username.clear();
                password.clear();
                refresh(userAdminService);
            } catch (Exception exception) {
                Notification.show(exception.getMessage());
            }
        });

        grid.addColumn(AppUserEntity::getId).setHeader("ID").setAutoWidth(true);
        grid.addColumn(AppUserEntity::getUsername).setHeader("Username").setAutoWidth(true);
        grid.addColumn(entity -> entity.getPlatformRole().name()).setHeader("Platform Role").setAutoWidth(true);
        grid.addColumn(AppUserEntity::isActive).setHeader("Active").setAutoWidth(true);
        grid.addComponentColumn(entity -> new Button(entity.isActive() ? "Deactivate" : "Activate", event -> {
            try {
                userAdminService.updateUser(entity.getId(), entity.getPlatformRole(), !entity.isActive(), null);
                refresh(userAdminService);
            } catch (Exception exception) {
                Notification.show(exception.getMessage());
            }
        })).setHeader("Actions");
        grid.setWidthFull();
        grid.setHeight("460px");

        add(new HorizontalLayout(username, password, role, create), grid);
        refresh(userAdminService);
    }

    private void refresh(UserAdminService userAdminService) {
        grid.setItems(userAdminService.listUsers());
    }
}

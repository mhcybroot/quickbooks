package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.domain.CompanyRole;
import com.example.quickbooksimporter.domain.PlatformRole;
import com.example.quickbooksimporter.persistence.AppUserEntity;
import com.example.quickbooksimporter.persistence.CompanyEntity;
import com.example.quickbooksimporter.persistence.CompanyMembershipEntity;
import com.example.quickbooksimporter.repository.CompanyMembershipRepository;
import com.example.quickbooksimporter.repository.CompanyRepository;
import com.example.quickbooksimporter.service.UserAdminService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import java.util.List;

@Route(value = "admin/users", layout = MainLayout.class)
@PageTitle("User Admin")
@RolesAllowed("PLATFORM_ADMIN")
public class UserAdminView extends VerticalLayout {

    private final Grid<AppUserEntity> grid = new Grid<>(AppUserEntity.class, false);
    private final Grid<CompanyMembershipEntity> membershipGrid = new Grid<>(CompanyMembershipEntity.class, false);

    public UserAdminView(UserAdminService userAdminService,
                         CompanyRepository companyRepository,
                         CompanyMembershipRepository membershipRepository) {
        addClassName("corp-page");
        add(new H2("User Management"));

        TextField username = new TextField("Username");
        PasswordField password = new PasswordField("Initial Password");
        ComboBox<PlatformRole> role = new ComboBox<>("Platform Role");
        role.setItems(PlatformRole.values());
        role.setValue(PlatformRole.USER);

        ComboBox<CompanyEntity> createCompany = new ComboBox<>("Company");
        createCompany.setItems(companyRepository.findByStatusOrderByNameAsc(com.example.quickbooksimporter.domain.CompanyStatus.ACTIVE));
        createCompany.setItemLabelGenerator(company -> company.getName() + " (" + company.getCode() + ")");
        ComboBox<CompanyRole> createCompanyRole = new ComboBox<>("Company Role");
        createCompanyRole.setItems(CompanyRole.values());
        createCompanyRole.setValue(CompanyRole.OPERATOR);
        Checkbox noMembershipForAdmin = new Checkbox("Allow no membership (platform admin only)", false);

        Button create = new Button("Create User", event -> {
            try {
                List<UserAdminService.MembershipAssignment> memberships = List.of();
                if (createCompany.getValue() != null && createCompanyRole.getValue() != null) {
                    memberships = List.of(new UserAdminService.MembershipAssignment(createCompany.getValue().getId(), createCompanyRole.getValue()));
                }
                if (memberships.isEmpty() && role.getValue() != PlatformRole.PLATFORM_ADMIN) {
                    Notification.show("Select at least one company membership.");
                    return;
                }
                if (memberships.isEmpty() && role.getValue() == PlatformRole.PLATFORM_ADMIN && !noMembershipForAdmin.getValue()) {
                    Notification.show("Platform admin without membership requires explicit confirmation checkbox.");
                    return;
                }
                userAdminService.createUser(username.getValue(), password.getValue(), role.getValue(), memberships);
                username.clear();
                password.clear();
                createCompany.clear();
                refresh(userAdminService);
            } catch (Exception exception) {
                Notification.show(exception.getMessage());
            }
        });

        grid.addColumn(AppUserEntity::getId).setHeader("ID").setAutoWidth(true);
        grid.addColumn(AppUserEntity::getUsername).setHeader("Username").setAutoWidth(true);
        grid.addColumn(entity -> entity.getPlatformRole().name()).setHeader("Platform Role").setAutoWidth(true);
        grid.addColumn(AppUserEntity::isActive).setHeader("Active").setAutoWidth(true);
        grid.addColumn(AppUserEntity::isBlocked).setHeader("Blocked").setAutoWidth(true);
        grid.addColumn(AppUserEntity::isMustChangePassword).setHeader("Must Change Password").setAutoWidth(true);
        grid.addComponentColumn(entity -> {
            Button toggle = new Button(entity.isBlocked() ? "Unblock" : "Block", event -> {
                try {
                    if (entity.isBlocked()) {
                        userAdminService.unblockUser(entity.getId());
                    } else {
                        userAdminService.blockUser(entity.getId(), "Blocked by platform admin");
                    }
                    refresh(userAdminService);
                    refreshMemberships(membershipRepository, entity.getId());
                } catch (Exception exception) {
                    Notification.show(exception.getMessage());
                }
            });
            return toggle;
        }).setHeader("Block");
        grid.addComponentColumn(entity -> {
            Button reset = new Button("Reset Password", event -> openResetDialog(userAdminService, entity));
            return reset;
        }).setHeader("Password");
        grid.addComponentColumn(entity -> {
            Button softDelete = new Button(entity.isActive() ? "Soft Delete" : "Reactivate", event -> {
                try {
                    if (entity.isActive()) {
                        userAdminService.softDeleteUser(entity.getId());
                    } else {
                        userAdminService.updateUser(entity.getId(), entity.getPlatformRole(), true);
                    }
                    refresh(userAdminService);
                    refreshMemberships(membershipRepository, entity.getId());
                } catch (Exception exception) {
                    Notification.show(exception.getMessage());
                }
            });
            return softDelete;
        }).setHeader("Lifecycle");
        grid.setWidthFull();
        grid.setHeight("360px");

        membershipGrid.addColumn(membership -> membership.getCompany().getName()).setHeader("Company").setAutoWidth(true);
        membershipGrid.addColumn(membership -> membership.getRole().name()).setHeader("Role").setAutoWidth(true);
        membershipGrid.setWidthFull();
        membershipGrid.setHeight("220px");

        ComboBox<CompanyEntity> membershipCompany = new ComboBox<>("Company");
        membershipCompany.setItems(companyRepository.findByStatusOrderByNameAsc(com.example.quickbooksimporter.domain.CompanyStatus.ACTIVE));
        membershipCompany.setItemLabelGenerator(company -> company.getName() + " (" + company.getCode() + ")");
        ComboBox<CompanyRole> membershipRole = new ComboBox<>("Role");
        membershipRole.setItems(CompanyRole.values());
        membershipRole.setValue(CompanyRole.OPERATOR);
        Button addMembership = new Button("Add/Update Membership", event -> {
            AppUserEntity selected = grid.asSingleSelect().getValue();
            if (selected == null) {
                Notification.show("Select a user first.");
                return;
            }
            if (membershipCompany.getValue() == null || membershipRole.getValue() == null) {
                Notification.show("Select company and role.");
                return;
            }
            try {
                userAdminService.setMembership(selected.getId(), membershipCompany.getValue().getId(), membershipRole.getValue());
                refreshMemberships(membershipRepository, selected.getId());
            } catch (Exception exception) {
                Notification.show(exception.getMessage());
            }
        });
        Button removeMembership = new Button("Remove Selected Membership", event -> {
            AppUserEntity selectedUser = grid.asSingleSelect().getValue();
            CompanyMembershipEntity selectedMembership = membershipGrid.asSingleSelect().getValue();
            if (selectedUser == null || selectedMembership == null) {
                Notification.show("Select user and membership row.");
                return;
            }
            try {
                userAdminService.removeMembership(selectedUser.getId(), selectedMembership.getCompany().getId());
                refreshMemberships(membershipRepository, selectedUser.getId());
            } catch (Exception exception) {
                Notification.show(exception.getMessage());
            }
        });

        grid.asSingleSelect().addValueChangeListener(event -> {
            AppUserEntity selected = event.getValue();
            if (selected == null) {
                membershipGrid.setItems(List.of());
                return;
            }
            refreshMemberships(membershipRepository, selected.getId());
        });

        add(new HorizontalLayout(username, password, role, createCompany, createCompanyRole, noMembershipForAdmin, create),
                grid,
                new H3("Membership Management"),
                membershipGrid,
                new HorizontalLayout(membershipCompany, membershipRole, addMembership, removeMembership));
        refresh(userAdminService);
    }

    private void openResetDialog(UserAdminService userAdminService, AppUserEntity user) {
        PasswordField tempPassword = new PasswordField("Temporary Password");
        Button apply = new Button("Apply Reset", event -> {
            try {
                userAdminService.adminResetPassword(user.getId(), tempPassword.getValue());
                Notification.show("Temporary password set. User must change it at next login.");
                event.getSource().getUI().ifPresent(ui -> ui.getPage().reload());
            } catch (Exception exception) {
                Notification.show(exception.getMessage());
            }
        });
        com.vaadin.flow.component.dialog.Dialog dialog = new com.vaadin.flow.component.dialog.Dialog(
                new VerticalLayout(new H3("Reset Password for " + user.getUsername()), tempPassword, apply));
        dialog.open();
    }

    private void refresh(UserAdminService userAdminService) {
        grid.setItems(userAdminService.listUsers());
    }

    private void refreshMemberships(CompanyMembershipRepository membershipRepository, Long userId) {
        membershipGrid.setItems(membershipRepository.findByUserId(userId));
    }
}

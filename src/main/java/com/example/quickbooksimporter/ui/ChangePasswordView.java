package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.service.UserAdminService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route("change-password")
@PageTitle("Change Password")
@PermitAll
public class ChangePasswordView extends VerticalLayout {

    public ChangePasswordView(UserAdminService userAdminService) {
        addClassName("corp-page");
        add(new H2("Change Your Password"));
        add(new Paragraph("Your administrator reset your password. You must set a new password before continuing."));

        PasswordField currentPassword = new PasswordField("Current / Temporary Password");
        PasswordField newPassword = new PasswordField("New Password");
        PasswordField confirmPassword = new PasswordField("Confirm New Password");

        Button save = new Button("Update Password", event -> {
            try {
                if (!newPassword.getValue().equals(confirmPassword.getValue())) {
                    Notification.show("New password and confirmation do not match.");
                    return;
                }
                userAdminService.changeOwnPassword(currentPassword.getValue(), newPassword.getValue());
                Notification.show("Password updated successfully.");
                getUI().ifPresent(ui -> ui.navigate(""));
            } catch (Exception exception) {
                Notification.show(exception.getMessage());
            }
        });

        add(new VerticalLayout(currentPassword, newPassword, confirmPassword, new HorizontalLayout(save)));
    }
}

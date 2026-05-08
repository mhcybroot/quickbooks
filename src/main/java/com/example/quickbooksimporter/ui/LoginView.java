package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.service.LegalUrlService;
import com.example.quickbooksimporter.ui.components.LegalLinks;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

@Route("login")
@PageTitle("Login | QuickBooks Importer")
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final LoginForm login = new LoginForm();

    public LoginView(LegalUrlService legalUrlService) {
        setSizeFull();
        addClassName("login-shell");
        login.setAction("login");

        VerticalLayout intro = new VerticalLayout();
        intro.setPadding(false);
        intro.setSpacing(true);
        Span eyebrow = new Span("CORPORATE EDITION");
        eyebrow.addClassNames("corp-badge", "corp-badge-gold");
        H2 title = new H2("QuickBooks Invoice Import Command Center");
        title.addClassNames(LumoUtility.Margin.NONE);
        Paragraph body = new Paragraph("Securely upload, validate, and sync invoices with executive-grade visibility.");
        body.addClassName("corp-muted");
        intro.add(eyebrow, title, body);
        intro.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        Paragraph legalNotice = new Paragraph("By signing in or connecting QuickBooks, you agree to the End-User License Agreement and Privacy Policy.");
        legalNotice.addClassName("corp-muted");

        VerticalLayout formWrap = new VerticalLayout(login, legalNotice, LegalLinks.inline(legalUrlService));
        formWrap.setPadding(false);
        formWrap.setSpacing(true);
        formWrap.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        formWrap.setAlignItems(FlexComponent.Alignment.CENTER);
        login.getStyle().set("max-width", "420px");

        HorizontalLayout card = new HorizontalLayout(intro, formWrap);
        card.setWidthFull();
        card.setSpacing(true);
        card.addClassNames("login-card", "corp-card");
        card.setAlignItems(FlexComponent.Alignment.STRETCH);
        card.expand(intro, formWrap);
        add(card);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (event.getLocation().getQueryParameters().getParameters().containsKey("error")) {
            login.setError(true);
        }
    }
}

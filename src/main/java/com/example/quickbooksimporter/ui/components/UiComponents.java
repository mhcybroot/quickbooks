package com.example.quickbooksimporter.ui.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.theme.lumo.LumoUtility;
import java.util.Arrays;

public final class UiComponents {

    private UiComponents() {
    }

    public static VerticalLayout card(Component... children) {
        VerticalLayout layout = new VerticalLayout(children);
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.addClassName("corp-card");
        return layout;
    }

    public static VerticalLayout softCard(Component... children) {
        VerticalLayout layout = new VerticalLayout(children);
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.addClassName("corp-card-soft");
        return layout;
    }

    public static Component kpi(String label, String value, String helper) {
        Span heading = new Span(label);
        heading.addClassNames("corp-muted", LumoUtility.FontSize.SMALL);
        Span amount = new Span(value);
        amount.addClassName("corp-kpi-value");
        Paragraph note = new Paragraph(helper);
        note.addClassNames("corp-muted", LumoUtility.Margin.NONE);
        return card(heading, amount, note);
    }

    public static H4 sectionTitle(String text) {
        H4 title = new H4(text);
        title.addClassNames(LumoUtility.Margin.NONE);
        return title;
    }

    public static Span badge(String text, String variantClass) {
        Span badge = new Span(text);
        badge.addClassNames("corp-badge", variantClass);
        return badge;
    }

    public static HorizontalLayout importStepper(String activeStep) {
        HorizontalLayout layout = new HorizontalLayout();
        layout.addClassName("corp-stepper");
        layout.setWidthFull();
        Arrays.asList("Upload", "Map", "Validate", "Review", "Run").forEach(step -> {
            Span badge = new Span(step);
            badge.addClassNames("corp-step", step.equals(activeStep) ? "corp-step-active" : "corp-step-idle");
            layout.add(badge);
        });
        return layout;
    }
}

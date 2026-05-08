package com.example.quickbooksimporter.ui.components;

import com.example.quickbooksimporter.service.LegalUrlService;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

public final class LegalLinks {

    private LegalLinks() {
    }

    public static HorizontalLayout inline(LegalUrlService legalUrlService) {
        Anchor eula = external(legalUrlService.eulaUrl(), "EULA");
        Anchor privacy = external(legalUrlService.privacyUrl(), "Privacy Policy");
        HorizontalLayout links = new HorizontalLayout(eula, new Span("|"), privacy);
        links.setSpacing(true);
        links.addClassName("legal-links");
        return links;
    }

    private static Anchor external(String href, String text) {
        Anchor anchor = new Anchor(href, text);
        anchor.setTarget("_blank");
        anchor.addClassName("legal-inline-link");
        return anchor;
    }
}

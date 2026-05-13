package com.example.quickbooksimporter.service;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;

@Controller
public class QuickBooksOAuthController {

    private static final String OAUTH_STATE = "qb.oauth.state";
    private static final String OAUTH_COMPANY_ID = "qb.oauth.company.id";

    private final QuickBooksConnectionService connectionService;

    public QuickBooksOAuthController(QuickBooksConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping("/oauth/quickbooks/connect")
    public RedirectView connect(@RequestParam("companyId") Long companyId, HttpSession session) {
        String state = UUID.randomUUID().toString();
        session.setAttribute(OAUTH_STATE, state);
        session.setAttribute(OAUTH_COMPANY_ID, companyId);
        return new RedirectView(connectionService.buildAuthorizationUrl(state, companyId));
    }

    @GetMapping("/oauth/quickbooks/callback")
    public RedirectView callback(@RequestParam("code") String code,
                                 @RequestParam("realmId") String realmId,
                                 @RequestParam("state") String state,
                                 HttpSession session) {
        String expectedState = (String) session.getAttribute(OAUTH_STATE);
        if (expectedState == null || !expectedState.equals(state)) {
            throw new IllegalStateException("QuickBooks OAuth state validation failed");
        }
        Long companyId = (Long) session.getAttribute(OAUTH_COMPANY_ID);
        session.removeAttribute(OAUTH_STATE);
        session.removeAttribute(OAUTH_COMPANY_ID);
        if (companyId == null) {
            throw new IllegalStateException("QuickBooks OAuth company context is missing");
        }
        connectionService.handleAuthorizationCallback(code, realmId, companyId);
        return new RedirectView("/settings");
    }
}

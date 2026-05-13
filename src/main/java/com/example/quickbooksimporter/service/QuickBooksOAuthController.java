package com.example.quickbooksimporter.service;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class QuickBooksOAuthController {
    private static final Logger log = LoggerFactory.getLogger(QuickBooksOAuthController.class);

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
        log.info("QBO connect start: companyId={}, state={}", companyId, state);
        return new RedirectView(connectionService.buildAuthorizationUrl(state, companyId));
    }

    @GetMapping("/oauth/quickbooks/callback")
    public RedirectView callback(@RequestParam("code") String code,
                                 @RequestParam("realmId") String realmId,
                                 @RequestParam("state") String state,
                                 HttpSession session) {
        String expectedState = (String) session.getAttribute(OAUTH_STATE);
        log.info("QBO callback received: realmId={}, state={}", realmId, state);
        if (expectedState == null || !expectedState.equals(state)) {
            log.warn("QBO callback state mismatch: expected={}, actual={}", expectedState, state);
            throw new IllegalStateException("QuickBooks OAuth state validation failed");
        }
        Long companyId = (Long) session.getAttribute(OAUTH_COMPANY_ID);
        session.removeAttribute(OAUTH_STATE);
        session.removeAttribute(OAUTH_COMPANY_ID);
        if (companyId == null) {
            log.error("QBO callback missing company context for realmId={}", realmId);
            throw new IllegalStateException("QuickBooks OAuth company context is missing");
        }
        log.info("QBO callback validated: companyId={}, realmId={}", companyId, realmId);
        connectionService.handleAuthorizationCallback(code, realmId, companyId);
        return new RedirectView("/settings");
    }
}

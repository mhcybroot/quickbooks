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

    private final QuickBooksConnectionService connectionService;

    public QuickBooksOAuthController(QuickBooksConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping("/oauth/quickbooks/connect")
    public RedirectView connect(HttpSession session) {
        String state = UUID.randomUUID().toString();
        session.setAttribute(OAUTH_STATE, state);
        return new RedirectView(connectionService.buildAuthorizationUrl(state));
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
        session.removeAttribute(OAUTH_STATE);
        connectionService.handleAuthorizationCallback(code, realmId);
        return new RedirectView("/settings");
    }
}

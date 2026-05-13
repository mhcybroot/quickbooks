package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.persistence.AppUserEntity;
import com.example.quickbooksimporter.repository.AppUserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final AppUserRepository appUserRepository;

    public CurrentUserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    public String requireUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new IllegalStateException("Authenticated user not found");
        }
        return authentication.getName();
    }

    public AppUserEntity requireUser() {
        String username = requireUsername();
        return appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User record not found: " + username));
    }
}

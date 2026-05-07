package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.config.QuickBooksProperties;
import com.example.quickbooksimporter.persistence.QboConnectionEntity;
import com.example.quickbooksimporter.repository.QboConnectionRepository;
import com.fasterxml.jackson.annotation.JsonAlias;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Service
public class QuickBooksConnectionService {

    private final QuickBooksProperties properties;
    private final QboConnectionRepository repository;
    private final RestClient restClient;

    public QuickBooksConnectionService(QuickBooksProperties properties,
                                       QboConnectionRepository repository,
                                       RestClient restClient) {
        this.properties = properties;
        this.repository = repository;
        this.restClient = restClient;
    }

    public String buildAuthorizationUrl(String state) {
        String scope = String.join(" ", properties.scopes());
        return properties.authorizationUrl()
                + "?client_id=" + encode(properties.clientId())
                + "&redirect_uri=" + encode(properties.redirectUri())
                + "&response_type=code"
                + "&scope=" + encode(scope)
                + "&state=" + encode(state);
    }

    public Optional<QboConnectionEntity> getConnection() {
        return repository.findAll().stream().findFirst();
    }

    public QuickBooksConnectionStatus getStatus() {
        return getConnection()
                .map(connection -> new QuickBooksConnectionStatus(
                        true,
                        connection.getRealmId(),
                        connection.getConnectedAt(),
                        connection.getExpiresAt(),
                        properties.environment()))
                .orElse(new QuickBooksConnectionStatus(false, null, null, null, properties.environment()));
    }

    @Transactional
    public void handleAuthorizationCallback(String code, String realmId) {
        TokenResponse response = exchangeToken(code);
        QboConnectionEntity entity = repository.findByRealmId(realmId).orElseGet(QboConnectionEntity::new);
        Instant now = Instant.now();
        entity.setRealmId(realmId);
        entity.setAccessToken(response.accessToken());
        entity.setRefreshToken(response.refreshToken());
        entity.setTokenType(response.tokenType());
        entity.setExpiresAt(now.plusSeconds(response.expiresIn()));
        entity.setRefreshExpiresAt(response.xRefreshTokenExpiresIn() == null ? null : now.plusSeconds(response.xRefreshTokenExpiresIn()));
        entity.setConnectedAt(entity.getConnectedAt() == null ? now : entity.getConnectedAt());
        entity.setUpdatedAt(now);
        repository.save(entity);
    }

    @Transactional
    public QboConnectionEntity getActiveConnection() {
        QboConnectionEntity connection = getConnection()
                .orElseThrow(() -> new IllegalStateException("QuickBooks is not connected"));
        if (connection.getExpiresAt().isBefore(Instant.now().plusSeconds(60))) {
            TokenResponse response = refreshToken(connection.getRefreshToken());
            Instant now = Instant.now();
            connection.setAccessToken(response.accessToken());
            connection.setRefreshToken(response.refreshToken());
            connection.setTokenType(response.tokenType());
            connection.setExpiresAt(now.plusSeconds(response.expiresIn()));
            connection.setRefreshExpiresAt(response.xRefreshTokenExpiresIn() == null ? null : now.plusSeconds(response.xRefreshTokenExpiresIn()));
            connection.setUpdatedAt(now);
            repository.save(connection);
        }
        return connection;
    }

    private TokenResponse exchangeToken(String code) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.put("grant_type", List.of("authorization_code"));
        body.put("code", List.of(code));
        body.put("redirect_uri", List.of(properties.redirectUri()));
        return restClient.post()
                .uri(URI.create(properties.tokenUrl()))
                .header(HttpHeaders.AUTHORIZATION, basicAuth())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(TokenResponse.class);
    }

    private TokenResponse refreshToken(String refreshToken) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.put("grant_type", List.of("refresh_token"));
        body.put("refresh_token", List.of(refreshToken));
        return restClient.post()
                .uri(URI.create(properties.tokenUrl()))
                .header(HttpHeaders.AUTHORIZATION, basicAuth())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(TokenResponse.class);
    }

    private String basicAuth() {
        String raw = properties.clientId() + ":" + properties.clientSecret();
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    public record TokenResponse(
            @JsonAlias("access_token") String accessToken,
            @JsonAlias("refresh_token") String refreshToken,
            @JsonAlias("token_type") String tokenType,
            @JsonAlias("expires_in") long expiresIn,
            @JsonAlias("x_refresh_token_expires_in") Long xRefreshTokenExpiresIn,
            Map<String, Object> idToken) {
    }
}

package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.config.QuickBooksProperties;
import com.example.quickbooksimporter.persistence.CompanyEntity;
import com.example.quickbooksimporter.persistence.QboConnectionEntity;
import com.example.quickbooksimporter.repository.CompanyRepository;
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
    private final CurrentCompanyService currentCompanyService;
    private final AuditLogService auditLogService;
    private final CompanyRepository companyRepository;
    private final CompanyQboCredentialsService companyQboCredentialsService;

    public QuickBooksConnectionService(QuickBooksProperties properties,
                                       QboConnectionRepository repository,
                                       RestClient restClient,
                                       CurrentCompanyService currentCompanyService,
                                       AuditLogService auditLogService,
                                       CompanyRepository companyRepository,
                                       CompanyQboCredentialsService companyQboCredentialsService) {
        this.properties = properties;
        this.repository = repository;
        this.restClient = restClient;
        this.currentCompanyService = currentCompanyService;
        this.auditLogService = auditLogService;
        this.companyRepository = companyRepository;
        this.companyQboCredentialsService = companyQboCredentialsService;
    }

    public String buildAuthorizationUrl(String state, Long companyId) {
        CompanyQboCredentialsService.EffectiveQboCredentials creds = companyQboCredentialsService.getEffective(companyId);
        String scope = String.join(" ", properties.scopes());
        return properties.authorizationUrl()
                + "?client_id=" + encode(creds.clientId())
                + "&redirect_uri=" + encode(creds.redirectUri())
                + "&response_type=code"
                + "&scope=" + encode(scope)
                + "&state=" + encode(state);
    }

    public Optional<QboConnectionEntity> getConnection() {
        Long companyId = currentCompanyService.requireCurrentCompanyId();
        return repository.findTopByCompanyIdAndConnectedTrueOrderByUpdatedAtDesc(companyId);
    }

    public CompanyEntity requireCurrentCompany() {
        return currentCompanyService.requireCurrentCompany();
    }

    public Optional<QboConnectionEntity> getConnection(Long companyId) {
        return repository.findTopByCompanyIdAndConnectedTrueOrderByUpdatedAtDesc(companyId);
    }

    public QuickBooksConnectionStatus getStatus() {
        Long companyId = currentCompanyService.requireCurrentCompanyId();
        CompanyQboCredentialsService.EffectiveQboCredentials creds = companyQboCredentialsService.getEffective(companyId);
        return getConnection()
                .map(connection -> new QuickBooksConnectionStatus(
                        true,
                        connection.getCompany().getId(),
                        connection.getCompany().getName(),
                        connection.getRealmId(),
                        connection.getConnectedAt(),
                        connection.getExpiresAt(),
                        properties.environment(),
                        connection.getCredentialSource(),
                        connection.getClientIdHint()))
                .orElse(new QuickBooksConnectionStatus(false, companyId,
                        currentCompanyService.requireCurrentCompany().getName(), null, null, null,
                        properties.environment(), creds.source().name(), creds.clientIdHint()));
    }

    @Transactional
    public void handleAuthorizationCallback(String code, String realmId, Long companyId) {
        TokenResponse response = exchangeToken(code, companyId);
        QboConnectionEntity existingRealm = repository.findByRealmIdAndConnectedTrue(realmId).orElse(null);
        if (existingRealm != null && !existingRealm.getCompany().getId().equals(companyId)) {
            throw new IllegalStateException("Realm already connected to another company");
        }
        QboConnectionEntity entity = repository.findTopByCompanyIdOrderByUpdatedAtDesc(companyId).orElseGet(QboConnectionEntity::new);
        CompanyEntity company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalStateException("Company not found: " + companyId));
        CompanyQboCredentialsService.EffectiveQboCredentials creds = companyQboCredentialsService.getEffective(companyId);
        Instant now = Instant.now();
        entity.setCompany(company);
        entity.setRealmId(realmId);
        entity.setAccessToken(response.accessToken());
        entity.setRefreshToken(response.refreshToken());
        entity.setTokenType(response.tokenType());
        entity.setExpiresAt(now.plusSeconds(response.expiresIn()));
        entity.setRefreshExpiresAt(response.xRefreshTokenExpiresIn() == null ? null : now.plusSeconds(response.xRefreshTokenExpiresIn()));
        entity.setConnectedAt(entity.getConnectedAt() == null ? now : entity.getConnectedAt());
        entity.setUpdatedAt(now);
        entity.setConnected(true);
        entity.setCredentialSource(creds.source().name());
        entity.setClientIdHint(creds.clientIdHint());
        repository.save(entity);
        auditLogService.log("QBO_CONNECT", company, "Connected realm " + realmId + " using " + creds.source().name());
    }

    @Transactional
    public QboConnectionEntity getActiveConnection() {
        Long companyId = currentCompanyService.requireCurrentCompanyId();
        return getActiveConnection(companyId);
    }

    @Transactional
    public QboConnectionEntity getActiveConnection(Long companyId) {
        QboConnectionEntity connection = getConnection(companyId)
                .orElseThrow(() -> new IllegalStateException("QuickBooks is not connected for selected company"));
        if (connection.getExpiresAt().isBefore(Instant.now().plusSeconds(60))) {
            TokenResponse response = refreshToken(connection.getRefreshToken(), companyId);
            CompanyQboCredentialsService.EffectiveQboCredentials creds = companyQboCredentialsService.getEffective(companyId);
            Instant now = Instant.now();
            connection.setAccessToken(response.accessToken());
            connection.setRefreshToken(response.refreshToken());
            connection.setTokenType(response.tokenType());
            connection.setExpiresAt(now.plusSeconds(response.expiresIn()));
            connection.setRefreshExpiresAt(response.xRefreshTokenExpiresIn() == null ? null : now.plusSeconds(response.xRefreshTokenExpiresIn()));
            connection.setUpdatedAt(now);
            connection.setCredentialSource(creds.source().name());
            connection.setClientIdHint(creds.clientIdHint());
            repository.save(connection);
        }
        return connection;
    }

    @Transactional
    public void disconnect(Long companyId) {
        QboConnectionEntity connection = repository.findTopByCompanyIdAndConnectedTrueOrderByUpdatedAtDesc(companyId)
                .orElseThrow(() -> new IllegalStateException("No active QuickBooks connection found"));
        connection.setConnected(false);
        connection.setUpdatedAt(Instant.now());
        repository.save(connection);
        auditLogService.log("QBO_DISCONNECT", connection.getCompany(), "Disconnected realm " + connection.getRealmId());
    }

    private TokenResponse exchangeToken(String code, Long companyId) {
        CompanyQboCredentialsService.EffectiveQboCredentials creds = companyQboCredentialsService.getEffective(companyId);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.put("grant_type", List.of("authorization_code"));
        body.put("code", List.of(code));
        body.put("redirect_uri", List.of(creds.redirectUri()));
        return restClient.post()
                .uri(URI.create(properties.tokenUrl()))
                .header(HttpHeaders.AUTHORIZATION, basicAuth(creds.clientId(), creds.clientSecret()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(TokenResponse.class);
    }

    private TokenResponse refreshToken(String refreshToken, Long companyId) {
        CompanyQboCredentialsService.EffectiveQboCredentials creds = companyQboCredentialsService.getEffective(companyId);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.put("grant_type", List.of("refresh_token"));
        body.put("refresh_token", List.of(refreshToken));
        return restClient.post()
                .uri(URI.create(properties.tokenUrl()))
                .header(HttpHeaders.AUTHORIZATION, basicAuth(creds.clientId(), creds.clientSecret()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(TokenResponse.class);
    }

    private String basicAuth(String clientId, String clientSecret) {
        String raw = clientId + ":" + clientSecret;
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

package com.pdh.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class ClientCredentialsTokenManager {

    private final RestClient restClient;

    @Value("${keycloak.token-uri}") String tokenUri;
    @Value("${keycloak.client-id}") String clientId;
    @Value("${keycloak.client-secret}") String clientSecret;

    private volatile String accessToken;
    private volatile long expiryEpochMillis;

    public ClientCredentialsTokenManager(RestClient restClient) {
        this.restClient = restClient;
    }

    public String getToken() {
        long now = System.currentTimeMillis();
        if (accessToken == null || now > (expiryEpochMillis - 120_000)) {
            refresh();
        }
        return accessToken;
    }

    private synchronized void refresh() {
        long now = System.currentTimeMillis();
        if (accessToken != null && now < (expiryEpochMillis - 120_000)) return;

        String form = "grant_type=client_credentials"
                + "&client_id=" + url(clientId)
                + "&client_secret=" + url(clientSecret);

        Map<String, Object> response = restClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toEntity(Map.class)
                .getBody();

        if (response == null || !response.containsKey("access_token")) {
            throw new IllegalStateException("Token response empty");
        }

        accessToken = (String) response.get("access_token");
        Number expiresIn = (Number) response.getOrDefault("expires_in", 60);
        expiryEpochMillis = System.currentTimeMillis() + expiresIn.longValue() * 1000L;
    }
    public long millisUntilExpiry() {
        return expiryEpochMillis - System.currentTimeMillis();
    }
    private static String url(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}

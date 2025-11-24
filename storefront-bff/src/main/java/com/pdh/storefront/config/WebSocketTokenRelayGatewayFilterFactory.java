package com.pdh.storefront.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class WebSocketTokenRelayGatewayFilterFactory
        extends AbstractGatewayFilterFactory<WebSocketTokenRelayGatewayFilterFactory.Config> {

    private final ServerOAuth2AuthorizedClientRepository authorizedClientRepository;

    public WebSocketTokenRelayGatewayFilterFactory(
            ServerOAuth2AuthorizedClientRepository authorizedClientRepository) {
        super(Config.class);
        this.authorizedClientRepository = authorizedClientRepository;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getURI().getPath();
            log.debug("WebSocketTokenRelay filter executing for path: {}", path);
            
            return exchange.getPrincipal()
                .flatMap(principal -> {
                    if (principal instanceof OAuth2AuthenticationToken oauthToken) {
                        log.debug("Found OAuth2 token for WebSocket connection");
                        return forwardToken(oauthToken, exchange, chain);
                    }
                    log.warn("Principal is not OAuth2AuthenticationToken, proceeding without token relay");
                    return chain.filter(exchange);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("No principal found for WebSocket connection to {}, proceeding without authentication", path);
                    return chain.filter(exchange);
                }));
        };
    }

    private Mono<Void> forwardToken(OAuth2AuthenticationToken oauthToken,
                                    ServerWebExchange exchange,
                                    org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        return authorizedClientRepository.loadAuthorizedClient(
                        oauthToken.getAuthorizedClientRegistrationId(),
                        oauthToken,
                        exchange
                )
                .flatMap(client -> relayToken(client, exchange, chain))
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("No authorized client found for WebSocket, proceeding without token");
                    return chain.filter(exchange);
                }));
    }

    private Mono<Void> relayToken(OAuth2AuthorizedClient client,
                                  ServerWebExchange exchange,
                                  org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        String tokenValue = client.getAccessToken().getTokenValue();
        log.debug("Relaying OAuth2 token to WebSocket backend: {}...", 
                  tokenValue.substring(0, Math.min(20, tokenValue.length())));
        
        ServerWebExchange mutated = exchange.mutate()
                .request(builder -> builder.header("Authorization", "Bearer " + tokenValue))
                .build();
        return chain.filter(mutated);
    }

    public static class Config {
        // Placeholder for potential future configuration
    }
}

package com.pdh.ai.config;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;

@Slf4j
@Configuration
public class McpClientManager implements DisposableBean {

    private final List<McpClient> clients = new ArrayList<>();
    private final ClientCredentialsTokenManager tokenManager;
    private final List<SseConn> sseConns = new ArrayList<>();
    public McpClientManager(McpClientProperties properties,
                            ClientCredentialsTokenManager tokenManager) {
        this.tokenManager = tokenManager;

        if (properties != null && properties.isEnabled()) {
            createSseClients(properties);
            createStdioClients(properties);
        }
    }

    public boolean isEmpty() {
        return clients.isEmpty();
    }

    @Bean
    public McpToolProvider buildToolProvider(McpClientProperties properties) {
        if (clients.isEmpty()) return null;
        return McpToolProvider.builder().mcpClients(clients).build();
    }

    private void createSseClients(McpClientProperties properties) {
        properties.getSse().getConnections().forEach((key, connection) -> {
            try {
                String baseUrl = connection.getUrl();

                String endpoint = connection.getSseEndpoint();
                if (baseUrl == null || endpoint == null) {
                    log.warn("Skipping SSE MCP client '{}' because url or sse-endpoint is missing", key);
                    return;
                }

                String sseUrl = (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                        + (endpoint.startsWith("/") ? endpoint : "/" + endpoint);
                System.out.println("sseUrl="+sseUrl);
                McpTransport  mcpTransport = new StreamableHttpMcpTransport.Builder()
                        .url(sseUrl)

                        // if your library supports a header supplier, prefer that;
                        // otherwise, this will use the current token at build time:
                        .customHeaders(Map.of("Authorization", "Bearer " + tokenManager.getToken()))
                        .logRequests(true)
                        .logResponses(true)
                        .build();

                McpClient client = new DefaultMcpClient.Builder()
                        .transport(mcpTransport)

                        .key(key)
                        .clientName(key)
                        .clientVersion("1.0")
                        .build();

                clients.add(client);
            } catch (Exception ex) {
                log.warn("Failed to initialise SSE MCP client '{}': {}", key, ex.getMessage(), ex);
            }
        });
    }

    private void createStdioClients(McpClientProperties properties) {
        properties.getStdio().getConnections().forEach((key, connection) -> {
            try {
                if (connection.getCommand() == null || connection.getCommand().isBlank()) {
                    log.warn("Skipping STDIO MCP client '{}' because command is missing", key);
                    return;
                }
                List<String> commandLine = new ArrayList<>();
                commandLine.add(connection.getCommand());
                commandLine.addAll(connection.getArgs());

                StdioMcpTransport.Builder builder = new StdioMcpTransport.Builder()
                        .command(commandLine);

                if (connection.getEnv() != null && !connection.getEnv().isEmpty()) {
                    builder.environment(connection.getEnv());
                }
                if (connection.isLogEvents()) {
                    builder.logEvents(true);
                }

                McpTransport transport = builder.build();
                McpClient client = new DefaultMcpClient.Builder()
                        .transport(transport)

                        .build();

                clients.add(client);
            } catch (Exception ex) {
                log.warn("Failed to initialise STDIO MCP client '{}': {}", key, ex.getMessage(), ex);
            }
        });
    }

    private void captureSseConns(McpClientProperties properties) {
        properties.getSse().getConnections().forEach((key, c) -> {
            if (c.getUrl() == null || c.getSseEndpoint() == null) {
                log.warn("Skipping SSE MCP client '{}' because url or sse-endpoint is missing", key);
                return;
            }
            String base = c.getUrl().endsWith("/") ? c.getUrl().substring(0, c.getUrl().length() - 1) : c.getUrl();
            String sseUrl = c.getSseEndpoint().startsWith("/") ? base + c.getSseEndpoint() : base + "/" + c.getSseEndpoint();
            sseConns.add(new SseConn(key, sseUrl));
        });
    }

    private synchronized void buildAllClients() {
        // close old
        for (McpClient client : clients) {
            try {
                client.close();
            } catch (Exception ignore) {
            }
        }
        clients.clear();

        // rebuild SSE
        for (SseConn sc : sseConns) {
            try {
                StreamableHttpMcpTransport t = new StreamableHttpMcpTransport
                        .Builder()
                        .url(sc.sseUrl())
                        .customHeaders(Map.of("Authorization", "Bearer " + tokenManager.getToken()))
                        .logRequests(true)
                        .logResponses(true)
                        .build();

                clients.add(new DefaultMcpClient.Builder().transport(t).build());
            } catch (Exception ex) {
                log.warn("Failed to init SSE MCP client '{}': {}", sc.key(), ex.getMessage(), ex);
            }
        }
        // (re)build any STDIO clients too if you like
    }

    // Refresh before expiry (e.g., every 30s check; rebuild if < 2 min remain)
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 30_000)
    void maybeRotateClients() {
        long left = tokenManager.millisUntilExpiry();
        if (left <= 120_000L) { // 2 minutes
            log.info("Access token expiring soon ({} ms). Rebuilding MCP SSE clients with a fresh token...", left);
            buildAllClients();
        }
    }

    @Override
    public void destroy() {
        for (McpClient c : clients)
            try {
                c.close();
            } catch (Exception ignore) {
            }
    }

    private record SseConn(String key, String sseUrl) {
    }
}

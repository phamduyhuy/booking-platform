package com.pdh.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "spring.ai.mcp.client")
public class McpClientProperties {

    private boolean enabled = false;
    private String name = "bookingsmart";
    private String version = "1.0.0";
    private Duration requestTimeout = Duration.ofSeconds(300);
    private boolean rootChangeNotification = true;

    @NestedConfigurationProperty
    private final SseProperties sse = new SseProperties();

    @NestedConfigurationProperty
    private final StdioProperties stdio = new StdioProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public boolean isRootChangeNotification() {
        return rootChangeNotification;
    }

    public void setRootChangeNotification(boolean rootChangeNotification) {
        this.rootChangeNotification = rootChangeNotification;
    }

    public SseProperties getSse() {
        return sse;
    }

    public StdioProperties getStdio() {
        return stdio;
    }

    public static class SseProperties {
        private final Map<String, SseConnection> connections = new HashMap<>();

        public Map<String, SseConnection> getConnections() {
            return connections;
        }
    }

    public static class StdioProperties {
        private final Map<String, StdioConnection> connections = new HashMap<>();

        public Map<String, StdioConnection> getConnections() {
            return connections;
        }
    }

    public static class SseConnection {
        private String url;
        private String sseEndpoint;
        private Duration timeout;
        private boolean logRequests;
        private boolean logResponses;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getSseEndpoint() {
            return sseEndpoint;
        }

        public void setSseEndpoint(String sseEndpoint) {
            this.sseEndpoint = sseEndpoint;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public boolean isLogRequests() {
            return logRequests;
        }

        public void setLogRequests(boolean logRequests) {
            this.logRequests = logRequests;
        }

        public boolean isLogResponses() {
            return logResponses;
        }

        public void setLogResponses(boolean logResponses) {
            this.logResponses = logResponses;
        }
    }

    public static class StdioConnection {
        private String command;
        private List<String> args = new ArrayList<>();
        private Map<String, String> env = new HashMap<>();
        private boolean logEvents;

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public List<String> getArgs() {
            return args;
        }

        public void setArgs(List<String> args) {
            this.args = args;
        }

        public Map<String, String> getEnv() {
            return env;
        }

        public void setEnv(Map<String, String> env) {
            this.env = env;
        }

        public boolean isLogEvents() {
            return logEvents;
        }

        public void setLogEvents(boolean logEvents) {
            this.logEvents = logEvents;
        }
    }
}

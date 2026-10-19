package com.agronomia.backend.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "agronomia.ai")
public class AiProviderProperties {
    private final OpenCode openCode = new OpenCode();
    private final OpenWebUi openWebUi = new OpenWebUi();
    private final Chat chat = new Chat();

    public OpenCode getOpenCode() { return openCode; }
    public OpenWebUi getOpenWebUi() { return openWebUi; }
    public Chat getChat() { return chat; }

    public static class OpenCode {
        private String baseUrl;
        private String username;
        private String password;
        private String providerId;
        private String modelId;
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getProviderId() { return providerId; }
        public void setProviderId(String providerId) { this.providerId = providerId; }
        public String getModelId() { return modelId; }
        public void setModelId(String modelId) { this.modelId = modelId; }
    }

    public static class OpenWebUi {
        private String baseUrl;
        private String apiKey;
        private String model;
        private Duration connectTimeout;
        private Duration readTimeout;
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
        public Duration getReadTimeout() { return readTimeout; }
        public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    }

    public static class Chat {
        private int maxMessages = 40;
        private int maxMessageCharacters = 8_000;
        private int maxResponseCharacters = 20_000;
        public int getMaxMessages() { return maxMessages; }
        public void setMaxMessages(int maxMessages) { this.maxMessages = maxMessages; }
        public int getMaxMessageCharacters() { return maxMessageCharacters; }
        public void setMaxMessageCharacters(int maxMessageCharacters) { this.maxMessageCharacters = maxMessageCharacters; }
        public int getMaxResponseCharacters() { return maxResponseCharacters; }
        public void setMaxResponseCharacters(int maxResponseCharacters) { this.maxResponseCharacters = maxResponseCharacters; }
    }
}

package com.xzit.agent;

public class LlmClient {

    private final HttpClient httpClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String chatApiKey;
    private String chatModel;
    private String chatApiUrl;

    private boolean enabled = false;

    public LlmClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        loadConfig();
    }

    private void loadConfig() {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("agent-config.properties")) {
            if (is != null) {
                props.load(is);
                this.chatModel = props.getProperty("llm.model", "");
                this.chatApiKey = props.getProperty("llm.api.key", "");
                this.chatApiUrl = props.getProperty("llm.api.url", "");

                this.enabled = !chatModel.isEmpty() && !chatApiKey.isEmpty() && !chatApiUrl.isEmpty();
                if (enabled) {
                    System.out.println("[LLM引擎] 已启用，模型：" + chatModel);
                } else {
                    System.out.println("[LLM引擎] 未配置，将使用纯规则模式");
                }
            }
        } catch (IOException e) {
            System.out.println("[LLM引擎] 配置加载失败:" + e.getMessage());
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String chatWithHistory(List<Map<String, String>> messages) {
        if (!enabled) {
            return "[LLM未启用]";
        }
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", chatModel);
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", 2048);
            requestBody.put("temperature", 0.3);

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(chatApiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + chatApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(120))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                return root.path("choices").path(0).path("message").path("content").asText();
            }
            System.out.println("[LLM引擎] API失败：" + response.statusCode());
            return "[LLM调用失败： HTTP" + response.statusCode() + "]";
        } catch (Exception e) {
            System.err.println("[LLM引擎] 调用异常: " + e.getMessage());
            return "[LLM调用异常:" + e.getMessage() + "]";
        }
    }

    public String chat(String systemPrompt, String userMessage) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userMessage));
        return chatWithHistory(messages);
    }
}

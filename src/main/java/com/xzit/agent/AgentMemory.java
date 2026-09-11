package org.example.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AgentMemory {

    private final Map<String, String> shortTermMemory = new HashMap<>();

    private final Map<String, Object> workingMemory = new HashMap<>();

    private List<Map<String, Object>> complianceRules = new ArrayList<>();

    private List<Map<String, Object>> historicalCases = new ArrayList<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String dataDir;

    public AgentMemory() {
        this.dataDir = "data";
    }

    private List<Map<String, Object>> loadJsonList(String filePath, String resourcePath) {
        File file = new File(filePath);
        if (file.exists()) {
            CollectionType listType = objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, Map.class);
            try {
                return objectMapper.readValue(file, listType);
            } catch (IOException e) {
                System.out.println("[长期记忆] 加载文件失败：" + filePath + " - " + e.getMessage());
            }
        }

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is != null) {
                CollectionType listType = objectMapper.getTypeFactory()
                        .constructCollectionType(List.class, Map.class);
                return objectMapper.readValue(is, listType);
            }
        } catch (IOException e) {
            System.out.println("[长期记忆] 加载资源失败：" + resourcePath + " - " + e.getMessage());
        }
        return new ArrayList<>();
    }

    private void saveJsonList(String filePath, List<Map<String, Object>> data) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File(filePath), data);
        } catch (IOException e) {
            System.out.println("[长期记忆] 保存文件失败：" + filePath + " - " + e.getMessage());
        }
    }

    public void loadLongTermMemory() {
        File dir = new File(dataDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        complianceRules = loadJsonList(dataDir + "/compliance-rules.json", "compliance-rules.json");
        historicalCases = loadJsonList(dataDir + "/historical-cases.json", "historical-cases.json");

        System.out.println("[长期记忆] 已加载合规规则 " + complianceRules.size() + " 条");
        System.out.println("[长期记忆] 已加载历史案例 " + historicalCases.size() + " 个");
    }

    public void saveLongTermMemory() {
        saveJsonList(dataDir + "/compliance-rules.json", complianceRules);
        saveJsonList(dataDir + "/historical-cases.json", historicalCases);
        System.out.println("[长期记忆] 已保存合规规则 " + complianceRules.size() + " 条");
        System.out.println("[长期记忆] 已保存历史案例 " + historicalCases.size() + " 个");
    }

    public void saveShortTerm(String key, String value) {
        shortTermMemory.put(key, value);
    }

    public String getShortTerm(String key) {
        return shortTermMemory.get(key);
    }

    public void clearShortTerm() {
        shortTermMemory.clear();
    }

    public void saveWorking(String key, Object value) {
        workingMemory.put(key, value);
    }

    public Object getWorking(String key) {
        return workingMemory.get(key);
    }

    public <T> T getWorking(String key, Class<T> type) {
        Object value = workingMemory.get(key);
        if (value == null) {
            return null;
        }
        return (T) value;
    }

    public void clearWorking() {
        workingMemory.clear();
    }

    public List<Map<String, Object>> getComplianceRules() {
        return complianceRules;
    }

    public List<Map<String, Object>> getHistoricalCases() {
        return historicalCases;
    }

    public void addComplianceRule(Map<String, Object> rule) {
        complianceRules.add(rule);
    }

    public void addHistoricalCase(Map<String, Object> caseItem) {
        historicalCases.add(caseItem);
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }
}

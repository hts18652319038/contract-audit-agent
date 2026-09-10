package com.xzit.tools;

import com.xzit.agent.AgentMemory;
import com.xzit.agent.AgentTool;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public class PersistExperienceTool implements AgentTool {
    @Override
    public String execute(AgentMemory memory) throws Exception {
        String summary = (String) memory.getWorking("experienceSummary");
        if (summary == null || summary.isEmpty()) {
            return "异常：未设置经验搞要（工作记忆experienceSummary为空）";
        }

        String contractPath = memory.getShortTerm("contractPath");
        String fileName = contractPath != null ? Paths.get(contractPath).getFileName().toString() : "未知";
        Map<String, Object> caseItem = new LinkedHashMap<>();
        caseItem.put("id", "CASE-" + System.currentTimeMillis());
        caseItem.put("contractName", fileName);
        caseItem.put("auditTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        caseItem.put("summary", summary);

        memory.addHistoricalCase(caseItem);
        memory.saveLongTermMemory();
        return "审核经验已沉淀：案例已写入长期记忆（当前共 " + memory.getHistoricalCases().size() + " 个案例)";
    }

    @Override
    public String getToolName() {
        return "沉淀审核经验";
    }

    @Override
    public String getToolDescription() {
        return "将本次审核结论写入长期记忆数据库，供后续审核复用。需要先通过工作记忆设置experienceSummary（本次审核的风险摘要和关键发现）。";
    }
}

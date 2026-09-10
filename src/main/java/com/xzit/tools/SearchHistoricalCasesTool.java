package com.xzit.tools;

import com.xzit.agent.AgentMemory;
import com.xzit.agent.AgentTool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SearchHistoricalCasesTool implements AgentTool {
    @Override
    public String execute(AgentMemory memory) throws Exception {
        String contractType = (String) memory.getWorking("queryContractType");
        List<Map<String, Object>> allCases = memory.getHistoricalCases();
        List<Map<String, Object>> matched = new ArrayList<>();

        for (Map<String, Object> caseItem : allCases) {
            if (contractType != null && !contractType.isEmpty()) {
                String caseType = (String) caseItem.getOrDefault("contractType", "");
                String caseName = (String) caseItem.getOrDefault("contractName", "");

                if (caseType.contains(contractType) || contractType.contains(caseType)  || caseName.contains(contractType)) {
                    matched.add(caseItem);
                }
            } else {
                matched.add(caseItem);
            }
        }

        if (matched.isEmpty()) {
            return "未找到匹配的历史案例，案例库共有 " + allCases.size() + " 个案例。";
        }

        StringBuilder sb  = new StringBuilder();
        sb.append("找到 ").append(matched.size()).append(" 个相关的历史案例: \n");
        for (Map<String, Object> c : matched) {
            sb.append("【").append(c.getOrDefault("id", "")).append("】");
            sb.append(" 合同：").append(c.getOrDefault("contractName", ""));
            sb.append(" | 类型: ").append(c.getOrDefault("contractType", ""));
            sb.append(" | 时间: ").append(c.getOrDefault("auditTime", ""));
            sb.append("\n  风险数: ").append(c.getOrDefault("riskCount", ""));
            sb.append(" (高:").append(c.getOrDefault("highRiskCount", ""));
            sb.append(" 中:").append(c.getOrDefault("mediumRiskCount", ""));
            sb.append(" 低:").append(c.getOrDefault("lowRiskCount", "")).append(")\n");
            Object risks = c.get("risks");
            if (risks instanceof List) {
                List<Map<String, String>> riskList = (List<Map<String, String>>) risks;
                for (Map<String, String> risk : riskList) {
                    sb.append(" - [").append(risk.getOrDefault("level", ""))
                            .append("] ").append(risk.getOrDefault("description", "")).append("\n");
                }
            }

            if (c.containsKey("riskDescription")) {
                sb.append(" 主要风险: ").append(c.getOrDefault("riskDescription", "")).append("\n");
                sb.append(" 修正建议: ").append(c.getOrDefault("suggestion", "")).append("\n");
            }
        }
        return sb.toString();
    }

    @Override
    public String getToolName() {
        return "搜索历史案例";
    }

    @Override
    public String getToolDescription() {
        return "从本地历史审核案例库中搜索相似合同的审核记录，包括发现的风险类型和数量。可通过工作记忆设置queryContractType筛选合同类型。";
    }
}

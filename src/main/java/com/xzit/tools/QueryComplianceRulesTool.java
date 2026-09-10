package com.xzit.tools;

import com.xzit.agent.AgentMemory;
import com.xzit.agent.AgentTool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class QueryComplianceRulesTool implements AgentTool {
    @Override
    public String execute(AgentMemory memory) throws Exception {
        String category = (String) memory.getWorking("queryCategory");
        String keyword = (String) memory.getWorking("queryKeyword");

        List<Map<String, Object>> allRules = memory.getComplianceRules();
        List<Map<String, Object>> matched = new ArrayList<>();

        for (Map<String, Object> rule : allRules) {
            boolean match = false;
            if (category != null && !category.isEmpty()) {
                String ruleCategory = (String) rule.getOrDefault("category", "");
                if (ruleCategory.contains(category) || category.contains(ruleCategory)) {
                    match = true;
                }
            }
            if (keyword != null && !keyword.isEmpty()) {
                String ruleKeywords = (String) rule.getOrDefault("keywords", "");
                String ruleDesc = (String) rule.getOrDefault("riskDescription", "");
                if (ruleKeywords.contains(keyword) || ruleDesc.contains(keyword)) {
                    match = true;
                }
            }

            if ((category == null || category.isEmpty()) && (keyword == null || keyword.isEmpty())) {
                match = true;
            }
            if (match) {
                matched.add(rule);
            }
        }
        StringBuilder sb = new StringBuilder();
        if (matched.isEmpty()) {
            matched = allRules;
            sb.append("未找到精准匹配， 返回全部 ").append(allRules.size()).append(" 条规则供参考： \n");
        } else {
            sb.append("查询到 ").append(matched.size()).append(" 条合规规则：\n");
        }
        for (Map<String, Object> rule : matched) {
            sb.append("【").append(rule.getOrDefault("id", "")).append("】");
            sb.append(" 类别： ").append(rule.getOrDefault("category", ""));
            sb.append("  | 等级: ").append(rule.getOrDefault("severity", ""));
            sb.append("\n  描述： ").append(rule.getOrDefault("riskDescription", ""));
            sb.append("\n  建议： ").append(rule.getOrDefault("suggestion", ""));
            sb.append("\n  关键词： ").append(rule.getOrDefault("keywords", "")).append("\n");
        }
        return sb.toString();
    }

    @Override
    public String getToolName() {
        return "查询合规规则";
    }

    @Override
    public String getToolDescription() {
        return "从本地合规规则库中查询指定类别或关键词的规则。可通过工作记忆设置queryCategory（类别）或queryKeyword（关键词）来筛选，不设置则返回全部规则。";
    }
}

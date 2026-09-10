package org.example.agent;

import org.example.tools.PersistExperienceTool;
import org.example.tools.QueryComplianceRulesTool;
import org.example.tools.SaveAuditReportTool;
import org.example.tools.SearchHistoricalCasesTool;

import javax.sound.midi.SysexMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ContractAuditAgent {

    private final AgentMemory memory;

    private final List<AgentTool> toolPool;

    private final LlmClient llmClient;

    private final Map<String, AgentTool> toolRegistry = new LinkedHashMap<>();

    private static final int MAX_REACT_ROUNDS = 15;

    public ContractAuditAgent() {
        this.memory = new AgentMemory();
        this.toolPool = new ArrayList<>();
        this.llmClient = new LlmClient();
        initTools();
    }

    private void initTools() {
        AgentTool[] tools = {
                new QueryComplianceRulesTool(),
                new SearchHistoricalCasesTool(),
                new SaveAuditReportTool(),
                new PersistExperienceTool()
        };
        for (AgentTool tool : tools) {
            toolPool.add(tool);
            toolRegistry.put(tool.getToolName(), tool);
        }
        System.out.println("[Agent] 已注册 " + toolPool.size() + "个工具：");
        for (AgentTool tool : toolPool) {
            System.out.println(" - " + tool.getToolName() + ": " + tool.getToolDescription());
        }
    }

    private List<String> planTask() {
        List<String> tasks = new ArrayList<>();
        tasks.add("读取合同");
        tasks.add("查询合规规则");
        tasks.add("搜索历史案例");
        tasks.add("合同审核分析");
        tasks.add("保存审核报告");
        tasks.add("沉淀审核经验");
        return tasks;
    }

    public void runAudit(String contractPath) {
        // print
        System.out.println("\n" + "=".repeat(50));
        System.out.println("    智能合同审核Agent （ReAct + PlanAct）");
        System.out.println("=".repeat(50));
        System.out.println("输入合同路径:" + contractPath + "\n");

        // ===1.记忆初始化
        memory.clearShortTerm();
        memory.clearWorking();
        memory.loadLongTermMemory();
        memory.saveShortTerm("contractPath", contractPath);
        if (!llmClient.isEnabled()) {
            System.out.println("[错误] LLM未启用，无法执行审核");
            return;
        }

        // ===2.PlanAct规划
        List<String> plan = planTask();
        memory.saveWorking("plan", plan);
        System.out.println("[PlanAct]任务规划：");
        for (int i = 0; i < plan.size(); i ++) {
            System.out.println(" " + (i + 1) + ". " + plan.get(i));
        }
        System.out.println();

        // ===3.按计划逐步执行
        // ======3.1 读取合同文件，如果读取失败，就直接返回
        String contractContent = executeReadContract(contractPath, plan);
        if (contractContent == null) {
            return;
        }

        // ======3.2 & 3.3 查询合规规则 & 搜索历史案例，Auto
        String rulesContext = executePlanStep(1, plan, "查询合规规则");
        String casesContext = executePlanStep(2, plan, "搜索历史案例");

        // ======3.4 进入ReAct推理循环，让LLM自主分析合同，最核心步骤。。
        executeReActAnalysis(3, plan, contractContent, rulesContext, casesContext);

        // ======3.5 保存审核报告，沉淀审核经验
        executeSaveStep(4, plan, "保存审核报告", "reportContent");
        executeSaveStep(5, plan, "沉淀审核经验", "experienceSummary");
        // ===4.完成
        System.out.println("=".repeat(50));
        System.out.println("   审核完毕（PlanAct " + plan.size() + "/" + plan.size() + "任务完成)");
        System.out.println("=".repeat(50));

        String reportPath =  (String) memory.getWorking("reportPath");
        if(reportPath != null) {
            System.out.println("[报告]" + reportPath);
        }
    }

    private void executeSaveStep(int i, List<String> plan, String toolName, String reportContent) {
        printPlanStep(i, plan);
        Object content = memory.getWorking(reportContent);
        if (content != null && !content.toString().isEmpty()) {
            System.out.println("[完成] " + safeExecute(toolName) + "\n");
        } else {
            System.out.println("[跳过] LLM未设置" + reportContent + "\n");
        }
    }

    private void executeReActAnalysis(int i, List<String> plan, String contractContent, String rulesContext, String casesContext) {
        printPlanStep(i, plan);
        String systemPrompt = buildSystemPrompt(contractContent, rulesContext, casesContext);
        runReActLoop(systemPrompt);
        System.out.println("[完成] LLM审核分析完毕\n");
    }

    private void runReActLoop(String systemPrompt) {
        List<Map<String, String>> conversation = new ArrayList<>();
        conversation.add(Map.of("role", "system", "content", systemPrompt));
        conversation.add(Map.of("role", "user", "content", "请开始审核。参考数据已提供，直接分析合同文本，给出审核结论。"));
        int round = 0;
        boolean finished = false;
        int retries = 0;

        while (round < MAX_REACT_ROUNDS && !finished) {
            round ++;
            System.out.println(" ---ReAct第" + round + "轮 ---");
            if (conversation.size() > 10) {
                Map<String, String> sysMsg = conversation.get(0);
                List<Map<String, String>> recent = new ArrayList<>(conversation.subList(Math.max(1, conversation.size() - 6), conversation.size()));
                conversation.clear();
                conversation.add(sysMsg);
                conversation.addAll(recent);
            }
            String llmResponse = llmClient.chatWithHistory(conversation);

            if (llmResponse == null || llmResponse.isEmpty() || llmResponse.startsWith("[LLM")) {
                if (++retries > 3) {
                    System.err.println(" [LLM] 连续失败，终止");
                    break;
                }
                System.out.println("[LLM] 推理失败， 重试。。。");
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                }
                round--;
                continue;
            }
            retries = 0;
            conversation.add(Map.of("role", "assistant", "content", llmResponse));

            for (String line : llmResponse.split("\n")) {
                if (!line.trim().isEmpty()) {
                    System.out.println("     " + line);
                }
            }
            parseMemorySettings(llmResponse);

            if (llmResponse.contains("最终答案:") || llmResponse.contains("最终答案：")) {
                finished = true;
                System.out.println(" [完成] LLM审核完成");
            } else {
                String action = extractAction(llmResponse);
                if (action != null) {
                    AgentTool tool = findTool(action);
                    String observation;
                    if (tool != null) {
                        try {
                            String result = tool.execute(memory);
                            System.out.println("  【工具结果】" + result);
                            observation = "观察： " + result;
                        } catch (Exception e) {
                            observation = "观察：工具异常 - " + e.getMessage();
                        }
                    } else {
                        observation = "观察：工具\"" + action + "\"不存在，可用：" + String.join(", ", toolRegistry.keySet());
                    }
                } else {
                    conversation.add(Map.of("role", "user", "content",
                            "观察: 请继续分析。完成后设置reportContent和experienceSummary，输出最终答案。"));
                }
            }
        }
    }

    private String extractAction(String llmResponse) {
        for (String line : llmResponse.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("行动:") || trimmed.startsWith("行动：") ){
                int idx = Math.max(trimmed.indexOf(":"), trimmed.indexOf("："));
                return trimmed.substring(idx + 1).trim();
            }
        }
        return null;
    }

    private void parseMemorySettings(String llmResponse) {
        String [] lines = llmResponse.split("\n");
        String currentKey = null;
        StringBuilder currentValue = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("设置记忆:") || trimmed.startsWith("设置记忆：")) {
                if (currentKey != null && currentValue.length() > 0) {
                    memory.saveWorking(currentKey, currentValue.toString().trim());
                    System.out.println("   [记忆]" + currentKey + " 已设置");
                }
                int colonIdx = Math.max(trimmed.indexOf(':'), trimmed.indexOf('：'));
                String kv = trimmed.substring(colonIdx + 1).trim();
                int eqIdx = kv.indexOf('=');
                if (eqIdx > 0) {
                    currentKey = kv.substring(0, eqIdx).trim();
                    currentValue = new StringBuilder(kv.substring(eqIdx + 1).trim());
                } else {
                    currentKey = kv.trim();
                    currentValue = new StringBuilder();
                }
            } else {
                if (trimmed.startsWith("行动:") || trimmed.startsWith("思考:") || trimmed.startsWith("行动：") || trimmed.startsWith("思考：")) {
                    if (currentKey != null && currentValue.length() >0) {
                        memory.saveWorking(currentKey, currentValue.toString().trim());
                        System.out.println("  [记忆]" + currentKey + "已设置");
                    }
                    currentKey = null;
                } else {
                    currentValue.append("\n").append(trimmed);
                }
            }
        }

        if (currentKey != null && currentValue.length() > 0) {
            memory.saveWorking(currentKey, currentValue.toString().trim());
            System.out.println("   [记忆]" + currentKey + " 已设置");
        }
    }

    private String buildSystemPrompt(String contractContent, String rulesContext, String casesContext) {
        StringBuilder sb = new StringBuilder();
        // 角色
        sb.append("你是专业的合同审核AI Agent。 请审核以下的合同。");

        // 参考数据
        sb.append("## 参考数据\n");
        sb.append("### 合规规则\n").append(rulesContext).append("\n");
        sb.append("### 历史案例\n").append(casesContext).append("\n");

        // 审核要求
        sb.append("## 审核要求\n");
        sb.append("逐项检查：主体信息完整性、金额条款（大小写/单位）、期限条款（日期逻辑）、");
        sb.append("违约责任（是否对等）、合规条款（保密/不可抗力/争议解决）。\n\n");

        //输出格式约定
        sb.append("## 输出格式\n");
        sb.append("1. 每步用\"思考:\"说明推理过程\n");
        sb.append("2. 审核完成后，用\"设置记忆: reportContent=报告内容\"保存报告（按高/中/低风险分类，含修正建议）\n");
        sb.append("3. 用\"设置记忆: experienceSummary=经验摘要\"保存关键发现\n");
        sb.append("4. 最后输出\"最终答案:\"给出审核结论\n\n");

        // 合同全文
        sb.append("## 合同全文\n").append(contractContent);
        return sb.toString();
    }


    private String executePlanStep(int i, List<String> plan, String toolName) {
        printPlanStep(i, plan);
        String result = safeExecute(toolName);
        System.out.println("[完成] 已获取参考数据\n");
        return result;
    }

    private String safeExecute(String toolName) {
        AgentTool tool = findTool(toolName);
        if (tool == null) {
            return "工具不存在:" + toolName;
        }
        try {
            return tool.execute(memory);
        } catch (Exception e) {
            return "异常：" + e.getMessage();
        }
    }

    private AgentTool findTool(String toolName) {
        if (toolRegistry.containsKey(toolName)) {
            return toolRegistry.get(toolName);
        }
        for (Map.Entry<String, AgentTool> e : toolRegistry.entrySet()) {
            if (e.getKey().contains(toolName) || toolName.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }


    private String executeReadContract(String contractPath, List<String> plan) {
        printPlanStep(0, plan);
        try {
            String contract = Files.readString(Paths.get(contractPath), StandardCharsets.UTF_8);
            System.out.println("[完成] 已读取合同（" + contract.length() + "字符)\n");
            return contract;
        } catch (IOException e) {
            System.out.println("[错误] 无法读取合同：" + e.getMessage());
            return null;
        }
    }

    private void printPlanStep(int index, List<String> plan) {
        System.out.println("-- [" + (index + 1) + "/" + plan.size() + "] " + plan.get(index) + " ---");
    }
}

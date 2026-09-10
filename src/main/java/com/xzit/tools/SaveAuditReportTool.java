package org.example.tools;

import org.example.agent.AgentMemory;
import org.example.agent.AgentTool;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SaveAuditReportTool implements AgentTool {
    @Override
    public String execute(AgentMemory memory) throws Exception {
        String reportContent = (String) memory.getWorking("reportContent");
        if (reportContent == null || reportContent.isEmpty()) {
            return "异常：未设置报告内容(工作记忆reportContent为空)";
        }
        String contractPath = memory.getShortTerm("contractPath");
        String fileName = Paths.get(contractPath).getFileName().toString();
        String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf(".")) :fileName;

        StringBuilder report = new StringBuilder();
        report.append("═══════════════════════════════════════════════════════════════\n");
        report.append("          智能合同审核报告（AI Agent生成）\n");
        report.append("═══════════════════════════════════════════════════════════════\n\n");
        report.append(" 合同文件:").append(fileName).append("\n");
        report.append(" 审核时间：").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        report.append(" 审核方式: LLM驱动的ReAct推理\n\n");
        report.append("═══════════════════════════════════════════════════════════════\n\n");
        report.append(reportContent).append("\n\n");
        report.append("═══════════════════════════════════════════════════════════════\n");
        report.append("报告由「智能合同审核Agent」自动生成\n");

        String dir = Paths.get(contractPath).getParent() != null ? Paths.get(contractPath).getParent().toString()
                : ".";
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String reportFileName = baseName + "_审核报告_" + timestamp + ".txt";
        Path reportPath = Paths.get(dir, reportFileName);

        Files.writeString(reportPath,report.toString(), StandardCharsets.UTF_8);

        memory.saveWorking("reportPath", reportPath.toAbsolutePath().toString());
        return "审核报告已保存至：" + reportPath.toAbsolutePath();
    }

    @Override
    public String getToolName() {
        return "保存审核报告";
    }

    @Override
    public String getToolDescription() {
        return "将LLM生成的审核报告保存到本地文件。需要先通过工作记忆设置reportContent（报告正文内容）。";
    }
}

package com.xzit.agent;

public interface AgentTool {
      // 执行
    String execute(AgentMemory memory) throws Exception;

    String getToolName();

    String getToolDescription();
}

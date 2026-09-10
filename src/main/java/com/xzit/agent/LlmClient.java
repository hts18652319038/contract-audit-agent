package com.xzit.agent;

public class LlmClient {
      // 执行
    String execute(AgentMemory memory) throws Exception;

    String getToolName();

    String getToolDescription();
}

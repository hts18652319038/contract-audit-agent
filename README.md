# Contract-Audit-Agent

> 项目模块：`chap-3-1-single-agent`

基于**原生 Java 17** 实现的**智能合同审核单 Agent 系统**，融合 PlanAct 分层任务规划 + ReAct 推理循环 + 三级记忆体系，调用大模型 LLM API 完成合同智能审核。

[![Java-17](https://img.shields.io/badge/Java-17-blue)](https://openjdk.org/projects/jdk/17/)
[![Maven](https://img.shields.io/badge/Maven-3.8%2B-orange)](https://maven.apache.org/)
[![Jackson](https://img.shields.io/badge/Jackson-JSON-green)](https://github.com/FasterXML/jackson)

## 📖 项目简介

本 Agent 实现端到端合同审核能力：读取合同文本 → 加载合规规则库 → 检索历史相似案例 → 通过 LLM ReAct 多轮推理分析合同风险 → 自动生成审核报告 → 将本次审核经验沉淀到历史案例库，供后续审核复用。

> 核心思想：
>
> - **LLM 大脑**：负责自然语言理解、ReAct 思考推理；
> - **工具手脚**：查询规则、检索案例、保存报告、沉淀经验；
> - **三级记忆**：短期记忆、工作记忆、长期持久化记忆（JSON 文件）。

## ✨ 核心特性

1. **PlanAct 分层决策**：固定 6 步审核流程，大模型仅在分析阶段启用 ReAct 循环，兼顾稳定性与智能性
2. **ReAct 推理循环**：`思考 → 行动 → 观察` 循环，最大 15 轮推理，支持工具调用；内置失败重试机制
3. **三级记忆架构**
   - 短期记忆：临时上下文，任务结束清空
   - 工作记忆：保存审核计划、中间结果、报告内容
   - 长期记忆：JSON 文件持久化，合规规则库、历史案例库，程序重启数据不丢失
4. **模块化工具设计**：统一 `AgentTool` 工具接口，4 个开箱即用工具，可方便扩展新增工具
5. **经验自动沉淀**：每一次审核完成，自动把风险、摘要沉淀为新案例，形成自增长案例库
6. **本地文件报告输出**：审核报告自动生成带时间戳的 txt 文件，不会覆盖历史审核结果
7. **兼容两套案例数据格式**：支持初始扁平种子数据、工具沉淀后的嵌套风险数组格式

## 📁 项目目录结构

```
chap-3-1-single-agent/
├── pom.xml                              # Maven 项目配置，Java 17
├── contracts/
│   └── 样例房屋租赁合同.txt              # 待审核样例合同（内置埋点风险）
├── data/
│   ├── compliance-rules.json            # 运行时：合规规则库（读写）
│   └── historical-cases.json            # 运行时：历史案例库（读写，自动新增案例）
├── src/main/java/org/example/
│   ├── Main.java                        # 程序入口
│   ├── agent/
│   │   ├── AgentTool.java               # 工具统一接口定义
│   │   ├── AgentMemory.java             # 三级记忆体系实现
│   │   ├── LlmClient.java               # LLM 大模型 HTTP 客户端（Java 11 原生 HttpClient）
│   │   └── ContractAuditAgent.java      # Agent 核心，PlanAct+ReAct 推理主逻辑
│   └── tools/
│       ├── QueryComplianceRulesTool.java    # 工具1：查询合规规则库
│       ├── SearchHistoricalCasesTool.java   # 工具2：检索匹配历史案例
│       ├── SaveAuditReportTool.java         # 工具3：保存审核报告至本地文件
│       └── PersistExperienceTool.java       # 工具4：沉淀审核经验写入案例库
└── src/main/resources/
    ├── agent-config.properties          # LLM 配置文件（api-key、url、model）
    ├── compliance-rules.json            # 初始种子合规规则库（12 条规则）
    └── historical-cases.json            # 初始种子历史案例库（3 条案例）
```

> 💡 说明：
> `src/main/resources` 下是**初始种子数据**；程序首次运行会复制到 `data/` 目录；后续所有读写全部操作 `data/` 目录下文件。

## 📋 样例合同内置风险点

`contracts/样例房屋租赁合同.txt` 预先埋入典型合同缺陷，用于 Agent 审核验证：

| 序号 | 风险问题 | 对应规则 ID |
|------|----------|-------------|
| 1 | 违约责任仅约束乙方，缺少甲方违约责任 | RULE-005 |
| 2 | 缺少保密条款 | RULE-006 |
| 3 | 缺少不可抗力条款 | RULE-007 |
| 4 | 缺少争议解决条款 | RULE-008 |
| 5 | 金额仅阿拉伯数字，无中文大写 | RULE-003 |
| 6 | 缺少通知与送达条款 | RULE-012 |

## 🔧 环境要求

- JDK 17+（Java HttpClient 要求）
- Maven 3.8+
- 可用的大模型 API（兼容 OpenAI 协议：火山方舟 Ark / OpenAI / 通义千问等）

> 依赖：仅 Jackson 用于 JSON 序列化，无其他重型第三方框架。

## ⚙️ 配置说明

修改配置文件：`src/main/resources/agent-config.properties`

```properties
# 大模型 API 地址，兼容 OpenAI 格式
llm.api.url=https://ark.cn-beijing.volces.com/api/v3/chat/completions
# 你的 API 密钥，**禁止明文提交密钥到 Git/GitHub！**
llm.api.key=sk-xxxxxxxxxxxxxxxx
# 模型名称
llm.api.model=glm-4-7-251222
# LLM 开关 true/false
llm.enabled=true
```

> ⚠️ 安全提醒：
>
> 1. **严禁把真实 api-key 提交到 git 仓库**；
> 2. 推荐使用本地环境变量注入密钥，不要硬编码；
> 3. `.gitignore` 忽略本地私有配置、data 运行目录、contracts 样例文件可按需忽略。

## 🚀 快速启动运行

### 1. 编译项目

```bash
cd chap-3-1-single-agent
mvn clean compile
```

### 2. 运行 Agent 程序

```bash
mvn exec:java
```

运行交互：

- 控制台提示输入合同路径，**直接回车使用默认样例合同 `contracts/样例房屋租赁合同.txt`**；
- 也可以输入自定义合同文件的完整路径。

### 3. 完整执行流程输出参考

```
+---[LLM引擎] 已启用，模型: glm-4-7-251222
|
+---[Agent] 已注册 4 个工具:
|     - 查询合规规则
|     - 搜索历史案例
|     - 保存审核报告
|     - 沉淀审核经验
|
+---[PlanAct] 任务规划:
|     1. 读取合同
|     2. 查询合规规则
|     3. 搜索历史案例
|     4. 合同审核分析
|     5. 保存审核报告
|     6. 沉淀审核经验
|
+--- [1/6] 读取合同         --> 已读取合同 (543 字符)
+--- [2/6] 查询合规规则     --> 已获取参考数据
+--- [3/6] 搜索历史案例     --> 已获取参考数据
+--- [4/6] 合同审核分析
|     +-- ReAct 第 1 轮
|     +-- ReAct 第 2 轮
|     +-- [记忆] reportContent 已设置
|     +-- [记忆] experienceSummary 已设置
|     +-- 最终答案: 审核完成...
+--- [5/6] 保存审核报告     --> 审核报告已保存至: contracts/样例房屋租赁合同_审核报告_20260910_153000.txt
+--- [6/6] 沉淀审核经验     --> 案例已写入长期记忆（当前共 4 个案例）
|
+--- 审核完毕 (PlanAct 6/6 任务完成)
+--- [报告] contracts/样例房屋租赁合同_审核报告_20260910_153000.txt
```

### 4. 运行产物查看

1. **审核报告**：生成在 `contracts/` 目录，文件名带时间戳，不会覆盖旧报告；
2. **沉淀案例**：打开 `data/historical-cases.json`，会多出一条本次审核生成的案例记录；
3. 规则库 `data/compliance-rules.json` 不会被程序修改。

## 🧠 系统核心原理

### 1. PlanAct 6 步任务流程

```
Step1 读取合同（自动）
Step2 查询合规规则（自动调用工具）
Step3 搜索历史案例（自动调用工具）
Step4 LLM ReAct 多轮推理分析（核心）
Step5 保存审核报告（自动调用工具）
Step6 沉淀审核经验（自动调用工具）
```

### 2. ReAct 推理循环

> `思考 → 行动 → 观察`

1. LLM 输出**思考**：分析当前合同、规则、案例；
2. LLM 输出**行动**指令，Agent 解析调用对应工具；
3. 工具执行返回结果，作为**观察**反馈给大模型；
4. 循环，直到 LLM 输出 `最终答案` 结束推理；
5. 最大限制 15 轮，防止无限循环；支持对话上下文压缩。

### 3. AgentTool 统一工具契约

所有工具实现统一接口，方便新增自定义工具：

```java
public interface AgentTool {
    // 执行工具逻辑，输入全部来自 AgentMemory 记忆，返回观察字符串给 LLM
    String execute(AgentMemory memory);
    // 返回工具名称，用于 LLM 识别调用
    String getToolName();
    // 返回工具描述，注入 System Prompt 告诉 LLM 工具能力
    String getToolDescription();
}
```

### 4. 三级记忆对比

| 记忆层级 | 存储载体 | 生命周期 | 存储内容 |
|----------|----------|----------|----------|
| 短期记忆 | HashMap | 单次任务结束清空 | 合同路径、临时信息 |
| 工作记忆 | HashMap | 单次任务结束清空 | 审核计划、中间结果、审核报告正文 |
| 长期记忆 | JSON 文件 | 程序重启持久保留 | 合规规则库、历史案例库 |

## 🛠 工具列表

| 工具名称 | 功能说明 |
|----------|----------|
| 查询合规规则 | 根据分类、关键词检索匹配合规规则，无参数返回全部 12 条规则 |
| 搜索历史案例 | 根据合同类型模糊匹配历史案例，兼容扁平/嵌套两种案例数据格式 |
| 保存审核报告 | 将工作记忆中的报告内容写入本地 txt，文件名附加时间戳 |
| 沉淀审核经验 | 将本次审核风险、摘要生成新案例，追加写入历史案例 JSON |

## ❓ 常见问题排查

| 问题现象 | 排查处理 |
|----------|----------|
| LLM 未启用 | 检查 `agent-config.properties` 配置：key、url、model、enabled=true |
| 读取合同文件失败 | 确认 contracts 目录与样例 txt 文件存在；检查文件路径 |
| LLM 调用报错/超时 | 检查网络连通、API-key 有效性、账号余额；增大 HTTP 超时时间 |
| 不会生成审核报告 | LLM 没有输出 `设置记忆:reportContent=` 指令，检查 System Prompt 提示词 |
| 案例库不会新增数据 | 确认执行第 6 步【沉淀审核经验】；检查 data 目录读写权限 |

## 📈 扩展方向

本项目是单 Agent 基线版本，可以从多维度继续扩展：

1. **工具层扩展**
   - 对接数据库/MySQL，替换 JSON 文件存储规则库、案例库；
   - 新增工具：调用工商信息接口、文书 OCR 解析 PDF 合同；
2. **Agent 推理层**
   - 将固定 PlanAct 计划改为 **LLM 动态规划**，由大模型自主生成执行步骤；
   - 增加多 Agent 协作：拆分主体审核 Agent、金额条款 Agent、风险汇总 Agent；
3. **记忆层增强**
   - JSON 文件 → SQLite / MongoDB；
   - 接入向量数据库，实现案例向量相似度检索，替代简单字符串 contains 匹配；
4. **对外服务化**
   - 包装为 SpringBoot Web 接口，接收 HTTP 请求上传合同，返回审核报告；
   - 增加 Web 前端页面，文件上传、在线查看报告。

## 📚 配套讲义

详细原理、源码分步讲解见仓库内 `LECTURE.md`。

## 📄 License

MIT

---

### Git 忽略建议（`.gitignore`）

```gitignore
# Java 编译产物
target/
*.class

# IDEA
.idea/
*.iml
*.iws
*.out

# 运行时数据，本地案例库、规则库
data/

# 本地私有配置，存放真实密钥
*-dev.properties

# 系统文件
.DS_Store
Thumbs.db

# 日志文件
*.log

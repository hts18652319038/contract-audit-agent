# 智能合同审核单Agent系统 — 实操讲义

> **项目名称**：chap-3-1-single-agent
> **技术栈**：Java 17 + Maven + Jackson + LLM API
> **核心知识点**：ReAct推理、PlanAct分层决策、AgentTool统一接口、Tools模块化、三级记忆体系

---

## 一、项目全景

我们要从头搭建一个**智能合同审核Agent系统**。它的工作流程是：读入一份合同文本，查询合规规则库和历史案例库，把参考资料交给LLM做分析，自动生成审核报告保存到磁盘，并把审核经验沉淀到案例库供下次复用。

### 1.1 Agent核心结构

不管怎么包装，Agent的核心结构就三样东西：

```
+---------------+       +---------------+       +---------------+
|               |       |               |       |               |
|    LLM 大脑    |       |     工具 手脚  |       |    记忆 上下文  |
|               |       |               |       |               |
|     思考推理   |       |      查数据    |       |     临时信息    |
|     自然理解   |       |      写文件    |       |     中间结果    |
|               |       |               |       |               |
+---------------+       +---------------+       +---------------+
```

### 1.2 系统架构图

```
+-------------------------------------------------------+
|                Main.java  入口层                       |
|           解析参数 -> 创建Agent -> 启动审核              |
+---------------------------+---------------------------+
                            |
+---------------------------v---------------------------+
|          ContractAuditAgent.java  决策层               |
|                                                       |
|  +-----------+       +------------------------+       |
|  |  PlanAct  |       |    ReAct 推理循环      |        |
|  |  任务规划 |------>| 思考->行动->观察->循环 |           |
|  |  6步计划  |       | (最多15轮，LLM自主推理)|           |
|  +-----------+       +-------------+-----------+      |
+-------------------------------------------------------+
                                          | 调用工具
+-----------------------------------------v-------------+
|                AgentTool  工具层                       |
|                                                       |
|  +--------------+ +--------------+ +--------------+   |
|  |查询合规规则  | |搜索历史案例  | |保存审核报告  |   |
|  +------+-------+ +------+-------+ +------+-------+   |
|         |                |                |           |
|  +------v----------------v----------------v---------+ |
|  |        沉淀审核经验（写入长期记忆）       | |
|  +----------------------------------------------+    |
+-------------------------------------------------------+
                             |
+----------------------------v--------------------------+
|                AgentMemory  记忆层                     |
|                                                       |
|  短期记忆         工作记忆         长期记忆               |
|  (HashMap)       (HashMap)       (JSON文件)            |
|  合同路径         审核计划         合规规则库             |
|  临时信息         中间结果         历史案例库             |
|                   报告内容         (12条规则+3个案例)    |
+-------------------------------------------------------+
                            |
+----------------------------v--------------------------+
|                 LlmClient  推理引擎层                   |
|        与LLM API通信，支持多轮对话（ReAct循环）            |
|        配置文件：agent-config.properties                |
+-------------------------------------------------------+
```

### 1.3 ReAct推理模式

ReAct来自Reasoning和Acting，核心是"想一步、做一步"：

```
每一轮推理产生三样东西：

+--------+       +--------+       +--------+
|  思考   |-----> |  行动  |-----> |  观察   |
| Reason |       | Action |       |Observe |
+--------+       +--------+       +--------+
  LLM判断           调用工具           拿到结果
  当前做什么        或输出结论         反馈给LLM
                                        |
                  循环，直到输出          |
                  "最终答案"   <---------+
```

### 1.4 PlanAct分层决策

在ReAct之上加一层全局规划，把复杂任务拆成有序步骤：

```
PlanAct规划（6步）             ReAct推理（第4步内部）
----------------------        -----------------------

第1步: 读取合同（自动）        +-- 第1轮 ------------------+
第2步: 查询规则（自动）        | 思考: 检查主体信息          |
第3步: 搜索案例（自动）        | 行动: 查询合规规则          |
                            | 观察: 拿到规则列表          |
第4步: LLM审核分析            | 思考: 发现缺少...          |
       +--ReAct循环-------+  | 行动: 继续分析             |
       | 最多15轮推理      |  | 观察: ...                 |
       | 直到输出最终答案   |  | 思考: 输出审核结论          |
       +------------------+ | 最终答案: 审核完毕          |
                            +--------------------------+
第5步: 保存报告（自动）
第6步: 沉淀经验（自动）
```

### 1.5 三级记忆体系

```
生命周期对比：

短期记忆             工作记忆             长期记忆
---------------    ---------------   ---------------
用完即清             任务结束即清         程序重启也在

便签纸               笔记本               铁皮柜
撕掉就没了           项目做完可归档       一直留着

合同路径             审核计划             合规规则库
临时信息             中间结果             历史案例库
                     报告内容

HashMap              HashMap              JSON文件
值类型: String       值类型: Object       读写: Jackson
```

### 1.6 文件结构

```
chap-3-1-single-agent/
|-- pom.xml                          <-- Maven配置
|-- contracts/
|   +-- 样例房屋租赁合同.txt          <-- 待审核的合同
|-- data/
|   |-- compliance-rules.json        <-- 合规规则库（运行时数据）
|   +-- historical-cases.json        <-- 历史案例库（运行时数据）
|-- src/main/java/org/example/
|   |-- Main.java                    <-- 程序入口
|   +-- agent/
|       |-- AgentTool.java           <-- 工具统一接口
|       |-- AgentMemory.java         <-- 三级记忆体系
|       |-- LlmClient.java           <-- LLM通信客户端
|       +-- ContractAuditAgent.java  <-- 核心Agent
|   +-- tools/
|       |-- QueryComplianceRulesTool.java    <-- 查询合规规则
|       |-- SearchHistoricalCasesTool.java   <-- 搜索历史案例
|       |-- SaveAuditReportTool.java         <-- 保存审核报告
|       +-- PersistExperienceTool.java       <-- 沉淀审核经验
+-- src/main/resources/
    |-- agent-config.properties       <-- LLM配置
    |-- compliance-rules.json         <-- 规则库初始数据
    +-- historical-cases.json         <-- 案例库初始数据
```

> `data/` 目录和 `src/main/resources/` 里各有一份规则库和案例库JSON。resources里的是初始种子数据，首次运行时从classpath加载，后续的读写都走data目录。

---

## 二、创建Maven工程与项目骨架

### 2.1 创建项目目录

> 打开终端，依次执行：

```
mkdir -p chap-3-1-single-agent
mkdir -p chap-3-1-single-agent/src/main/java/org/example/agent
mkdir -p chap-3-1-single-agent/src/main/java/org/example/tools
mkdir -p chap-3-1-single-agent/src/main/resources
mkdir -p chap-3-1-single-agent/data
mkdir -p chap-3-1-single-agent/contracts
```

> 验证目录结构：

```
ls -R chap-3-1-single-agent/
```

> 预期输出：

```
chap-3-1-single-agent/:
contracts  data  src

chap-3-1-single-agent/contracts:

chap-3-1-single-agent/data:

chap-3-1-single-agent/src:
main

chap-3-1-single-agent/src/main:
java  resources

chap-3-1-single-agent/src/main/java:
org

chap-3-1-single-agent/src/main/java/org:
example

chap-3-1-single-agent/src/main/java/org/example:
agent  tools
```

### 2.2 创建 pom.xml

> 在项目根目录下创建 `pom.xml`

> 完整内容见项目中的 `pom.xml` 文件。

> 关键配置说明：
> - Java版本：17（LTS长期支持版本）
> - 唯一外部依赖：Jackson（JSON处理库）
> - 入口类：`org.example.Main`（通过 exec-maven-plugin 配置）

### 2.3 验证Maven编译

```
cd chap-3-1-single-agent
mvn compile
```

> 预期输出关键行：

```
[INFO] Nothing to compile - no sources
[INFO] BUILD SUCCESS
```

> 确认Java版本：

```
mvn -version
```

> 确认 `Java version: 17.x.x`

### 2.4 查看Jackson的ObjectMapper

> 在IDEA中展开 External Libraries，定位到：
> `Maven: com.fasterxml.jackson.core:jackson-databind:2.15.2`
> -> `com.fasterxml.jackson.databind` 包 -> `ObjectMapper` 类

> 重点看两个方法：
> - `readValue` — JSON转Java对象
> - `writeValue` — Java对象转JSON

### 2.5 创建LLM配置文件

> 在 `src/main/resources/` 目录下创建 `agent-config.properties`

> 完整内容见项目中的 `src/main/resources/agent-config.properties` 文件。

> 注意：`llm.api.key` 需要替换为有效的API密钥。

### 2.6 创建合规规则库

> 在 `src/main/resources/` 目录下创建 `compliance-rules.json`

> 完整内容见项目中的 `src/main/resources/compliance-rules.json` 文件。

> 数据结构说明——每条规则包含7个字段：

```
规则字段说明：

+----------------+-----------------------------------------+
| 字段           | 说明                                     |
+----------------+-----------------------------------------+
| id             | 唯一编号，如 RULE-001                     |
| category       | 审核维度：主体信息、金额条款等               |
| keywords       | 匹配合同文本用的关键词                      |
| riskDescription| 描述要检查什么风险                         |
| suggestion     | 发现问题后的修正建议                        |
| severity       | 风险等级：HIGH / MEDIUM / LOW             |
| required       | 对应条款是否合同中必须包含                   |
+----------------+-----------------------------------------+

覆盖的审核维度（12条规则）：

+----------+-----------+-------------------------------------+
| 维度     | 规则数量  | 检查要点                               |
+----------+-----------+-------------------------------------+
| 主体信息 | 2条       | 甲乙双方信息完整性、信用代码              |
| 金额条款 | 1条       | 大小写对照                             |
| 期限条款 | 1条       | 起止日期明确性                          |
| 违约责任 | 1条       | 双方对等                               |
| 合规条款 | 5条       | 保密、不可抗力、争议解决等                |
| 合同签署 | 1条       | 签章栏、日期、地点                       |
| 付款约定 | 1条       | 方式、账户、时间                         |
+----------+-----------+-------------------------------------+
```

### 2.7 创建历史案例库

> 在 `src/main/resources/` 目录下创建 `historical-cases.json`

> 完整内容见项目中的 `src/main/resources/historical-cases.json` 文件。

> 包含3个初始案例，覆盖3种合同类型：

```
初始案例库：

+----------+-------------+--------+--------+--------+
| 编号     | 合同类型     |  高风险 |  中风险 |   低风险 |
+----------+-------------+--------+--------+--------+
| CASE-001 | 租赁合同    |   1    |   2    |    1    |
| CASE-002 | 买卖合同    |   1    |   2    |    0    |
| CASE-003 | 服务合同    |   2    |   2    |    1    |
+----------+-------------+--------+--------+--------+
```

### 2.8 创建样例合同

> 在 `contracts/` 目录下创建 `样例房屋租赁合同.txt`

> 完整内容见项目中的 `contracts/样例房屋租赁合同.txt` 文件。

> 这份合同中故意埋了几个典型问题，供Agent发现：

```
样例合同中的埋点问题：

+------+--------------------------------------------+------------+
| 序号 | 问题                                        | 对应规则     |
+------+--------------------------------------------+------------+
| 1    | 违约责任仅约束乙方，甲方无违约责任               | RULE-005   |
| 2    | 缺少保密条款                                 | RULE-006   |
| 3    | 缺少不可抗力条款                              | RULE-007   |
| 4    | 缺少争议解决条款                              | RULE-008   |
| 5    | 金额仅有阿拉伯数字，无中文大写                  | RULE-003   |
| 6    | 缺少通知与送达条款                            | RULE-012   |
+------+--------------------------------------------+------------+
```

### 2.9 验证骨架完整性

```
ls src/main/resources/
```

> 预期看到：`agent-config.properties  compliance-rules.json  historical-cases.json`

```
ls contracts/
```

> 预期看到：`样例房屋租赁合同.txt`

```
mvn compile
```

> 预期看到：`BUILD SUCCESS`

---

## 三、搭建统一工具接口 — AgentTool

> 在 `src/main/java/org/example/agent/` 目录下创建 `AgentTool.java`

> 完整内容见项目中的 `src/main/java/org/example/agent/AgentTool.java` 文件。

> 接口定义了三个方法的契约：

```
AgentTool 接口契约：

+-----------------------------------------------------+
|                 <<interface>>                        |
|                   AgentTool                          |
+-----------------------------------------------------+
| + execute(memory: AgentMemory): String               |
| + getToolName(): String                              |
| + getToolDescription(): String                       |
+-----------------------------------------------------+
         |                |                |
         v                v                v
   执行工具的具体    返回工具名称         返回工具的
   操作，返回结果    供LLM识别调用        自然语言描述

设计要点：
- 参数统一为 AgentMemory，不传独立参数
- 工具输入全通过记忆传递（调工具前先存入工作记忆）
- 返回值统一为 String（作为 Observation 反馈给 LLM）
```

---

## 四、搭建三级记忆体系 — AgentMemory

> 在 `src/main/java/org/example/agent/` 目录下创建 `AgentMemory.java`

> 完整内容见项目中的 `src/main/java/org/example/agent/AgentMemory.java` 文件。

> 分段编写顺序：字段与构造方法 -> 短期记忆操作 -> 工作记忆操作 -> 长期记忆操作 -> 加载与保存方法 -> JSON读写私有方法

```
AgentMemory 类结构：

+--------------------------------------------------+
|                  AgentMemory                     |
+--------------------------------------------------+
| - shortTermMemory: Map<String, String>  // 短期   |
| - workingMemory:   Map<String, Object>  // 工作   |
| - complianceRules: List<Map<String,Object>> 规则  |
| - historicalCases: List<Map<String,Object>> 案例  |
| - objectMapper:    ObjectMapper          // JSON |
| - dataDir:         String                // 目录  |
+--------------------------------------------------+
| + saveShortTerm(key, value)                       |
| + getShortTerm(key): String                       |
| + clearShortTerm()                                |
| + saveWorking(key, value)                         |
| + getWorking(key): Object                         |
| + getWorking(key, type): T                        |
| + clearWorking()                                  |
| + getComplianceRules(): List                      |
| + getHistoricalCases(): List                      |
| + addHistoricalCase(caseItem)                     |
| + loadLongTermMemory()                            |
| + saveLongTermMemory()                            |
| - loadJsonList(filePath, resourcePath): List      |
| - saveJsonList(filePath, data)                    |
+--------------------------------------------------+
```

> 长期记忆的加载策略：

```
loadJsonList 加载策略：

尝试从文件系统 data/ 目录加载
         |
    文件存在？
    +---- 是 ----> 读取并返回
    |
    否
    |
    v
尝试从 classpath (resources/) 加载
         |
    资源存在？
    +---- 是 ----> 读取并返回
    |
    否
    |
    v
返回空 ArrayList（不抛异常）
```

### 4.7 编译验证

```
mvn compile
```

> 预期输出：编译2个源文件，`BUILD SUCCESS`

---

## 五、搭建LLM推理引擎 — LlmClient

> 在 `src/main/java/org/example/agent/` 目录下创建 `LlmClient.java`

> 完整内容见项目中的 `src/main/java/org/example/agent/LlmClient.java` 文件。

> 分段编写顺序：字段与构造方法 -> 配置加载 -> 多轮对话方法 -> 单轮便捷方法

```
LlmClient 类结构：

+--------------------------------------------------+
|                    LlmClient                     |
+--------------------------------------------------+
| - httpClient:    HttpClient       // Java 11     |
| - objectMapper:  ObjectMapper     // JSON        |
| - chatApiKey:    String           // API密钥      |
| - chatModel:     String           // 模型名称      |
| - chatApiUrl:    String           // API地址      |
| - enabled:       boolean          // 启用开关      |
+--------------------------------------------------+
| + LlmClient()                   // 构造，加载配置   |
| - loadConfig()                  // 读取配置        |
| + isEnabled(): boolean          // 检查是否可用     |
| + chatWithHistory(messages)     // 多轮对话        |
| + chat(systemPrompt, userMsg)   // 单轮便捷        |
+--------------------------------------------------+
```

> chatWithHistory 的请求响应流程：

```
chatWithHistory 请求流程：

+------------------+     +------------------+     +------------------+
| 构建请求体         |---->| 发送HTTP POST    |---->| 解析响应JSON       |
|                  |     |                  |     |                  |
| model            |     | Content-Type:    |     | choices[0]       |
| messages         |     |   application/   |     |   .message       |
| max_tokens: 2048 |     |   json           |     |   .content       |
| temperature: 0.3 |     | Authorization:   |     |                  |
|                  |     |   Bearer {key}   |     |                  |
+------------------+     +------------------+     +------------------+
```

### 5.5 编译验证

```
mvn compile
```

> 预期输出：编译3个源文件，`BUILD SUCCESS`

---

## 六、实现四个具体工具

### 6.1 工具层整体设计

```
四个工具与Agent的协作关系：

+----------------------------------------------------+
|              ContractAuditAgent                    |
|                                                    |
|  PlanAct 第2步        PlanAct 第3步                 |
|  查询合规规则         搜索历史案例                     |
|       |                    |                       |
|       v                    v                       |
|  +--------------+   +---------------+              |
|  | Query        |   | Search        |              |
|  | Compliance   |   | Historical    |  数据获取     |
|  | RulesTool    |   | CasesTool     |              |
|  +------+-------+   +-------+-------+              |
|         |                   |                      |
|         +-------+   +-------+                      |
|                 |   |                              |
|                 v   v                              |
|           AgentMemory                              |
|           (三级记忆读写)                             |
|                 |   ^                              |
|         +-------+   +-------+                      |
|         |                   |                      |
|         v                   v                      |
|  +------+-------+   +-------+-------+              |
|  | Save         |   | Persist       |              |
|  | AuditReport  |   | Experience    |  数据输出     |
|  | Tool         |   | Tool          |              |
|  +--------------+   +---------------+              |
|                                                    |
|  PlanAct 第5步        PlanAct 第6步                 |
|  保存审核报告         沉淀审核经验                     |
+----------------------------------------------------+
```

### 6.2 工具一：QueryComplianceRulesTool

> 在 `src/main/java/org/example/tools/` 目录下创建 `QueryComplianceRulesTool.java`

> 完整内容见项目中的 `src/main/java/org/example/tools/QueryComplianceRulesTool.java` 文件。

> 工具的匹配逻辑：

```
QueryComplianceRulesTool 匹配策略：

输入：工作记忆中的 queryCategory 和 queryKeyword
  |
  v
遍历所有规则（12条）
  |
  +-- 有 category ？ --是--> 双向 contains 匹配 category 字段
  |
  +-- 有 keyword  ？ --是--> 匹配 keywords 和 riskDescription 字段
  |
  +-- 两个都没有 ？ --是--> 标记为匹配（返回全部）
  |
  v
matched 列表为空？ --是--> 回退返回全部规则（容错设计）
  |
  否
  |
  v
格式化输出匹配到的规则文本
```

### 6.3 工具二：SearchHistoricalCasesTool

> 在 `src/main/java/org/example/tools/` 目录下创建 `SearchHistoricalCasesTool.java`

> 完整内容见项目中的 `src/main/java/org/example/tools/SearchHistoricalCasesTool.java` 文件。

> 兼容两种数据格式：

```
SearchHistoricalCasesTool 兼容的数据格式：

格式A：初始种子数据（扁平结构）
{
  "id": "CASE-001",
  "contractName": "...",
  "riskDescription": "...",    <-- 直接有风险描述
  "suggestion": "..."
}

格式B：经验沉淀写入的数据（嵌套结构）
{
  "id": "CASE-xxx",
  "contractName": "...",
  "risks": [                   <-- 风险作为数组
    { "level": "HIGH", "description": "..." },
    { "level": "MEDIUM", "description": "..." }
  ],
  "summary": "..."
}

工具输出时兼容检查两种格式，确保都能正确展示。
```

### 6.4 工具三：SaveAuditReportTool

> 在 `src/main/java/org/example/tools/` 目录下创建 `SaveAuditReportTool.java`

> 完整内容见项目中的 `src/main/java/org/example/tools/SaveAuditReportTool.java` 文件。

> 报告文件命名规则：

```
SaveAuditReportTool 文件命名：

合同文件名: 样例房屋租赁合同.txt
                |
                v
基本名: 样例房屋租赁合同
                |
                +----> 报告文件名: 样例房屋租赁合同_审核报告_20250419_143052.txt
                                     |                    |
                                     基本名               时间戳（精确到秒）

同一份合同多次审核不会互相覆盖。
报告保存在合同文件同目录下。
```

### 6.5 工具四：PersistExperienceTool

> 在 `src/main/java/org/example/tools/` 目录下创建 `PersistExperienceTool.java`

> 完整内容见项目中的 `src/main/java/org/example/tools/PersistExperienceTool.java` 文件。

> 案例ID生成策略：

```
PersistExperienceTool ID生成：

"CASE-" + System.currentTimeMillis()

示例: CASE-1745078400000

currentTimeMillis() 返回从 1970-01-01 00:00:00 UTC 到现在的毫秒数，
精确到毫秒级，基本不会重复，无需手写编号。
```

### 6.6 编译验证

```
mvn compile
```

> 预期输出：编译7个源文件，`BUILD SUCCESS`

---

## 七、搭建核心Agent — ContractAuditAgent

> 在 `src/main/java/org/example/agent/` 目录下创建 `ContractAuditAgent.java`

> 完整内容见项目中的 `src/main/java/org/example/agent/ContractAuditAgent.java` 文件。

> 这是项目中最大的文件，分段编写顺序：骨架与工具注册 -> PlanAct规划 -> 主执行入口runAudit -> 各步骤执行方法 -> System Prompt构建 -> ReAct推理循环 -> 辅助方法

### 7.1 类结构总览

```
ContractAuditAgent 类结构：

+---------------------------------------------------+
|              ContractAuditAgent                   |
+---------------------------------------------------+
| 字段：                                             |
| - memory: AgentMemory            // 三级记忆       |
| - toolPool: List<AgentTool>      // 工具列表       |
| - llmClient: LlmClient           // LLM客户端      |
| - toolRegistry: Map<String, AgentTool>            |
| - MAX_REACT_ROUNDS = 15          // 最大推理轮数    |
+---------------------------------------------------+
| 公开方法：                                          |
| + ContractAuditAgent()         // 构造，注册工具     |
| + runAudit(contractPath)       // 主执行入口        |
| + getMemory()                  // 获取记忆对象       |
+---------------------------------------------------+
| PlanAct：                                          |
| - planTask(): List<String>     // 生成6步计划       |
| - executeReadContract(...)     // 第1步：读合同     |
| - executePlanStep(...)         // 第2/3步：查数据   |
| - executeReActAnalysis(...)    // 第4步：推理       |
| - executeSaveStep(...)         // 第5/6步：保存     |
| - printPlanStep(...)           // 打印步骤进度      |
+---------------------------------------------------+
| ReAct：                                           |
| - buildSystemPrompt(...)       // 构建系统提示词    |
| - runReActLoop(systemPrompt)   // 核心推理循环      |
+---------------------------------------------------+
| 辅助：                                             |
| - safeExecute(toolName)        // 安全执行工具      |
| - parseMemorySettings(...)     // 解析指令         |
| - extractAction(...)           // 提取工具名        |
| - findTool(actionName)         // 查找工具         |
+---------------------------------------------------+
```

### 7.2 工具注册

```
initTools 注册过程：

创建4个工具实例
         |
         v
+------------------+     +------------------+
|    toolPool      |     |   toolRegistry   |
|    (List)        |     |    (Map)         |
+------------------+     +------------------+
| 查询合规规则     | --> | "查询合规规则" -> 实例 |
| 搜索历史案例     | --> | "搜索历史案例" -> 实例 |
| 保存审核报告     | --> | "保存审核报告" -> 实例 |
| 沉淀审核经验     | --> | "沉淀审核经验" -> 实例 |
+------------------+     +------------------+

toolPool:   用于遍历所有工具
toolRegistry: 用于按名称快速查找（LLM说出工具名 -> 找到实例）
```

### 7.3 runAudit 主流程

```
runAudit(contractPath) 完整执行流程：

+-- 1. 记忆初始化 ----------------------------------------------+
|  clearShortTerm()    清空短期记忆                             |
|  clearWorking()      清空工作记忆                             |
|  loadLongTermMemory() 从JSON加载规则库和案例库                 |
|  saveShortTerm("contractPath", contractPath)                |
+-------------------------------------------------------------+
         |
         v
+-- 2. 检查LLM ------------------------------------------------+
|  llmClient.isEnabled() ？                                    |
|  +---- 否 ----> 打印错误信息，return                           |
|  +---- 是 ----> 继续                                          |
+--------------------------------------------------------------+
         |
         v
+-- 3. PlanAct 任务规划 ----------------------------------------+
|  plan = planTask()     生成6步列表                             |
|  saveWorking("plan", plan)                                   |
|  打印计划：1. 读取合同  2. 查询规则  3. 搜索案例                   |
|           4. 审核分析  5. 保存报告  6. 沉淀经验                  |
+--------------------------------------------------------------+
         |
         v
+-- 4. 按计划逐步执行 ------------------------------------------+
|                                                             |
|  [1/6] executeReadContract ---> 读取合同全文                  |
|  [2/6] executePlanStep     ---> 查询合规规则（12条）           |
|  [3/6] executePlanStep     ---> 搜索历史案例（3个）            |
|  [4/6] executeReActAnalysis --> 构建 Prompt + ReAct循环      |
|  [5/6] executeSaveStep     ---> 保存审核报告到文件             |
|  [6/6] executeSaveStep     ---> 沉淀经验到案例库              |
|                                                             |
+--------------------------------------------------------------+
         |
         v
+-- 5. 输出结果 -----------------------------------------------+
|  打印审核完毕信息                                             |
|  打印报告文件路径                                             |
+-------------------------------------------------------------+
```

### 7.4 ReAct推理循环

```
runReActLoop 内部流程：

初始化对话列表
  |-- system: 系统提示词（角色+参考数据+要求+格式+合同全文）
  |-- user:   "请开始审核。参考数据已提供，直接分析合同文本。"
       |
       v
  +--- while (round < 15 && !finished) { ------------+
  |                                                    |
  |   round++                                          |
  |                                                    |
  |   对话 > 10条？ --是--> 压缩：保留system + 最近6条     |
  |                                                    |
  |   调用 LLM（chatWithHistory）                       |
  |                                                    |
  |   调用失败？ --是--> 重试（最多3次，间隔3秒）            |
  |                                                    |
  |   将LLM回复加入对话（assistant角色）                   |
  |   打印LLM思考过程                                    |
  |   解析"设置记忆:"指令 -> 写入工作记忆                   |
  |                                                    |
  |   包含"最终答案"？                                   |
  |   +-- 是 --> finished = true，退出循环               |
  |   +-- 否 -->                                       |
  |            |                                       |
  |            检查"行动:"指令                           |
  |            +-- 有 --> findTool -> execute           |
  |            |         |                              |
  |            |         +-- 成功 --> "观察: 结果"        |
  |            |         +-- 异常 --> "观察: 异常信息"     |
  |            |         +-- 不存在 --> "观察: 可用列表"   |
  |            |                                        |
  |            +-- 无 --> "观察: 请继续分析"               |
  |            |                                        |
  |            v                                        |
  |       将观察结果加入对话（user角色）                    |
  |       -> 回到循环顶部                                |
  |                                                    |
  +--- } ----------------------------------------------+
```

### 7.5 LLM与Agent的文本通信协议

```
LLM与Agent之间的通信协议：

LLM通过文本输出与Agent交互，Agent通过文本解析理解LLM的意图。

+----------------------------+     +----------------------------+
|        LLM 输出            |     |      Agent 解析             |
+----------------------------+     +----------------------------+
|                            |     |                            |
| 思考: 我来分析主体信息...     | --> | 打印到控制台                 |
|                            |     |                            |
| 行动: 查询合规规则            | --> | extractAction()            |
|                            |     | findTool() + execute()     |
|                            |     | 结果作为"观察"反馈给LLM       |
|                            |     |                            |
| 设置记忆: reportContent=     | --> | parseMemorySettings()     |
|   审核报告正文...            |     | saveWorking("reportContent",
|                            |     |   "审核报告正文...")         |
|                            |     |                            |
| 设置记忆: experienceSummary  | --> | 同上                       |
|   = 经验摘要...              |     |                           |
|                             |     |                           |
| 最终答案: 审核完毕...         | --> | 退出ReAct循环               |
|                            |     |                            |
+----------------------------+     +----------------------------+
```

### 7.6 编译验证

```
mvn compile
```

> 预期输出：编译8个源文件，`BUILD SUCCESS`

---

## 八、创建程序入口 — Main

> 在 `src/main/java/org/example/` 目录下创建 `Main.java`

> 完整内容见项目中的 `src/main/java/org/example/Main.java` 文件。

> 合同路径解析逻辑：

```
Main 合同路径解析：

命令行参数 args[0] 存在？
  +-- 是 --> 使用 args[0] 作为合同路径
  |
  否
  |
  v
使用默认路径 "contracts/样例房屋租赁合同.txt"
  |
  v
提示用户输入（Scanner）
  |
  用户输入非空？
  +-- 是 --> 使用用户输入的路径
  +-- 否 --> 保持默认路径
  |
  v
创建 ContractAuditAgent，调用 runAudit(contractPath)
```

### 8.2 最终编译验证

```
mvn compile
```

> 预期输出：编译9个源文件，`BUILD SUCCESS`

---

## 九、端到端验证

### 9.1 确认所有文件就位

```
find src -name "*.java" | sort
```

> 预期输出：

```
src/main/java/org/example/Main.java
src/main/java/org/example/agent/AgentMemory.java
src/main/java/org/example/agent/AgentTool.java
src/main/java/org/example/agent/ContractAuditAgent.java
src/main/java/org/example/agent/LlmClient.java
src/main/java/org/example/tools/PersistExperienceTool.java
src/main/java/org/example/tools/QueryComplianceRulesTool.java
src/main/java/org/example/tools/SaveAuditReportTool.java
src/main/java/org/example/tools/SearchHistoricalCasesTool.java
```

```
ls src/main/resources/
```

> 预期输出：`agent-config.properties  compliance-rules.json  historical-cases.json`

```
ls contracts/
```

> 预期输出：`样例房屋租赁合同.txt`

### 9.2 最终编译

```
mvn clean compile
```

> 预期输出：

```
[INFO] Compiling 9 source files to .../target/classes
[INFO] BUILD SUCCESS
```

> 使用 `clean compile` 而非 `compile`，先删除target目录再从头编译，排除旧编译缓存干扰。

### 9.3 启动Agent运行审核

> 确认 `agent-config.properties` 中的 `llm.api.key` 已填入有效密钥，然后运行：

```
mvn exec:java
```

> 程序提示输入合同路径时，直接按回车使用默认样例。

> 预期输出关键流程：

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
|     +-- ...
|     +-- [记忆] reportContent 已设置
|     +-- [记忆] experienceSummary 已设置
|     +-- 最终答案: 审核完成...
+--- [5/6] 保存审核报告     --> 审核报告已保存至: ...
+--- [6/6] 沉淀审核经验     --> 案例已写入长期记忆（当前共 4 个案例）
|
+--- 审核完毕 (PlanAct 6/6 任务完成)
+--- [报告] .../样例房屋租赁合同_审核报告_xxxxxxxx_xxxxxx.txt
```

### 9.4 检查生成的审核报告

```
ls contracts/
```

> 预期多出一个文件：`样例房屋租赁合同_审核报告_*.txt`

```
cat "contracts/样例房屋租赁合同_审核报告_"*.txt
```

### 9.5 检查经验沉淀

```
cat data/historical-cases.json
```

> 案例库从3个变成4个，多出的就是刚才审核的经验。

> `data/compliance-rules.json` 规则库不变（12条），系统不会自动修改规则库。

### 9.6 常见问题排查

```
问题排查速查：

+-------------+----------------------+----------------------+
| 问题         | 现象                 | 解决                  |
+-------------+----------------------+----------------------+
| LLM未启用    | [错误] LLM未启用       | 检查 agent-config.   |
|             |                      | properties 中        |
|             |                      | key/url/model        |
+-------------+----------------------+----------------------+
| 合同读取     | [错误] 无法读取合同     | 确认 contracts/ 目录  |
| 失败         |                      | 下有合同文件           |
+-------------+----------------------+----------------------+
| API持续      | [LLM] 推理失败        | 检查API密钥、网络      |
| 失败         | 重试...              | 余额                  |
+-------------+----------------------+----------------------+
| 无审核       | 第5步显示 [跳过]       | LLM未输出"设置记忆:"   |
| 报告         |                      | 指令，检查Prompt      |
+-------------+----------------------+----------------------+
```

---

## 十、总结回顾

### 10.1 完整执行流程图

```
用户启动程序，传入合同路径
        |
        v
  +-- 记忆初始化 -------------------------------------------+
  |  清空短期/工作记忆，加载长期记忆（规则+案例）                |
  +-------------------------------------------------------+
        |
        v
  +-- PlanAct 规划 -----------------------------------------+
  |  生成6步计划，存入工作记忆                                 |
  +--------------------------------------------------------+
        |
        v
  +-- Step 1: 读取合同 -------------------------------------+
  |  Files.readString() -> 拿到合同全文                      |
  +--------------------------------------------------------+
        |
        v
  +-- Step 2: 查询合规规则 -----------------------------------+
  |  QueryComplianceRulesTool -> 12条规则                    |
  +---------------------------------------------------------+
        |
        v
  +-- Step 3: 搜索历史案例 -----------------------------------+
  |  SearchHistoricalCasesTool -> 3个案例                    |
  +---------------------------------------------------------+
        |
        v
  +-- Step 4: ReAct推理循环（核心） ---------------------------+
  |                                                          |
  |  构建 System Prompt（合同+规则+案例+要求）                   |
  |        |                                                 |
  |        v                                                 |
  |  +-- ReAct循环（最多15轮） ---------------------------+    |
  |  |                                                  |    |
  |  |  +-- 思考 --+                                     |    |
  |  |  | LLM分析  |                                     |    |
  |  |  +----+-----+                                    |    |
  |  |       |                                          |    |
  |  |       v                                          |    |
  |  |  +-- 行动 --+     +-- 观察 --+                    |    |
  |  |  | 调工具？  |---->| 拿到结果 |                      |    |
  |  |  +---------+     +----+-----+                    |    |
  |  |                       |                          |    |
  |  |               有"最终答案"？                       |    |
  |  |                |是       |否                      |    |
  |  |                v         v                       |    |
  |  |           退出循环    继续下一轮                    |    |
  |  +--------------------------------------------------+    |
  |                                                          |
  |  解析"设置记忆:"指令，提取报告和经验摘要                       |
  +---------------------------------------------------------+
        |
        v
  +-- Step 5: 保存审核报告 ------------------------------------+
  |  SaveAuditReportTool -> 写入 .txt 文件                    |
  +----------------------------------------------------------+
        |
        v
  +-- Step 6: 沉淀审核经验 -----------------------------------+
  |  PersistExperienceTool -> 追加到案例库JSON                |
  +---------------------------------------------------------+
        |
        v
  输出报告路径，审核完毕
```

### 10.2 知识点与文件对照

```
5个核心知识点：

+------+-----------------+----------------------------------------+
| 编号 | 知识点            | 对应文件                                |
+------+-----------------+----------------------------------------+
| 1    | ReAct推理        | ContractAuditAgent.java (runReActLoop) |
| 2    | PlanAct分层决策  | ContractAuditAgent.java (planTask/      |
|      |                 |   runAudit)                            |
| 3    | AgentTool接口    | AgentTool.java                         |
| 4    | Tools模块化      | QueryComplianceRulesTool.java          |
|      |                 | SearchHistoricalCasesTool.java         |
|      |                 | SaveAuditReportTool.java               |
|      |                 | PersistExperienceTool.java             |
| 5    | 三级记忆体系      | AgentMemory.java                       |
+------+-----------------+----------------------------------------+
```

### 10.3 类依赖关系

```
类依赖关系图：

                      Main
                        |
                        v
              ContractAuditAgent
              /      |        \
             v       v         v
      AgentMemory  LlmClient  AgentTool (interface)
             ^                   ^
             |              /  |  \  \
             |             v   v   v  v
             |          Query Search Save Persist
             |          Rules Cases Report Experience
             |          Tool  Tool  Tool  Tool
             |                   |          |
             +-------------------+----------+
                  (工具通过memory读写数据)
```

### 10.4 扩展方向

```
可扩展的方向：

+-----------+-----------------------------------------+
| 层面       | 扩展内容                                 |
+-----------+-----------------------------------------+
| 工具层面    | 连接数据库查工商信息、调第三方API核验        |
| Agent层面  | 固定计划改为动态规划，LLM自主决定审核        |
| 记忆层面    | JSON文件换成SQLite或MongoDB              |
| 架构层面    | 单Agent扩展为多Agent协作，分领域审核        |
+-----------+-----------------------------------------+
```
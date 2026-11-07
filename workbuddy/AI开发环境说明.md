# AI 辅助开发环境说明（DeepSeek / Ollama / DeepSeek Harness）

> 本文档记录「苍穹外卖」项目接入 AI 辅助开发环境的实践过程，与简历中「AI 应用」一项对应。
> 该能力属于开发环境配置，不侵入项目业务代码，因此以文档形式沉淀。

## 1. 实践目标

在 Sky-Take-Out 后端项目上搭建 AI Agent 辅助开发环境，实现：

- 用自然语言指令辅助代码理解（读代码、讲逻辑、查调用链）；
- 按任务复杂度在不同模型之间切换（云端模型 vs 本地模型）；
- 沉淀可复现的 Maven / 目录结构分析流程。

## 2. 使用的模型与工具

| 工具 | 类型 | 用途 |
|------|------|------|
| DeepSeek Flash | 云端大模型 | 复杂任务、长上下文代码理解、整体架构分析 |
| Ollama | 本地模型运行时 | 离线 / 低成本场景，快速问答与轻量任务 |
| DeepSeek Harness | 工作区接入层 | 把项目目录接入 AI 工作区，读取与分析 Maven 配置、目录结构 |
| AI Agent | 编排能力 | 按任务复杂度路由到不同模型，串联「读取 → 理解 → 生成」 |

## 3. 环境准备

### 3.1 DeepSeek Flash（云端）

1. 注册 DeepSeek 开放平台，获取 API Key；
2. 在 AI 工具（或 DeepSeek Harness）中配置 API 地址与 Key；
3. 配置模型名称为 DeepSeek Flash（响应快，适合对话式代码问答）。

### 3.2 Ollama（本地）

1. 从 ollama.com 下载并安装 Ollama；
2. 拉取本地代码模型，例如：
   ```bash
   ollama pull qwen2.5-coder:7b
   ollama run qwen2.5-coder:7b
   ```
3. 本地模型适合离线环境与隐私敏感场景，速度取决于本机硬件。

## 4. 项目接入步骤（DeepSeek Harness）

1. **工作区接入**：将 `sky-take-out` 工程目录挂载到 DeepSeek Harness 工作区；
2. **Maven 配置读取**：让 Agent 解析根 `pom.xml` 及三个子模块
   （`sky-common` / `sky-pojo` / `sky-server`）的依赖与继承关系；
3. **目录结构分析**：让 Agent 梳理分层结构
   （`controller` → `service` → `mapper`）以及 `annotation` / `aspect` /
   `interceptor` / `task` / `websocket` 等横切模块；
4. **模型切换策略**：
   - 简单问答、字段含义 → 本地 Ollama 模型；
   - 跨模块调用链分析、复杂业务逻辑 → 云端 DeepSeek Flash。

## 5. 典型用法示例

```text
# 理解代码
"解释 OrderServiceImpl 里支付超时订单的取消逻辑"

# 定位调用链
"梳理从 OrderController 下单到 OrderMapper 插入订单的完整调用链"

# 辅助开发
"给 CategoryService 的 deleteById 方法写一份 JUnit + Mockito 单元测试"
```

## 6. 沉淀产出

- 本项目已补充 `CategoryServiceImplTest`、`EmployeeServiceImplTest` 两个单元测试类
  （13 个用例），覆盖登录、删除关联校验等核心业务路径；
- 通过 AI 辅助理解后，将「分层架构 / AOP / 缓存 / WebSocket」等知识点固化为测试与文档。

## 7. 注意事项

- API Key 属于敏感信息，**切勿**提交到代码仓库，建议通过环境变量注入；
- 本地模型效果依赖硬件与模型选择，复杂任务仍建议回退到云端模型；
- AI 生成代码需人工审查后再合入，避免引入隐藏缺陷。

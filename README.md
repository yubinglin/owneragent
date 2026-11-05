# OwnerAgent

一个用 **Spring Boot 4 + Spring AI 2 + LangGraph4j 1.9 + DeepSeek** 实现的 ReAct Agent 示例。

Agent 会自动判断是否需要调用工具（查时间、做四则运算、检索内置知识库），并把工具调用轨迹一并返回给前端。支持按 `threadId` 的多轮会话记忆。

## 技术栈

| 组件 | 版本 |
|---|---|
| Java | 17 |
| Spring Boot | 4.1.1 |
| Spring AI | 2.0.1（`spring-ai-starter-model-deepseek`） |
| LangGraph4j | 1.9.6（`langgraph4j-springai-agentexecutor`、`langgraph4j-studio-springboot`） |
| LangGraph4j Builder | 1.6-SNAPSHOT（内置独立 Node 运行副本，端口 3000） |
| 前端 | 原生 HTML/CSS/JS，无 CDN 依赖 |

## 快速开始

```bash
# 先在 src/main/resources/application.yml 中填写 spring.ai.deepseek.api-key
mvn spring-boot:run
```

浏览器打开 <http://localhost:18081> 即可对话。

也可以直接用 `curl`：

```bash
curl -s localhost:18081/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"threadId":"demo","message":"现在上海几点？"}'
```

响应示例：

```json
{
  "threadId": "demo",
  "reply": "现在是 2026-10-22 18:00:00 CST。",
  "steps": [
    {"type": "tool_call", "name": "getCurrentDateTime", "detail": "{\"zoneId\":\"Asia/Shanghai\"}"},
    {"type": "tool_result", "name": "getCurrentDateTime", "detail": "2026-10-22 18:00:00 CST"}
  ]
}
```

同一 `threadId` 继续发消息即可带上历史：

```bash
curl -s localhost:18081/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"threadId":"demo","message":"用 calculate 算一下 (12+8)*3"}'
```

`threadId` 缺省时由服务端生成 UUID，并在响应的 `threadId` 字段返回。

## Studio 可视化调试

项目通过 `langgraph4j-studio-springboot` 接入 LangGraph4j Studio，目前注册了三张图：

| instance | 图 | 输入 |
|---|---|---|
| `owneragent` | 主聊天 ReAct 图（Spring AI + DeepSeek） | `messages`（必填） |
| `support-triage` | Builder 示例「工单分流」 | `ticket`（必填） |
| `draft-review-loop` | Builder 示例「草稿评审循环」 | `topic`（必填）、`maxRounds`（可选，默认 3） |

```bash
mvn spring-boot:run
```

打开对应地址，在表单里填入输入后运行：

- <http://localhost:18081/studio.html?instance=owneragent>
- <http://localhost:18081/studio.html?instance=support-triage>
- <http://localhost:18081/studio.html?instance=draft-review-loop>

- `owneragent` 展示 `START → greeter → agent ⇄ action → responder → END`，包括 `action` 上的两条条件边 `agent` / `end`；运行时会真实调用配置好的 DeepSeek 模型和工具。
- `support-triage` 展示 `classify` 的两条 `route` 分支（`auto` → `auto_reply`、`human` → `escalate`），不调用模型，可离线反复运行。
- `draft-review-loop` 展示 `review` 的三条 `route` 分支以及 `revise → draft` 回边；`maxRounds` 留空时使用默认值 3，输入 `1` 可以观察预算耗尽走 `abort` 的路径。
- 每次运行会流式返回每个节点的输出（主图包括 `state.messages`、`greeting`、`acknowledged` 等；示例图包括 `category`、`reply`、`trail` 等），便于定位问题。
- 每个 instance 使用独立的 `MemorySaver`，同一个 Studio thread 可以多轮执行；它和 `/api/chat` 的会话记忆互不影响。

> Studio 的 UI 资源由依赖 jar 提供，但它原本也占用 `static/index.html`。本工程的聊天页需要这个路径，所以另外复制了一份 `static/studio.html` 作为 Studio 入口（`/` 仍然是聊天页）。升级 LangGraph4j 后如资源文件名（`webui.*.js`）变化，需要同步更新该文件。
>
> `/init` 和 `/stream/*` 两个端点由 `LangGraphStudioConfig` 自动注册，不需要额外写 Controller。

## Builder 可视化编排

LangGraph4j Builder 是一个独立的 Node.js 画布应用，用来拖拽设计图并生成 LangGraph4j Java 脚手架。它不是 Spring Boot 依赖，所以以独立进程运行；工程内置了可直接运行的上游副本（`tools/langgraph4j-builder/`，不依赖网络）：

```bash
./scripts/start-builder.sh
```

打开 <http://localhost:3000>：

- 在画布上添加节点、连线、条件分支，也可以从 Templates 载入示例；
- Generate Code 会生成 Java 的 `XxxBuilder.java`（图结构 stub）、`XxxTest.java`（占位实现）以及 `pom.xml` / `README.md`，可以打包成 ZIP 下载；
- 语言下拉框默认 Java，由本机内置的 generator 生成，完全离线；Python / TypeScript 选项由上游 UI 转发到外部 Cloud Run 服务，访问不了网络时只会显示为空，不影响 Java；
- Save Graph 把画布 JSON 保存到工程根目录的 `builder-workspace/`；
- 条件边的占位实现生成的是 `return ;`，需要手动改成 `return "<目标节点>"` 或 `END` 后才能编译。

端口默认 3000，可用 `./scripts/start-builder.sh --port 3001` 或 `BUILDER_PORT=3001` 覆盖（聊天页右上角的 Builder 链接按默认端口写死）。

如果本机 Docker 可用且能访问 Docker Hub，也可以改用官方镜像：

```bash
./scripts/start-builder.sh --docker
```

> 与 Studio 的区别：Studio 调试的是工程里已经写好的主聊天图；Builder 用来从零设计新图并生成代码，两者互不影响。
>
> 可运行示例（均包含 `spec.yaml`、生成的 Builder 和测试，进入目录执行 `mvn test` 即可）：
> - `builder-examples/support-triage/`：工单分流，条件边 + 两条分支；
> - `builder-examples/draft-review-loop/`：草稿评审，包含 `revise → draft` 回边、循环终止条件和执行轨迹断言。
>
> 两个目录下的 `graph.json` 可以在 Builder 里点 **Load → Choose File** 直接打开查看（首次使用需先完成 7 步引导，或用 `localStorage.setItem("initialOnboardingComplete","true")` 跳过后刷新）。
>
> 这两张示例图也已经注册进 Studio（`support-triage` / `draft-review-loop`），不启动 Builder 也能直接在应用里查看结构和运行。
>
> 内置副本来自上游 tag `v20250721`，与 Docker 镜像 `bsorrentino/langgraph4j-builder:1.6-SNAPSHOT` 同源。Maven Central 上发布的 generator 只到 1.5.12，且该版本生成的 stub 有编译错误，因此这里用同版本源码重新构建了 generator。版本、来源和升级方法见 `tools/langgraph4j-builder/README.md`。

## 配置

所有配置都在 `src/main/resources/application.yml`：

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `spring.ai.deepseek.api-key` | 占位符 | 填你自己的 DeepSeek API Key |
| `spring.ai.deepseek.base-url` | `https://api.deepseek.com` | API 地址 |
| `spring.ai.deepseek.chat.model` | `deepseek-chat` | 模型名 |
| `server.port` | `18081` | 服务端口，可用环境变量 `SERVER_PORT` 覆盖 |
| `logging.level.com.owneragent.agent.react` | `INFO` | 模型调用日志级别；设为 `WARN` 可关闭 |

> `api-key` 当前直接写在配置文件里。请勿把填了真实 Key 的 `application.yml` 提交到公开仓库；如果之后要纳入 Git，建议先把它改为从环境变量或本地未跟踪文件读取。

## 接口

### `POST /api/chat`

请求体：

```json
{"threadId": "可选，字母/数字/下划线/短横线，1-64 位", "message": "必填，≤ 4000 字符"}
```

响应体：

```json
{"threadId": "...", "reply": "...", "steps": [{"type": "tool_call | tool_result", "name": "...", "detail": "..."}]}
```

错误统一返回 JSON：`{"error": "..."}`，参数问题为 400，服务端异常为 500。

## 测试

测试完全离线，使用脚本化的假 `ChatModel`，不需要 Key，也不会访问网络：

```bash
mvn test
```

覆盖内容：

- `CalculatorTest`：运算优先级、括号、负号、小数、取模、除零与非法表达式；
- `AgentServiceTest`：工具调用循环、多轮记忆、重复消息保留、自定义 `GreeterNode` / `ResponderNode` 通道、checkpoint 序列化往返（含 `content = ""` 与 `content = null` 两种 tool-call 消息）；
- `ChatControllerTest`：正常请求、工具轨迹、空消息 / 非法 threadId / 非法 JSON 的 400 响应；
- `ModelCallLoggingChatModelTest`：模型调用日志包含请求消息与响应 tool_calls，失败时记录并原样抛出异常；
- `ModelRawPayloadLoggingInterceptorTest`：原始请求/响应报文被打印，且响应体被读取后仍可重复读取；
- `ExamplesGraphTest`：Studio 里的两张 Builder 示例图行为正确（工单两条分支、循环达标与预算耗尽两条退出路径）；
- `StudioConfigTest`：Studio 注册了 `owneragent` / `support-triage` / `draft-review-loop` 三个实例，输入参数与转换器正确，`/init` 返回的图定义包含各自的全部节点；
- `OwnerAgentApplicationTest`：完整 Spring 上下文启动。

打包：

```bash
mvn -DskipTests package
java -jar target/owneragent-1.0-SNAPSHOT.jar
```

## 实现要点

```
START → greeter（GreeterNode，写入 greeting 通道）
          │
          ▼
        agent（DeepSeek，可能返回 tool_calls）
          │  ▲
          ▼  │
        action（执行工具，把 ToolResponseMessage 写回 state）
          │
          └─ 没有 tool_calls（"end"）→ responder（ResponderNode，写入 acknowledged 通道）→ END
```

- **ReAct 图**：`AgentConfig` 直接构建自定义 `StateGraph<SimpleState>`，复用 `langgraph4j-springai-agentexecutor` 提供的 `CallModelAction`（调模型）和 `ExecuteToolsAction`（执行工具），并在首尾插入自定义的 `GreeterNode` / `ResponderNode`。`ExecuteToolsAction` 用 `"end"` 表示工具循环结束，这里通过 `EdgeMappings.to("responder", "end")` 把它映射到收尾节点。`DefaultChatService` 会把 `DemoTools` 上的三个 `@Tool` 自动挂到 `DeepSeekChatOptions`（它实现了 `ToolCallingChatOptions`）。
- **Studio 集成**：`AgentConfig.agentStateGraph` 只负责构图、不编译，应用 bean 用它编译出带 `MemorySaver` 的运行图；`StudioConfig` 另取一份未编译图交给 Studio，由 Studio 按会话自行编译。主图的字符串输入通过 `addInputStringArg("messages", ...)` 转换成 `UserMessage` 后再进图。两张 Builder 示例图由 `examples` 包中的 `SupportTriageGraph` / `DraftReviewGraph` 构图，`StudioConfig` 直接注册；`draft-review-loop` 的 `maxRounds` 通过转换器把 Studio 传来的字符串转成整数，留空时回退到默认值 3。
- **Builder 集成**：Builder 上游是独立的 Next.js 应用，无法作为 Maven 依赖嵌入 Spring Boot。工程内置其 standalone 运行产物和由同版本源码构建的 generator jar，由 `scripts/start-builder.sh` 以独立进程启动；Node 进程的工作目录下用 `workspace` 软链指向 `builder-workspace/`，Docker 模式则把同一目录挂载到 `/app/workspace`。
- **自定义状态**：`SimpleState` 继承 `MessagesState<Message>`，除 `messages` 外还声明了 `greeting`、`acknowledged` 两个标量通道。两个自定义节点只写这两个通道，不往 `messages` 里塞字符串——否则会污染发给模型的历史，也会干扰“最后一条 `AssistantMessage` 就是最终回复”的提取逻辑。
- **多轮记忆**：`MemorySaver` + `CompileConfig.releaseThread(false)`。默认 `releaseThread=true` 会在图到达 END 时释放 checkpoint，多轮记忆会直接失效。
- **重复消息**：默认 `Channels.appender` 会去重完全相同的消息；这里换成 `Channels.appenderWithDuplicate`，用户连发两句相同的话时两条 `UserMessage` 都会保留。
- **null content 归一化**：DeepSeek 的 tool-call 消息 `content` 可能是 `null`，而 LangGraph4j 的 checkpoint 序列化器会把 `null` 反序列化成字面量 `"null"`，下一轮会当成历史回复发给模型。`ToolCallContentNormalizingChatModel` 在图入口处把这类消息统一成空字符串。
- **模型调用日志**：`AgentExecutor` 直接持有 `ChatModel`、不经过 `ChatClient`，所以 Advisor 拦不到模型调用。`ModelCallLoggingChatModel` 以装饰器形式包在模型最外层，每次调用打印请求（模型参数、工具数、各条消息及其 tool_calls）与响应（耗时、finishReason、文本、tool_calls、token 用量）；调用抛异常时也会记录。日志级别由 `logging.level.com.owneragent.agent.react` 控制。
- **模型原始报文日志**：Spring AI 的 DeepSeek 集成底层用 `RestClient` 发请求。`ModelRawPayloadLoggingConfig` 通过 `RestClientCustomizer` 挂上 `ModelRawPayloadLoggingInterceptor`，把真正上线传输的原始 JSON 请求体/响应体也打印一份（含 messages、tools、usage 等）。只记录报文、不记录请求头，避免泄露 `Authorization` 里的 API Key；响应体被拦截器读取后会包一层缓存，不影响后续解析。原始报文包含完整对话历史和工具 schema、体积较大，只关闭它可设置 `logging.level.com.owneragent.agent.react.ModelRawPayloadLoggingInterceptor=WARN`。
- **流式迭代器的资源释放**：`AgentService` 用 `graph.stream(...)` 并取最后一个节点输出，`finally` 中调用 `cancel(true)`。async-generator 的 `cancel(false)` 不会关闭非 daemon executor，会导致 JVM 不退出。

## 工具

| 工具 | 作用 |
|---|---|
| `getCurrentDateTime(zoneId)` | 按 IANA 时区返回当前时间 |
| `calculate(expression)` | 手写递归下降解析器，支持 `+ - * / % ( )` 与小数 |
| `searchKnowledgeBase(query)` | 在内置的几条团队/产品资料中做关键词匹配 |

新增工具：在 `DemoTools` 里加一个 `@Tool` 方法即可，`toolsFromObject` 会自动扫描。

## 已知限制

- 记忆保存在进程内（`MemorySaver`），重启即丢失；
- `threadId` 没有鉴权，仅适合本地 demo；
- 全局 `ReentrantLock` 串行执行所有请求（保证 MemorySaver 读写正确），高并发场景应按 `threadId` 分段加锁或换持久化 checkpoint；
- 默认模型是 `deepseek-chat`。`deepseek-reasoner` 的工具调用依赖回传 `reasoning_content`，而当前 checkpoint 序列化器只保存 text/toolCalls，多轮工具链不保证正确；
- 未实现流式输出（SSE），`/api/chat` 是同步返回。

## 目录结构

```
src/main/java/com/owneragent/
├── Main.java / SimpleGraphApp.java           # 独立运行的 LangGraph4j 入门示例
└── agent/
    ├── OwnerAgentApplication.java
    ├── StudioConfig.java                     # 注册主图 + 两张 Builder 示例图
    ├── react/                                # Spring AI ReAct 聊天实现
    │   ├── AgentConfig.java                  # 自定义 StateGraph 构图 + MemorySaver
    │   ├── AgentService.java                 # 执行、串行化、结果提取
    │   ├── SimpleState.java                  # 主聊天图状态（messages + greeting/acknowledged）
    │   ├── GreeterNode.java / ResponderNode.java
    │   ├── DemoTools.java / Calculator.java  # 工具
    │   ├── ModelCallLoggingChatModel.java    # 模型调用入参/出参日志
    │   ├── ModelRawPayloadLoggingConfig.java # 挂载 RestClient 原始报文拦截器
    │   ├── ModelRawPayloadLoggingInterceptor.java
    │   └── ToolCallContentNormalizingChatModel.java
    ├── examples/                             # 不依赖 Spring AI 的 Builder 示例图
    │   ├── SupportTriageGraph.java           # 工单分流（条件边）
    │   └── DraftReviewGraph.java             # 草稿评审（回边 + 循环终止）
    └── web/                                  # REST 接口与错误处理

src/main/resources/
├── application.yml
└── static/
    ├── index.html                            # 聊天页（/，右上角有 Studio / Builder 入口）
    └── studio.html                           # LangGraph4j Studio 入口（/studio.html?instance=owneragent）

scripts/start-builder.sh                      # 启动 LangGraph4j Builder（本地副本 / Docker）
docker-compose.yml                            # Builder 的 Docker 运行方式
builder-workspace/                            # Builder 画布保存的 JSON
builder-examples/support-triage/              # Builder 生成的工单分流示例（可运行）
builder-examples/draft-review-loop/           # Builder 生成的带循环草稿评审示例（可运行）
tools/langgraph4j-builder/                    # 内置 Builder 运行副本（webui + generator jar + 来源说明）
```
# owneragent

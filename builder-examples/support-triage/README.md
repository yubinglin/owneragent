# Builder 示例：工单分流

这个示例是先用 LangGraph4j Builder 画布生成脚手架，再补上业务实现得到的可运行工程。

图结构（`spec.yaml`）：

```
START → classify ──route──┬─ auto_reply → END
                           └─ escalate   → END
```

- `classify`：根据工单内容分类；
- `route`：条件边，普通问题走 `auto_reply`，包含「退款」「投诉」的工单走 `escalate`；
- 两个分支分别写入 `reply` 通道后结束。

## 文件说明

| 文件 | 来源 |
|---|---|
| `spec.yaml` | 画布生成代码时使用的 YAML spec |
| `graph.json` | 可在 Builder 里用 **Load** 直接打开的画布文件 |
| `src/main/java/org/bsc/langgraph4j/gen/AgentWorkflowBuilder.java` | Builder 生成，仅修复了下面提到的 channels 透传问题 |
| `src/test/java/org/bsc/langgraph4j/gen/AgentWorkflowTest.java` | Builder 生成的占位测试，仅把 `return ;` 改成 `return "auto_reply";` |
| `src/test/java/org/bsc/langgraph4j/gen/SupportTriageDemoTest.java` | 手写示例：自定义 `TriageState`、节点逻辑和两条分支的断言 |
| `pom.xml` | 替换掉 Builder 生成的 parent POM，固定 LangGraph4j 1.9.6 |

## 运行

```bash
cd builder-examples/support-triage
mvn test
```

预期结果：

```
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
```

## 在 Builder 中打开这张图

1. 浏览器打开 `http://localhost:3000`（Builder 未启动时先执行 `./scripts/start-builder.sh`）。
2. 如果是第一次打开，先完成 7 步引导，否则左上角的 **Load** 是灰的：
   点 **Start** → 连续点 **Next** → 最后再点 **Start**。
   也可以打开浏览器 DevTools Console 执行下面这行后刷新，直接跳过引导：
   ```js
   localStorage.setItem("initialOnboardingComplete", "true")
   ```
3. 点 **Load** → **Choose File**，选择本目录下的 `graph.json`。
4. 画布会显示 `classify` 和两条 `route` 条件分支；顶部标题为 `SupportTriage`。
5. 需要重新生成代码时点 **Generate Code**，Java 代码由本地 generator 生成。

> `Load` 是文件选择框，不是服务端图列表；一次只能打开一张图，再 Load 另一张会替换当前画布。

## 在 Studio 中运行

主工程已经用 `com.owneragent.agent.examples.SupportTriageGraph` 实现了同样的图，并注册为 Studio 实例
`support-triage`。启动主工程（`mvn spring-boot:run`）后打开：

<http://localhost:18081/studio.html?instance=support-triage>

在 `ticket` 输入框填入工单内容即可运行：包含「退款」「投诉」的工单会走 `escalate`，其余走 `auto_reply`。
这张图不调用模型，可以离线反复执行。

## 从 Builder 重新生成

启动 Builder（`./scripts/start-builder.sh`），在画布上按 `spec.yaml` 的结构添加节点和条件边，点击
Generate Code 即可得到同样的脚手架。生成结果需要手工处理三处：

1. **条件边占位符**：生成的条件节点返回 `return ;`，必须改成某个目标节点名或 `END`，否则无法编译。
2. **parent POM**：生成的 `pom.xml` 继承 `langgraph4j-parent:1.6-SNAPSHOT`，该版本没有发布，
   直接构建会解析失败；这里换成了固定 1.9.6 的独立 POM。
3. **channels 透传**：1.6-SNAPSHOT 生成器的 `build(factory, channels)` 重载内部漏传了
   `channels`（构造 `StateGraph` 时只传了 factory）。示例里补成
   `new StateGraph<>(channels, factory)`，否则自定义 Channel/reducer 不会生效。

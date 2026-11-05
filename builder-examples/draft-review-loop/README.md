# Builder 示例：带循环的草稿评审

这个示例同样来自 LangGraph4j Builder 生成的脚手架，但比「工单分流」更复杂：图里包含一条回边，
会真正跑循环，并用 `trail` 通道记录每次节点执行，用来验证循环次数。

图结构（`spec.yaml`）：

```
START → plan → draft → review ──route──┬─ revise → draft（回边，形成循环）
                                        ├─ finalize → END
                                        └─ abort    → END
```

- `plan`：生成初稿；
- `draft`：写第 N 稿，并提升评分（每轮 +25，最高 100）；
- `review`：评审；
- `route`：条件边，三条路径：
  - 评分 ≥ 90 → `finalize`；
  - 否则若 `round ≥ maxRounds` → `abort`（防止死循环的预算保护）；
  - 否则 → `revise`；
- `revise`：给出修改意见，然后回到 `draft`，形成循环；
- `finalize` / `abort`：写入最终状态并结束。

## 文件说明

| 文件 | 来源 |
|---|---|
| `spec.yaml` | 画布生成代码时使用的 YAML spec |
| `graph.json` | 可在 Builder 里用 **Load** 直接打开的画布文件（包含回边） |
| `src/main/java/org/bsc/langgraph4j/gen/AgentWorkflowBuilder.java` | Builder 生成，仅修复了下面提到的 channels 透传问题 |
| `src/test/java/org/bsc/langgraph4j/gen/AgentWorkflowTest.java` | Builder 生成的占位测试，仅把 `return ;` 改成 `return "finalize";` |
| `src/test/java/org/bsc/langgraph4j/gen/DraftReviewDemoTest.java` | 手写示例：自定义 `ReviewState`、循环逻辑、两种退出路径的断言 |
| `pom.xml` | 替换掉 Builder 生成的 parent POM，固定 LangGraph4j 1.9.6 |

## 运行

```bash
cd builder-examples/draft-review-loop
mvn test
```

预期结果：

```
循环达标 -> rounds=2, trail=[plan, draft, review, revise, draft, review, finalize], draft=第2稿：《LangGraph4j 入门》 ✅
预算耗尽 -> rounds=1, trail=[plan, draft, review, abort], status=aborted
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
```

- `loops_until_score_is_good_enough`：`maxRounds=5`，第 1 轮 75 分 → 修改 → 第 2 轮 100 分 →
  `finalize`；`trail` 里能看到 `draft`、`review` 各出现两次。
- `aborts_when_round_budget_is_exhausted`：`maxRounds=1`，第 1 轮 75 分未达标且预算耗尽 →
  `abort`。

## 在 Builder 中打开这张图

1. 浏览器打开 `http://localhost:3000`（Builder 未启动时先执行 `./scripts/start-builder.sh`）。
2. 如果是第一次打开，先完成 7 步引导，否则左上角的 **Load** 是灰的：
   点 **Start** → 连续点 **Next** → 最后再点 **Start**。
   也可以打开浏览器 DevTools Console 执行下面这行后刷新，直接跳过引导：
   ```js
   localStorage.setItem("initialOnboardingComplete", "true")
   ```
3. 点 **Load** → **Choose File**，选择本目录下的 `graph.json`。
4. 画布会显示 `plan → draft → review` 的三条 `route` 分支，以及 `revise → draft` 的回边；
   顶部标题为 `DraftReviewLoop`。
5. 需要重新生成代码时点 **Generate Code**，Java 代码由本地 generator 生成。

> `Load` 是文件选择框，不是服务端图列表；一次只能打开一张图，再 Load 另一张会替换当前画布。

## 在 Studio 中运行

主工程已经用 `com.owneragent.agent.examples.DraftReviewGraph` 实现了同样的图，并注册为 Studio 实例
`draft-review-loop`。启动主工程（`mvn spring-boot:run`）后打开：

<http://localhost:18081/studio.html?instance=draft-review-loop>

输入 `topic` 后运行；`maxRounds` 可选，留空默认 3。填 `5` 可以看到循环两轮后 `finalize`，
填 `1` 则第一轮 75 分未达标、预算耗尽后走 `abort`。每个节点的输出里包含 `trail`，能直观看到回边执行了几次。

## 循环场景的两个注意点

1. **回边直接写成普通边**：`revise → draft` 就是一条普通 `addEdge`，LangGraph4j 不会因此报错，
   但必须像 `route` 一样提供退出条件，否则会一直循环下去。示例用 `maxRounds` 兜底。
2. **trail 要用 `appenderWithDuplicate`**：`Channels.appender(...)` 底层是
   `ReducerDisallowDuplicate`，会按值去重；循环中第二轮 `List.of("draft")`、`List.of("review")`
   与第一轮相同，会被静默丢弃，`trail` 看起来像没循环。改用
   `Channels.appenderWithDuplicate(ArrayList::new)` 才能保留重复执行记录。

## 从 Builder 重新生成

启动 Builder（`./scripts/start-builder.sh`），在画布上按 `spec.yaml` 添加 6 个节点、3 条条件分支
和 `revise → draft` 回边，点击 Generate Code 即可得到同样的脚手架。生成结果需要手工处理三处：

1. **条件边占位符**：生成的条件节点返回 `return ;`，必须改成某个目标节点名或 `END`，否则无法编译。
2. **parent POM**：生成的 `pom.xml` 继承 `langgraph4j-parent:1.6-SNAPSHOT`，该版本没有发布，
   直接构建会解析失败；这里换成了固定 1.9.6 的独立 POM。
3. **channels 透传**：1.6-SNAPSHOT 生成器的 `build(factory, channels)` 重载内部漏传了
   `channels`（构造 `StateGraph` 时只传了 factory）。示例里补成
   `new StateGraph<>(channels, factory)`，否则自定义 Channel/reducer 不会生效。

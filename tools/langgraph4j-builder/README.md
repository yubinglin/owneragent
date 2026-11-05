# LangGraph4j Builder（工程内置运行副本）

这里是 [langgraph4j-builder](https://github.com/langgraph4j/langgraph4j-builder) 的可运行副本，
让 OwnerAgent 可以在本地通过画布设计 LangGraph4j 图，并生成 Java 脚手架代码。

## 版本与来源

| 项目 | 值 |
|---|---|
| 上游仓库 | <https://github.com/langgraph4j/langgraph4j-builder> |
| 上游版本 | tag `v20250721`，commit `e79df6e` |
| Docker 镜像标签 | `bsorrentino/langgraph4j-builder:1.6-SNAPSHOT`（Docker Hub 上仅有的两个标签之一） |
| 许可证 | Apache-2.0，见 [LICENSE](LICENSE) |

- `webui/`：上游 `builder-webui` 的 Next.js standalone 运行产物（`server.cjs`、`.next`、
  `node_modules`、`public`）。不包含上游源码和作者本机的 `.env`。
- `generator-1.6-SNAPSHOT-jar-with-dependencies.jar`：用上游同一 commit 的 `src/main`
  构建。Maven Central 上的 `org.bsc.langgraph4j:generator` 只发布到 **1.5.12**，
  而 1.5.12 生成的 stub 有错误（链式方法返回了不存在的类、缺少 `Map`/`Channel` import），
  所以这里没有使用已发布的 1.5.12。
- `generator-pom.xml`：构建上面这个 jar 时使用的无父 POM 版本（上游 `pom.xml` 依赖
  未发布的 `langgraph4j-parent:1.6-SNAPSHOT`，直接 `mvn package` 会解析失败）。

## 启动

```bash
# 工程根目录执行
./scripts/start-builder.sh
# 打开 http://localhost:3000
```

也可以使用 Docker（需要能访问 Docker Hub）：

```bash
./scripts/start-builder.sh --docker
```

## 代码生成

语言下拉框默认 **Java**，由 `generator-1.6-SNAPSHOT-jar-with-dependencies.jar`
在本机生成，无需联网。UI 里的 Python / TypeScript 选项会把请求转发到上游的
Google Cloud Run 服务，访问不了外网时这两种语言的预览为空，不影响 Java。

## 重新构建 generator

```bash
git clone https://github.com/langgraph4j/langgraph4j-builder.git /tmp/lg4j-builder
cd /tmp/lg4j-builder
cp /path/to/owneragent/tools/langgraph4j-builder/generator-pom.xml ./pom-standalone.xml
mvn -f pom-standalone.xml -Dmaven.test.skip=true package
cp target/lg4j-generator-local-1.6-SNAPSHOT-jar-with-dependencies.jar \
   /path/to/owneragent/tools/langgraph4j-builder/generator-1.6-SNAPSHOT-jar-with-dependencies.jar
```

升级 `webui/` 时，从上游 `builder-webui` 复制 `server.cjs`、`.next`、`node_modules`、`public`
即可（保持 `workspace` 软链指向工程根目录的 `builder-workspace/`）。

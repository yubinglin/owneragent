#!/usr/bin/env bash
#
# 启动 LangGraph4j Builder（可视化图编排 + Java 脚手架生成）。
#
# 默认使用工程内自带的运行副本（不需要 Docker、不需要联网）：
#   ./scripts/start-builder.sh
#
# 也可以改用官方 Docker 镜像（需要能访问 Docker Hub）：
#   ./scripts/start-builder.sh --docker
#
# 端口默认 3000，可用 --port 或 BUILDER_PORT 覆盖。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="local"
PORT="${BUILDER_PORT:-3000}"

usage() {
    sed -n '2,11p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --docker)
            MODE="docker"
            shift
            ;;
        --port)
            PORT="${2:?--port 需要一个端口号}"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "未知参数: $1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

if [[ "$MODE" == "docker" ]]; then
    if ! command -v docker >/dev/null 2>&1; then
        echo "未找到 docker 命令。请改用本地模式：./scripts/start-builder.sh" >&2
        exit 1
    fi
    if ! docker info >/dev/null 2>&1; then
        echo "Docker daemon 未运行。请启动 Docker Desktop，或改用本地模式：./scripts/start-builder.sh" >&2
        exit 1
    fi
    mkdir -p "$ROOT/builder-workspace"
    echo "启动 LangGraph4j Builder（Docker）: http://localhost:${PORT}"
    BUILDER_PORT="$PORT" exec docker compose -f "$ROOT/docker-compose.yml" up
fi

WEBUI="$ROOT/tools/langgraph4j-builder/webui"
JAR="$ROOT/tools/langgraph4j-builder/generator-1.6-SNAPSHOT-jar-with-dependencies.jar"

if ! command -v node >/dev/null 2>&1; then
    echo "未找到 node 命令（需要 Node.js 18+）。也可以改用 Docker：./scripts/start-builder.sh --docker" >&2
    exit 1
fi
for path in "$WEBUI/server.cjs" "$JAR"; do
    if [[ ! -e "$path" ]]; then
        echo "内置 Builder 文件缺失: $path" >&2
        exit 1
    fi
done

mkdir -p "$ROOT/builder-workspace"
# UI 的「保存图」接口会写入进程工作目录下的 workspace/，这里指向工程根目录的 builder-workspace/
ln -sfn ../../../builder-workspace "$WEBUI/workspace"

echo "启动 LangGraph4j Builder（本地）: http://localhost:${PORT}"
echo "画布保存目录: $ROOT/builder-workspace"
cd "$WEBUI"
# HOSTNAME=127.0.0.1 避免把开发工具暴露到局域网；需要局域网访问时自行覆盖。
HOSTNAME="${BUILDER_HOST:-127.0.0.1}" \
LANGRAPH4J_GEN="$JAR" \
PORT="$PORT" \
exec node server.cjs

#!/bin/bash
# ai-dev-agent（codeFlow）Docker 一键部署脚本
set -e

echo "=========================================="
echo "  ai-dev-agent Docker 部署"
echo "=========================================="

# 检查 .env 文件
if [ ! -f .env ]; then
    echo "⚠️  未找到 .env 文件，正在从模板创建..."
    cp .env.example .env
    echo "📝 请编辑 .env 文件填入实际配置："
    echo "   vim .env"
    echo ""
    echo "必填项：CODE_FLOW_KEY（阿里云百炼 API Key）、MYSQL_ROOT_PASSWORD"
    exit 1
fi

# 检查服务器专属配置
if [ ! -f config/application.yml ]; then
    echo "⚠️  未找到 config/application.yml，从模板创建..."
    mkdir -p config
    cp config/application.example.yml config/application.yml
    echo "📝 请编辑 config/application.yml 配置目标项目（也可留空，后续在对话里接入）："
    echo "   vim config/application.yml"
    exit 1
fi

# 检查 Docker 是否安装
if ! command -v docker &> /dev/null; then
    echo "❌ 未安装 Docker，请先安装："
    echo "   curl -fsSL https://get.docker.com | sh"
    exit 1
fi

echo "🔨 构建镜像..."
# 复用依赖下载层缓存，加快部署
docker compose build

echo "🚀 启动服务..."
docker compose up -d

echo ""
echo "=========================================="
echo "  ✅ 部署完成！"
echo "=========================================="
echo ""
echo "  前端访问: http://服务器IP"
echo "  后端 API: http://服务器IP:8080"
echo "  MySQL:    localhost:3306"
echo "  Redis:    localhost:6379"
echo ""
echo "  查看日志: docker compose logs -f"
echo "  停止服务: docker compose down"
echo "  重启服务: docker compose restart"
echo ""
echo "  目标项目: 把项目放到 ./projects 目录，打开页面在「AI 对话」中让 AI 接入，或在「代码生成」页签选择"
echo ""
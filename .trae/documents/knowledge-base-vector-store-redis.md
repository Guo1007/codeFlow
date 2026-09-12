# 知识库向量化存储（Redis 向量库 + 对话 RAG）

## Context（背景）

codeFlow 目前**没有知识库**：无配套表/实体/页面，唯一"上传文档"在开发工作台、仅用于需求文档解析，非向量化。AI 只接了对话模型（chat/streaming），未配 embedding；Redis 仅作聊天消息字符串缓存，且 `redis:7-alpine` 无向量能力。

目标：新增**知识库**，支持
1. **手动上传文档**供对话问答（RAG），
2. **项目产出文档在"定稿"时自动向量化入库** —— 只在最终决定通过的版本入库，避免刚生成未定稿就存、造成重复存储。

向量数据库**使用 Redis（RediSearch）**，embedding 模型与 Redis 镜像参照 `D:\FurnitureSystem`（用户指定）。

## 参考（用户指定）

- Redis 镜像：`redis/redis-stack-server:7.4.0-v8`（含 RediSearch），healthcheck `redis-cli ping`（见 D:\FurnitureSystem\docker-compose.yml）
- embedding 模型：`langchain4j.open-ai.embedding-model`，`base-url: https://dashscope.aliyuncs.com/compatible-mode/v1`，`model-name: qwen3.7-text-embedding`，`max-segments-per-batch: 10`，api-key（见 D:\FurnitureSystem\application.yml L85-L91）

## 设计决策

- **向量库**：引入 langchain4j `RedisEmbeddingStore`（RediSearch 的 FT.CREATE/FT.SEARCH），索引名 `kb_embeddings`，每文档切片各存一段向量，带 `documentId/projectId` 元数据，支持按 project 过滤检索。（依赖版本与 langchain4j 1.19.0-beta29 对齐，实施时选官方 `langchain4j-redis` 或 `langchain4j-community-redis`，以本地能编译为准）
- **元数据在 MySQL**：新建 `kb_document` 表（documentId 关联向量段、project/type/version/name/content、embedding 状态、creator/时间、逻辑删除），向量段实体放 Redis。这样列表/去重/删除管理走 MySQL，检索走 Redis。
- **去重/幂等**：自动索引以 `(projectId + docType + version)` 为关键字；已入库的同版本跳过，新定稿版本（version+1）替换旧版本段再入库。手动上传以上传时的 key 唯一。
- **入库时机 = 定稿**：`DevDesignServiceImpl` 设计文档定稿（FINALIZED）成功处调用知识库索引；不轮询、不在生成/未定稿阶段入库。
- **对话 RAG**：对话请求（同步 + 流式）在调用 AI 前，用当前项目 + 用户输入做向量检索，命中片段拼入 AI 上下文（只对"手动上传+该项目文档"及"该项目定稿文档"检索）。
- **按项目隔离**：知识库文档带 project 归属；普通用户只见自己（creator），沿用现有 UserContext。

## 实施清单

### 后端
1. **pom.xml**：新增 langchain4j Redis embedding store 依赖（`langchain4j-redis` 或 `langchain4j-community-redis`）。
2. **application.yaml**：`langchain4j.open-ai` 下新增 `embedding-model`（base-url / model-name=qwen3.7-text-embedding / api-key=${EMBEDDING_KEY} / max-segments-per-batch）。`.env.example` 增加 `EMBEDDING_KEY=`。
3. **sql/dev.sql**：新增 `kb_document` 表（id/document_id/project/type/version/name/content/embedding_status/creator/create_time/update_time/deleted）。
4. **数据对象**：`dal/entity/KbDocument` + `dal/mapper/KbDocumentMapper`。
5. **服务 `KnowledgeBaseService` + Impl**：
   - `uploadDocument(userId, project, name, content)`：切分 → 逐段 embedding → RedisEmbeddingStore.add；MySQL 记文档。
   - `deleteDocument(id, userId)`：删 MySQL 记录 + 对应向量段。
   - `indexDesign(projectId)`：设计文档定稿版本向量化入库（幂等，替换旧版本段）。
   - `search(project, query, topK)`：query embedding → Redis 检索 → 返回文本片段。
   - embedding 维度：用 embeddingModel 运行时取（embed 一段得到 dimension）构造 store。
6. **DevDesignServiceImpl**：定稿成功分支调用 `indexDesign(projectId)`。
7. **对话注入**：`ProjectChatServiceFactory`/`ChatAiService` 在用 chat 前调用 `search` 把命中片段拼入 prompt 或 system 上下文；同步 `generate` 与流式 `generate-stream` 均生效。
8. **Controller**：新增 `KnowledgeBaseController`（上传/列表/删除/检索，用户隔离）。
9. **docker-compose.yml**：redis 镜像改 `redis/redis-stack-server:7.4.0-v8`；添加 `EMBEDDING_KEY` 透传 env。

### 前端（ai-dev-agent-ui）
10. **api/knowledge/index.ts**：封装上传/列表/删除/检索。
11. **App.vue**：`activeView` 增 `'knowledge'`，左侧导航加"知识库"入口。
12. **views/knowledge/index.vue**：上传（文件名/内容/所属项目可选）、文档列表（名称/项目/类型/版本/时间/删除），风格沿用 theme.css。
13. **对话页**：无需改动（检索在服务端注入）。

## 复用现有代码
- `UserContext.getUserIdStr()`（归属）、`JwtUtil` 鉴权通过现有 `LoginInterceptor` 自动覆盖。
- langchain4j 既有 `langchain4j-open-ai-spring-boot4-starter` 承载 embedding model；redis 连接复用已有 spring-data-redis 配置。
- yudao 风格 Mapper/实体、前端 `request.ts`/`del`、`theme.css` 变量。

## 验证（Verification）
1. **编译**：前后端编译通过。（编译由用户触发）
2. **Redis**：`docker compose up -d redis` 后 `redis-cli FT._LIST` 应有 `kb_embeddings`；索引创建于首次入库。
3. **手动上传**：前端知识库页上传一篇文档 → 列表出现；在对话中问相关问题，AI 引用文档内容作答（可用同一项目下不含该文档口径对比）。
4. **自动索引**：完成一个开发任务并让设计文档定稿 → 知识库出现该项目 design 文档（version 标记）；再次定稿新版本 → 替换而非重复追加。
5. **隔离/删除**：不同用户看不到彼此上传；删除文档后检索不再命中。
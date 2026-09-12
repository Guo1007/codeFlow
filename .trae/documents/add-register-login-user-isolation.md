# 注册登录 + 用户数据隔离方案

## Context（背景）

当前 codeFlow 是**无认证的单用户系统**：前端 `request.ts` 注释明确"登录暂缓、去 token"，后端用户写死为 `DEFAULT_USER = "admin"`（ChatController）和 `DEFAULT_CREATOR = "admin"`（DevProjectServiceImpl），所有会话/项目列表均不按用户过滤。数据库也没有用户表。

用户希望"最多加个注册登录，保证用户隔离就行"——即**不做完整 RBAC**，仅需：
1. 用户注册 + 登录（签发 token）；
2. 不同用户的数据互相隔离（对话会话 + 开发项目），**目标项目注册表保持全局可见**（它是服务器端需手动登记的目录资源）。

已与用户确认两项决策：
- 认证方式：**轻量 JWT 自研**（不引入 Spring Security 完整体系）；
- 隔离范围：**对话 + 开发项目都隔离**，目标项目注册表全局。

## 现状关键点（来自代码调查）

- 后端无任何认证拦截器；`ChatController` 通过 `buildMemoryId()` 用固定 `DEFAULT_USER + 项目 + conversationId` 构建 Redis 记忆 Key；`listSessions`/`deleteSession`/`history` 仅按 project/conversationId 过滤，无用户维度。
- `ChatHistoryServiceImpl` 建会话写 `creator = "admin"`；`listSessions` 不按用户过滤。
- `DevProjectServiceImpl.createProject` 写 `creator = "admin"`；`listProjects` 查全部未过滤；`getProjectDetail` 不校验归属。
- 各业务表使用 yudao `BaseDO` 风格，含 `creator/deleted` 字段，**无独立 userId 列**。
- `pom.xml` 无 jwt/security/hutool 依赖。
- 前端无 vue-router，`App.vue` 用 `activeView: 'chat' | 'workbench'` 变量切换两个视图（ChatView/WorkbenchView），KeepAlive 缓存。

## 设计决策

- **用户标识复用 `creator` 字段**（存 userId 字符串），不新增独立 userId 列——与 yudao 通用字段一致，改动最小。
- **密码用 BCrypt 加密**：仅引入轻量 `spring-security-crypto`（只取 BCrypt 工具，不引完整安全框架）。
- **JWT**：引入 `io.jsonwebtoken:jjwt`（api/impl/jackson），token 载荷含 `userId`，过期时间设 24h。
- **登录用户上下文**：`LoginInterceptor` 从 `Authorization: Bearer <token>` 解析 userId → 存入 `ThreadLocal` 的 `UserContext`；`ChatController`/`DevProjectServiceImpl` 从 `UserContext` 取当前用户替换写死的 admin。
- **未认证全局处理**：拦截器对未登录返回 401，前端 request.ts 响应拦截器统一跳回登录页。
- 目标项目（`agent_target_project`、`/chat/projects`）**不做用户隔离**，保持所有人可见。

## 实施清单

### 后端

1. **pom.xml**：新增 `spring-security-crypto`（BCrypt）与 `io.jsonwebtoken:jjwt` 依赖。

2. **用户表 `sys_user`**（append 到 `sql/dev.sql`）：
   `id` 自增、`username`(唯一)、`password`(BCrypt 密文)、`nickname`、`create_time`、`deleted`(逻辑删除)。

3. **用户数据对象**：
   - `dal/entity/SysUser.java` + `dal/mapper/SysUserMapper.java`（MyBatis-Plus 框架风格，与现有 Mapper 一致）。

4. **JWT 工具 + 认证接口**：
   - `common/UserContext.java`（ThreadLocal：getUserId/getUsername 静态方法）。
   - `component/JwtUtil.java`（generate(userId)/parse(token)）。
   - `service/AuthService` + `service/impl/AuthServiceImpl`：`register`（用户名唯一校验 + BCrypt + 插入 + 签发 token）、`login`（校验密码 + 签发 token）。
   - `controller/AuthController`：`POST /auth/register`、`POST /auth/login`（返回 token + 用户信息）。
   - `config/JwtProperties`（secret/expire 配置，放 `application.yaml`）。

5. **拦截器（鉴权）**：
   - `config/LoginInterceptor`：放行 `/auth/login`、`/auth/register`；其余接口从请求头解析 token → 校验 → 写入 `UserContext`，失败抛 401。
   - `config/WebConfig`（实现 `WebMvcConfigurer`）注册拦截器。

6. **对话用户隔离**（`ChatController.java` + `ChatHistoryServiceImpl.java`）：
   - 移除 `DEFAULT_USER`，改为从 `UserContext` 取当前 userId 字符串。
   - `buildMemoryId()` 用当前 userId 替换固定值。
   - 会话创建/`listSessions`/`deleteSession`/`history` 增加按 userId 过滤；删除时校验会话归属（非本人报错）。

7. **开发项目用户隔离**（`DevProjectServiceImpl.java`）：
   - `createProject`：`creator` 改用当前 userId。
   - `listProjects`：加 `eq(creator, userId)` 过滤。
   - `getProjectDetail`：校验 `creator == 当前 userId`，否则拒绝。

### 前端（`ai-dev-agent-ui`）

8. **`src/api/request.ts`**：请求拦截器注入 `localStorage['cf-token']` 到 `Authorization` 头；响应拦截器对 401 清空 token 并切回登录态。

9. **`src/api/auth/index.ts`**：封装 `login`、`register`、`logout`。

10. **登录/注册页 `src/views/login/index.vue`**：登录/注册切换表单（用户名、密码、昵称），风格与现有 `theme.css` 一致；成功后将 token 写入 localStorage。

11. **`App.vue`**：外层包一层登录态判断——未登录渲染 `<LoginView />`，登录后渲染现有 shell；并提供**登出入口**（侧边栏底部添加用户头像/登出按钮），便于演示多用户切换。

## 需复用的现有代码

- yudao 风格实体与 `BaseMapperX`/LambdaQueryWrapper（`DevProjectMapper`/`ChatHistoryServiceImpl` 已示范，如 `chatMapper.selectList`）。
- 前端 `request.ts` 统一 `ApiResponse` 解包、`post/get` 封装。
- `appearance`/`theme.css` 的 CSS 变量（登录页复用配色）。

## 验证（Verification）

1. **后端编译**：`mvn -q -DskipTests compile` 通过。（编译由用户自行触发，见用户偏好）
2. **数据库**：执行新增后的 `sql/dev.sql`（或增量建 `sys_user` 表）。
3. **端到端**：
   - 启动前后端，浏览器打开站点应先见登录页；
   - 注册用户 A、B 两个账号；
   - A 登录创建若干会话 + 一个开发项目；
   - 退出登录，用 B 登录，确认看不到 A 的会话列表和开发项目；
   - 目标项目下拉列表仍为全局（B 能看到已注册的目标项目）。
4. **未登录访问受保护接口**：不带 token 调任意业务接口应返回 401，前端自动跳回登录页。
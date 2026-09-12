# 接入项目删除 + 历史对话物理删除

## Context（背景）

codeFlow 的"接入项目/目标项目"由两部分合并提供（`TargetProjectRegistry`）：
1. yaml 静态配置（`application.yaml` 的 `agent.targets`，改配置重启生效）——当前仅有 `ruoyi-vue-pro` 一个示例项；
2. 数据库 `agent_target_project` 动态注册（对话里 AI 调用接入工具写入，立即生效，重启不丢）。

用户需求：
1. **接入项目加删除**：从下拉框选择删除，**只删动态注册到 DB 的**；yaml 静态示例项目不可删，并把 yaml 里的示例项目标注为"接入示例，不要在这里工作"。
2. **删除交互**：下拉框底部固定项"删除接入项目…"，点击后弹窗选项目 + 确认删除。
3. **历史对话删除改为物理删除**：`agent_chat_session` 与 `agent_chat_message` 均物理删除。

## 现状关键点

- `TargetProjectRegistry`（[TargetProjectRegistry.java](file:///d:/workplace/project/Glimcy/codeFlow/src/main/java/com/aidev/agent/config/TargetProjectRegistry.java)）：内存 `targets` 列表 + `byName` map；`list()/resolve()/register()` 已存在，**无 remove**；register 写入 DB 时 `entity.setCreator("admin")`（上轮接入用户隔离后此处置于全局，可不改）。
- 前端数据源：`GET /chat/projects`（[ChatController.java](file:///d:/workplace/project/Glimcy/codeFlow/src/main/java/com/aidev/agent/controller/ChatController.java) getTargetProjects）把 `TargetProjectRegistry.list()` 转 `TargetProjectVO{name,path,hasProfile}`。
- 历史删除：`ChatHistoryServiceImpl.deleteSession`（[ChatHistoryServiceImpl.java](file:///d:/workplace/project/Glimcy/codeFlow/src/main/java/com/aidev/agent/service/impl/ChatHistoryServiceImpl.java#L104-L113)）会话记录 `setDeleted(true)` 逻辑删除 + 消息物理删除。需改全物理删除。
- 前端下拉：`chat/index.vue` 顶部项目下拉、`workbench/index.vue` 代码生成目标项目下拉，都绑定 `/chat/projects`。

## 设计决策

- **示例项目标记**：给 `AgentProperties.TargetProject` 加 `example` 字段（默认 false），yaml 里 `ruoyi-vue-pro` 设 `example: true`。`TargetProjectVO` 透出 `example`，前端对示例项显示"接入示例，勿在此工作"且从删除候选排除。
- **删除边界**：`TargetProjectRegistry.remove(name)` —— 若 DB `agent_target_project` 无对应行（yaml 静态/示例，或不存在命名）则抛 `ServiceException`（"该接入项目由配置管理，不可删除"）；否则物理删除 DB 行 + 从内存 `targets`/`byName` 移除 + 清 `profileCache`。
- **删除接口**：新增 `DELETE /chat/project?name=`，放 ChatController。
- **历史物理删除**：`deleteSession` 会话记录改用物理删除（`deleteById`），消息保持物理删除。
- **删除入口**：放对话页顶部下拉底部（接入项目为全局单一集合，一个入口即可）；示例提示在对话页与工作台两处下拉都加。

## 实施清单

### 后端
1. **ApplicationProperties.TargetProject**：加 `private Boolean example = false;`（[AgentProperties.java](file:///d:/workplace/project/Glimcy/codeFlow/src/main/java/com/aidev/agent/config/AgentProperties.java)静态类）。
2. **application.yaml**：`agent.targets` 的 `ruoyi-vue-pro` 加 `example: true`，并更新注释为"示例接入项目，勿在此工作；新增目标项目请追加配置或对话接入"。
3. **TargetProjectRegistry**：
   - `validateAndAdd` 透传 `target.getExample()`；
   - `loadFromDatabase` 动态项目 `example=false`；
   - 新增 `remove(String name)`：校验 DB 存在对应行（`targetProjectMapper.selectCount(eq name)`），否则抛 ServiceException；存在则 `delete` 该行，`synchronized(targets)` 移除内存项、`byName.remove`、`profileCache.remove`。
4. **TargetProjectVO**：加 `private Boolean example;`。
5. **ChatController**：
   - `getTargetProjects` 构造 VO 时 `setExample(target.getExample())`；
   - 新增 `@DeleteMapping("/project") ApiResponse<Boolean> removeTargetProject(@RequestParam String name)` → `registry.remove(name)`。
6. **ChatHistoryServiceImpl.deleteSession**：把 `session.setDeleted(true); sessionMapper.updateById(session);` 改为物理删除 `sessionMapper.deleteById(session.getId());`（消息物理删除保持不变）。

### 前端（ai-dev-agent-ui）
7. **api/chat/index.ts**：
   - `TargetProjectVO` 加 `example: boolean`；`TargetProjectVO` 处补 `isExample`（沿用现有字段命名 `hasProfile` 风格，用 `example`）；
   - 新增 `removeTargetProject(name)` → `del('/chat/project', { name })`。
8. **views/chat/index.vue** 顶部下拉：
   - 示例项在展开选项中显示 `name（path）（接入示例，勿在此工作）`；
   - 下拉底部固定项"删除接入项目…"，点击打开删除对话框：目标项目下拉列出**可删**（非 example）项目 + 确认按钮 → 调 `removeTargetProject` → 成功后刷新 `projects`。
9. **views/workbench/index.vue** 代码生成下拉：示例项加同样的"接入示例，勿在此工作"提示（不做删除入口）。

## 复用现有代码
- 删除接口复用 `TargetProjectRegistry` bean；物理删除用现有 `AgentTargetProjectMapper`（BaseMapper 自带 delete/selectCount），不新增 Mapper。
- 前端复用 `del` 封装（request.ts）、现有下拉/弹窗（`el-select`/`el-dialog`）结构。

## 验证（Verification）
1. **编译**：前后端编译通过；数据库需已含 `agent_target_project`（已有）与上轮新增的 `sys_user`。
2. **yaml 示例**：启动后 `GET /chat/projects` 返回 `ruoyi-vue-pro` 且 `example=true`；前端下拉显示示例提示，删除弹窗候选列表不包含它。
3. **动态注册 + 删除**：对话中接入一个新项目（或手动往 `agent_target_project` 插一行），下拉出现；底部"删除接入项目…"选择它确认删除 → `GET /chat/projects` 不再返回、DB 该行被物理删除。
4. **删 yaml/示例**：调用删除接口删示例项目应返回"不可删除"业务提示。
5. **历史物理删除**：删除一个会话后，`agent_chat_session` 与 `agent_chat_message` 中对应行均被物理移除（`deleted` 不再残留 TRUE 行）。
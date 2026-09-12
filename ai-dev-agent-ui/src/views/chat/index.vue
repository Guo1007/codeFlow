<template>
  <div class="chat">
    <!-- ===== 左侧：会话列表栏 ===== -->
    <aside class="sessions-panel">
      <div class="sessions-head">
        <span class="sessions-title">历史对话</span>
        <el-tooltip content="新建对话" placement="top">
          <el-button size="small" :icon="Plus" circle :disabled="sending" @click="handleNewConversation" />
        </el-tooltip>
      </div>

      <!-- 目标项目选择 -->
      <div class="session-project">
        <el-select
          v-model="currentProject"
          placeholder="目标项目"
          size="small"
          :disabled="sending"
          @change="handleProjectChange"
        >
          <el-option
            v-for="p in projects"
            :key="p.name"
            :label="p.name"
            :value="p.name"
          >
            <div class="project-option-row">
              <span class="project-option-text">
                {{ p.name }}（{{ p.path }}）
                <em v-if="p.example" class="project-example-tip">接入示例，勿在此工作</em>
              </span>
              <button
                v-if="!p.example"
                class="project-remove"
                :title="`删除接入项目 ${p.name}`"
                @click.stop="confirmDeleteTarget(p.name)"
              >
                <el-icon :size="13"><Close /></el-icon>
              </button>
            </div>
          </el-option>
        </el-select>
      </div>

      <!-- 历史会话列表 -->
      <div ref="sessionListRef" class="session-list" v-loading="loadingSessions">
        <div
          v-for="s in sessions"
          :key="s.conversationId"
          class="session-item"
          :class="{ 'is-active': s.conversationId === activeConversationId }"
          @click="openSession(s.conversationId)"
        >
          <div class="session-item-main">
            <div class="session-item-title">{{ s.title || '（无标题）' }}</div>
            <div class="session-item-meta">
              <span class="mono">{{ s.messageCount }} 条</span>
              <span class="session-item-time mono">{{ formatTime(s.updateTime) }}</span>
            </div>
          </div>
          <div class="session-item-ops" @click.stop>
            <el-tooltip content="重命名" placement="top">
              <el-button size="small" text :icon="EditPen" @click="handleRename(s)" />
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button size="small" text type="danger" :icon="Delete" @click="handleDelete(s)" />
            </el-tooltip>
          </div>
        </div>
        <el-empty
          v-if="!loadingSessions && sessions.length === 0"
          description="暂无历史对话"
          :image-size="52"
        />
      </div>
    </aside>

    <!-- ===== 右侧：对话区 ===== -->
    <main class="chat-main">
      <header class="chat-top">
        <div class="chat-title">
          <h1>AI 对话</h1>
          <el-tag v-if="activeConversationId" size="small" type="info" class="conv-tag mono">
            {{ activeConversationId.slice(0, 8) }}
          </el-tag>
        </div>
        <div class="chat-actions">
          <el-switch
            v-model="syncMode"
            active-text="同步"
            inactive-text="流式"
            inline-prompt
            :disabled="sending"
          />
          <el-button type="primary" plain :icon="Plus" @click="handleNewConversation" :disabled="sending">
            新建对话
          </el-button>
        </div>
      </header>

      <!-- 对话流：居中列 -->
      <div ref="messageListRef" class="chat-scroll">
        <div class="chat-col">
          <div v-if="messageList.length === 0" class="chat-hero">
            <div class="hero-badge">
              <el-icon :size="26"><ChatDotRound /></el-icon>
            </div>
            <div class="hero-title">
              {{ activeConversationId ? '继续这段对话' : '开始你的第一段对话' }}
            </div>
            <div class="hero-sub">
              AI 可以阅读目标项目的真实源码再回答，对话会自动保存为历史，可随时回来继续
            </div>
          </div>

          <div v-for="(msg, index) in messageList" :key="index" class="msg-row">
            <div v-if="msg.role === 'user'" class="msg msg-user">
              <div class="msg-bubble msg-bubble-user">{{ msg.content }}</div>
            </div>
            <div v-else class="msg msg-assistant">
              <div class="msg-avatar">AI</div>
              <div class="msg-bubble msg-bubble-assistant">
                <MarkdownView v-if="msg.content" :content="msg.content" />
                <span v-if="msg.loading" class="chat-typing">
                  <span /><span /><span />
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区：卡片式合成器 -->
      <footer class="chat-composer">
        <div class="composer-card">
          <el-input
            v-model="prompt"
            type="textarea"
            :rows="2"
            resize="none"
            class="composer-input"
            placeholder="输入消息，Enter 发送，Shift + Enter 换行"
            :disabled="sending"
            @keydown.enter="handleEnter"
          />
          <div class="composer-bar">
            <span class="composer-hint">对话自动保存 · 可读取目标项目源码</span>
            <el-button
              v-if="!sending"
              type="primary"
              :icon="Promotion"
              :disabled="!prompt.trim()"
              @click="handleSend"
            >
              发送
            </el-button>
            <el-button v-else type="danger" plain :icon="VideoPause" @click="handleStop">
              停止生成
            </el-button>
          </div>
        </div>
      </footer>
    </main>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatDotRound,
  Close,
  Delete,
  EditPen,
  Plus,
  Promotion,
  VideoPause
} from '@element-plus/icons-vue'
import { ChatApi } from '@/api/chat'
import type {
  ChatContentEvent,
  ChatErrorEvent,
  ChatMetaEvent,
  ChatSessionVO,
  TargetProjectVO
} from '@/api/chat'
import MarkdownView from '@/components/MarkdownView/index.vue'

// 消息数据结构
interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  loading?: boolean // AI 正在生成
}

const projects = ref<TargetProjectVO[]>([]) // 可选目标项目列表
const currentProject = ref('') // 当前目标项目
const prompt = ref('')
const sending = ref(false)
const syncMode = ref(false) // 同步/流式
const messageList = ref<ChatMessage[]>([]) // 当前展示的消息（会话内）
const activeConversationId = ref('') // 当前会话 ID
const sessions = ref<ChatSessionVO[]>([]) // 历史会话列表
const loadingSessions = ref(false) // 会话列表加载中
const messageListRef = ref<HTMLElement | null>(null)
const sessionListRef = ref<HTMLElement | null>(null)
let abortController: AbortController | null = null

/** 生成会话 ID（前端预生成，确保同步/载入历史也可精确回显） */
const genId = () => {
  const base = (globalThis.crypto?.randomUUID?.() ?? Math.random().toString(36).slice(2))
  return base.replace(/-/g, '')
}

/** Enter 发送，Shift + Enter 换行 */
const handleEnter = (e: KeyboardEvent) => {
  if (e.shiftKey) return
  e.preventDefault()
  handleSend()
}

const scrollToBottom = () => {
  nextTick(() => {
    const el = messageListRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

/** 发送消息 */
const handleSend = async () => {
  const content = prompt.value.trim()
  if (!content || sending.value) return
  // 新会话时生成 ID
  if (!activeConversationId.value) {
    activeConversationId.value = genId()
  }
  prompt.value = ''
  messageList.value.push({ role: 'user', content })
  messageList.value.push({ role: 'assistant', content: '', loading: true })
  sending.value = true
  scrollToBottom()
  try {
    if (syncMode.value) {
      await doSyncChat(content)
    } else {
      await doStreamChat(content)
    }
  } finally {
    finishAssistantMessage()
    sending.value = false
    scrollToBottom()
    await Promise.all([loadProjects(), refreshSessions()])
  }
}

/** 同步对话 */
const doSyncChat = async (content: string) => {
  try {
    const result = await ChatApi.generateChat({
      prompt: content,
      conversationId: activeConversationId.value,
      project: currentProject.value || undefined
    })
    setAssistantContent(result)
  } catch (e: any) {
    setAssistantContent(`请求失败：${e.message || e}`)
  }
}

/** 流式对话：SSE 逐块推送 */
const doStreamChat = async (content: string) => {
  abortController = new AbortController()
  await ChatApi.generateChatStream(
    {
      prompt: content,
      conversationId: activeConversationId.value,
      project: currentProject.value || undefined
    },
    {
      onMessage: (event) => {
        if (event === null) return
        const meta = event as ChatMetaEvent
        if ('type' in meta && meta.type === 'meta') {
          activeConversationId.value = meta.conversationId
          return
        }
        const err = event as ChatErrorEvent
        if ('error' in err) {
          setAssistantContent(`AI 服务异常：${err.error}`)
          return
        }
        const ce = event as ChatContentEvent
        if ('content' in ce) {
          appendAssistantContent(ce.content)
          scrollToBottom()
        }
      },
      onError: (err) => {
        if (err?.name !== 'AbortError') {
          setAssistantContent(`流式请求失败：${err?.message || err}`)
        }
      }
    },
    abortController
  )
}

const finishAssistantMessage = () => {
  const last = messageList.value[messageList.value.length - 1]
  if (last && last.role === 'assistant') {
    last.loading = false
    if (!last.content) last.content = '（无回复内容）'
  }
}

const setAssistantContent = (content: string) => {
  const last = messageList.value[messageList.value.length - 1]
  if (last && last.role === 'assistant') last.content = content
}

const appendAssistantContent = (chunk: string) => {
  const last = messageList.value[messageList.value.length - 1]
  if (last && last.role === 'assistant') last.content += chunk
}

const handleStop = () => abortController?.abort()

/** 新建对话：清空当前消息与会话 ID */
const handleNewConversation = () => {
  if (sending.value) {
    ElMessage.warning('正在生成中，请先停止生成')
    return
  }
  activeConversationId.value = ''
  messageList.value = []
}

/** 打开历史会话：载入完整消息回显 */
const openSession = async (conversationId: string) => {
  if (sending.value) return
  if (conversationId === activeConversationId.value) return
  activeConversationId.value = conversationId
  try {
    const history = await ChatApi.listHistory(conversationId)
    messageList.value = history.map((m) => ({ role: m.role, content: m.content }))
  } catch (e: any) {
    ElMessage.error(`加载历史失败：${e?.message || e}`)
    messageList.value = []
  }
  scrollToBottom()
}

/** 重命名会话 */
const handleRename = async (s: ChatSessionVO) => {
  const { value } = await ElMessageBox.prompt('请输入新的对话标题', '重命名对话', {
    inputValue: s.title || '',
    inputPlaceholder: '对话标题',
    confirmButtonText: '保存',
    cancelButtonText: '取消'
  })
  if (value) {
    await ChatApi.renameSession(s.conversationId, value.trim())
    await refreshSessions()
    ElMessage.success('已重命名')
  }
}

/** 删除会话 */
const handleDelete = async (s: ChatSessionVO) => {
  await ElMessageBox.confirm('删除后该对话的历史记录将无法恢复。确认删除？', '删除对话', {
    type: 'warning',
    confirmButtonText: '确认删除',
    cancelButtonText: '取消'
  })
  await ChatApi.deleteSession(s.conversationId, s.project || currentProject.value || undefined)
  sessions.value = sessions.value.filter((x) => x.conversationId !== s.conversationId)
  // 若删除的是当前会话，回到空态
  if (s.conversationId === activeConversationId.value) {
    activeConversationId.value = ''
    messageList.value = []
  }
  ElMessage.success('已删除')
}

/** 切换目标项目：刷新会话列表（按项目隔离）并回到空态 */
const handleProjectChange = () => {
  activeConversationId.value = ''
  messageList.value = []
  refreshSessions()
}

/** 刷新会话列表（按当前项目过滤） */
const refreshSessions = async () => {
  try {
    loadingSessions.value = true
    sessions.value = await ChatApi.listSessions(currentProject.value || undefined)
  } catch (e) {
    console.warn('[Chat][会话列表加载失败]', e)
  } finally {
    loadingSessions.value = false
  }
}

/** 加载目标项目列表并默认选中第一个 */
const loadProjects = async () => {
  try {
    projects.value = await ChatApi.getTargetProjects()
    if (projects.value.length > 0 && !currentProject.value) {
      currentProject.value = projects.value[0].name
      refreshSessions()
    }
  } catch (e) {
    console.warn('[Chat][目标项目列表加载失败]', e)
  }
}

// ===== 删除接入项目 =====
const confirmDeleteTarget = async (name: string) => {
  try {
    await ElMessageBox.confirm(
      `确认删除接入项目「${name}」？（该操作不可逆）`,
      '删除接入项目',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return // 用户取消
  }
  try {
    await ChatApi.removeTargetProject(name)
    ElMessage.success('已删除接入项目')
    // 若删除的正是当前选中的项目，重置选择
    if (currentProject.value === name) {
      currentProject.value = ''
    }
    await loadProjects()
    refreshSessions()
  } catch (e) {
    console.warn('[Chat][删除接入项目失败]', e)
  }
}

/** 时间展示（月-日 时:分） */
const formatTime = (t?: Date | string) => {
  if (!t) return ''
  const d = new Date(t)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(() => {
  loadProjects()
  refreshSessions()
})

onBeforeUnmount(() => {
  abortController?.abort()
})
</script>

<style scoped>
.mono {
  font-family: var(--cf-mono);
}

.chat {
  display: flex;
  height: 100%;
  overflow: hidden;
  animation: cf-rise 0.4s ease both;
}

/* ===== 左侧会话栏 ===== */
.sessions-panel {
  display: flex;
  flex-direction: column;
  width: 280px;
  flex-shrink: 0;
  background: var(--cf-panel);
  border-right: 1px solid var(--cf-border);
}

.sessions-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 14px;
  border-bottom: 1px solid var(--cf-border);
  flex-shrink: 0;
}

.sessions-title {
  font-size: 14px;
  font-weight: 700;
}

.session-project {
  padding: 10px 12px;
  border-bottom: 1px solid var(--cf-border);
  flex-shrink: 0;
}

.session-project :deep(.el-select) {
  width: 100%;
}

.project-option-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
}

.project-option-text {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-example-tip {
  flex-shrink: 0;
  font-style: normal;
  font-size: 11px;
  color: var(--cf-amber);
  background: var(--cf-amber-soft);
  border-radius: 4px;
  padding: 1px 6px;
}

.project-remove {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--cf-text-faint);
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.project-remove:hover {
  background: var(--cf-danger-soft, rgba(245, 108, 108, 0.12));
  color: var(--cf-danger, #f56c6c);
}

.session-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 8px;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
  padding: 8px 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.session-item:hover {
  background: var(--cf-panel-2);
}

.session-item.is-active {
  background: var(--cf-accent-soft);
}

.session-item-main {
  flex: 1;
  min-width: 0;
}

.session-item-title {
  font-size: 13px;
  color: var(--cf-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-item.is-active .session-item-title {
  color: var(--cf-accent);
  font-weight: 600;
}

.session-item-meta {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: var(--cf-text-faint);
  margin-top: 2px;
}

.session-item-ops {
  display: none;
  align-items: center;
  flex-shrink: 0;
}

.session-item:hover .session-item-ops {
  display: flex;
}

/* ===== 右侧对话区 ===== */
.chat-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.chat-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  height: 56px;
  padding: 0 20px;
  background: var(--cf-panel);
  border-bottom: 1px solid var(--cf-border);
  flex-shrink: 0;
}

.chat-title {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.chat-title h1 {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  white-space: nowrap;
}

.conv-tag {
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  white-space: nowrap;
}

/* 对话流 */
.chat-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.chat-col {
  max-width: 860px;
  margin: 0 auto;
  padding: 28px 20px 20px;
}

.chat-hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 88px 24px 40px;
}

.hero-badge {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  margin-bottom: 18px;
  border-radius: 16px;
  color: var(--cf-accent);
  background: var(--cf-accent-soft);
  border: 1px solid var(--cf-border-strong);
}

.hero-title {
  font-size: 19px;
  font-weight: 700;
  margin-bottom: 8px;
}

.hero-sub {
  max-width: 460px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--cf-text-dim);
}

.msg-row {
  margin-bottom: 22px;
  animation: cf-rise 0.28s ease both;
}

.msg-user {
  display: flex;
  justify-content: flex-end;
}

.msg-bubble-user {
  max-width: 78%;
  padding: 10px 14px;
  background: var(--cf-accent);
  color: var(--cf-accent-contrast);
  border-radius: 14px 14px 4px 14px;
  font-size: 14px;
  line-height: 1.65;
  word-break: break-word;
  white-space: pre-wrap;
}

.msg-assistant {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.msg-avatar {
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 2px;
  border-radius: 8px;
  font-family: var(--cf-mono);
  font-size: 11px;
  font-weight: 600;
  color: var(--cf-accent);
  background: var(--cf-accent-soft);
  border: 1px solid var(--cf-border-strong);
}

.msg-bubble-assistant {
  min-width: 0;
  max-width: calc(100% - 44px);
  padding: 10px 14px;
  background: var(--cf-panel);
  border: 1px solid var(--cf-border);
  border-radius: 4px 14px 14px 14px;
  box-shadow: var(--cf-shadow);
  font-size: 14px;
  line-height: 1.65;
  word-break: break-word;
  white-space: normal;
}

/* 输入区 */
.chat-composer {
  flex-shrink: 0;
  padding: 12px 20px 16px;
}

.composer-card {
  max-width: 860px;
  margin: 0 auto;
  padding: 10px 12px 8px;
  background: var(--cf-panel);
  border: 1px solid var(--cf-border-strong);
  border-radius: 14px;
  box-shadow: var(--cf-shadow);
  transition: border-color 0.15s ease;
}

.composer-card:focus-within {
  border-color: var(--cf-accent);
}

.composer-input :deep(.el-textarea__inner) {
  border: none;
  box-shadow: none !important;
  background: transparent;
  padding: 4px 4px 0;
}

.composer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 6px;
}

.composer-hint {
  font-size: 12px;
  color: var(--cf-text-faint);
  padding-left: 4px;
}

/* 打字动画 */
.chat-typing {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 0;
}

.chat-typing span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: var(--cf-accent);
  animation: chat-typing-blink 1.2s infinite;
}

.chat-typing span:nth-child(2) {
  animation-delay: 0.2s;
}

.chat-typing span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes chat-typing-blink {
  0%,
  60%,
  100% {
    opacity: 0.2;
  }

  30% {
    opacity: 1;
  }
}
</style>
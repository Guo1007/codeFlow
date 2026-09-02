<template>
  <div class="chat">
    <!-- 顶栏 -->
    <header class="chat-top">
      <div class="chat-title">
        <h1>AI 对话</h1>
        <el-tag v-if="conversationId" size="small" type="info" class="conv-tag">
          会话：{{ conversationId }}
        </el-tag>
      </div>
      <div class="chat-actions">
        <el-select
          v-model="currentProject"
          class="project-select"
          placeholder="目标项目"
          :disabled="sending"
          @change="handleProjectChange"
        >
          <el-option v-for="p in projects" :key="p.name" :label="p.name" :value="p.name">
            <span class="project-option-name">{{ p.name }}</span>
            <span class="project-option-path">{{ p.path }}</span>
          </el-option>
        </el-select>
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
        <!-- 空状态：欢迎语 -->
        <div v-if="messageList.length === 0" class="chat-hero">
          <div class="hero-badge">
            <el-icon :size="26"><ChatDotRound /></el-icon>
          </div>
          <div class="hero-title">开始你的第一段对话</div>
          <div class="hero-sub">
            AI 可以阅读目标项目的真实源码再回答，同一会话内记住上下文（多轮记忆存于 Redis）
          </div>
        </div>

        <!-- 消息列表 -->
        <div v-for="(msg, index) in messageList" :key="index" class="msg-row">
          <!-- 用户消息 -->
          <div v-if="msg.role === 'user'" class="msg msg-user">
            <div class="msg-bubble msg-bubble-user">{{ msg.content }}</div>
          </div>
          <!-- AI 消息 -->
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
          <span class="composer-hint">AI 由 DeepSeek 驱动 · 可读取目标项目源码</span>
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
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Plus, Promotion, VideoPause } from '@element-plus/icons-vue'
import { ChatApi } from '@/api/chat'
import type { ChatContentEvent, ChatErrorEvent, ChatMetaEvent, TargetProjectVO } from '@/api/chat'
import MarkdownView from '@/components/MarkdownView/index.vue'

// 消息数据结构
interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  loading?: boolean // AI 正在生成
}

const projects = ref<TargetProjectVO[]>([]) // 可选目标项目列表
const currentProject = ref('') // 当前目标项目（读码工具的工作范围按它隔离）
const prompt = ref('') // 输入框内容
const sending = ref(false) // 是否正在生成
const syncMode = ref(false) // 同步模式（验证 /generate），false 为流式模式（验证 /generate-stream）
const conversationId = ref('') // 会话 ID，首次对话后由 meta 事件返回
const messageList = ref<ChatMessage[]>([]) // 消息列表
const messageListRef = ref<HTMLElement | null>(null) // 消息列表容器，用于自动滚动
let abortController: AbortController | null = null // 停止生成的控制器

/** Enter 发送，Shift + Enter 换行 */
const handleEnter = (e: KeyboardEvent) => {
  if (e.shiftKey) {
    return // 换行，交给默认行为
  }
  e.preventDefault()
  handleSend()
}

/** 滚动到底部 */
const scrollToBottom = () => {
  nextTick(() => {
    const el = messageListRef.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

/** 发送消息 */
const handleSend = async () => {
  const content = prompt.value.trim()
  if (!content || sending.value) {
    return
  }
  prompt.value = ''
  messageList.value.push({ role: 'user', content })
  // AI 占位消息，流式过程中逐步填充
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
    // AI 可能在对话中接入了新目标项目（registerTargetProject 工具），刷新选择器列表
    loadProjects()
  }
}

/** 同步对话：等待完整结果一次性返回 */
const doSyncChat = async (content: string) => {
  try {
    const result = await ChatApi.generateChat({
      prompt: content,
      conversationId: conversationId.value || undefined,
      project: currentProject.value || undefined
    })
    setAssistantContent(result)
    // 同步接口不返回会话 ID，首次调用后生成一个占位标识，仅用于页面展示
    if (!conversationId.value) {
      conversationId.value = '（同步接口无会话管理）'
    }
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
      conversationId: conversationId.value || undefined,
      project: currentProject.value || undefined
    },
    {
      onMessage: (event) => {
        // null 表示收到 [DONE] 结束标记
        if (event === null) {
          return
        }
        const meta = event as ChatMetaEvent
        if ('type' in meta && meta.type === 'meta') {
          // meta 事件：保存新会话 ID，后续请求延续上下文
          conversationId.value = meta.conversationId
          return
        }
        const err = event as ChatErrorEvent
        if ('error' in err) {
          setAssistantContent(`AI 服务异常：${err.error}`)
          return
        }
        const contentEvent = event as ChatContentEvent
        if ('content' in contentEvent) {
          appendAssistantContent(contentEvent.content)
          scrollToBottom()
        }
      },
      onError: (err) => {
        // 主动停止（AbortError）不算异常
        if (err?.name !== 'AbortError') {
          setAssistantContent(`流式请求失败：${err?.message || err}`)
        }
      }
    },
    abortController
  )
}

/** 结束当前 AI 消息的生成状态 */
const finishAssistantMessage = () => {
  const last = messageList.value[messageList.value.length - 1]
  if (last && last.role === 'assistant') {
    last.loading = false
    if (!last.content) {
      last.content = '（无回复内容）'
    }
  }
}

/** 设置 AI 消息内容（覆盖） */
const setAssistantContent = (content: string) => {
  const last = messageList.value[messageList.value.length - 1]
  if (last && last.role === 'assistant') {
    last.content = content
  }
}

/** 追加 AI 消息内容（流式） */
const appendAssistantContent = (chunk: string) => {
  const last = messageList.value[messageList.value.length - 1]
  if (last && last.role === 'assistant') {
    last.content += chunk
  }
}

/** 停止生成 */
const handleStop = () => {
  abortController?.abort()
}

/** 新建对话：清空消息与会话 ID */
const handleNewConversation = () => {
  if (sending.value) {
    ElMessage.warning('正在生成中，请先停止生成')
    return
  }
  conversationId.value = ''
  messageList.value = []
}

/** 切换目标项目：清空当前会话（不同项目的记忆与上下文互不通用） */
const handleProjectChange = () => {
  conversationId.value = ''
  messageList.value = []
  const p = projects.value.find((item) => item.name === currentProject.value)
  if (p) {
    ElMessage.success(`已切换到「${p.name}」，AI 将读取该项目的代码`)
  }
}

/** 加载目标项目列表并默认选中第一个 */
const loadProjects = async () => {
  try {
    projects.value = await ChatApi.getTargetProjects()
    if (projects.value.length > 0 && !currentProject.value) {
      currentProject.value = projects.value[0].name
    }
  } catch (e) {
    // 加载失败不阻塞页面，对话请求会走后端默认项目
    console.warn('[Chat][目标项目列表加载失败]', e)
  }
}

onMounted(() => {
  loadProjects()
})

onBeforeUnmount(() => {
  abortController?.abort()
})
</script>

<style scoped>
.chat {
  display: flex;
  flex-direction: column;
  height: 100%;
  animation: cf-rise 0.4s ease both;
}

/* ===== 顶栏 ===== */
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

/* 目标项目选择器 */
.project-select {
  width: 220px;
}

.project-option-name {
  margin-right: 10px;
  font-weight: 500;
}

.project-option-path {
  font-size: 12px;
  color: var(--cf-text-faint);
}

/* ===== 对话流（居中列） ===== */
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

/* 空状态 */
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

/* 消息 */
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

/* ===== 输入区（卡片合成器） ===== */
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

/* ===== AI 生成中的打字动画 ===== */
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

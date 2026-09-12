import { del, get, post } from '../request'
import { fetchSseStream } from '../sse'

// AI 对话请求 VO
export interface ChatReqVO {
  prompt: string // 用户输入
  conversationId?: string // 会话唯一标识（多轮对话），首次可不传
  project?: string // 目标项目名称（为空取默认项目）
}

// 目标项目 VO
export interface TargetProjectVO {
  name: string // 项目标识
  path: string // 项目根目录
  hasProfile: boolean // 是否配置了框架画像
  example: boolean // 是否示例项目（接入示例，勿在此工作，不可删）
}

// SSE meta 事件（新会话时返回会话 ID）
export interface ChatMetaEvent {
  type: 'meta'
  conversationId: string
}

// SSE content 事件（AI 回复片段）
export interface ChatContentEvent {
  content: string
}

// SSE error 事件
export interface ChatErrorEvent {
  error: string
}

// 历史会话 VO
export interface ChatSessionVO {
  conversationId: string
  project: string
  title: string
  messageCount: number
  createTime: Date
  updateTime: Date
}

// 历史消息 VO
export interface ChatMessageVO {
  role: 'user' | 'assistant'
  content: string
  createTime: Date
}

// AI 对话 API
export const ChatApi = {
  // 目标项目列表（前端项目选择器数据源）
  getTargetProjects: async (): Promise<TargetProjectVO[]> => {
    return await get<TargetProjectVO[]>('/chat/projects')
  },

  // 删除动态接入的目标项目（yaml 静态/示例项目不可删）
  removeTargetProject: async (name: string): Promise<boolean> => {
    return await del<boolean>('/chat/project', { name })
  },

  // 同步对话：等待模型输出完整结果后一次性返回
  generateChat: async (data: ChatReqVO): Promise<string> => {
    return await post<string>('/chat/generate', data)
  },

  // 流式对话（SSE 逐块推送）
  // 为什么不用 axios？因为它不支持 SSE 流式调用
  generateChatStream: async (
    data: ChatReqVO,
    handlers: {
      onMessage: (event: ChatMetaEvent | ChatContentEvent | ChatErrorEvent | null) => void
      onError?: (error: any) => void
      onClose?: () => void
    },
    ctrl: AbortController
  ) => {
    return fetchSseStream(
      '/chat/generate-stream',
      data,
      {
        onData: (data, done) => {
          // done=true 表示收到 [DONE] 结束标记
          if (done) {
            handlers.onMessage(null)
            return
          }
          try {
            handlers.onMessage(JSON.parse(data))
          } catch {
            // 兼容无法解析的数据，避免中断流
            console.warn('[Chat][无法解析的 SSE 数据]', data)
          }
        },
        onError: handlers.onError,
        onClose: handlers.onClose
      },
      ctrl
    )
  },

  // 历史会话列表（可选按目标项目过滤）
  listSessions: async (project?: string): Promise<ChatSessionVO[]> => {
    return await get<ChatSessionVO[]>('/chat/sessions', project ? { project } : {})
  },

  // 某会话完整历史消息
  listHistory: async (conversationId: string): Promise<ChatMessageVO[]> => {
    return await get<ChatMessageVO[]>('/chat/history', { conversationId })
  },

  // 删除历史会话（需传所属项目用于清对应 Redis 记忆）
  deleteSession: async (conversationId: string, project?: string) => {
    return await del('/chat/session', { conversationId, project })
  },

  // 重命名会话标题
  renameSession: async (conversationId: string, title: string) => {
    return await post('/chat/session/rename', { conversationId, title })
  }
}

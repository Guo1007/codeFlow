import { get, post } from '../request'
import { BASE_URL } from '../request'
import { fetchSseStream } from '../sse'

// AI 开发任务创建 VO
export interface ProjectCreateReqVO {
  name: string // 任务名称
  requirementContent: string // 需求文档内容（Markdown）
}

// AI 开发任务详情 VO
export interface ProjectRespVO {
  id: number
  name: string
  requirementContent: string
  stage: number // 0-需求已录入 1-设计评审中 2-设计已定稿
  targetProject?: string // 代码生成阶段绑定的目标项目名称
  designContent?: string // 最新设计文档内容
  designVersion?: number // 最新设计文档版本号
  designStatus?: number // 0-AI 生成 1-人工修改 2-已定稿
  codeVersion?: number // 最新代码产物版本号
  manualContent?: string // 最新使用说明内容
  manualVersion?: number // 最新使用说明版本号
  manualStatus?: number // 最新使用说明状态：2-已定稿
  createTime: Date
}

// AI 开发任务摘要 VO（历史列表用）
export interface ProjectSimpleVO {
  id: number
  name: string
  stage: number
  createTime: Date
}

// 沙箱文件树节点 VO
export interface CodeSandboxNodeVO {
  name: string // 文件/目录名
  path: string // 相对路径
  isDir: boolean // 是否目录
  size: number // 文件大小（字节）
  children?: CodeSandboxNodeVO[] // 子节点（目录）
}

// AI 开发流水线 API
export const DevApi = {
  // 创建开发任务（录入需求文档）
  createProject: async (data: ProjectCreateReqVO): Promise<number> => {
    return await post<number>('/dev/project/create', data)
  },

  // 历史任务列表（按创建时间倒序，最多 50 条）
  listProjects: async (): Promise<ProjectSimpleVO[]> => {
    return await get<ProjectSimpleVO[]>('/dev/project/list')
  },

  // 获取开发任务详情（含最新设计文档）
  getProject: async (id: number): Promise<ProjectRespVO> => {
    return await get<ProjectRespVO>('/dev/project/get', { id })
  },

  // 流式生成/修订设计文档（无 opinion 为首次生成，有则为按评审意见修订；结束后后端自动保存版本）
  generateDesignStream: async (
    projectId: number,
    opinion: string | undefined,
    handlers: {
      onData: (chunk: string, done: boolean) => void
      onError?: (error: any) => void
      onClose?: () => void
    },
    ctrl: AbortController
  ) => {
    // 后端返回裸文本片段（非 JSON），直接透传
    return fetchSseStream('/dev/design/generate-stream', { projectId, opinion }, handlers, ctrl)
  },

  // 保存人工编辑的设计文档
  saveDesign: async (projectId: number, content: string) => {
    return await post('/dev/design/save', { projectId, content })
  },

  // 定稿设计文档（定稿后不可再修改）
  approveDesign: async (projectId: number, content?: string) => {
    return await post('/dev/design/approve', { projectId, content })
  },

  // ===== 代码生成（设计定稿后）=====

  // 流式生成代码：AI 叙述 + 写入沙箱；结束后自动保存代码产物清单并推进阶段
  generateCodeStream: async (
    projectId: number,
    targetProject: string,
    handlers: {
      onData: (chunk: string, done: boolean) => void
      onError?: (error: any) => void
      onClose?: () => void
    },
    ctrl: AbortController
  ) => {
    return fetchSseStream('/dev/code/generate-stream', { projectId, targetProject }, handlers, ctrl)
  },

  // 沙箱文件树预览
  listSandboxFiles: async (projectId: number): Promise<CodeSandboxNodeVO[]> => {
    return await get<CodeSandboxNodeVO[]>('/dev/code/tree', { projectId })
  },

  // 读取沙箱文件内容（预览）
  getSandboxFile: async (projectId: number, path: string): Promise<string> => {
    return await get<string>('/dev/code/file', { projectId, path })
  },

  // 应用全部代码到目标项目真实路径
  applyAllCode: async (projectId: number) => {
    return await post('/dev/code/apply-all', { projectId })
  },

  // 应用单个文件到目标项目真实路径
  applyCode: async (projectId: number, relativePath: string) => {
    return await post('/dev/code/apply', { projectId, relativePath })
  },

  // 清空沙箱（撤销本次生成）
  clearSandbox: async (projectId: number) => {
    return await post('/dev/code/clear', { projectId })
  },

  // 代码定稿（锁定代码生成阶段）
  finalizeCode: async (projectId: number) => {
    return await post('/dev/code/finalize', { projectId })
  },

  // 导出沙箱代码为 zip 压缩包（前端下载）
  exportCodeZip: async (projectId: number, filename: string) => {
    const res = await fetch(`${BASE_URL}/dev/code/export?projectId=${projectId}`)
    if (!res.ok) {
      throw new Error(`下载失败：HTTP ${res.status}`)
    }
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    link.click()
    URL.revokeObjectURL(url)
  },

  // ===== 使用说明（代码定稿后）=====

  // 流式生成使用说明：AI 基于定稿设计文档 + 目标项目代码生成 Markdown；
  // 结束后自动落库为 MANUAL 产物并推进阶段到终态（支持重新生成，版本 +1）
  generateManualStream: async (
    projectId: number,
    handlers: {
      onData: (chunk: string, done: boolean) => void
      onError?: (error: any) => void
      onClose?: () => void
    },
    ctrl: AbortController
  ) => {
    return fetchSseStream('/dev/usage/generate-stream', { projectId }, handlers, ctrl)
  }
}

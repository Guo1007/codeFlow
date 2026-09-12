import { del, get, post } from '../request'
import service from '../request'

// 知识库文档 VO
export interface KnowledgeDocVO {
  id: number
  documentId: string
  project: string
  docType: string // upload=手动上传 / design=设计文档（定稿自动索引）
  version: number
  name: string
  embeddingStatus: number
  createTime: string
}

// 检索命中片段 VO
export interface KnowledgeHitVO {
  name: string
  project: string
  content: string
}

export const KnowledgeApi = {
  // 文档列表（当前用户）
  listDocuments: async (project?: string): Promise<KnowledgeDocVO[]> => {
    return await get<KnowledgeDocVO[]>('/knowledge/documents', project ? { project } : {})
  },

  // 上传文档并向量化
  uploadDocument: async (data: { project?: string; name: string; content: string }): Promise<KnowledgeDocVO> => {
    return await post<KnowledgeDocVO>('/knowledge/document/upload', data)
  },

  // 上传文件并自动解析后向量化（PDF/Word/Excel/PPT/HTML/TXT 等）
  uploadDocumentFile: async (file: File, project?: string): Promise<KnowledgeDocVO> => {
    const fd = new FormData()
    fd.append('file', file)
    if (project) fd.append('project', project)
    const resp = await service.post('/knowledge/document/upload-file', fd)
    return resp as unknown as KnowledgeDocVO
  },

  // 删除文档
  deleteDocument: async (id: number): Promise<boolean> => {
    return await del<boolean>('/knowledge/document', { id })
  },

  // 向量检索
  search: async (q: string, project?: string, topK = 3): Promise<KnowledgeHitVO[]> => {
    return await get<KnowledgeHitVO[]>('/knowledge/search', { q, project: project || undefined, topK })
  }
}

export default { KnowledgeApi }
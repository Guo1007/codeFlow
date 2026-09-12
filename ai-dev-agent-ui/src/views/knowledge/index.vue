<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Search, Upload } from '@element-plus/icons-vue'
import { KnowledgeApi, type KnowledgeDocVO } from '../../api/knowledge'
import { ChatApi, type TargetProjectVO } from '../../api/chat'

const loading = ref(false)
const documents = ref<KnowledgeDocVO[]>([])
const projects = ref<TargetProjectVO[]>([])

// 上传表单
const uploadVisible = ref(false)
const uploading = ref(false)
const fileUploading = ref(false)
const form = reactive({ name: '', project: '', content: '' })

// 检索
const query = ref('')
const searchProject = ref('')
const hits = ref<{ name: string; content: string }[]>([])
const searching = ref(false)

const loadDocuments = async (project?: string) => {
  loading.value = true
  try {
    documents.value = await KnowledgeApi.listDocuments(project)
  } finally {
    loading.value = false
  }
}

const loadProjects = async () => {
  try {
    projects.value = await ChatApi.getTargetProjects()
  } catch (e) {
    console.warn('[Knowledge][项目列表加载失败]', e)
  }
}

const openUpload = () => {
  form.name = ''
  form.project = ''
  form.content = ''
  uploadVisible.value = true
}

const submitUpload = async () => {
  if (!form.name.trim() || !form.content.trim()) {
    ElMessage.warning('请填写文档名称和内容')
    return
  }
  uploading.value = true
  try {
    await KnowledgeApi.uploadDocument({
      name: form.name.trim(),
      project: form.project || undefined,
      content: form.content
    })
    ElMessage.success('已上传并向量化')
    uploadVisible.value = false
    await loadDocuments()
  } catch (e) {
    console.warn('[Knowledge][上传失败]', e)
  } finally {
    uploading.value = false
  }
}

// 选择文件自动解析后上传（保留粘贴文本方式）
const handleFileChange = async (file: { raw: File }) => {
  if (!file?.raw) return
  fileUploading.value = true
  try {
    await KnowledgeApi.uploadDocumentFile(file.raw, form.project || undefined)
    ElMessage.success(`已解析并向量化「${file.raw.name}」`)
    uploadVisible.value = false
    await loadDocuments()
  } catch (e) {
    console.warn('[Knowledge][文件上传失败]', e)
  } finally {
    fileUploading.value = false
  }
}

const confirmDelete = async (doc: KnowledgeDocVO) => {
  try {
    await ElMessageBox.confirm(
      `删除后该文档及其向量将不可恢复。确认删除「${doc.name}」？`,
      '删除文档',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  await KnowledgeApi.deleteDocument(doc.id)
  ElMessage.success('已删除')
  await loadDocuments()
}

const doSearch = async () => {
  if (!query.value.trim()) {
    ElMessage.warning('请输入检索内容')
    return
  }
  searching.value = true
  try {
    hits.value = await KnowledgeApi.search(query.value.trim(), searchProject.value || undefined, 5)
  } finally {
    searching.value = false
  }
}

const formatType = (t: string) => (t === 'design' ? '设计文档' : '手动上传')

onMounted(async () => {
  await Promise.all([loadDocuments(), loadProjects()])
})
</script>

<template>
  <div class="kb">
    <!-- 左栏：文档管理 -->
    <section class="kb-docs">
      <div class="kb-head">
        <span class="kb-title">知识库文档</span>
        <el-button type="primary" size="small" :icon="Plus" @click="openUpload">上传文档</el-button>
      </div>
      <div class="kb-filter">
        <el-select v-model="searchProject" placeholder="全部项目" clearable size="small" @change="loadDocuments(searchProject || undefined)">
          <el-option v-for="p in projects" :key="p.name" :label="p.name" :value="p.name" />
        </el-select>
      </div>
      <div class="kb-list" v-loading="loading">
        <div v-for="doc in documents" :key="doc.id" class="kb-item">
          <div class="kb-item-main">
            <div class="kb-item-name" :title="doc.name">{{ doc.name }}</div>
            <div class="kb-item-meta">
              <el-tag size="small" :type="doc.docType === 'design' ? 'success' : 'info'" effect="plain">
                {{ formatType(doc.docType) }}
              </el-tag>
              <span v-if="doc.docType === 'design'" class="kb-version">v{{ doc.version }}</span>
              <span v-if="doc.project" class="kb-project">{{ doc.project }}</span>
            </div>
          </div>
          <el-button size="small" text type="danger" :icon="Delete" @click="confirmDelete(doc)">删除</el-button>
        </div>
        <el-empty v-if="!loading && documents.length === 0" description="暂无文档，上传一篇开始构建知识库" :image-size="70" />
      </div>
    </section>

    <!-- 右栏：语义检索 -->
    <section class="kb-search">
      <div class="kb-head">
        <span class="kb-title">语义检索</span>
      </div>
      <div class="kb-searchbar">
        <el-select v-model="searchProject" placeholder="目标项目" clearable size="small">
          <el-option v-for="p in projects" :key="p.name" :label="p.name" :value="p.name" />
        </el-select>
      </div>
      <div class="kb-searchbar">
        <el-input v-model="query" placeholder="输入问题，检索相关内容…" clearable @keyup.enter="doSearch" />
        <el-button type="primary" :icon="Search" :loading="searching" @click="doSearch">检索</el-button>
      </div>
      <div class="kb-hits">
        <div v-for="(h, i) in hits" :key="i" class="kb-hit">
          <div class="kb-hit-name">{{ h.name }}</div>
          <div class="kb-hit-content">{{ h.content }}</div>
        </div>
        <el-empty v-if="hits.length === 0" description="暂无检索结果" :image-size="70" />
      </div>
    </section>

    <!-- 上传文档弹窗 -->
    <el-dialog v-model="uploadVisible" title="上传知识库文档" width="560px" :close-on-click-modal="false">
      <!-- 方式一：上传文件自动解析 -->
      <div class="kb-upload-file">
        <el-upload
          :show-file-list="false"
          :auto-upload="false"
          :on-change="handleFileChange"
          accept=".txt,.md,.markdown,.html,.htm,.json,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx"
          :disabled="fileUploading"
        >
          <el-button type="primary" plain :icon="Upload" :loading="fileUploading">
            选择文件上传
          </el-button>
        </el-upload>
        <div class="kb-upload-hint">支持 PDF / Word / Excel / PPT / Markdown / TXT 等，将自动提取文本并向量化</div>
      </div>

      <el-divider class="kb-divider">或粘贴文本</el-divider>

      <!-- 方式二：粘贴文本 -->
      <el-form label-position="top">
        <el-form-item label="文档名称">
          <el-input v-model="form.name" placeholder="例如：优惠券模块需求说明" maxlength="200" />
        </el-form-item>
        <el-form-item label="所属项目（可选，留空为全局，对所有项目对话可见）">
          <el-select v-model="form.project" placeholder="全部项目" clearable>
            <el-option v-for="p in projects" :key="p.name" :label="p.name" :value="p.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="文档内容">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="8"
            placeholder="粘贴或输入文档内容，将自动切分并向量化存储"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :icon="Upload" :loading="uploading" @click="submitUpload">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.kb {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--cf-bg);
}

/* 左栏文档管理 */
.kb-docs {
  width: 380px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--cf-border);
  background: var(--cf-panel);
}

.kb-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--cf-border);
}

.kb-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--cf-text);
}

.kb-filter {
  padding: 10px 16px;
}

.kb-filter .el-select {
  width: 100%;
}

.kb-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 4px 10px 12px;
}

.kb-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px;
  border-radius: 8px;
  transition: background 0.15s ease;
}

.kb-item:hover {
  background: var(--cf-panel-2);
}

.kb-item-main {
  flex: 1;
  min-width: 0;
}

.kb-item-name {
  font-size: 13.5px;
  color: var(--cf-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 5px;
}

.kb-version,
.kb-project {
  font-size: 11.5px;
  color: var(--cf-text-faint);
}

/* 右栏检索 */
.kb-search {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.kb-searchbar {
  display: flex;
  gap: 8px;
  padding: 10px 16px 0;
}

.kb-searchbar .el-select {
  width: 220px;
  flex-shrink: 0;
}

.kb-searchbar .el-input {
  flex: 1;
}

.kb-hits {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 14px 16px;
}

.kb-hit {
  padding: 12px 14px;
  margin-bottom: 10px;
  background: var(--cf-panel);
  border: 1px solid var(--cf-border);
  border-radius: 8px;
}

.kb-hit-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--cf-accent);
  margin-bottom: 6px;
}

.kb-hit-content {
  font-size: 13px;
  color: var(--cf-text);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.kb-upload-file {
  display: flex;
  align-items: center;
  gap: 12px;
}

.kb-upload-hint {
  font-size: 12px;
  color: var(--cf-text-faint);
}

.kb-divider {
  margin: 14px 0;
}
</style>
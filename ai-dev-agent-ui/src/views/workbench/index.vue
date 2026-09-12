<template>
  <div class="wb">
    <!-- ===== 顶栏：任务信息与全局操作 ===== -->
    <header class="wb-top">
      <div class="wb-title">
        <h1>开发工作台</h1>
        <template v-if="project">
          <span class="task-chip">
            {{ project.name }} <span class="mono">#{{ project.id }}</span>
          </span>
          <span class="stage-chip" :data-stage="project.stage">{{ stageText }}</span>
        </template>
      </div>
      <div class="wb-top-actions">
        <!-- 历史任务：下拉切换，一键载入旧需求 -->
        <el-select
          v-model="selectedHistoryId"
          class="history-select"
          placeholder="载入历史任务"
          :disabled="generating"
          @change="handleHistoryChange"
        >
          <el-option
            v-for="p in historyProjects"
            :key="p.id"
            :label="`${p.name}（#${p.id}）`"
            :value="p.id"
          >
            <span class="history-opt-name">{{ p.name }}</span>
            <span class="history-opt-stage" :data-stage="p.stage">{{ stageTextOf(p.stage) }}</span>
            <span class="history-opt-time">{{ formatTime(p.createTime) }}</span>
          </el-option>
        </el-select>
        <el-button
          v-if="project"
          text
          size="small"
          :icon="showReq ? Fold : Expand"
          @click="showReq = !showReq"
        >
          {{ showReq ? '收起需求' : '展开需求' }}
        </el-button>
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">新建任务</el-button>
      </div>
    </header>

    <!-- ===== 主区：流程栏 | 需求文档（可折叠） | 设计文档 ===== -->
    <div class="wb-body">
      <!-- 左：流水线垂直导航 + 历史任务 + 任务信息 -->
      <aside class="wb-flow">
        <div class="flow-title">流水线</div>
        <el-steps direction="vertical" :active="stageIndex" finish-status="success" class="flow-steps">
          <el-step title="需求录入" description="录入需求文档" />
          <el-step title="设计生成" description="AI 生成设计文档" />
          <el-step title="人机评审" description="提意见修订 / 人工编辑" />
          <el-step title="设计定稿" description="锁定设计文档" />
          <el-step title="代码生成" description="AI 生成代码 / 应用" />
          <el-step title="使用说明" description="AI 生成使用文档" />
        </el-steps>

        <div v-if="project" class="task-meta">
          <div class="meta-row">
            <span class="meta-label">创建时间</span>
            <span class="meta-value">{{ formatTime(project.createTime) }}</span>
          </div>
          <div class="meta-row">
            <span class="meta-label">设计版本</span>
            <span class="meta-value">
              {{ project.designVersion ? 'v' + project.designVersion : '—' }}
              <em v-if="project.designVersion" class="meta-status">{{ designStatusText }}</em>
            </span>
          </div>
          <div class="meta-row">
            <span class="meta-label">当前阶段</span>
            <span class="meta-value">{{ stageText }}</span>
          </div>
        </div>
        <div v-else class="task-meta task-meta-empty">
          尚未选择任务，点击右上角「新建任务」开始
        </div>
      </aside>

      <!-- 中：需求文档（评审对照用，可折叠） -->
      <section v-show="showReq" class="wb-req">
        <div class="panel-head">
          <span class="panel-title">需求文档</span>
          <span class="panel-tag">INPUT</span>
        </div>
        <div v-if="project" class="panel-scroll req-text">
          {{ project.requirementContent }}
        </div>
        <el-empty v-else description="先新建任务录入需求文档" :image-size="64" />
      </section>

      <!-- 右：设计文档 / 代码生成（主区，页签切换） -->
      <section class="wb-design">
        <div class="panel-head design-head">
          <div class="design-head-left">
            <div class="wb-tabs">
              <button
                class="wb-tab"
                :class="{ 'is-active': worktab === 'design' }"
                :disabled="!project"
                @click="worktab = 'design'"
              >
                设计文档
              </button>
              <button
                class="wb-tab"
                :class="{ 'is-active': worktab === 'code' }"
                :disabled="!codeReady"
                @click="worktab = 'code'"
              >
                代码生成
              </button>
              <button
                class="wb-tab"
                :class="{ 'is-active': worktab === 'manual' }"
                :disabled="!manualReady"
                @click="worktab = 'manual'"
              >
                使用说明
              </button>
            </div>
            <el-tag
              v-if="worktab === 'design' && project?.designVersion"
              size="small"
              class="version-tag mono"
            >
              v{{ project.designVersion }} · {{ designStatusText }}
            </el-tag>
            <el-tag
              v-else-if="worktab === 'code' && codeVersion"
              size="small"
              class="version-tag mono"
            >
              代码 v{{ codeVersion }}
            </el-tag>
            <el-tag
              v-else-if="worktab === 'manual' && project?.manualVersion"
              size="small"
              class="version-tag mono"
            >
              使用说明 v{{ project.manualVersion }}
            </el-tag>
          </div>
          <div class="design-head-right">
            <template v-if="worktab === 'design'">
              <el-radio-group
                v-if="project?.designContent && !generating"
                v-model="editMode"
                size="small"
                :disabled="!designEditable"
              >
                <el-radio-button label="preview">预览</el-radio-button>
                <el-radio-button label="edit">编辑</el-radio-button>
              </el-radio-group>
              <el-button
                v-if="editMode === 'edit' && designEditable && designDirty"
                size="small"
                type="primary"
                :loading="saving"
                @click="handleSaveDesign"
              >
                保存修改
              </el-button>
              <el-tooltip content="导出当前设计文档为 .md 文件" placement="top">
                <el-button
                  v-if="designContent && !generating"
                  size="small"
                  :icon="Download"
                  @click="handleExportDesign"
                />
              </el-tooltip>
            </template>
            <template v-else-if="worktab === 'manual'">
              <el-button
                v-if="!manualGenerating"
                type="primary"
                :icon="MagicStick"
                @click="handleGenerateManual"
              >
                {{ manualContent ? '重新生成' : '生成使用说明' }}
              </el-button>
              <el-button v-else type="danger" plain :icon="VideoPause" @click="handleStopManual">
                停止生成
              </el-button>
              <el-tooltip content="导出当前使用说明为 .md 文件" placement="top">
                <el-button
                  v-if="manualContent && !manualGenerating"
                  size="small"
                  :icon="Download"
                  @click="handleExportManual"
                />
              </el-tooltip>
            </template>
          </div>
        </div>

        <!-- ===== 设计文档页签 ===== -->
        <template v-if="worktab === 'design'">
          <div v-if="generating" class="panel-scroll">
            <div v-if="!designContent" class="generating-tip">
              <span class="design-typing"><span /><span /><span /></span>
              AI 正在{{ opinion ? '按评审意见修订' : '根据需求文档生成' }}设计文档...
            </div>
            <MarkdownView :content="designContent" />
          </div>
          <el-input
            v-else-if="editMode === 'edit' && designEditable"
            v-model="designContent"
            type="textarea"
            :autosize="false"
            class="design-editor"
            resize="none"
            placeholder="尚未生成设计文档"
          />
          <div v-else class="panel-scroll">
            <MarkdownView v-if="designContent" :content="designContent" />
            <el-empty v-else description="点击下方「生成设计文档」，由 AI 根据需求文档生成" :image-size="64" />
          </div>
        </template>

        <!-- ===== 代码生成页签 ===== -->
        <div v-else-if="worktab === 'code'" class="code-panel">
          <!-- 工具栏：目标项目 + 操作 -->
          <div class="code-toolbar">
            <el-select
              v-model="codeTargetProject"
              class="code-target"
              placeholder="选择目标项目"
              :disabled="codeGenerating"
            >
              <el-option
                v-for="p in targetProjects"
                :key="p.name"
                :label="p.name"
                :value="p.name"
              >
                <span class="project-option-name">{{ p.name }}</span>
                <em v-if="p.example" class="project-option-example">接入示例，勿在此工作</em>
                <span class="project-option-path">{{ p.path }}</span>
              </el-option>
            </el-select>
            <span v-if="codeVersion" class="code-regen-tip">已生成 v{{ codeVersion }}</span>
            <div class="code-toolbar-actions">
              <el-tooltip
                v-if="codeFinalized"
                content="代码已定稿，不可再生成"
                placement="top"
              >
                <span>
                  <el-button type="primary" :icon="MagicStick" disabled>
                    {{ codeVersion ? '重新生成' : '生成代码' }}
                  </el-button>
                </span>
              </el-tooltip>
              <el-button
                v-else-if="!codeGenerating"
                type="primary"
                :icon="MagicStick"
                :disabled="!codeTargetProject"
                @click="handleGenerateCode"
              >
                {{ codeVersion ? '重新生成' : '生成代码' }}
              </el-button>
              <el-button v-else type="danger" plain :icon="VideoPause" @click="handleStopCode">
                停止生成
              </el-button>
              <el-button
                v-if="!codeGenerating && hasSandbox && !codeFinalized"
                type="danger"
                plain
                :icon="Delete"
                @click="handleClearSandbox"
              >
                清空重新生成
              </el-button>
              <el-button
                v-if="!codeGenerating && hasSandbox && !codeFinalized"
                type="success"
                plain
                :icon="FolderChecked"
                @click="handleApplyAll"
              >
                应用到项目
              </el-button>
              <el-button
                v-if="!codeGenerating && hasSandbox && !codeFinalized"
                type="success"
                :icon="CircleCheck"
                @click="handleFinalizeCode"
              >
                代码定稿
              </el-button>
              <el-tooltip content="导出所有生成代码为 .zip 压缩包" placement="top">
                <el-button
                  v-if="!codeGenerating && hasSandbox"
                  size="small"
                  :icon="Download"
                  @click="handleExportCode"
                />
              </el-tooltip>
            </div>
          </div>

          <!-- 生成中：AI 叙述 -->
          <div v-if="codeGenerating" class="code-narrate">
            <div class="code-narrate-head">
              <span class="design-typing"><span /><span /><span /></span>
              AI 正在阅读设计文档与项目代码并生成文件...</div>
            <pre class="code-narrate-text mono">{{ codeNarration || '' }}</pre>
          </div>

          <!-- 文件树 + 预览 -->
          <div v-else class="code-browser">
            <div class="code-tree">
              <div class="code-tree-head">
                <span class="panel-title">生成文件</span>
                <span class="code-tree-count mono">{{ sandboxFilesCount }} 个文件</span>
              </div>
              <el-empty
                v-if="!sandboxTree.length"
                description="尚未生成代码文件，点击上方「生成代码」"
                :image-size="60"
              />
              <el-tree
                v-else
                class="code-tree-el"
                :data="sandboxTree"
                node-key="path"
                :expand-on-click-node="false"
                :props="{ label: 'name', children: 'children' }"
                @node-click="handleCodeNodeClick"
                @node-expand="() => {}"
              >
                <template #default="{ data }">
                  <span class="tree-node" :class="{ 'is-dir': data.isDir }">
                    <el-icon :size="14" class="tree-icon">
                      <Folder v-if="data.isDir" />
                      <Document v-else />
                    </el-icon>
                    <span class="tree-label">{{ data.name }}</span>
                    <span v-if="!data.isDir" class="tree-size mono">{{ fmtSize(data.size) }}</span>
                  </span>
                </template>
              </el-tree>
            </div>
            <div class="code-view">
              <div class="code-view-head">
                <span class="code-view-path mono">{{ selectedCodePath || '选择左侧文件查看内容' }}</span>
                <el-button
                  v-if="selectedCodePath"
                  size="small"
                  type="primary"
                  plain
                  :icon="Download"
                  @click="handleApplyOne"
                >
                  应用此文件
                </el-button>
              </div>
              <pre v-if="codePreview !== null" class="code-view-pre mono">{{ codePreview }}</pre>
              <el-empty
                v-else-if="!sandboxTree.length"
                description="生成代码后可在此预览文件内容"
                :image-size="60"
              />
              <el-empty v-else description="点击左侧文件查看预览" :image-size="60" />
            </div>
          </div>
        </div>

        <!-- ===== 使用说明页签（代码定稿后）===== -->
        <div v-else class="manual-panel">
          <!-- 生成中：AI 叙述/内容流式展示 -->
          <div v-if="manualGenerating" class="manual-generating">
            <div class="manual-generating-head">
              <span class="design-typing"><span /><span /><span /></span>
              AI 正在阅读定稿设计文档与项目代码并生成使用说明...</div>
            <MarkdownView class="manual-preview" :content="manualContent" />
          </div>
          <!-- 空态 -->
          <div v-else-if="!manualContent" class="panel-scroll empty-wrap">
            <el-empty
              description="点击右上角「生成使用说明」，AI 将基于定稿设计文档与目标项目代码自动生成"
              :image-size="64"
            />
          </div>
          <!-- 内容预览 -->
          <div v-else class="panel-scroll">
            <MarkdownView :content="manualContent" />
          </div>
        </div>
      </section>
    </div>

    <!-- ===== 底栏：评审操作（人工卡点，仅设计页签） ===== -->
    <footer v-if="worktab === 'design'" class="wb-review">
      <el-input
        v-model="opinion"
        type="textarea"
        :rows="2"
        resize="none"
        class="review-input"
        placeholder="评审意见：告诉 AI 哪里需要修改（留空点击生成则为首次生成）"
        :disabled="!project || generating"
      />
      <div class="review-actions">
        <el-tooltip
          v-if="!designEditable && !!project"
          content="设计文档已定稿，不可再生成或修订"
          placement="top"
        >
          <span>
            <el-button type="primary" :icon="Lightning" disabled>
              {{ designContent ? '按意见修订' : '生成设计文档' }}
            </el-button>
          </span>
        </el-tooltip>
        <el-button
          v-else-if="!generating"
          type="primary"
          :icon="Lightning"
          :disabled="!project"
          @click="handleGenerate"
        >
          {{ designContent ? '按意见修订' : '生成设计文档' }}
        </el-button>
        <el-button v-else type="danger" plain :icon="VideoPause" @click="handleStop">
          停止生成
        </el-button>
        <el-button
          v-if="!generating"
          type="success"
          plain
          :icon="CircleCheck"
          :disabled="!project || !designContent || !designEditable"
          @click="handleApprove"
        >
          定稿设计文档
        </el-button>
      </div>
    </footer>
  </div>

  <!-- 新建任务弹窗 -->
  <el-dialog v-model="createDialogVisible" title="新建 AI 开发任务" width="620px" append-to-body>
    <el-form :model="createForm" label-width="90px">
      <el-form-item label="任务名称" required>
        <el-input v-model="createForm.name" placeholder="例如：优惠券管理模块" maxlength="50" />
      </el-form-item>
      <el-form-item label="需求文档" required>
        <div class="upload-area">
          <div class="upload-toolbar">
            <el-upload
              :show-file-list="false"
              :auto-upload="false"
              accept=".md,.txt,.docx"
              :on-change="handleRequirementFile"
            >
              <el-button size="small" :icon="Upload" :loading="parsing">上传文档</el-button>
            </el-upload>
            <span v-if="requirementFileName" class="upload-name mono">
              {{ requirementFileName }}
            </span>
            <span class="upload-tip">支持 .md / .txt / .docx，或直接粘贴</span>
          </div>
          <el-input
            v-model="createForm.requirementContent"
            type="textarea"
            :rows="10"
            placeholder="粘贴 Markdown 格式的需求文档，或点击上方按钮上传文档，功能点越具体生成的设计越准确"
          />
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="creating" @click="handleCreate">创建并进入工作台</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  CircleCheck,
  Delete,
  Document,
  Download,
  Expand,
  Folder,
  FolderChecked,
  Fold,
  Lightning,
  MagicStick,
  Plus,
  Upload,
  VideoPause
} from '@element-plus/icons-vue'
import { DevApi } from '@/api/dev'
import type { CodeSandboxNodeVO, ProjectRespVO, ProjectSimpleVO } from '@/api/dev'
import { ChatApi } from '@/api/chat'
import type { TargetProjectVO } from '@/api/chat'
import MarkdownView from '@/components/MarkdownView/index.vue'

// mammoth 仅用于浏览器端解析 .docx 为纯文本，按需动态引入减小首屏体积
let mammoth: any = null
const loadMammoth = async () => {
  if (!mammoth) {
    mammoth = await import('mammoth')
  }
  return mammoth
}

const project = ref<ProjectRespVO | null>(null) // 当前任务
const historyProjects = ref<ProjectSimpleVO[]>([]) // 历史任务列表（下拉一键载入）
const selectedHistoryId = ref<number | undefined>(undefined) // 历史任务下拉当前值
const opinion = ref('') // 评审意见
const designContent = ref('') // 设计文档内容（编辑/流式输出共用）
const editMode = ref<'edit' | 'preview'>('preview') // 设计文档展示模式
const generating = ref(false) // 是否正在流式生成
const saving = ref(false) // 是否正在保存人工编辑
const creating = ref(false) // 是否正在创建任务
const createDialogVisible = ref(false) // 新建任务弹窗
const createForm = ref({ name: '', requirementContent: '' }) // 新建任务表单
const parsing = ref(false) // 是否正在解析上传的文档
const requirementFileName = ref('') // 已上传的文档名
const showReq = ref(true) // 需求文档面板显隐（评审对照时可收起，专心看设计）
let abortController: AbortController | null = null // 停止生成的控制器

// ===== 代码生成页签状态 =====
const worktab = ref<'design' | 'code' | 'manual'>('design') // 主区页签：设计文档 / 代码生成 / 使用说明
const targetProjects = ref<TargetProjectVO[]>([]) // 可选目标项目
const codeTargetProject = ref('') // 代码生成目标项目
const codeGenerating = ref(false) // 正在生成代码
const codeNarration = ref('') // AI 叙述（生成过程）
const sandboxTree = ref<CodeSandboxNodeVO[]>([]) // 沙箱文件树
const selectedCodePath = ref('') // 当前预览文件路径
const codePreview = ref<string | null>(null) // 当前预览文件内容
let codeAbortController: AbortController | null = null // 停止代码生成的控制器

// ===== 使用说明页签状态 =====
const manualContent = ref('') // 使用说明内容（流式输出/展示共用）
const manualGenerating = ref(false) // 正在生成使用说明
let manualAbortController: AbortController | null = null // 停止使用说明生成的控制器

/** 阶段 → 步骤条序号（6 步流水线：需求录入→设计生成→评审→设计定稿→代码生成→使用说明）
 * 注意：stage 编号并不与步骤一一对应（“设计定稿”完成时评审也已走完），故用显式映射而非简单 +1 */
const stageIndex = computed(() => {
  if (!project.value) return 0
  const stage = project.value.stage ?? 0
  // stage0需求录入→在第1步；stage2设计定稿→前4步(0~3)完成，当前第5步代码生成；全完成→6
  const map = [1, 2, 4, 4, 5, 6]
  return stage < map.length ? map[stage] : 6
})

/** 阶段状态文案（与后端 DevStageEnum 对应） */
const stageText = computed(() => {
  return stageTextOf(project.value?.stage)
})

/** 阶段编号 → 文案（历史任务列表复用） */
const stageTextOf = (stage?: number | null) => {
  const names = ['需求已录入', '设计评审中', '设计已定稿', '代码生成中', '代码已定稿', '使用说明已生成']
  return names[stage ?? 0] ?? ''
}

/** 设计文档状态文案 */
const designStatusText = computed(() => {
  const status = project.value?.designStatus
  if (status === undefined || status === null) return ''
  return ['AI 生成', '人工修改', '已定稿'][status] ?? ''
})

/** 设计文档是否可编辑：任务存在、已生成、未定稿、未在生成中 */
const designEditable = computed(() => {
  if (!project.value?.designContent) return false
  return project.value.stage !== null && project.value.stage < 2
})

/** 内容是否被人工改动（与后端最新版本比对） */
const designDirty = computed(() => designContent.value !== (project.value?.designContent ?? ''))

/** 代码生成页签是否可用：任务存在且设计已定稿 */
const codeReady = computed(() => {
  return !!project.value && (project.value.stage ?? 0) >= 2
})

/** 使用说明页签是否可用：任务存在且代码已定稿（stage ≥ 4） */
const manualReady = computed(() => {
  return !!project.value && (project.value.stage ?? 0) >= 4
})

/** 最新代码产物版本号（未生成为 undefined） */
const codeVersion = computed(() => project.value?.codeVersion)

/** 代码是否已定稿 */
const codeFinalized = computed(() => {
  return project.value?.stage === 4
})

/** 沙箱是否已有生成文件 */
const hasSandbox = computed(() => sandboxTree.value.length > 0)

/** 沙箱文件总数（递归统计非目录节点） */
const sandboxFilesCount = computed(() => {
  const count = (nodes: CodeSandboxNodeVO[]): number =>
    nodes.reduce((acc, n) => acc + (Boolean(n.isDir) ? count(n.children ?? []) : 1), 0)
  return count(sandboxTree.value)
})

/** 文件大小格式化 */
const fmtSize = (size: number) => {
  if (size > 1024 * 1024) return (size / 1024 / 1024).toFixed(1) + 'M'
  if (size > 1024) return (size / 1024).toFixed(1) + 'K'
  return size + 'B'
}

/** 创建时间展示（仅日期 + 时分） */
const formatTime = (t?: string | Date) => {
  if (!t) return '—'
  const d = new Date(t)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 新建任务 */
const openCreateDialog = () => {
  createForm.value = { name: '', requirementContent: '' }
  requirementFileName.value = ''
  createDialogVisible.value = true
}

/** 读取上传的需求文档：.md/.txt 直接读文本，.docx 用 mammoth 解析为纯文本 */
const handleRequirementFile = async (uploadFile: any) => {
  const rawFile: File = uploadFile.raw
  if (!rawFile) {
    return
  }
  const name = rawFile.name
  const ext = name.substring(name.lastIndexOf('.')).toLowerCase()
  if (!['.md', '.txt', '.docx'].includes(ext)) {
    ElMessage.error('仅支持 .md / .txt / .docx 格式的文档')
    return
  }
  parsing.value = true
  try {
    let text = ''
    if (ext === '.docx') {
      const mammothLib = await loadMammoth()
      const arrayBuffer = await rawFile.arrayBuffer()
      const result = await mammothLib.extractRawText({ arrayBuffer })
      text = result.value
    } else {
      text = await rawFile.text()
    }
    text = text.trim()
    if (!text) {
      ElMessage.error('文档内容为空')
      return
    }
    createForm.value.requirementContent = text
    requirementFileName.value = name
    // 未填任务名时用文档名（去掉扩展名）兜底
    if (!createForm.value.name.trim()) {
      createForm.value.name = name.substring(0, name.lastIndexOf('.'))
    }
    ElMessage.success(`已读取文档「${name}」，可在下方核对或修改内容`)
  } catch (e: any) {
    ElMessage.error(`文档解析失败：${e?.message || e}`)
  } finally {
    parsing.value = false
  }
}

const handleCreate = async () => {
  const { name, requirementContent } = createForm.value
  if (!name.trim() || !requirementContent.trim()) {
    ElMessage.warning('请填写任务名称与需求文档')
    return
  }
  creating.value = true
  try {
    const id = await DevApi.createProject({ name: name.trim(), requirementContent })
    createDialogVisible.value = false
    await loadProject(id)
    await loadHistory()
    ElMessage.success('任务已创建，可开始生成设计文档')
  } finally {
    creating.value = false
  }
}

/** 加载任务详情（含最新设计文档），并同步历史下拉选中值 */
const loadProject = async (id: number) => {
  project.value = await DevApi.getProject(id)
  designContent.value = project.value.designContent ?? ''
  editMode.value = 'preview'
  selectedHistoryId.value = id
  // 代码页签：若任务已绑定目标项目，回填选择器
  if (project.value.targetProject) {
    codeTargetProject.value = project.value.targetProject
  }
  selectedCodePath.value = ''
  codePreview.value = null
  // 已进入代码生成阶段则刷新文件树
  if ((project.value.stage ?? 0) >= 3) {
    loadSandbox(id)
  } else {
    sandboxTree.value = []
  }
  // 同步最新使用说明（未在生成中才覆盖，避免打断流式展示）
  if (!manualGenerating.value) {
    manualContent.value = project.value.manualContent ?? ''
  }
}

/** 历史任务下拉切换：一键载入 */
const handleHistoryChange = (id: number) => {
  if (id) {
    loadProject(id)
  }
}

/** 加载历史任务列表（创建任务 / 完成生成后刷新） */
const loadHistory = async () => {
  try {
    historyProjects.value = await DevApi.listProjects()
    // 未选任务时默认选中下拉第一项对应的 id（仅作为展示，不自动载入，保持空态提示）
    if (project.value) {
      selectedHistoryId.value = project.value.id
    }
  } catch (e: any) {
    console.error('[Workbench][历史任务列表加载失败]', e)
    ElMessage.error(`历史任务加载失败：${e?.message || e}`)
  }
}

/** 生成 / 按意见修订 */
const handleGenerate = async () => {
  if (!project.value || generating.value) return
  const hasOpinion = opinion.value.trim().length > 0
  if (designContent.value && !hasOpinion) {
    ElMessage.warning('请填写评审意见后再修订，或清空内容重新生成')
    return
  }
  generating.value = true
  designContent.value = ''
  abortController = new AbortController()
  try {
    await DevApi.generateDesignStream(
      project.value.id,
      hasOpinion ? opinion.value.trim() : undefined,
      {
        onData: (chunk) => {
          designContent.value += chunk
        },
        onError: (err) => {
          if (err?.name !== 'AbortError') {
            ElMessage.error(`生成失败：${err?.message || err}`)
          }
        }
      },
      abortController
    )
  } finally {
    generating.value = false
    opinion.value = ''
    // 重新加载任务：获取后端保存的新版本号与阶段状态
    if (project.value) {
      await loadProject(project.value.id)
    }
  }
}

/** 停止生成 */
const handleStop = () => {
  abortController?.abort()
}

// ===== 代码生成页签逻辑 =====

/** 加载目标项目列表（默认选中当前任务绑定的项目，否则选第一个） */
const loadTargetProjects = async () => {
  try {
    targetProjects.value = await ChatApi.getTargetProjects()
    if (codeTargetProject.value) return
    if (targetProjects.value.length > 0) {
      codeTargetProject.value = targetProjects.value[0].name
    }
  } catch (e) {
    console.warn('[Workbench][目标项目列表加载失败]', e)
  }
}

/** 刷新沙箱文件树 */
const loadSandbox = async (id: number) => {
  try {
    sandboxTree.value = await DevApi.listSandboxFiles(id)
  } catch (e) {
    console.error('[Workbench][沙箱文件树加载失败]', e)
  }
}

/** 生成 / 重新生成代码 */
const handleGenerateCode = async () => {
  if (!project.value || !codeTargetProject.value || codeGenerating.value) return
  codeGenerating.value = true
  codeNarration.value = ''
  selectedCodePath.value = ''
  codePreview.value = null
  sandboxTree.value = []
  codeAbortController = new AbortController()
  try {
    await DevApi.generateCodeStream(
      project.value.id,
      codeTargetProject.value,
      {
        onData: (chunk) => {
          codeNarration.value += chunk
        },
        onError: (err) => {
          if (err?.name !== 'AbortError') {
            ElMessage.error(`代码生成失败：${err?.message || err}`)
          }
        }
      },
      codeAbortController
    )
  } finally {
    codeGenerating.value = false
    if (project.value) {
      await loadProject(project.value.id)
      await loadSandbox(project.value.id)
    } else {
      sandboxTree.value = []
    }
  }
}

/** 停止代码生成 */
const handleStopCode = () => {
  codeAbortController?.abort()
}

/** 点击文件树节点：目录展开，文件加载预览 */
const handleCodeNodeClick = (data: CodeSandboxNodeVO) => {
  if (Boolean(data.isDir)) return
  if (!project.value) return
  selectedCodePath.value = data.path
  DevApi.getSandboxFile(project.value.id, data.path)
    .then((content) => {
      if (selectedCodePath.value === data.path) {
        codePreview.value = content
      }
    })
    .catch((e) => {
      ElMessage.error(`读取文件失败：${e?.message || e}`)
    })
}

/** 清空沙箱，重新生成 */
const handleClearSandbox = async () => {
  if (!project.value) return
  await ElMessageBox.confirm('清空后本次生成的所有文件将被删除，可重新生成。确认清空？', '清空沙箱', {
    type: 'warning',
    confirmButtonText: '确认清空',
    cancelButtonText: '取消'
  })
  await DevApi.clearSandbox(project.value.id)
  sandboxTree.value = []
  selectedCodePath.value = ''
  codePreview.value = null
  await loadProject(project.value.id)
  ElMessage.success('沙箱已清空，可重新生成代码')
}

/** 应用全部代码到目标项目真实路径 */
const handleApplyAll = async () => {
  if (!project.value) return
  await ElMessageBox.confirm(
    '将把沙箱中生成的全部文件复制到目标项目的真实路径（同名文件会被覆盖）。确认应用？',
    '应用代码到项目',
    { type: 'warning', confirmButtonText: '确认应用', cancelButtonText: '再检查一下' }
  )
  await DevApi.applyAllCode(project.value.id)
  await loadProject(project.value.id)
  ElMessage.success('代码已应用到目标项目')
}

/** 应用当前预览的单个文件 */
const handleApplyOne = async () => {
  if (!project.value || !selectedCodePath.value) return
  await ElMessageBox.confirm(
    `将把文件「${selectedCodePath.value}」复制到目标项目真实路径（同名会覆盖）。确认应用？`,
    '应用单个文件',
    { type: 'warning', confirmButtonText: '确认应用', cancelButtonText: '取消' }
  )
  await DevApi.applyCode(project.value.id, selectedCodePath.value)
  ElMessage.success(`「${selectedCodePath.value}」已应用`)
}

/** 代码定稿（锁定代码生成阶段） */
const handleFinalizeCode = async () => {
  if (!project.value) return
  await ElMessageBox.confirm(
    '定稿后代码生成阶段将锁定，可进入下一步「使用说明」。确认定稿？',
    '代码定稿',
    { type: 'warning', confirmButtonText: '确认定稿', cancelButtonText: '再检查一下' }
  )
  await DevApi.finalizeCode(project.value.id)
  await loadProject(project.value.id)
  ElMessage.success('代码已定稿')
}

/** 导出沙箱代码为 zip 压缩包 */
const handleExportCode = async () => {
  if (!project.value || !hasSandbox.value) return
  const name = project.value.name || 'code'
  const filename = `${name.replace(/[\\/:*?"<>|]/g, '_')}-代码.zip`
  try {
    await DevApi.exportCodeZip(project.value.id, filename)
    ElMessage.success(`已导出「${filename}」`)
  } catch (e: any) {
    ElMessage.error(`导出失败：${e?.message || e}`)
  }
}

/** 导出当前设计文档为 .md 文件（含未保存的编辑内容） */
const handleExportDesign = () => {
  if (!designContent.value) return
  const name = project.value?.name || 'design'
  const version = project.value?.designVersion ?? 'draft'
  // 文件名：任务名-设计文档-v版本号.md（Windows 文件名非法字符替换）
  const fileName = `${name.replace(/[\\/:*?"<>|]/g, '_')}-设计文档-v${version}.md`
  const blob = new Blob([designContent.value], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
  ElMessage.success(`已导出「${fileName}」`)
}

// ===== 使用说明页签逻辑 =====

/** 生成 / 重新生成使用说明（代码定稿后） */
const handleGenerateManual = async () => {
  if (!project.value || manualGenerating.value) return
  manualGenerating.value = true
  manualContent.value = ''
  manualAbortController = new AbortController()
  try {
    await DevApi.generateManualStream(
      project.value.id,
      {
        onData: (chunk) => {
          manualContent.value += chunk
        },
        onError: (err) => {
          if (err?.name !== 'AbortError') {
            ElMessage.error(`使用说明生成失败：${err?.message || err}`)
          }
        }
      },
      manualAbortController
    )
  } finally {
    manualGenerating.value = false
    // 重新加载任务：获取后端落库的新版本号与阶段状态（终态 5）
    if (project.value) {
      await loadProject(project.value.id)
    }
  }
}

/** 停止使用说明生成 */
const handleStopManual = () => {
  manualAbortController?.abort()
}

/** 导出当前使用说明为 .md 文件 */
const handleExportManual = () => {
  if (!manualContent.value) return
  const name = project.value?.name || 'manual'
  const version = project.value?.manualVersion ?? 'draft'
  const fileName = `${name.replace(/[\\/:*?"<>|]/g, '_')}-使用说明-v${version}.md`
  const blob = new Blob([manualContent.value], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
  ElMessage.success(`已导出「${fileName}」`)
}

/** 保存人工编辑 */
const handleSaveDesign = async () => {
  if (!project.value) return
  saving.value = true
  try {
    await DevApi.saveDesign(project.value.id, designContent.value)
    await loadProject(project.value.id)
    ElMessage.success('已保存为人工修改版本')
  } finally {
    saving.value = false
  }
}

/** 定稿 */
const handleApprove = async () => {
  if (!project.value) return
  await ElMessageBox.confirm(
    '定稿后设计文档将锁定，不可再生成或修订，流水线进入下一阶段。确认定稿？',
    '设计文档定稿',
    { type: 'warning', confirmButtonText: '确认定稿', cancelButtonText: '再改改' }
  )
  // 定稿携带当前编辑内容（含未保存的人工修改）
  const content = designDirty.value ? designContent.value : undefined
  await DevApi.approveDesign(project.value.id, content)
  await loadProject(project.value.id)
  ElMessage.success('设计文档已定稿')
}

/** 进入页面时按 URL 携带的 id 加载任务（?id=1），并加载历史任务列表与目标项目 */
onMounted(() => {
  loadHistory()
  loadTargetProjects()
  const id = Number(new URLSearchParams(window.location.search).get('id'))
  if (id) {
    loadProject(id)
  }
})
</script>

<style scoped>
.mono {
  font-family: var(--cf-mono);
}

.wb {
  display: flex;
  flex-direction: column;
  height: 100%;
  animation: cf-rise 0.4s ease both;
}

/* ===== 顶栏 ===== */
.wb-top {
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

.wb-title {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.wb-title h1 {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.01em;
  white-space: nowrap;
}

.task-chip {
  padding: 3px 10px;
  font-size: 12.5px;
  color: var(--cf-text-dim);
  background: var(--cf-panel-2);
  border: 1px solid var(--cf-border);
  border-radius: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 260px;
}

.task-chip .mono {
  color: var(--cf-accent);
}

/* 阶段状态芯片：按阶段着色 */
.stage-chip {
  padding: 3px 10px;
  font-size: 12px;
  font-weight: 500;
  border-radius: 999px;
  white-space: nowrap;
  color: var(--cf-accent);
  background: var(--cf-accent-soft);
  border: 1px solid var(--cf-accent);
}

.stage-chip[data-stage='0'] {
  color: var(--cf-text-dim);
  background: var(--cf-panel-2);
  border-color: var(--cf-border-strong);
}

.stage-chip[data-stage='1'] {
  color: var(--cf-amber);
  background: var(--cf-amber-soft);
  border-color: var(--cf-amber);
}

.stage-chip[data-stage='2'] {
  color: var(--cf-green);
  background: var(--cf-green-soft);
  border-color: var(--cf-green);
}

.wb-top-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}

/* ===== 主区（三栏） ===== */
.wb-body {
  display: flex;
  flex: 1;
  min-height: 0;
  gap: 12px;
  padding: 12px 16px;
}

/* 左：流程栏 */
.wb-flow {
  display: flex;
  flex-direction: column;
  width: 208px;
  flex-shrink: 0;
  padding: 16px 14px;
  background: var(--cf-panel);
  border: 1px solid var(--cf-border);
  border-radius: 10px;
  box-shadow: var(--cf-shadow);
}

.flow-title {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.1em;
  color: var(--cf-text-faint);
  text-transform: uppercase;
  margin-bottom: 14px;
}

.flow-steps {
  flex-shrink: 0;
}

.flow-steps :deep(.el-step__title) {
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
}

.flow-steps :deep(.el-step__description) {
  font-size: 11.5px;
}

.flow-steps :deep(.el-step) {
  flex-basis: 58px !important;
}

/* 历史任务下拉（顶栏） */
.history-select {
  width: 220px;
}

.history-opt-name {
  font-weight: 500;
  margin-right: 8px;
  color: var(--cf-text);
}

.history-opt-stage {
  font-size: 12px;
  margin-right: 8px;
}

.history-opt-stage[data-stage='1'] {
  color: var(--cf-amber);
}

.history-opt-stage[data-stage='2'] {
  color: var(--cf-green);
}

.history-opt-time {
  float: right;
  font-family: var(--cf-mono);
  font-size: 11px;
  color: var(--cf-text-faint);
}

/* 任务信息卡 */
.task-meta {
  margin-top: auto;
  padding-top: 16px;
  border-top: 1px dashed var(--cf-border-strong);
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.task-meta-empty {
  font-size: 12px;
  color: var(--cf-text-faint);
  line-height: 1.7;
}

.meta-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 8px;
  font-size: 12px;
}

.meta-label {
  color: var(--cf-text-faint);
  flex-shrink: 0;
}

.meta-value {
  color: var(--cf-text);
  font-weight: 500;
  text-align: right;
}

.meta-status {
  font-style: normal;
  margin-left: 4px;
  color: var(--cf-accent);
}

/* 通用面板 */
.wb-req,
.wb-design {
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--cf-panel);
  border: 1px solid var(--cf-border);
  border-radius: 10px;
  box-shadow: var(--cf-shadow);
  overflow: hidden;
}

.wb-req {
  width: 320px;
  flex-shrink: 0;
}

.wb-design {
  flex: 1;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  height: 44px;
  padding: 0 16px;
  background: var(--cf-panel-2);
  border-bottom: 1px solid var(--cf-border);
  flex-shrink: 0;
}

.panel-title {
  position: relative;
  padding-left: 10px;
  font-size: 13.5px;
  font-weight: 600;
}

.panel-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 13px;
  border-radius: 2px;
  background: var(--cf-accent);
}

.wb-req .panel-title::before {
  background: var(--cf-amber);
}

.panel-tag {
  font-family: var(--cf-mono);
  font-size: 10px;
  letter-spacing: 0.16em;
  color: var(--cf-text-faint);
}

.panel-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 14px 16px;
}

.req-text {
  font-size: 13px;
  line-height: 1.75;
  color: var(--cf-text-dim);
  white-space: pre-wrap;
  word-break: break-word;
}

/* 设计面板头 */
.design-head-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.design-head-right {
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}

.version-tag {
  background: transparent;
  border-color: var(--cf-border-strong);
  color: var(--cf-accent);
  font-size: 11px;
}

.design-editor {
  flex: 1;
  min-height: 0;
  padding: 0;
}

.design-editor :deep(.el-textarea__inner) {
  height: 100%;
  border: none;
  border-radius: 0;
  box-shadow: none !important;
  background: var(--cf-panel);
}

.generating-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-family: var(--cf-mono);
  font-size: 12px;
  letter-spacing: 0.04em;
  color: var(--cf-accent);
}

/* ===== 主区页签（设计文档 / 代码生成） ===== */
.wb-tabs {
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  background: var(--cf-panel-2);
  border: 1px solid var(--cf-border-strong);
  border-radius: 8px;
}

.wb-tab {
  padding: 4px 12px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--cf-text-dim);
  font-size: 12.5px;
  font-weight: 500;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
  white-space: nowrap;
}

.wb-tab:hover:not(:disabled) {
  color: var(--cf-text);
}

.wb-tab.is-active {
  background: var(--cf-accent);
  color: var(--cf-accent-contrast);
  font-weight: 600;
}

.wb-tab:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* ===== 代码生成面板 ===== */
.code-panel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.code-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--cf-border);
  background: var(--cf-panel);
  flex-shrink: 0;
}

.code-target {
  width: 240px;
}

.project-option-name {
  margin-right: 8px;
  font-weight: 500;
}

.project-option-example {
  margin-right: 8px;
  font-style: normal;
  font-size: 11px;
  color: var(--cf-amber);
  background: var(--cf-amber-soft);
  border-radius: 4px;
  padding: 1px 6px;
}

.project-option-path {
  float: right;
  font-size: 12px;
  color: var(--cf-text-faint);
}

.code-regen-tip {
  font-family: var(--cf-mono);
  font-size: 12px;
  color: var(--cf-green);
}

.code-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-left: auto;
}

/* AI 叙述区 */
.code-narrate {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 14px 16px;
}

.code-narrate-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-family: var(--cf-mono);
  font-size: 12px;
  letter-spacing: 0.04em;
  color: var(--cf-accent);
}

.code-narrate-text {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.7;
  color: var(--cf-text-dim);
  margin: 0;
}

/* 文件树 + 预览 */
.code-browser {
  flex: 1;
  min-height: 0;
  display: flex;
}

.code-tree {
  width: 300px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--cf-border);
  min-height: 0;
}

.code-tree-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 40px;
  padding: 0 14px;
  border-bottom: 1px solid var(--cf-border);
  background: var(--cf-panel-2);
  flex-shrink: 0;
}

.code-tree-count {
  font-size: 11px;
  color: var(--cf-text-faint);
}

.code-tree-el {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 8px 6px;
  background: var(--cf-panel);
}

.tree-node {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: var(--cf-text);
  width: 100%;
  overflow: hidden;
}

.tree-node.is-dir {
  color: var(--cf-text-dim);
  font-weight: 500;
}

.tree-icon {
  color: var(--cf-amber);
  flex-shrink: 0;
}

.tree-node:not(.is-dir) .tree-icon {
  color: var(--cf-text-faint);
}

.tree-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-size {
  margin-left: auto;
  padding-right: 8px;
  font-size: 10.5px;
  color: var(--cf-text-faint);
  flex-shrink: 0;
}

/* 文件预览区 */
.code-view {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--cf-panel);
}

.code-view-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  height: 40px;
  padding: 0 14px;
  border-bottom: 1px solid var(--cf-border);
  background: var(--cf-panel-2);
  flex-shrink: 0;
}

.code-view-path {
  font-size: 12px;
  color: var(--cf-accent);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.code-view-pre {
  flex: 1;
  min-height: 0;
  overflow: auto;
  margin: 0;
  padding: 14px 16px;
  font-size: 12.5px;
  line-height: 1.65;
  color: var(--cf-text-dim);
  white-space: pre;
  background: var(--cf-panel);
}

/* ===== 使用说明面板 ===== */
.manual-panel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.manual-generating {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 14px 16px;
}

.manual-generating-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-family: var(--cf-mono);
  font-size: 12px;
  letter-spacing: 0.04em;
  color: var(--cf-accent);
}

.empty-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ===== 底栏：评审操作 ===== */
.wb-review {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 12px 16px;
  background: var(--cf-panel);
  border-top: 1px solid var(--cf-border);
  flex-shrink: 0;
}

.review-input {
  flex: 1;
}

.review-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* ===== 新建任务弹窗 ===== */
.upload-area {
  width: 100%;
}

.upload-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.upload-name {
  font-size: 12px;
  color: var(--cf-accent);
}

.upload-tip {
  font-size: 12px;
  color: var(--cf-text-faint);
}

/* ===== 生成中的打字动画 ===== */
.design-typing {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.design-typing span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: var(--cf-accent);
  animation: design-typing-blink 1.2s infinite;
}

.design-typing span:nth-child(2) {
  animation-delay: 0.2s;
}

.design-typing span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes design-typing-blink {
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

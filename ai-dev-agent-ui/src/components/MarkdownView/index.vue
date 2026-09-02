<template>
  <div ref="contentRef" class="markdown-view" v-html="renderedMarkdown"></div>
</template>

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import 'highlight.js/styles/vs2015.min.css'

// 定义组件属性
const props = defineProps({
  content: {
    type: String,
    required: true
  }
})

const contentRef = ref<HTMLElement | null>(null)

const md = new MarkdownIt({
  highlight: function (str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        const copyHtml = `<div class="code-copy" data-copy='${str.replace(/'/g, '&#39;')}'>复制</div>`
        return `<pre style="position: relative;">${copyHtml}<code class="hljs">${hljs.highlight(str, { language: lang }).value}</code></pre>`
      } catch {
        // 高亮失败则按普通代码块处理
      }
    }
    return ''
  }
})

/** 渲染 markdown */
const renderedMarkdown = computed(() => {
  return md.render(props.content)
})

/** 点击复制代码 */
const handleClick = (e: MouseEvent) => {
  const target = e.target as HTMLElement
  if (target.classList?.contains('code-copy')) {
    const code = target.dataset.copy || ''
    navigator.clipboard.writeText(code).then(() => {
      ElMessage.success('复制成功!')
    })
  }
}

onMounted(() => {
  contentRef.value?.addEventListener('click', handleClick)
})

onBeforeUnmount(() => {
  contentRef.value?.removeEventListener('click', handleClick)
})
</script>

<style>
.markdown-view {
  max-width: 100%;
  font-size: 14px;
  line-height: 1.7;
  color: var(--md-text);
  text-align: left;
  word-break: break-word;
}

.markdown-view .code-copy {
  position: absolute;
  right: 10px;
  top: 5px;
  color: rgba(255, 255, 255, 0.7);
  cursor: pointer;
  font-size: 12px;
  z-index: 1;
}

.markdown-view .code-copy:hover {
  color: var(--cf-accent);
}

.markdown-view pre {
  position: relative;
  margin: 8px 0;
  background: var(--md-code-bg);
  border: 1px solid var(--md-border);
}

.markdown-view pre code.hljs,
.markdown-view code.hljs {
  display: block;
  width: auto;
  padding-top: 24px;
  border-radius: 6px;
  overflow-x: auto;
  background: transparent;
}

.markdown-view p {
  margin: 0 0 6px;
}

/* 标题 */
.markdown-view h1,
.markdown-view h2,
.markdown-view h3,
.markdown-view h4,
.markdown-view h5,
.markdown-view h6 {
  margin: 16px 0 8px;
  font-weight: 600;
  color: var(--md-heading);
}

.markdown-view h1 {
  font-size: 20px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--md-border);
}

.markdown-view h2 {
  font-size: 18px;
  padding-left: 10px;
  border-left: 3px solid var(--cf-accent);
}

.markdown-view h3 {
  font-size: 16px;
}

.markdown-view h4,
.markdown-view h5,
.markdown-view h6 {
  font-size: 15px;
}

/* 列表 */
.markdown-view ul,
.markdown-view ol {
  padding-left: 24px;
  margin: 0 0 8px;
}

.markdown-view li {
  margin: 2px 0;
}

.markdown-view ul > li {
  list-style-type: disc;
}

.markdown-view ol > li {
  list-style-type: decimal;
}

.markdown-view strong {
  color: var(--md-heading);
}

/* 表格 */
.markdown-view table {
  border-collapse: collapse;
  margin: 8px 0;
  font-size: 13px;
}

.markdown-view table th,
.markdown-view table td {
  border: 1px solid var(--md-border);
  padding: 6px 12px;
}

.markdown-view table th {
  background: var(--md-th-bg);
  color: var(--md-heading);
  font-weight: 600;
}

.markdown-view table tr:hover td {
  background: var(--md-th-bg);
}

.markdown-view blockquote {
  margin: 8px 0;
  padding: 4px 12px;
  border-left: 3px solid var(--md-quote-bar);
  background: var(--cf-accent-soft);
  color: var(--md-quote);
}

/* 行内代码 */
.markdown-view code:not(.hljs) {
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--md-inline-bg);
  color: var(--md-inline);
  font-family: var(--cf-mono);
  font-size: 13px;
}

/* 分割线 */
.markdown-view hr {
  border: none;
  border-top: 1px dashed var(--cf-border-strong);
  margin: 16px 0;
}
</style>

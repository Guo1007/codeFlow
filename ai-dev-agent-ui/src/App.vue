<script setup lang="ts">
import { onMounted, ref, computed, watch } from 'vue'
import { Moon, Sunny } from '@element-plus/icons-vue'
import ChatView from './views/chat/index.vue'
import WorkbenchView from './views/workbench/index.vue'
import LoginView from './views/login/index.vue'
import { authState } from './api/request'

// 页面切换（页面较少，暂不引入 vue-router）
const activeView = ref<'chat' | 'workbench'>('workbench')

// 当前登录用户昵称（localStorage: cf-user），登出时清空
const currentUser = computed(() => localStorage.getItem('cf-user') || '')

function logout() {
  localStorage.removeItem('cf-token')
  localStorage.removeItem('cf-user')
  authState.value = false
}

// ===== 明暗主题切换（持久化到 localStorage）=====
const THEME_KEY = 'cf-theme'
const isDark = ref(false)

const applyTheme = () => {
  document.documentElement.classList.toggle('dark', isDark.value)
}

watch(isDark, () => {
  applyTheme()
  localStorage.setItem(THEME_KEY, isDark.value ? 'dark' : 'light')
})

onMounted(() => {
  isDark.value = localStorage.getItem(THEME_KEY) === 'dark'
  applyTheme()
})
</script>

<template>
  <!-- 未登录显示登录/注册页 -->
  <LoginView v-if="!authState" />

  <!-- 已登录显示主界面 -->
  <div v-else class="shell">
    <!-- 左侧导航栏（双主题恒定深色，作为视觉锚点） -->
    <aside class="rail">
      <div class="rail-brand">
        <span class="rail-logo" />
        <span class="rail-name">codeFlow</span>
      </div>

      <nav class="rail-nav">
        <button
          class="rail-item"
          :class="{ 'is-active': activeView === 'workbench' }"
          @click="activeView = 'workbench'"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" class="rail-icon">
            <rect x="3" y="4" width="18" height="6" rx="1.5" />
            <rect x="3" y="14" width="10" height="6" rx="1.5" />
            <path d="M17 14v6M14 17h6" stroke-linecap="round" />
          </svg>
          <span>开发工作台</span>
        </button>
        <button
          class="rail-item"
          :class="{ 'is-active': activeView === 'chat' }"
          @click="activeView = 'chat'"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" class="rail-icon">
            <path d="M21 12a8 8 0 0 1-8 8H5l-2 2V12a8 8 0 0 1 8-8h2a8 8 0 0 1 8 8Z" stroke-linejoin="round" />
            <path d="M8.5 11h7M8.5 14.5h4" stroke-linecap="round" />
          </svg>
          <span>AI 对话</span>
        </button>
      </nav>

      <div class="rail-bottom">
        <button class="rail-theme" :title="isDark ? '切换为浅色' : '切换为深色'" @click="isDark = !isDark">
          <el-icon :size="15">
            <Sunny v-if="isDark" />
            <Moon v-else />
          </el-icon>
          <span>{{ isDark ? '浅色模式' : '深色模式' }}</span>
        </button>
        <!-- 当前登录用户 + 登出 -->
        <div class="rail-user">
          <span class="rail-user-avatar">{{ (currentUser || 'U').charAt(0) }}</span>
          <span class="rail-user-name" :title="currentUser">{{ currentUser || '未登录' }}</span>
          <button class="rail-logout" title="退出登录" @click="logout">退出</button>
        </div>
        <div class="rail-version">v1.0</div>
      </div>
    </aside>

    <!-- 内容区：KeepAlive 缓存组件实例，切换页面不丢失工作台进度与对话记录 -->
    <main class="content">
      <KeepAlive>
        <WorkbenchView v-if="activeView === 'workbench'" />
        <ChatView v-else />
      </KeepAlive>
    </main>
  </div>
</template>

<style scoped>
.shell {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* ===== 左侧导航栏 ===== */
.rail {
  display: flex;
  flex-direction: column;
  width: 200px;
  flex-shrink: 0;
  background: var(--rail-bg);
  padding: 14px 10px;
}

.rail-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 10px 18px;
}

.rail-logo {
  width: 11px;
  height: 11px;
  background: #3b82f6;
  transform: rotate(45deg);
  border-radius: 2px;
  box-shadow: 0 0 12px rgba(59, 130, 246, 0.6);
}

.rail-name {
  font-family: var(--cf-mono);
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.03em;
  color: #fff;
}

.rail-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.rail-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--rail-text);
  font-size: 13.5px;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.rail-item:hover {
  background: var(--rail-hover);
  color: #d5dce8;
}

.rail-item.is-active {
  background: var(--rail-active-bg);
  color: var(--rail-active);
  font-weight: 600;
}

.rail-icon {
  width: 17px;
  height: 17px;
  flex-shrink: 0;
}

.rail-bottom {
  margin-top: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 0 2px;
}

.rail-theme {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  background: transparent;
  color: var(--rail-text);
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.rail-theme:hover {
  background: var(--rail-hover);
  color: #d5dce8;
}

.rail-user {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
}

.rail-user-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  border-radius: 50%;
  background: rgba(59, 130, 246, 0.25);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}

.rail-user-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--rail-text);
  font-size: 12.5px;
}

.rail-logout {
  padding: 4px 8px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 6px;
  background: transparent;
  color: var(--rail-text);
  font-size: 11.5px;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s ease;
}

.rail-logout:hover {
  background: var(--rail-hover);
  color: #d5dce8;
}

.rail-version {
  text-align: center;
  font-family: var(--cf-mono);
  font-size: 11px;
  color: rgba(139, 151, 171, 0.55);
}

/* ===== 内容区 ===== */
.content {
  flex: 1;
  min-width: 0;
  height: 100vh;
  overflow: hidden;
}
</style>

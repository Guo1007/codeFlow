<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AuthApi from '../../api/auth'
import { authState } from '../../api/request'

// 登录 / 注册切换
const isRegister = ref(false)
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  nickname: ''
})

async function submit() {
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const base = { username: form.username.trim(), password: form.password }
    const resp = isRegister.value
      ? await AuthApi.register({ ...base, nickname: form.nickname.trim() })
      : await AuthApi.login(base)
    localStorage.setItem('cf-token', resp.token)
    localStorage.setItem('cf-user', resp.nickname || resp.username)
    authState.value = true
    ElMessage.success(isRegister.value ? '注册成功' : '登录成功')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-shell">
    <div class="login-card">
      <div class="brand">
        <span class="brand-logo" />
        <span class="brand-name">codeFlow</span>
      </div>
      <p class="brand-tag">AI 辅助开发 Agent</p>

      <div class="tabs">
        <button class="tab" :class="{ active: !isRegister }" @click="isRegister = false">登 录</button>
        <button class="tab" :class="{ active: isRegister }" @click="isRegister = true">注 册</button>
      </div>

      <el-form label-position="top" class="login-form" @submit.prevent="submit">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" size="large" clearable />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            size="large"
            show-password
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-form-item v-if="isRegister">
          <el-input v-model="form.nickname" placeholder="昵称（可选）" size="large" clearable />
        </el-form-item>
        <el-button
          type="primary"
          size="large"
          class="submit-btn"
          :loading="loading"
          @click="submit"
        >
          {{ isRegister ? '注 册' : '登 录' }}
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.login-shell {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: radial-gradient(circle at 50% 30%, var(--cf-accent-soft), transparent 60%),
    var(--cf-bg);
}

.login-card {
  width: 380px;
  padding: 40px 36px 34px;
  background: var(--cf-panel);
  border: 1px solid var(--cf-border);
  border-radius: 12px;
  box-shadow: var(--cf-shadow);
}

.brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.brand-logo {
  width: 12px;
  height: 12px;
  background: #3b82f6;
  transform: rotate(45deg);
  border-radius: 2px;
  box-shadow: 0 0 12px rgba(59, 130, 246, 0.6);
}

.brand-name {
  font-family: var(--cf-mono);
  font-size: 22px;
  font-weight: 600;
  letter-spacing: 0.03em;
  color: var(--cf-text);
}

.brand-tag {
  margin: 6px 0 24px;
  text-align: center;
  font-size: 12.5px;
  color: var(--cf-text-faint);
}

.tabs {
  display: flex;
  gap: 6px;
  margin-bottom: 20px;
  padding: 4px;
  background: var(--cf-panel-2);
  border: 1px solid var(--cf-border);
  border-radius: 8px;
}

.tab {
  flex: 1;
  padding: 8px 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--cf-text-dim);
  font-size: 13.5px;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s ease;
}

.tab.active {
  background: var(--cf-accent);
  color: var(--cf-accent-contrast);
  font-weight: 600;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.submit-btn {
  width: 100%;
  margin-top: 6px;
  letter-spacing: 0.15em;
}
</style>
<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '@/api'

const router = useRouter()
const companyCode = ref('')
const account = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function onSubmit() {
  if (!companyCode.value || !account.value || !password.value) {
    error.value = '请输入客户代码、账号和密码'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const session = await login(companyCode.value.trim(), account.value.trim(), password.value)
    // 短期访问令牌；刷新令牌只存摘要（后端架构第 9 节），本地仅存访问令牌
    localStorage.setItem('access_token', session.accessToken)
    // 缓存当前用户信息，供工作台等页面展示（后端暂无 /me 接口）
    localStorage.setItem('user', JSON.stringify(session.user))
    router.replace('/dashboard')
  } catch (e) {
    error.value = '登录失败，请检查客户代码、账号和密码'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <form class="login-card card" @submit.prevent="onSubmit">
      <h1 class="brand">虫害治理</h1>
      <p class="sub">员工现场作业平台</p>
      <label>
        <span>客户代码</span>
        <input v-model="companyCode" autocomplete="organization" placeholder="如 SWSTEST" />
      </label>
      <label>
        <span>账号</span>
        <input v-model="account" autocomplete="username" placeholder="登录账号" />
      </label>
      <label>
        <span>密码</span>
        <input v-model="password" type="password" autocomplete="current-password" placeholder="密码" />
      </label>
      <p v-if="error" class="error">{{ error }}</p>
      <button type="submit" :disabled="loading">{{ loading ? '登录中…' : '登录' }}</button>
    </form>
  </div>
</template>

<style scoped>
.login {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.login-card {
  width: 100%;
  max-width: 360px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.brand {
  margin: 0;
  color: var(--c-primary);
  font-size: 24px;
}
.sub {
  margin: 0;
  color: var(--c-text-weak);
  font-size: 13px;
}
label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--c-text-weak);
}
input {
  height: 40px;
  padding: 0 12px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius-card);
  font-size: 15px;
}
button {
  height: 44px;
  border: none;
  border-radius: var(--radius-card);
  background: var(--c-primary);
  color: #fff;
  font-size: 16px;
  cursor: pointer;
}
button:disabled {
  opacity: 0.6;
}
.error {
  margin: 0;
  color: var(--c-danger);
  font-size: 13px;
}
</style>

<template>
  <main class="auth-page">
    <form class="auth-form" @submit.prevent="submit">
      <h1>法律责任初步分析 Agent</h1>
      <el-input v-model="username" placeholder="用户名" />
      <el-input v-model="password" placeholder="密码" type="password" show-password />
      <el-button type="primary" native-type="submit" :loading="loading">登录</el-button>
      <RouterLink to="/register">创建账号</RouterLink>
    </form>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const username = ref('')
const password = ref('')
const loading = ref(false)

async function submit() {
  try {
    loading.value = true
    await auth.login(username.value, password.value)
    router.push('/chat')
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="agent-shell">
    <SessionList
      :sessions="chat.sessions"
      :active-id="chat.activeSessionId"
      @create="chat.createSession"
      @open="chat.openSession"
    />
    <section class="chat-pane">
      <header class="topbar">
        <div>
          <strong>{{ chat.activeSession?.title || '法律咨询' }}</strong>
          <span>{{ chat.activeSession?.caseType || '等待案件描述' }}</span>
        </div>
        <div class="top-actions">
          <el-tooltip content="重命名">
            <el-button :icon="Edit" circle @click="renameActive" />
          </el-tooltip>
          <el-tooltip content="删除">
            <el-button :icon="Delete" circle @click="deleteActive" />
          </el-tooltip>
          <el-tooltip content="后台">
            <el-button :icon="Setting" circle @click="$router.push('/admin')" />
          </el-tooltip>
        </div>
      </header>
      <ChatMessages :messages="chat.messages" :streaming-text="streamingText" />
      <ChatInput :loading="chat.loading" @send="send" @upload="upload" />
    </section>
    <AnalysisPanel :analysis="chat.analysis" @report="generateReport" />
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Delete, Edit, Setting } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SessionList from '../components/SessionList.vue'
import ChatMessages from '../components/ChatMessages.vue'
import ChatInput from '../components/ChatInput.vue'
import AnalysisPanel from '../components/AnalysisPanel.vue'
import { api } from '../api'
import { streamChat } from '../api/client'
import { useChatStore } from '../stores/chat'

const router = useRouter()
const chat = useChatStore()
const streamingText = ref('')

onMounted(() => chat.bootstrap())

function send(content: string) {
  if (!chat.activeSessionId || chat.loading) return
  chat.loading = true
  streamingText.value = ''
  chat.messages.push({
    id: Date.now(),
    sessionId: chat.activeSessionId,
    userId: 0,
    role: 'USER',
    content,
    metadata: '{}',
    createdAt: new Date().toISOString()
  })
  streamChat(
    chat.activeSessionId,
    content,
    (chunk) => {
      streamingText.value += chunk
    },
    async () => {
      chat.loading = false
      streamingText.value = ''
      await chat.openSession(chat.activeSessionId)
    }
  )
}

async function upload(file: File) {
  try {
    const result = await api.uploadFile(file)
    ElMessage.success(String(result.summary ?? '文件已上传'))
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function renameActive() {
  if (!chat.activeSessionId) return
  const { value } = await ElMessageBox.prompt('输入新的咨询标题', '重命名', {
    inputValue: chat.activeSession?.title
  })
  await chat.renameSession(chat.activeSessionId, value)
}

async function deleteActive() {
  if (!chat.activeSessionId) return
  await ElMessageBox.confirm('确认删除当前咨询？', '删除咨询', { type: 'warning' })
  await chat.deleteSession(chat.activeSessionId)
}

async function generateReport() {
  if (!chat.activeSessionId) return
  try {
    const report = await api.generateReport(chat.activeSessionId)
    ElMessage.success('报告已生成')
    router.push({ path: '/reports', query: { id: report.id } })
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}
</script>

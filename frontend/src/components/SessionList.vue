<template>
  <aside class="session-list">
    <div class="pane-header">
      <strong>法律咨询</strong>
      <el-tooltip content="新建咨询">
        <el-button :icon="Plus" circle size="small" @click="$emit('create')" />
      </el-tooltip>
    </div>
    <div class="sessions">
      <button
        v-for="session in sessions"
        :key="session.id"
        class="session-item"
        :class="{ active: session.id === activeId }"
        @click="$emit('open', session.id)"
      >
        <span class="session-title">{{ session.title }}</span>
        <small>{{ session.caseType || '待分析' }}</small>
      </button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { Plus } from '@element-plus/icons-vue'
import type { LegalSession } from '../api/types'

defineProps<{
  sessions: LegalSession[]
  activeId: number
}>()

defineEmits<{
  create: []
  open: [id: number]
}>()
</script>

<template>
  <main class="simple-page">
    <header class="page-header">
      <h1>法律分析报告</h1>
      <el-button @click="$router.push('/chat')">返回咨询</el-button>
    </header>
    <el-alert title="报告内容由当前案件结构化分析生成，仅供初步参考。" type="warning" show-icon />
    <section class="report-preview">
      <p>报告已在后端生成。可通过接口下载 Markdown 文件：</p>
      <code>/api/reports/{{ reportId }}/download</code>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const reportId = computed(() => route.query.id ?? '-')
</script>

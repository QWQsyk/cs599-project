<template>
  <main class="simple-page">
    <header class="page-header">
      <h1>管理后台</h1>
      <el-button @click="$router.push('/chat')">返回咨询</el-button>
    </header>
    <el-tabs model-value="articles">
      <el-tab-pane label="法条管理" name="articles">
        <el-table :data="articleRows">
          <el-table-column prop="title" label="标题" />
          <el-table-column prop="status" label="状态" width="120" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="案例管理" name="cases">
        <el-table :data="caseRows">
          <el-table-column prop="title" label="标题" />
          <el-table-column prop="type" label="类型" width="160" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="Prompt 模板" name="prompts">
        <el-table :data="promptRows">
          <el-table-column prop="code" label="编码" width="180" />
          <el-table-column prop="name" label="名称" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </main>
</template>

<script setup lang="ts">
const articleRows = [
  { title: '劳动合同法 第十条', status: '有效' },
  { title: '消费者权益保护法', status: '有效' }
]
const caseRows = [
  { title: '未签劳动合同二倍工资差额争议示例', type: '劳动纠纷' },
  { title: '无借条民间借贷争议示例', type: '民间借贷纠纷' }
]
const promptRows = [
  { code: 'case_classify', name: '案件类型识别' },
  { code: 'fact_extract', name: '法律事实抽取' }
]
</script>

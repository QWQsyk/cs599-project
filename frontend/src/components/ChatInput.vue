<template>
  <div class="chat-input">
    <el-upload :auto-upload="false" :show-file-list="false" :on-change="onFilePicked">
      <el-tooltip content="上传材料">
        <el-button :icon="Paperclip" />
      </el-tooltip>
    </el-upload>
    <el-input
      v-model="text"
      type="textarea"
      :autosize="{ minRows: 1, maxRows: 4 }"
      placeholder="描述你的案件经过或法律问题"
      @keydown.ctrl.enter.prevent="submit"
    />
    <el-tooltip content="发送">
      <el-button type="primary" :icon="Position" :loading="loading" @click="submit" />
    </el-tooltip>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Paperclip, Position } from '@element-plus/icons-vue'
import type { UploadFile } from 'element-plus'

defineProps<{ loading: boolean }>()
const emit = defineEmits<{
  send: [text: string]
  upload: [file: File]
}>()

const text = ref('')

function submit() {
  const value = text.value.trim()
  if (!value) return
  text.value = ''
  emit('send', value)
}

function onFilePicked(uploadFile: UploadFile) {
  if (uploadFile.raw) emit('upload', uploadFile.raw)
}
</script>

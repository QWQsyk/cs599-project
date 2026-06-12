<template>
  <div ref="scrollRef" class="messages">
    <div v-for="message in messages" :key="message.id" class="message-row" :class="message.role.toLowerCase()">
      <div class="message-bubble">
        <span class="role">{{ message.role === 'USER' ? '我' : 'Agent' }}</span>
        <p>{{ message.content }}</p>
      </div>
    </div>
    <div v-if="streamingText" class="message-row assistant">
      <div class="message-bubble streaming">
        <span class="role">Agent</span>
        <p>{{ streamingText }}</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import type { ChatMessage } from '../api/types'

const props = defineProps<{
  messages: ChatMessage[]
  streamingText: string
}>()

const scrollRef = ref<HTMLElement>()

watch(
  () => [props.messages.length, props.streamingText],
  async () => {
    await nextTick()
    if (scrollRef.value) scrollRef.value.scrollTop = scrollRef.value.scrollHeight
  },
  { deep: true }
)
</script>
